// Host globals exposed to extension scripts, mirroring RhinoExtensionRuntime.kt's `installGlobals`
// one-to-one (Jsoup/Base64/Gzip/Url/AnimeTitle/collectPaginated/console/fetch/challenge/
// browserFetch/preferredLanguage) so the .js payloads in hibiki-sources/extensions run unmodified.
import zlib from "node:zlib";
import { createRequire } from "node:module";
import { JsoupBinding } from "./jsoupShim";
import type { ChallengeProvider, BrowserFetchProvider, NetFetchProvider, NetFetchRequest } from "./browserBridge";
import type { ExtensionStorageBinding } from "@shared/extensionCallStorage";

export interface FetchOptions {
  method?: string;
  headers?: Record<string, string>;
  form?: Record<string, string>;
  body?: string;
}

export interface FetchResult {
  status: number;
  ok: boolean;
  body: string;
  headers: Record<string, string>;
}

/** Turns an extension's call shape (`form` is a convenience the scripts use a lot) into a plain
 * method/headers/body triple. Shared by both paths below so they behave identically. */
function normalizeRequest(options?: FetchOptions): { method: string; headers: Record<string, string>; body?: string } {
  const method = (options?.method ?? "GET").toUpperCase();
  const headers: Record<string, string> = { ...(options?.headers ?? {}) };
  let body: string | undefined = options?.body;

  if (options?.form) {
    body = new URLSearchParams(options.form).toString();
    headers["Content-Type"] = headers["Content-Type"] ?? "application/x-www-form-urlencoded";
  }
  return { method, headers, body };
}

// Last-resort path, used only when there is no host to bridge to (unit tests, tooling running
// execute.ts directly). Inside the app every fetch goes through the NetFetchProvider instead -
// see core/extensions/netFetch.ts for why: sync-fetch spawns a child process of the *Electron binary* per
// request, with no timeout of any kind, so a stalled connect hangs until the worker is killed.
// Loaded on demand, like Cheerio in jsoupShim.ts: inside the app this path never runs, and its
// dependency tree would otherwise be parsed on every worker start for nothing.
const require = createRequire(import.meta.url);
let syncFetchModule: typeof import("sync-fetch").default | null = null;

function loadSyncFetch(): typeof import("sync-fetch").default {
  syncFetchModule ??= require("sync-fetch") as typeof import("sync-fetch").default;
  return syncFetchModule;
}

function syncFetchFallback(url: string, options?: FetchOptions): FetchResult {
  const { method, headers, body } = normalizeRequest(options);
  const res = loadSyncFetch()(url, { method, headers, body });
  const text = res.text();
  const responseHeaders: Record<string, string> = {};
  res.headers.forEach((value: string, key: string) => {
    responseHeaders[key.toLowerCase()] = value;
  });

  return { status: res.status, ok: res.status >= 200 && res.status < 300, body: text, headers: responseHeaders };
}

const Base64Binding = {
  decode(value: string): string {
    const cleaned = value.replace(/[^A-Za-z0-9+/]/g, "");
    const padded = cleaned + "=".repeat((4 - (cleaned.length % 4)) % 4);
    try {
      return Buffer.from(padded, "base64").toString("binary");
    } catch {
      return "";
    }
  },
  encode(value: string): string {
    return Buffer.from(value, "binary").toString("base64");
  },
};

const GzipBinding = {
  inflate(value: string): string {
    const bytes = Buffer.from(value, "binary");
    return zlib.gunzipSync(bytes).toString("utf-8");
  },
};

const UrlBinding = {
  normalize(url: string): string {
    try {
      return new URL(url).toString();
    } catch {
      return url;
    }
  },
  resolve(base: string, reference: string): string {
    try {
      return new URL(reference, base).toString();
    } catch {
      return reference;
    }
  },
  origin(url: string): string {
    try {
      return new URL(url).origin;
    } catch {
      return url;
    }
  },
  scheme(url: string): string {
    try {
      return new URL(url).protocol.replace(/:$/, "");
    } catch {
      return "";
    }
  },
  host(url: string): string | null {
    try {
      return new URL(url).host || null;
    } catch {
      return null;
    }
  },
  path(url: string): string {
    try {
      return new URL(url).pathname;
    } catch {
      return url;
    }
  },
  isAbsolute(url: string): boolean {
    return /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(url);
  },
  decodeShifted(raw: string): string {
    // Mirrors Kotlin's decodeShiftedBase64 (UrlSupport.kt): Kodik's obfuscation shifts each
    // *letter* forward by 18 (mod 26) in the still-base64-encoded string, then base64-decodes the
    // result - not the other way around.
    if (raw.includes("//")) return raw;
    const shifted = Array.from(raw)
      .map((ch) => {
        if (!/[A-Za-z]/.test(ch)) return ch;
        const base = ch >= "A" && ch <= "Z" ? 65 : 97;
        return String.fromCharCode(((ch.charCodeAt(0) - base + 18) % 26) + base);
      })
      .join("");
    const padded = shifted + "=".repeat((4 - (shifted.length % 4)) % 4);
    return Buffer.from(padded, "base64").toString("utf-8");
  },
};

