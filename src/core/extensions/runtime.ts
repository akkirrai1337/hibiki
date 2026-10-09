// Orchestrator for Hibiki's scripted extensions (hibiki-sources/extensions/*.js). `list()` answers
// from the manifests read by the last reload() and stays synchronous; every call that actually runs a
// script (search/latest/getById/...) goes to the platform's extension host, which runs it off the
// UI thread - extension scripts call a *synchronous* fetch() (matching the Rhino runtime they were
// written for), which blocks whichever thread runs it. The script's host calls (fetch, challenge,
// browserFetch) come back to `bridge` below.
import type {
  AnimeTitle,
  PlaybackGroup,
  PlayerLink,
  PlayerLinkPreference,
  PlayerLinkType,
  SearchFilterCatalog,
  SearchRequest,
  SourceAccount,
  SourceComment,
  SourceInfo,
  SourceLibraryEntry,
  SourceReview,
} from "@shared/types";
import type { ExtensionMethod } from "./methods";
import type { BridgeHandler } from "../../platform/types";
import { getPlatform } from "../platform";
import { performNetFetch, performNetFetchAll, type NetFetchResult } from "./netFetch";
import { challengeUrlInError, isCloudflareChallenge } from "./cloudflare";
import { cloudflareCheckError } from "@shared/cloudflare";
import { logger } from "../logger";
import { ExtensionStorage } from "./extensionStorage";
import { withTitleFacts } from "./titleFacts";
import { chronologicalGroups } from "./episodeOrder";

const WORKER_TIMEOUT_MS = 30_000;
const FILTER_TYPES = ["select", "multi", "tristate", "text", "range"];
const RESOLVE_TOTAL_TIMEOUT_MS = 45_000;
// How long a resolver attempt may run alone before the next candidate is started beside it.
const RESOLVE_STAGGER_MS = 1_200;

function playerUrlLabel(raw: string): string {
  try {
    const url = new URL(raw);
    const tail = url.pathname.split("/").filter(Boolean).slice(-2).map((part) =>
      part.length > 24 ? `${part.slice(0, 8)}…` : part,
    ).join("/");
    return `${url.host}/${tail || "…"}${url.search ? "?…" : ""}`;
  } catch {
    return "<invalid-url>";
  }
}

const RESOLVER_STREAM_TYPE_TO_PLAYER_LINK_TYPE: Record<string, PlayerLinkType | undefined> = {
  HLS: "DIRECT_HLS",
  MP4: "DIRECT_MP4",
  DASH: "DIRECT_DASH",
};

interface Manifest {
  id: string;
  name: string;
  version: string;
  iconUrl?: string;
  lang?: string;
  isNsfw?: boolean;
  capabilities?: string[];
  resolverDependencies?: string[];
  settings?: SourceInfo["settings"];
  website?: string;
}

export interface InstalledResolverRequirement {
  sourceId: string;
  originUrl: string;
  resolverIds: string[];
}

interface LoadedExtension {
  manifest: Manifest;
}

// Player resolvers (extensions/extractors/*.js in hibiki-sources) are installed alongside a
// source via its manifest's resolverDependencies (see marketplace.ts), not by the user directly -
// they're not sources themselves (no search/catalog capability) and Android hides them from its
// source picker for the same reason, so they live in their own subdirectory rather than
// extensionsDir, which keeps them out of list() for free instead of needing a type-based filter.
interface ResolverManifest {
  id: string;
  version: string;
  hosts: string[];
  // Most resolvers are plain HTTP (Provider.resolve(linkJson), runs on this app's existing
  // sandboxed-worker infra - see execute.ts). A "BROWSER" runtime resolver instead exposes
  // Provider.browserScript(linkJson), which runs inside a real browser engine (see
  // BrowserPort.resolve) via runBrowserResolver() below.
  runtime?: "NODE" | "BROWSER";
}

interface ResolverHealth {
  consecutiveFailures: number;
  lastFailureAt: number;
}

// A resolver that failed once - a dropped connection, an ISP hiccup at app start - must not stay
// demoted for the whole session waiting for a success it is never ordered first enough to get.
// Failures older than this stop counting against it.
const RESOLVER_FAILURE_DECAY_MS = 10 * 60_000;

// These hosts only expose their own iframe UI in practice. CVH and Sibnet both intermittently
// report "video unavailable" there, which leaves the application without its player controls and
// gives the viewer no reliable way to switch away. Keep the retirement centralized so old local
// resolver files and a source update declaring either dependency cannot re-enable them.
const RETIRED_RESOLVER_IDS = new Set(["cvh", "sibnet"]);
const RETIRED_PLAYER_NAMES = new Set(["cvh", "sibnet"]);
const RETIRED_PLAYER_HOSTS = ["yummyani.me", "sibnet.ru"];

function replaceMap<K, V>(target: Map<K, V>, source: Map<K, V>): void {
  target.clear();
  for (const [key, value] of source) target.set(key, value);
}

export function isRetiredResolver(id: string): boolean {
  return RETIRED_RESOLVER_IDS.has(id.toLowerCase());
}

interface BrowserResolverPayload {
  script: string;
  /** Optional page to host the embed frame under, supplied by the resolver when required. */
  parentUrl?: string;
}

const BROWSER_RESOLVER_PAYLOAD_PREFIX = "hibiki-browser-resolver:v1:";

function parseBrowserResolverPayload(value: string): BrowserResolverPayload {
  if (!value.startsWith(BROWSER_RESOLVER_PAYLOAD_PREFIX)) return { script: value };
  try {
    const parsed = JSON.parse(value.slice(BROWSER_RESOLVER_PAYLOAD_PREFIX.length)) as Partial<BrowserResolverPayload>;
    if (typeof parsed.script !== "string" || !parsed.script) throw new Error("missing script");
    return {
      script: parsed.script,
      ...(typeof parsed.parentUrl === "string" ? { parentUrl: parsed.parentUrl } : {}),
    };
  } catch (error) {
    throw new Error(`Invalid browser resolver payload: ${String(error)}`);
  }
}

