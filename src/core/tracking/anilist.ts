// AniList over its GraphQL API: sign-in, search, the account's entry for a title, and writes to it.
// Talks through platform.http, so it runs the same in Electron's main process and the Android WebView.
//
// Sign-in is AniList's implicit grant, as in the Kotlin app: the browser comes back to
// hibiki://anilist-auth with the token in the URL fragment, and no client secret exists anywhere.
// The client id is public by nature (it is in every sign-in URL) and baked in at build time.
import type { TrackerMedia, TrackerStatus } from "@shared/types";
import { logger } from "../logger";
import { getPlatform } from "../platform";

declare const __ANILIST_CLIENT_ID__: string | undefined;

export const ANILIST_REDIRECT = "hibiki://anilist-auth";
const GRAPHQL_URL = "https://graphql.anilist.co";

export function anilistClientId(): string {
  return typeof __ANILIST_CLIENT_ID__ === "string" ? __ANILIST_CLIENT_ID__.trim() : "";
}

/** Only client_id and response_type: the server sends the token to the redirect registered for the
 * client, and it refused the flow (unsupported_grant_type) when also given a redirect_uri. */
export function anilistAuthorizeUrl(clientId: string): string {
  return `https://anilist.co/api/v2/oauth/authorize?client_id=${encodeURIComponent(clientId)}&response_type=token`;
}

export type AniListRedirect =
  | { ok: true; accessToken: string; expiresInSeconds: number | null }
  | { ok: false; error: string };

/** Reads hibiki://anilist-auth#access_token=...&expires_in=..., or the error AniList sent instead.
 * Null for any other link. */