const NULL_DEFAULT_KEYS = [
  "russianName", "englishName", "japaneseName", "year", "type", "episodeCount",
  "posterUrl", "pageUrl", "status", "description", "nextEpisodeAt", "ageRating", "viewCount",
  "trailer", "sourceMaterial", "season", "availableEpisodeCount", "posterFallbackUrl",
];
const EMPTY_LIST_DEFAULT_KEYS = [
  "synonyms", "genres", "ratings", "screenshots", "studios",
  "mainCharacters", "similarAnime", "franchiseAnime", "relatedAnime",
];

function AnimeTitleFactory(fields: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const key of NULL_DEFAULT_KEYS) result[key] = null;
  for (const key of EMPTY_LIST_DEFAULT_KEYS) result[key] = [];
  Object.assign(result, fields);
  return result;
}

function collectPaginated(
  fetchPage: (page: number) => Array<{ id: string }> | null | undefined,
  wanted: number,
  pageSize: number,
): Array<{ id: string }> {
  const results: Array<{ id: string }> = [];
  const seen = new Set<string>();
  let page = 1;
  while (results.length < wanted && page <= 50) {
    let items: Array<{ id: string }> | null | undefined;
    try {
      items = fetchPage(page);
    } catch {
      break;
    }
    if (!items || items.length === 0) break;
    for (const item of items) {
      if (results.length >= wanted) break;
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      results.push(item);
    }
    if (items.length < pageSize) break;
    page += 1;
  }
  return results;
}

export interface ExtensionLogger {
  log(message: unknown): void;
  warn(message: unknown): void;
  error(message: unknown): void;
}

export interface BuildGlobalsOptions {
  logger: ExtensionLogger;
  preferredLanguage: string;
  challenge: ChallengeProvider;
  browserFetch: BrowserFetchProvider;
  /** Omitted only outside the app (see syncFetchFallback). */
  netFetch?: NetFetchProvider;
  /** Omitted by callers that have nowhere to persist to; the script then sees an empty store that
   * forgets every write, which is the right shape for tooling and tests. */
  storage?: ExtensionStorageBinding;
}

export function buildExtensionGlobals(options: BuildGlobalsOptions) {
  return {
    Jsoup: JsoupBinding,
    Base64: Base64Binding,
    Gzip: GzipBinding,
    Url: UrlBinding,
    AnimeTitle: AnimeTitleFactory,
    collectPaginated,
    console: {
      log: (m: unknown) => options.logger.log(m),
      warn: (m: unknown) => options.logger.warn(m),
      error: (m: unknown) => options.logger.error(m),
    },
    fetch: (url: string, fetchOptions?: FetchOptions): FetchResult => {
      if (!options.netFetch) return syncFetchFallback(url, fetchOptions);
      const { method, headers, body } = normalizeRequest(fetchOptions);
      return options.netFetch.fetch(url, { method, headers, body });
    },
    // Several requests concurrently, answers in the order asked. Extension scripts are synchronous
    // by design (see the note on fetch above), which makes every request serial even when they are
    // independent of one another - this is the way out of that without changing what a script
    // looks like. A request that never got an answer comes back as { ok: false, status: 0, error },
    // rather than throwing and taking the rest of the batch with it.
    //
    // Accepts a plain url string or { url, method, headers, form, body } per entry, so the common
    // case reads as fetchAll([a, b, c]).
    fetchAll: (requests: Array<string | ({ url: string } & FetchOptions)>): FetchResult[] => {
      const entries = (requests ?? []).map((request) => (typeof request === "string" ? { url: request } : request));
      if (!options.netFetch) {
        // No host to batch through (unit tests, tooling): correctness over concurrency.
        return entries.map((entry) => syncFetchFallback(entry.url, entry));
      }
      const payload: NetFetchRequest[] = entries.map((entry) => {
        const { method, headers, body } = normalizeRequest(entry);
        return { url: entry.url, options: { method, headers, body } };
      });
      return options.netFetch.fetchAll(payload);
    },
    challenge: (url: string, cookieNames: string[], forceRefresh?: boolean) =>
      options.challenge.acquire(url, cookieNames ?? [], Boolean(forceRefresh)),
    browserFetch: (pageUrl: string, targetUrl: string, fetchOptions?: FetchOptions) =>
      options.browserFetch.fetch(pageUrl, targetUrl, fetchOptions),
    // What makes an account possible: a token survives the call that fetched it. A script must
    // never put a password in here - only what it needs to prove itself again later.
    storage: options.storage ?? {
      get: () => null,
      set: () => {},
      remove: () => {},
    },
    preferredLanguage: options.preferredLanguage,
  };
}