function isRetiredPlayerLink(link: PlayerLink): boolean {
  if (link.playerName && RETIRED_PLAYER_NAMES.has(link.playerName.trim().toLowerCase())) return true;
  try {
    const host = new URL(link.url).hostname.toLowerCase();
    return RETIRED_PLAYER_HOSTS.some((retiredHost) => host === retiredHost || host.endsWith(`.${retiredHost}`));
  } catch {
    return false;
  }
}

/** Ids of the platform's native sources (ApkSourcesPort) - never a JS source's. */
function isChallengeAnswer(result: unknown): boolean {
  const answer = result as Partial<NetFetchResult> | null;
  return !!answer && typeof answer.status === "number" && isCloudflareChallenge(answer.status, answer.headers ?? {}, answer.body ?? "");
}

/** The pages a bridge answer stopped at a Cloudflare check on. */
function challengedUrls(kind: Parameters<BridgeHandler>[0], payload: Record<string, unknown>, result: unknown): string[] {
  if (kind === "netFetch") return isChallengeAnswer(result) ? [payload.url as string] : [];
  if (kind === "browserFetch") return isChallengeAnswer(result) ? [payload.pageUrl as string] : [];
  if (kind === "netFetchAll" && Array.isArray(result)) {
    const requests = (payload.requests as Array<{ url: string }>) ?? [];
    return result.flatMap((answer, index) => (isChallengeAnswer(answer) && requests[index] ? [requests[index].url] : []));
  }
  return [];
}

const isEmptyResult = (result: unknown) => result === null || result === undefined || (Array.isArray(result) && result.length === 0);

export function isApkSource(sourceId: string): boolean {
  return sourceId.startsWith("apk:");
}

export class ExtensionRuntime {
  private readonly extensions = new Map<string, LoadedExtension>();
  /** The platform's own sources (APK sources on Android), as of the last reload. */
  private apkSources: Array<SourceInfo & { packageName: string }> = [];
  private readonly resolvers = new Map<string, ResolverManifest>();
  private readonly resolverHealth = new Map<string, ResolverHealth>();
  private readonly inFlightReads = new Map<string, Promise<unknown>>();
  private readonly cancellations = new Map<string, () => void>();
  /** Each installed source's `<id>.origin` (the repository it came from), read at reload(). */
  private readonly origins = new Map<string, string>();

  // Beside the extensions rather than inside them (platform.paths.extensionStorage): uninstalling a
  // source should be able to take its stored token with it without the store having to survive a
  // directory being rewritten.
  private readonly storage = new ExtensionStorage(() => getPlatform().paths.extensionStorage);

  constructor(private readonly extensionsDir: string) {}

  private get resolversDir(): string {
    return getPlatform().files.join(this.extensionsDir, "resolvers");
  }

  /** Called when a source is removed - a token for a source that is no longer installed is only a
   * secret waiting to leak. */
  forgetStorage(sourceId: string): Promise<void> {
    return this.storage.clear(sourceId);
  }

  /**
   * The values behind a source's declared settings rows.
   *
   * Only declared keys, never the whole store: a source's session token lives in the same place,
   * and the settings screen has no business reading it - nor does anything else in the renderer.
   */
  async readSettings(sourceId: string): Promise<Record<string, string>> {
    // An APK source keeps its own preferences; the platform reads them out of it.
    if (isApkSource(sourceId)) return (await getPlatform().apkSources?.call(sourceId, "readSettings", [])) as Record<string, string>;
    const declared = new Set(this.settingKeysOf(sourceId));
    const stored = await this.storage.read(sourceId);
    return Object.fromEntries(Object.entries(stored).filter(([key]) => declared.has(key)));
  }

  async writeSetting(sourceId: string, key: string, value: string | null): Promise<void> {
    if (isApkSource(sourceId)) {
      await getPlatform().apkSources?.call(sourceId, "writeSetting", [key, value]);
      return;
    }
    if (!this.settingKeysOf(sourceId).includes(key)) {
      throw new Error(`Source "${sourceId}" declares no setting named "${key}"`);
    }
    await this.storage.apply(sourceId, { [key]: value });
  }

  /** ACCOUNT rows are excluded: they stand for the sign-in block, not for a value. */
  private settingKeysOf(sourceId: string): string[] {
    const manifest = this.extensions.get(sourceId)?.manifest;
    return (manifest?.settings ?? []).filter((setting) => setting.type !== "ACCOUNT").map((setting) => setting.key);
  }