export function parseAniListRedirect(raw: string): AniListRedirect | null {
  if (!raw.toLowerCase().startsWith(ANILIST_REDIRECT)) return null;
  const rest = raw.slice(ANILIST_REDIRECT.length).replace(/^\/?[#?]/, "");
  const params = new URLSearchParams(rest);
  const error = params.get("error");
  if (error) return { ok: false, error: params.get("error_description") || error };
  const accessToken = params.get("access_token");
  if (!accessToken) return { ok: false, error: "no token in the redirect" };
  const expires = Number(params.get("expires_in"));
  return { ok: true, accessToken, expiresInSeconds: Number.isFinite(expires) && expires > 0 ? expires : null };
}

/** The account's token was refused: signed out elsewhere, revoked, or a year old. */
export class AniListAuthError extends Error {
  constructor(message = "AniList sign-in is no longer valid") {
    super(message);
    this.name = "AniListAuthError";
  }
}

const STATUS_FROM_ANILIST: Record<string, TrackerStatus> = {
  CURRENT: "watching",
  PLANNING: "planned",
  COMPLETED: "completed",
  DROPPED: "dropped",
  PAUSED: "on_hold",
  REPEATING: "rewatching",
};

const STATUS_TO_ANILIST: Record<TrackerStatus, string> = {
  watching: "CURRENT",
  planned: "PLANNING",
  completed: "COMPLETED",
  dropped: "DROPPED",
  on_hold: "PAUSED",
  rewatching: "REPEATING",
};

const MEDIA_FIELDS = "id title { romaji english native userPreferred } synonyms seasonYear startDate { year } format episodes coverImage { large } siteUrl";

export interface AniListMedia {
  id: number;
  title?: { romaji?: string | null; english?: string | null; native?: string | null; userPreferred?: string | null } | null;
  synonyms?: string[] | null;
  seasonYear?: number | null;
  startDate?: { year?: number | null } | null;
  format?: string | null;
  episodes?: number | null;
  coverImage?: { large?: string | null } | null;
  siteUrl?: string | null;
}

export interface AniListEntry {
  status: TrackerStatus | null;
  progress: number;
  /** The list entry's own id, which deleting it needs; absent where AniList did not send it. */
  id?: number;
}

export interface AniListViewer {
  id: number;
  name: string;
  avatarUrl: string | null;
}

/** Every name AniList files a title under, for matching. */
export function aniListNames(media: AniListMedia): string[] {
  const names = [media.title?.english, media.title?.romaji, media.title?.native, ...(media.synonyms ?? [])];
  return [...new Set(names.filter((name): name is string => typeof name === "string" && name.trim().length > 0))];
}

export function aniListYear(media: AniListMedia): number | null {
  return media.seasonYear ?? media.startDate?.year ?? null;
}

export function toTrackerMedia(media: AniListMedia): TrackerMedia {
  const title = media.title?.userPreferred || media.title?.romaji || media.title?.english || media.title?.native || String(media.id);
  const alt = [media.title?.english, media.title?.romaji, media.title?.native].find((name) => name && name !== title) ?? null;
  return {
    id: media.id,
    title,
    altTitle: alt,
    year: aniListYear(media),
    format: media.format ?? null,
    episodes: media.episodes ?? null,
    coverUrl: media.coverImage?.large ?? null,
    url: media.siteUrl ?? `https://anilist.co/anime/${media.id}`,
  };
}

function entryFrom(raw: { id?: number | null; status?: string | null; progress?: number | null } | null | undefined): AniListEntry | null {
  if (!raw) return null;
  return { status: raw.status ? (STATUS_FROM_ANILIST[raw.status] ?? null) : null, progress: raw.progress ?? 0, ...(raw.id != null ? { id: raw.id } : {}) };
}

// AniList allows 90 requests a minute (30 while it is under load). Everything this app sends goes
// through one queue, a request at a time, so a burst - a whole import's statuses - is spaced out by
// itself rather than run into the limit.
const MIN_GAP_MS = 700;
let queue: Promise<unknown> = Promise.resolve();
let lastRequestAt = 0;

function enqueue<T>(work: () => Promise<T>): Promise<T> {
  const run = queue.then(async () => {
    const wait = lastRequestAt + MIN_GAP_MS - Date.now();
    if (wait > 0) await sleep(wait);
    try {
      return await work();
    } finally {
      lastRequestAt = Date.now();
    }
  });
  queue = run.catch(() => undefined);
  return run;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

interface GraphQlResponse<T> {
  data?: T | null;
  errors?: Array<{ message?: string; status?: number }> | null;
}

/** What a request was, for the log: its kind and root field (Viewer, Page, SaveMediaListEntry...). */
function operationOf(query: string): string {
  const kind = /^\s*mutation/.test(query) ? "mutation" : "query";
  return `${kind} ${/\{\s*(\w+)/.exec(query)?.[1] ?? "?"}`;
}

/** One GraphQL call. `token` null reads anonymously (public data only). */
export function anilistQuery<T>(token: string | null, query: string, variables: Record<string, unknown> = {}): Promise<T> {
  const operation = operationOf(query);
  return enqueue(async () => {
    for (let attempt = 0; ; attempt++) {
      const startedAt = Date.now();
      const response = await getPlatform().http.request({
        url: GRAPHQL_URL,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        // undefined variables are dropped here, which is what leaves a field unchanged in a mutation;
        // an explicit null would clear it.
        body: JSON.stringify({ query, variables }),
        timeoutMs: 20_000,
      }).catch((error: unknown) => {
        logger.warn("tracking", `AniList ${operation}: no answer after ${Date.now() - startedAt}ms: ${error instanceof Error ? error.message : String(error)}`);
        throw error;
      });
      logger.debug("tracking", `AniList ${operation}: http ${response.status} in ${Date.now() - startedAt}ms`);
      if (response.status === 429 && attempt === 0) {
        const retryAfter = Number(response.headers["retry-after"]?.[0]);
        const waitSeconds = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 30;
        logger.warn("tracking", `AniList rate limit hit; retrying in ${waitSeconds}s`);
        await sleep(waitSeconds * 1000);
        continue;
      }
      let parsed: GraphQlResponse<T> | null = null;
      try {
        parsed = JSON.parse(response.body) as GraphQlResponse<T>;
      } catch {
        // Not JSON: an outage page. Reported by status below.
      }
      const error = parsed?.errors?.[0];
      if (response.status === 401 || error?.status === 401 || /invalid token|unauthorized/i.test(error?.message ?? "")) {
        logger.warn("tracking", `AniList ${operation}: token refused (http ${response.status})`);
        throw new AniListAuthError();
      }
      if (error) {
        logger.warn("tracking", `AniList ${operation}: ${error.message ?? "request failed"} (http ${response.status})`);
        throw new Error(`AniList: ${error.message ?? "request failed"}`);
      }
      if (response.status < 200 || response.status >= 300) throw new Error(`AniList answered HTTP ${response.status}`);
      if (!parsed?.data) throw new Error("AniList answered with no data");
      return parsed.data;
    }
  });
}

export async function getViewer(token: string): Promise<AniListViewer> {
  const data = await anilistQuery<{ Viewer: { id: number; name: string; avatar?: { large?: string | null } | null } }>(
    token,
    "query { Viewer { id name avatar { large } } }",
  );
  return { id: data.Viewer.id, name: data.Viewer.name, avatarUrl: data.Viewer.avatar?.large ?? null };
}

export async function searchMedia(token: string | null, query: string, perPage = 10): Promise<AniListMedia[]> {
  const data = await anilistQuery<{ Page: { media: AniListMedia[] | null } }>(
    token,
    `query ($q: String, $perPage: Int) { Page(perPage: $perPage) { media(search: $q, type: ANIME) { ${MEDIA_FIELDS} } } }`,
    { q: query, perPage },
  );
  return data.Page.media ?? [];
}

/** A title with the signed-in account's own entry for it and whether it is a favourite there. */
export async function getMediaWithEntry(token: string, mediaId: number): Promise<{ media: AniListMedia; entry: AniListEntry | null; favourite: boolean }> {
  const data = await anilistQuery<{ Media: AniListMedia & { isFavourite?: boolean; mediaListEntry?: { status?: string | null; progress?: number | null } | null } }>(
    token,
    `query ($id: Int) { Media(id: $id, type: ANIME) { ${MEDIA_FIELDS} isFavourite mediaListEntry { id status progress } } }`,
    { id: mediaId },
  );
  return { media: data.Media, entry: entryFrom(data.Media.mediaListEntry), favourite: data.Media.isFavourite ?? false };
}

/** Creates or updates the account's entry. An omitted field stays as it is on AniList. */
export async function saveEntry(token: string, mediaId: number, change: { status?: TrackerStatus; progress?: number }): Promise<AniListEntry> {
  const data = await anilistQuery<{ SaveMediaListEntry: { status?: string | null; progress?: number | null } }>(
    token,
    "mutation ($mediaId: Int, $status: MediaListStatus, $progress: Int) { SaveMediaListEntry(mediaId: $mediaId, status: $status, progress: $progress) { status progress } }",
    {
      mediaId,
      status: change.status ? STATUS_TO_ANILIST[change.status] : undefined,
      progress: change.progress,
    },
  );
  return entryFrom(data.SaveMediaListEntry) ?? { status: change.status ?? null, progress: change.progress ?? 0 };
}

/** Deletes a list entry by its own id (not the title's). */
export async function deleteEntry(token: string, entryId: number): Promise<void> {
  await anilistQuery(token, "mutation ($id: Int) { DeleteMediaListEntry(id: $id) { deleted } }", { id: entryId });
}

/** Flips the favourite - so the caller checks it is off first. */
export async function toggleFavourite(token: string, mediaId: number): Promise<void> {
  await anilistQuery(token, "mutation ($id: Int) { ToggleFavourite(animeId: $id) { anime { pageInfo { total } } } }", { id: mediaId });
}

export interface AniListListItem {
  media: AniListMedia;
  entry: AniListEntry | null;
  favourite: boolean;
}

/** Everything on the account's lists, plus favourites that are on no list. A title on a custom list
 * as well comes once, with its status-list entry. */
export async function getUserLibrary(token: string, userId: number): Promise<AniListListItem[]> {
  const collection = await anilistQuery<{
    MediaListCollection: { lists: Array<{ isCustomList?: boolean | null; entries?: Array<{ status?: string | null; progress?: number | null; media: AniListMedia }> | null }> | null };
  }>(
    token,
    `query ($userId: Int) { MediaListCollection(userId: $userId, type: ANIME) { lists { isCustomList entries { status progress media { ${MEDIA_FIELDS} } } } } }`,
    { userId },
  );
  const items = new Map<number, AniListListItem>();
  const lists = [...(collection.MediaListCollection.lists ?? [])].sort((a, b) => Number(a.isCustomList ?? false) - Number(b.isCustomList ?? false));
  for (const list of lists) {
    for (const raw of list.entries ?? []) {
      if (!items.has(raw.media.id)) items.set(raw.media.id, { media: raw.media, entry: entryFrom(raw), favourite: false });
    }
  }
  for (let page = 1; page <= 20; page++) {
    const data = await anilistQuery<{ User: { favourites?: { anime?: { pageInfo?: { hasNextPage?: boolean | null } | null; nodes?: AniListMedia[] | null } | null } | null } | null }>(
      token,
      `query ($id: Int, $page: Int) { User(id: $id) { favourites { anime(page: $page, perPage: 25) { pageInfo { hasNextPage } nodes { ${MEDIA_FIELDS} } } } } }`,
      { id: userId, page },
    );
    const anime = data.User?.favourites?.anime;
    for (const media of anime?.nodes ?? []) {
      const existing = items.get(media.id);
      if (existing) existing.favourite = true;
      else items.set(media.id, { media, entry: null, favourite: true });
    }
    if (!anime?.pageInfo?.hasNextPage) break;
  }
  return [...items.values()];
}