  async reload(): Promise<void> {
    const { files } = getPlatform();
    // Read into fresh maps and swap them in at the end, so a list() or a call arriving while the
    // files are being read still sees the previous, complete set rather than a half-filled one.
    const extensions = new Map<string, LoadedExtension>();
    const origins = new Map<string, string>();
    for (const file of await files.list(this.extensionsDir)) {
      if (!file.endsWith(".manifest.json")) continue;
      const manifestPath = files.join(this.extensionsDir, file);
      const manifest = JSON.parse(await files.readText(manifestPath)) as Manifest;
      const scriptPath = manifestPath.replace(/\.manifest\.json$/, ".js");
      if (!(await files.exists(scriptPath))) continue;
      extensions.set(manifest.id, { manifest });
      const originPath = this.originPath(manifest.id);
      if (await files.exists(originPath)) origins.set(manifest.id, (await files.readText(originPath)).trim());
    }

    await this.removeRetiredResolverFiles();
    const resolvers = new Map<string, ResolverManifest>();
    for (const file of await files.list(this.resolversDir)) {
      if (!file.endsWith(".manifest.json")) continue;
      const manifestPath = files.join(this.resolversDir, file);
      const manifest = JSON.parse(await files.readText(manifestPath)) as ResolverManifest;
      const scriptPath = manifestPath.replace(/\.manifest\.json$/, ".js");
      if (!(await files.exists(scriptPath))) continue;
      if (isRetiredResolver(manifest.id)) continue;
      resolvers.set(manifest.id, {
        id: manifest.id,
        version: manifest.version ?? "0.0.0",
        hosts: manifest.hosts ?? [],
        runtime: manifest.runtime,
      });
    }

    // New source files/settings must not adopt a request that started against the previous loaded
    // extension. The old work may still finish for its original caller, but no new call shares it.
    const apkSources = await getPlatform().apkSources?.list().catch((error: unknown) => {
      logger.warn("ext", `APK sources unavailable: ${error instanceof Error ? error.message : String(error)}`);
      return [];
    }) ?? [];

    this.inFlightReads.clear();
    replaceMap(this.extensions, extensions);
    replaceMap(this.origins, origins);
    replaceMap(this.resolvers, resolvers);
    this.apkSources = apkSources;
  }

  list(): SourceInfo[] {
    return [...this.jsSources(), ...this.apkSources];
  }

  private jsSources(): SourceInfo[] {
    return [...this.extensions.values()].map(({ manifest }) => ({
      id: manifest.id,
      name: manifest.name,
      version: manifest.version,
      iconUrl: manifest.iconUrl ?? null,
      lang: manifest.lang ?? null,
      isNsfw: manifest.isNsfw === true,
      capabilities: (manifest.capabilities ?? []) as SourceInfo["capabilities"],
      runtime: "NODE",
      settings: manifest.settings ?? [],
      website: manifest.website ?? null,
    }));
  }

  // Answers one challenge()/browserFetch()/fetch() request from a running extension script (see
  // syncHostBridge.ts) - the real, GUI-capable work runs here, outside the worker, while the script
  // waits for the answer.
  private readonly bridge: BridgeHandler = (kind, payload) => {
    if (kind === "challenge") {
      return getPlatform().browser.challenge(
        payload.url as string,
        (payload.cookieNames as string[]) ?? [],
        Boolean(payload.forceRefresh),
      );
    } else if (kind === "netFetchAll") {
      return performNetFetchAll(
        (payload.requests as Array<{ url: string; options?: Record<string, unknown> }>) ?? [],
      );
    } else if (kind === "netFetch") {
      return performNetFetch(
        payload.url as string,
        (payload.options as { method?: string; headers?: Record<string, string>; body?: string } | undefined) ?? {},
      );
    }
    return getPlatform().browser.browserFetch(
      payload.pageUrl as string,
      payload.targetUrl as string,
      payload.options as { method?: string; headers?: Record<string, string>; body?: string } | undefined,
    );
  };

  /** Starts worker bundle parsing ahead of the renderer's first source queries. */
  warmWorkers(): void {
    getPlatform().extensionHost.warm();
  }

  private run<T>(
    method: ExtensionMethod,
    sourceId: string,
    args: unknown[],
    options?: { extensionsDir?: string; requestId?: string; timeoutMs?: number },
  ): Promise<T> {
    if (isApkSource(sourceId)) return this.runApk<T>(method, sourceId, args);
    const extensionsDir = options?.extensionsDir ?? this.extensionsDir;
    const timeoutMs = options?.timeoutMs ?? WORKER_TIMEOUT_MS;
    if (!options?.extensionsDir && !this.extensions.has(sourceId)) return Promise.reject(new Error(`Unknown source: ${sourceId}`));

    const startedAt = Date.now();

    // Every request this call makes is watched for Cloudflare's check. A call that then fails, or
    // comes back with nothing, did so because of it - the source only ever saw the check's HTML - so
    // it fails with an error that names the check (shared/cloudflare.ts), and the screen showing the
    // error offers to pass it. A call that got what it needed despite one blocked request (one of
    // several mirrors, say) is left alone.
    const challenged: string[] = [];
    const bridge: BridgeHandler = (kind, payload) =>
      this.bridge(kind, payload).then((result) => {
        challenged.push(...challengedUrls(kind, payload, result));
        return result;
      });
    const challengeError = () => (challenged.length > 0 ? cloudflareCheckError(sourceId, challenged[0]) : null);

    // Registered before anything is awaited: a search the renderer supersedes a keystroke later
    // must be cancellable from the moment it exists. A cancel that lands before the worker is
    // started reaches the host as an already-aborted signal, which it refuses.
    const controller = new AbortController();
    const cancel = () => controller.abort();
    if (options?.requestId) this.cancellations.set(options.requestId, cancel);
    const clearCancellation = () => {
      if (options?.requestId && this.cancellations.get(options.requestId) === cancel) {
        this.cancellations.delete(options.requestId);
      }
    };

    // Read once per call, at dispatch: the script sees a consistent snapshot for its whole run,
    // and a call that writes has its writes applied when it comes back.
    return this.storage
      .read(sourceId)
      .then((storage) =>
        getPlatform().extensionHost.run({ extensionsDir, sourceId, method, args, storage }, bridge, { timeoutMs, signal: controller.signal }),
      )
      .then(
        async (message) => {
          clearCancellation();
          // Before resolve/reject either way: a call that stored a token and then failed still
          // stored the token.
          if (message.storageWrites) await this.storage.apply(sourceId, message.storageWrites);
          if (message.ok) {
            logger.debug("ext", `${sourceId}.${method}() ok in ${Date.now() - startedAt}ms`);
            const blocked = isEmptyResult(message.result) ? challengeError() : null;
            if (blocked) throw blocked;
            return message.result as T;
          }
          logger.warn("ext", `${sourceId}.${method}() failed in ${Date.now() - startedAt}ms: ${message.error}`);
          throw challengeError() ?? new Error(message.error);
        },
        (error: unknown) => {
          clearCancellation();
          const failure = error instanceof Error ? error : new Error(String(error));
          if (failure.name === "AbortError") throw new Error(`Source "${sourceId}" cancelled calling ${method}()`);
          if (failure.name === "TimeoutError") {
            logger.error("ext", `${sourceId}.${method}() timed out after ${timeoutMs}ms`);
            throw new Error(`Source "${sourceId}" timed out calling ${method}()`);
          }
          logger.error("ext", `${sourceId}.${method}() crashed in ${Date.now() - startedAt}ms: ${failure.message}`);
          throw challengeError() ?? failure;
        },
      );
  }

  // An APK source answers the same call natively (see ApkSourcesPort): no worker, no storage
  // snapshot - the extension keeps its own preferences - and the deadline is the native side's.
  private async runApk<T>(method: ExtensionMethod, sourceId: string, args: unknown[]): Promise<T> {
    const port = getPlatform().apkSources;
    if (!port || !this.apkSources.some((source) => source.id === sourceId)) throw new Error(`Unknown source: ${sourceId}`);
    const startedAt = Date.now();
    try {
      const raw = await port.call(sourceId, method, args);
      logger.debug("ext", `${sourceId}.${method}() ok in ${Date.now() - startedAt}ms`);
      // Aniyomi extensions leave year, type, episode counts and the like as text in the name and
      // description; they are read back into the title's fields here (see titleFacts.ts).
      if (method === "getById") return withTitleFacts(raw as AnimeTitle) as T;
      if (method === "search" || method === "latest") return (raw as AnimeTitle[]).map(withTitleFacts) as T;
      return raw as T;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logger.warn("ext", `${sourceId}.${method}() failed in ${Date.now() - startedAt}ms: ${message}`);
      // Aniyomi's interceptor tries the check in a hidden page first and gives up on one that needs a person.
      const challengeUrl = challengeUrlInError(message);
      throw challengeUrl ? cloudflareCheckError(sourceId, challengeUrl) : new Error(message);
    }
  }

  cancelRequest(requestId: string): boolean {
    const cancel = this.cancellations.get(requestId);
    if (!cancel) return false;
    cancel();
    return true;
  }

  /** Tears the worker pool down - called on app quit, so idle threads don't hold the process open. */
  dispose(): void {
    getPlatform().extensionHost.dispose();
  }

  /**
   * Shares only an identical read that is currently running. This is deliberately not a result
   * cache: source data and signed URLs retain their existing freshness rules, while two screens
   * arriving in the same tick no longer spawn duplicate workers and duplicate network requests.
   */
  private shareRead<T>(method: string, sourceId: string, args: unknown[], read: () => Promise<T>): Promise<T> {
    const key = JSON.stringify([method, sourceId, ...args]);
    const existing = this.inFlightReads.get(key);
    if (existing) return existing as Promise<T>;

    const pending = read();
    this.inFlightReads.set(key, pending);
    const clear = () => {
      if (this.inFlightReads.get(key) === pending) this.inFlightReads.delete(key);
    };
    void pending.then(clear, clear);
    return pending;
  }

  search(sourceId: string, request: SearchRequest, requestId?: string): Promise<AnimeTitle[]> {
    // A renderer search carries a cancellation id, so it must own its worker rather than sharing
    // one whose other caller might still need it. Background/catalog reads remain coalesced.
    if (requestId) return this.run("search", sourceId, [request], { requestId });
    return this.shareRead("search", sourceId, [request], () => this.run("search", sourceId, [request]));
  }

  latest(sourceId: string, limit: number): Promise<AnimeTitle[]> {
    return this.shareRead("latest", sourceId, [limit], () => this.run("latest", sourceId, [limit]));
  }

  /**
   * Account and the things it unlocks.
   *
   * All of them are optional on the script side and gated by the source's declared capabilities -
   * see ExtensionMethod in execute.ts. Credentials pass straight through to the script and are
   * never written anywhere by the host; whatever the script needs to prove itself again later goes
   * in its own store instead.
   */
  login(sourceId: string, credentials: { login: string; password: string }): Promise<SourceAccount> {
    return this.run("login", sourceId, [credentials]);
  }

  /** The ACCOUNT row's own `webLoginUrl`/`webLoginSuccessCookie` - a real sign-in window instead
   * of a login+password pair (see BrowserPort.login for what that actually opens). */
  async loginWeb(sourceId: string): Promise<SourceAccount> {
    const manifest = this.extensions.get(sourceId)?.manifest;
    const row = (manifest?.settings ?? []).find((setting) => setting.type === "ACCOUNT");
    if (!row?.webLoginUrl || !row.webLoginSuccessCookie) {
      throw new Error(`Source "${sourceId}" does not declare a web login`);
    }
    const cookies = await getPlatform().browser.login(sourceId, row.webLoginUrl, row.webLoginSuccessCookie);
    return this.run("loginWeb", sourceId, [cookies]);
  }

  logout(sourceId: string): Promise<void> {
    return this.run("logout", sourceId, []);
  }

  getAccount(sourceId: string): Promise<SourceAccount | null> {
    return this.run("getAccount", sourceId, []);
  }

  listComments(sourceId: string, request: { animeId: string; parentId?: string | null; offset?: number }): Promise<SourceComment[]> {
    return this.run("listComments", sourceId, [request]);
  }

  postComment(sourceId: string, request: { animeId: string; text: string; parentId?: string | null }): Promise<SourceComment> {
    return this.run("postComment", sourceId, [request]);
  }

  /** 1 to like, -1 to dislike, 0 to take a vote back. */
  voteComment(sourceId: string, request: { commentId: string; vote: number }): Promise<boolean> {
    return this.run("voteComment", sourceId, [request]);
  }

  listReviews(sourceId: string, request: { animeId: string; offset?: number }): Promise<SourceReview[]> {
    return this.run("listReviews", sourceId, [request]);
  }

  postReview(sourceId: string, request: { animeId: string; text: string; rating?: number | null }): Promise<SourceReview> {
    return this.run("postReview", sourceId, [request]);
  }

  /**
   * Whether this source both can sync a library and has been told to.
   *
   * Asked before every push, and cheap on purpose - it reads the manifest and the source's own
   * store, no script and no network. A source that declares nothing, or whose switch is off, must
   * cost nothing at all on a library change.
   */
  isLibrarySyncEnabled(sourceId: string): Promise<boolean> {
    return this.isSwitchOn(sourceId, "LIBRARY_SYNC");
  }

  /** Whether this source reports watching to its account. Asked on every progress save, so it
   * reads the manifest and the source's store and nothing else. */
  isActivitySyncEnabled(sourceId: string): Promise<boolean> {
    return this.isSwitchOn(sourceId, "ACTIVITY_SYNC");
  }

  /** A capability the source declares, plus the switch of the same type being on. */
  private async isSwitchOn(sourceId: string, kind: "LIBRARY_SYNC" | "ACTIVITY_SYNC"): Promise<boolean> {
    const manifest = this.extensions.get(sourceId)?.manifest;
    if (!manifest || !(manifest.capabilities ?? []).includes(kind)) return false;
    const row = (manifest.settings ?? []).find((setting) => setting.type === kind);
    if (!row) return false;
    const stored = (await this.storage.read(sourceId))[row.key];
    return stored === undefined ? row.default === true : stored === "true";
  }

  /** Everything in the signed-in account's own lists - what the first-run reconciliation needs in
   * order to say "3 there, 50 here" rather than asking blind. */
  listLibrary(sourceId: string): Promise<SourceLibraryEntry[]> {
    return this.run("listLibrary", sourceId, []);
  }

  /**
   * Reports one episode's watching: the seconds actually played, not the position reached.
   *
   * Answers false when there was nothing new to report, which is the normal case between two
   * saves that happened while paused.
   */
  reportPlayback(
    sourceId: string,
    request: { videoId: string; positionSeconds: number; durationSeconds: number; watchedSeconds: number[] },
  ): Promise<boolean> {
    return this.run("reportPlayback", sourceId, [request]);
  }

  /** Marks the account online for today - what its day streak counts. */
  pingOnline(sourceId: string): Promise<boolean> {
    return this.run("pingOnline", sourceId, []);
  }

  /** Pushes one library row's status (and rating, when there is one) to the account. */
  syncLibraryEntry(
    sourceId: string,
    request: { animeId: string; category: string | null; rating?: number | null },
  ): Promise<void> {
    return this.run("syncLibraryEntry", sourceId, [request]);
  }

  getById(sourceId: string, id: string): Promise<AnimeTitle> {
    return this.shareRead("getById", sourceId, [id], () => this.run("getById", sourceId, [id]));
  }

  getPlaybackGroups(sourceId: string, titleId: string): Promise<PlaybackGroup[]> {
    return this.shareRead("getPlaybackGroups", sourceId, [titleId], async () => chronologicalGroups(await this.run<PlaybackGroup[]>("getPlaybackGroups", sourceId, [titleId])));
  }

  getPlayerLinks(sourceId: string, titleId: string, groupId: string, episodeId: string, preference?: PlayerLinkPreference): Promise<PlayerLink[]> {
    return this.shareRead("getPlayerLinks", sourceId, [titleId, groupId, episodeId, preference ?? null], async () => {
      const sourceLinks = await this.run<PlayerLink[]>("getPlayerLinks", sourceId, [titleId, groupId, episodeId]);
      const links = sourceLinks.filter((link) => !isRetiredPlayerLink(link));
      if (links.length !== sourceLinks.length) {
        logger.info("resolve", `removed ${sourceLinks.length - links.length} retired CVH/Sibnet link(s) before playback`);
      }
      const preferredIndex = this.preferredLinkIndex(links, preference);
      // A source-provided direct link needs no resolver at all. Let the renderer adopt the saved
      // choice from the returned list instead of resolving an unrelated EMBED first.
      if (preferredIndex >= 0 && links[preferredIndex].type !== "EMBED") return links;
      return this.withoutUnplayableEmbeds(await this.resolveEmbedLinks(links, preferredIndex));
    });
  }

  async resolvePlayerLink(link: PlayerLink): Promise<PlayerLink[]> {
    if (isRetiredPlayerLink(link)) return [];
    return this.withoutUnplayableEmbeds(await this.resolveEmbedLinks([link]));
  }

  // The app plays only in its own player: a third-party page is never shown in an iframe. An EMBED
  // link no installed resolver claims can never become a stream, so it is not an option at all
  // rather than a link that leads to a page. One a resolver does claim stays - it is resolved when
  // picked, and reported as failed if it can't be.
  private withoutUnplayableEmbeds(links: PlayerLink[]): PlayerLink[] {
    return links.filter((link) => link.type !== "EMBED" || this.findResolverForUrl(link.url) !== null);
  }

  private findResolverForUrl(url: string): ResolverManifest | null {
    let host: string;
    try {
      host = new URL(url).hostname.toLowerCase();
    } catch {
      return null;
    }
    for (const manifest of this.resolvers.values()) {
      if (manifest.hosts.some((h) => host === h || host.endsWith(`.${h}`))) return manifest;
    }
    return null;
  }

  private preferredLinkIndex(links: PlayerLink[], preference?: PlayerLinkPreference): number {
    if (!preference?.translation && !preference?.playerName) return -1;
    // A persisted pick can outlive the player it names. Treat a retired player as no preference
    // at all rather than letting a translation-only partial match jump to a different mirror.
    if (preference.playerName && RETIRED_PLAYER_NAMES.has(preference.playerName.trim().toLowerCase())) return -1;
    let bestIndex = -1;
    let bestScore = 0;
    for (let i = 0; i < links.length; i++) {
      const score =
        (preference.translation && links[i].translation === preference.translation ? 1 : 0) +
        (preference.playerName && links[i].playerName === preference.playerName ? 1 : 0);
      if (score > bestScore) {
        bestIndex = i;
        bestScore = score;
      }
    }
    return bestIndex;
  }

  private noteResolverResult(resolverId: string, succeeded: boolean): void {
    if (succeeded) {
      this.resolverHealth.delete(resolverId);
      return;
    }
    const previous = this.recentFailures(resolverId);
    this.resolverHealth.set(resolverId, { consecutiveFailures: previous + 1, lastFailureAt: Date.now() });
  }

  private recentFailures(resolverId: string): number {
    const health = this.resolverHealth.get(resolverId);
    if (!health) return 0;
    if (Date.now() - health.lastFailureAt >= RESOLVER_FAILURE_DECAY_MS) {
      this.resolverHealth.delete(resolverId);
      return 0;
    }
    return health.consecutiveFailures;
  }

  // BROWSER-runtime resolvers (extractors/alloha.js and similar) expose Provider.browserScript()
  // instead of Provider.resolve() - the script text itself is plain, portable JS (fetched cheaply
  // via the same sandboxed worker as every other extension call), but *running* it has to happen
  // inside a real page in a real browser context, which is Electron-main-only territory (see
  // BrowserPort.resolve, same reasoning as challenge()/browserFetch() elsewhere in this app).
  private async runBrowserResolver(resolverId: string, link: PlayerLink, deadline: number): Promise<Array<PlayerLink & { type: string }>> {
    const resolveStartedAt = Date.now();
    logger.info("resolve", `${resolverId} browser resolver: loading extractor script`);
    const result = await this.run<string>("browserScript", resolverId, [JSON.stringify(link)], {
      extensionsDir: this.resolversDir,
      timeoutMs: Math.max(1, Math.min(WORKER_TIMEOUT_MS, deadline - Date.now())),
    });
    const { script, parentUrl } = parseBrowserResolverPayload(result);
    logger.info("resolve", `${resolverId} extractor script ready in ${Date.now() - resolveStartedAt}ms (${script.length} chars); starting hidden-browser resolve`);
    const browserStartedAt = Date.now();
    const streams = await getPlatform().browser.resolve(link, script, Math.max(1, deadline - Date.now()), parentUrl);
    logger.info("resolve", `${resolverId} hidden-browser resolve finished in ${Date.now() - browserStartedAt}ms: ${streams.length} stream(s)`);
    return streams as unknown as Array<PlayerLink & { type: string }>;
  }

  // Mirrors Android's PlaybackResolver: an EMBED link is a third-party player *page*, not a media
  // file, so try the resolvers installed for it (see resolverDependencies in marketplace.ts) to
  // turn it into a real DIRECT_HLS/DIRECT_MP4 stream before the UI ever falls back to showing that
  // page in an iframe. The first resolver to return something playable wins.
  //
  // A source can return a dozen+ EMBED mirrors for one episode (YummyAnime does), most of them
  // pointing at the same handful of providers. The plan below caps the work two ways:
  // MAX_ATTEMPTS caps the total, and MAX_ATTEMPTS_PER_RESOLVER caps how much of it any single
  // provider may consume. A per-resolver allowance of 1 was the bug behind "Kodik sometimes just
  // doesn't load" - a *mirror* failing is not the same as a *provider* failing, and one retry on a
  // *different* mirror costs nothing on the normal path.
  //
  // Attempts used to run strictly one after another, so a slow or dead provider at the head of the
  // list (a browser-runtime resolver that fails after ~2.5s, twice, for its two mirrors) was paid
  // for in full before the next provider was even tried. They now overlap: the first starts at
  // once, and each next one starts as soon as the previous fails - or after RESOLVE_STAGGER_MS of
  // it still being in flight, whichever comes first. The stagger keeps the source's preferred
  // provider ahead in a healthy race (it gets a head start rather than a handicap), and keeps a
  // fast plain-HTTP fallback from waiting behind a slow one. Browser-runtime resolvers open a real
  // hidden window, so only one of them runs at a time.
  private async resolveEmbedLinks(links: PlayerLink[], preferredIndex = -1): Promise<PlayerLink[]> {
    const MAX_ATTEMPTS = 5;
    const MAX_ATTEMPTS_PER_RESOLVER = 2;
    const startedAt = Date.now();
    const deadline = startedAt + RESOLVE_TOTAL_TIMEOUT_MS;

    const orderedIndexes = links.map((_link, index) => index);
    if (preferredIndex >= 0) {
      orderedIndexes.splice(preferredIndex, 1);
      orderedIndexes.unshift(preferredIndex);
    }
    // Preserve the source's declared ordering while all providers are healthy. Once one starts
    // failing, put untouched/working resolvers ahead of it on subsequent episodes instead of
    // repeatedly paying its timeout first. The failed resolver remains in the list as fallback.
    const failuresByIndex = links.map((link) => {
      const resolver = link.type === "EMBED" ? this.findResolverForUrl(link.url) : null;
      return resolver ? this.recentFailures(resolver.id) : 0;
    });
    orderedIndexes.sort((a, b) => {
      if (a === preferredIndex) return -1;
      if (b === preferredIndex) return 1;
      return failuresByIndex[a] - failuresByIndex[b] || a - b;
    });

    interface Candidate { index: number; link: PlayerLink; resolver: ResolverManifest }
    const plan: Candidate[] = [];
    const plannedByResolver = new Map<string, number>();
    for (const index of orderedIndexes) {
      if (plan.length >= MAX_ATTEMPTS) break;
      const link = links[index];
      if (link.type !== "EMBED") continue;
      const resolver = this.findResolverForUrl(link.url);
      if (!resolver) continue;
      const used = plannedByResolver.get(resolver.id) ?? 0;
      if (used >= MAX_ATTEMPTS_PER_RESOLVER) continue;
      plannedByResolver.set(resolver.id, used + 1);
      plan.push({ index, link, resolver });
    }
    if (plan.length === 0) return links;

    const total = plan.length;
    let launched = 0;
    let active = 0;
    let browserActive = 0;
    let settled = false;
    let stagger: NodeJS.Timeout | undefined;

    return new Promise<PlayerLink[]>((resolveAll) => {
      const finish = (result: PlayerLink[], winner?: { resolverId: string; resolved: PlayerLink[] }) => {
        if (settled) return;
        settled = true;
        clearTimeout(stagger);
        if (winner) {
          logger.info("resolve", `${winner.resolverId} resolved ${winner.resolved.length} stream(s) [${winner.resolved.map((r) => r.quality ?? "?").join(", ")}] (${Date.now() - startedAt}ms total, ${launched}/${total} attempts started)`);
        } else {
          logger.warn("resolve", `no resolver produced a playable stream after ${launched} attempt(s) in ${Date.now() - startedAt}ms - falling back to the raw embed list`);
        }
        resolveAll(result);
      };

      const launchNext = () => {
        clearTimeout(stagger);
        if (settled) return;
        if (plan.length === 0 || Date.now() >= deadline) {
          if (active === 0) finish(links);
          return;
        }
        // The first candidate that is allowed to run now: a browser resolver has to wait for the
        // one before it, but must not hold up a plain-HTTP candidate queued behind it.
        const pick = plan.findIndex((c) => c.resolver.runtime !== "BROWSER" || browserActive === 0);
        if (pick < 0) return; // Only browser candidates are left and one is running: its end relaunches.
        const [candidate] = plan.splice(pick, 1);
        launched += 1;
        active += 1;
        const isBrowser = candidate.resolver.runtime === "BROWSER";
        if (isBrowser) browserActive += 1;
        if (plan.length > 0) stagger = setTimeout(launchNext, RESOLVE_STAGGER_MS);

        void this.attemptResolve(candidate.resolver, candidate.link, launched, total, deadline)
          .then((resolved) => {
            if (resolved.length > 0) {
              this.noteResolverResult(candidate.resolver.id, true);
              finish([...resolved, ...links.slice(0, candidate.index), ...links.slice(candidate.index + 1)], {
                resolverId: candidate.resolver.id,
                resolved,
              });
              return;
            }
            this.noteResolverResult(candidate.resolver.id, false);
            logger.warn("resolve", `${candidate.resolver.id} returned nothing playable`);
          })
          .catch((error: unknown) => {
            this.noteResolverResult(candidate.resolver.id, false);
            logger.warn("resolve", `${candidate.resolver.id} failed on ${candidate.link.url}: ${error instanceof Error ? error.message : String(error)}`);
          })
          .finally(() => {
            active -= 1;
            if (isBrowser) browserActive -= 1;
            launchNext();
          });
      };

      launchNext();
    });
  }

  /** One resolver run for one link, with the resolver's stream vocabulary mapped onto this app's
   * PlayerLink. Resolves to [] when the resolver found nothing playable. */
  private async attemptResolve(resolver: ResolverManifest, link: PlayerLink, attemptNo: number, of: number, deadline: number): Promise<PlayerLink[]> {
    logger.info("resolve", `attempt ${attemptNo}/${of} via ${resolver.id} (${resolver.runtime ?? "NODE"}) for ${link.playerName ?? "?"}/${link.translation ?? "?"} ${playerUrlLabel(link.url)}`);
    const raw =
      resolver.runtime === "BROWSER"
        ? await this.runBrowserResolver(resolver.id, link, deadline)
        : await this.run<Array<PlayerLink & { type: string }>>("resolve", resolver.id, [JSON.stringify(link)], {
            extensionsDir: this.resolversDir,
            timeoutMs: Math.max(1, Math.min(WORKER_TIMEOUT_MS, deadline - Date.now())),
          });
    // Resolvers speak the same VideoStream.type vocabulary as their compiled-in Kotlin originals
    // (HLS/MP4/DASH - see extractors/kodik.js's streamTypeFor), not this app's own PlayerLinkType -
    // mapped to the DIRECT_* counterpart the player actually understands.
    //
    // A resolver only ever returns stream-technical info (url/quality/type) - it has no way to know
    // which dub studio or embed provider the EMBED link it resolved even came from, so without this
    // the in-player "Озвучка"/"Плеер" picker shows a blank selection for whatever resolved link ends
    // up auto-selected. Carrying it over from the original link (only where the resolver itself
    // didn't already supply one) keeps the picker's selection in sync with reality.
    //
    // Same story for `segments` (opening/ending skip windows): only kodik.js parses its own from the
    // embed page - every other extractor just hardcodes `segments: []`, which would otherwise
    // silently break "auto-skip opening/ending" for any episode that resolves through one of those.
    //
    // And `videoId`: the source's own id for the episode, which reporting watch time to an account
    // is addressed by. A resolver has no idea what it is, so dropping it here left every resolved
    // stream - which is most of them - unable to report anything.
    return raw
      .map((candidate): Omit<PlayerLink, "type"> & { type: PlayerLinkType | undefined } => ({
        ...candidate,
        type: RESOLVER_STREAM_TYPE_TO_PLAYER_LINK_TYPE[candidate.type],
        translation: candidate.translation ?? link.translation,
        playerName: candidate.playerName ?? link.playerName,
        segments: candidate.segments && candidate.segments.length > 0 ? candidate.segments : link.segments,
        videoId: candidate.videoId ?? link.videoId,
        subtitles: candidate.subtitles && candidate.subtitles.length > 0 ? candidate.subtitles : link.subtitles,
      }))
      .filter((candidate): candidate is PlayerLink => candidate.type !== undefined);
  }

  /** What the source offers to search by: its own sort orders and filters, as it describes them. */
  async getFilterCatalog(sourceId: string): Promise<SearchFilterCatalog> {
    const settings = await this.run<Partial<SearchFilterCatalog>>("getSettings", sourceId, []);
    return {
      sortOptions: (settings.sortOptions ?? []).filter((o) => o && typeof o.id === "string" && o.id && typeof o.title === "string"),
      filters: (settings.filters ?? []).filter((f) => f && f.id && f.title && FILTER_TYPES.includes(f.type)),
    };
  }

  installedVersions(): Map<string, string> {
    return new Map([
      ...[...this.extensions].map(([id, { manifest }]): [string, string] => [id, manifest.version]),
      ...this.apkSources.map((source): [string, string] => [source.id, source.version]),
    ]);
  }

  /** The package an installed APK source comes in (sources of one package go together). */
  apkPackageOf(sourceId: string): string | null {
    return this.apkSources.find((source) => source.id === sourceId)?.packageName ?? null;
  }

  /** Resolvers aren't user-installable, so they never appear in list() - but the Sources screen
   * still has to know their versions to notice that one of them has an update waiting. */
  installedResolverVersions(): Record<string, string> {
    return Object.fromEntries([...this.resolvers].map(([id, manifest]) => [id, manifest.version]));
  }

  /** The resolver dependencies that installed sources expect to be present. Keeping this on the
   * runtime means startup repair reads the installed manifest (the actual source contract), not a
   * possibly stale marketplace index entry. */
  installedResolverRequirements(): InstalledResolverRequirement[] {
    const requirements: InstalledResolverRequirement[] = [];
    for (const [sourceId, { manifest }] of this.extensions) {
      const resolverIds = [...new Set((manifest.resolverDependencies ?? []).filter((id) => id && !isRetiredResolver(id)))];
      if (resolverIds.length === 0) continue;
      const originUrl = this.originOf(sourceId);
      if (originUrl) requirements.push({ sourceId, originUrl, resolverIds });
    }
    return requirements;
  }

  private originPath(id: string): string {
    return getPlatform().files.join(this.extensionsDir, `${id}.origin`);
  }

  originOf(id: string): string | null {
    return this.origins.get(id) ?? null;
  }

  /**
   * Writes `<id>.manifest.json` + `<id>.js`, refusing to overwrite an id that was last installed
   * from a *different* repository — without this, a third-party repository could silently replace
   * trusted code published under an id like "animego" the moment anyone reinstalls/updates it.
   * Mirrors the Android app's ScriptExtensionRepository.install origin guard.
   */
  async install(id: string, manifestJson: string, jsPayload: string, originUrl: string): Promise<void> {
    let manifest: { id?: string };
    try {
      manifest = JSON.parse(manifestJson);
    } catch {
      throw new Error(`Manifest for "${id}" is not valid JSON`);
    }
    if (manifest.id !== id) throw new Error(`Manifest id "${manifest.id}" doesn't match "${id}"`);

    const existingOrigin = this.originOf(id);
    if (existingOrigin && existingOrigin !== originUrl) {
      throw new Error(`"${id}" is already installed from a different repository and can't be overwritten`);
    }

    const { files } = getPlatform();
    await files.mkdir(this.extensionsDir);
    await files.writeText(files.join(this.extensionsDir, `${id}.manifest.json`), manifestJson);
    await files.writeText(files.join(this.extensionsDir, `${id}.js`), jsPayload);
    await files.writeText(this.originPath(id), originUrl);
    await this.reload();
  }

  /** Installs a player resolver (best-effort dependency of a source, see marketplace.ts) - unlike
   * install(), there's no origin-trust guard here: resolvers aren't user-facing or user-chosen,
   * and a source can freely redeclare/update its own resolverDependencies. */
  async installResolver(id: string, manifestJson: string, jsPayload: string): Promise<void> {
    if (isRetiredResolver(id)) {
      logger.info("resolvers", `skipping retired resolver ${id}`);
      return;
    }
    let manifest: { id?: string };
    try {
      manifest = JSON.parse(manifestJson);
    } catch {
      throw new Error(`Resolver manifest for "${id}" is not valid JSON`);
    }
    if (manifest.id !== id) throw new Error(`Resolver manifest id "${manifest.id}" doesn't match "${id}"`);

    const { files } = getPlatform();
    await files.mkdir(this.resolversDir);
    await files.writeText(files.join(this.resolversDir, `${id}.manifest.json`), manifestJson);
    await files.writeText(files.join(this.resolversDir, `${id}.js`), jsPayload);
    await this.reload();
  }

  private async removeRetiredResolverFiles(): Promise<void> {
    const { files } = getPlatform();
    for (const id of RETIRED_RESOLVER_IDS) {
      for (const suffix of [".manifest.json", ".js"]) {
        await files.remove(files.join(this.resolversDir, `${id}${suffix}`));
      }
    }
  }

  async uninstall(id: string): Promise<void> {
    // A stored token for a source that is no longer installed is a secret nobody is watching.
    await this.forgetStorage(id);
    const { files } = getPlatform();
    for (const suffix of [".manifest.json", ".js", ".origin"]) {
      await files.remove(files.join(this.extensionsDir, `${id}${suffix}`));
    }
    await this.reload();
  }
}
