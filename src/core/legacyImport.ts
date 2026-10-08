// Carrying over what the Kotlin hibiki left behind when this app replaces it on a phone.
//
// Both are the same Android package (org.akkirrai.hibiki), so after the update this app can read the
// old one's private files: its library database (databases/hibiki_library.db) and its saved episode
// positions (the hibiki_watch_state preferences). The platform reads them raw (LegacyAppData); here
// they are mapped onto this app's tables, once, and never over anything this app already has.
import type { AnimeTitle } from "@shared/types";
import { cachedAnime, library, watchProgress } from "./db/schema";
import { logger } from "./logger";
import { getPlatform } from "./platform";

/** The old app's data, as the platform found it. */
export interface LegacyAppData {
  library: Array<{ titleId: string; animeJson?: string | null; categories: string; addedAt?: number | null }>;
  /** hibiki_watch_state entries whose key starts with "progress_". */
  progress: Record<string, string>;
}

/** A title id of the old app as (sourceId, animeId) here, or null when it cannot be placed. */
export function legacyTitleKey(rawId: string): { sourceId: string; animeId: string } | null {
  const id = rawId.trim();
  // Before titles carried their source, every title was YummyAnime's, by its numeric id.
  if (/^\d+$/.test(id)) return { sourceId: "yummy-anime", animeId: id };
  if (!id.startsWith("source:")) return null;
  const separator = id.indexOf(":", "source:".length);
  if (separator < 0 || separator === id.length - 1) return null;
  let slug = id.slice("source:".length, separator);
  const animeId = id.slice(separator + 1);
  // Enum names from the oldest builds (ANI_LIBERTY) became slugs (ani-liberty).
  if (/^[A-Z0-9]+(?:_[A-Z0-9]+)*$/.test(slug)) slug = slug.toLowerCase().replace(/_/g, "-");
  // An Aniyomi source was "aniyomi-<package>-<hash>-<source id in base 36>"; here it is "apk:<the same id in decimal>".
  if (slug.startsWith("aniyomi-")) {
    const apkId = unsignedBase36ToDecimal(slug.slice(slug.lastIndexOf("-") + 1));
    return apkId ? { sourceId: `apk:${apkId}`, animeId } : null;
  }
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) ? { sourceId: slug, animeId } : null;
}

function unsignedBase36ToDecimal(text: string): string | null {
  if (!/^[0-9a-z]+$/.test(text)) return null;
  let value = 0n;
  for (const char of text) value = value * 36n + BigInt(Number.parseInt(char, 36));
  return value.toString();
}

// One category per title here; the old app kept a set (a status, plus favourite and saved). The
// status wins - it is what the library is sorted by - then favourite, then saved.
const CATEGORY_ORDER = ["watching", "planned", "completed", "dropped", "on_hold", "favorite", "saved"];

export function legacyCategory(categories: string): string | null {
  const present = new Set(categories.split(",").map((value) => value.trim()));
  return CATEGORY_ORDER.find((category) => present.has(category)) ?? null;
}

/** The old app's stored title as this app's title - enough for a library card until the source is asked again. */
export function legacyAnimeTitle(json: string | null | undefined, key: { sourceId: string; animeId: string }): AnimeTitle | null {
  if (!json) return null;
  let raw: Record<string, unknown>;
  try {
    raw = JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
  const text = (value: unknown) => (typeof value === "string" && value.trim() ? value : null);
  const list = (value: unknown) => (Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && item.trim() !== "") : []);
  const title = text(raw.title);
  if (!title) return null;
  const nextEpisodeAt = typeof raw.nextEpisodeAt === "number" && raw.nextEpisodeAt > 0 ? raw.nextEpisodeAt : null;
  return {
    id: key.animeId,
    sourceId: key.sourceId,
    russianName: title,
    synonyms: list(raw.alternativeTitles),
    posterUrl: text(raw.posterUrl) ?? text(raw.posterFallbackUrl),
    description: text(raw.description),
    genres: list(raw.genres),
    screenshots: list(raw.screenshots),
    ageRating: text(raw.ageRating),
    nextEpisodeAt,
  };
}

interface LegacyProgress {
  sourceId: string;
  titleId: string;
  episodeId: string;
  episodeNumber: number;
  translation: string | null;
  quality: string | null;
  positionMs: number;
  durationMs: number;
  watched: boolean;
  updatedAt: number;
}

const EPISODE_SEPARATOR = "|episode|";
const SOURCE_SEPARATOR = "|source|";
// What the old app counted as watched (isEpisodeWatched): nearly the whole episode, or a mark from the list.
const WATCHED_SHARE = 0.9;

/**
 * The saved positions, one per episode. The old app kept one per episode and dub; here a title's
 * episode has one, so the latest save wins. Its dub ("|watch|" group ids) has no counterpart here
 * and is dropped; the dub's name is kept as the translation to prefer when it is resumed.
 */
export function legacyProgressRows(entries: Record<string, string>): LegacyProgress[] {
  const latest = new Map<string, LegacyProgress>();
  for (const [storageKey, encoded] of Object.entries(entries)) {
    if (!storageKey.startsWith("progress_")) continue;
    const payload = storageKey.slice("progress_".length);
    let rawTitleId: string;
    let episodeId: string;
    if (payload.includes(EPISODE_SEPARATOR)) {
      rawTitleId = payload.slice(0, payload.indexOf(EPISODE_SEPARATOR));
      const rest = payload.slice(payload.indexOf(EPISODE_SEPARATOR) + EPISODE_SEPARATOR.length);
      episodeId = rest.includes(SOURCE_SEPARATOR) ? rest.slice(0, rest.indexOf(SOURCE_SEPARATOR)) : rest;
    } else if (payload.startsWith("source:")) {
      rawTitleId = payload.slice(0, payload.lastIndexOf(":"));
      episodeId = payload.slice(payload.lastIndexOf(":") + 1);
    } else if (payload.includes(":")) {
      rawTitleId = payload.slice(0, payload.indexOf(":"));
      episodeId = payload.slice(payload.indexOf(":") + 1);
    } else continue;
    const key = legacyTitleKey(rawTitleId);
    const parts = encoded.split("\u001f");
    if (!key || !episodeId || parts.length < 8) continue;
    const episodeNumber = Number.parseFloat(parts[0]);
    const positionMs = Number.parseInt(parts[5], 10) || 0;
    const durationMs = Number.parseInt(parts[6], 10) || 0;
    const updatedAt = Number.parseInt(parts[7], 10) || 0;
    if (!Number.isFinite(episodeNumber)) continue;
    const row: LegacyProgress = {
      sourceId: key.sourceId,
      titleId: key.animeId,
      episodeId,
      episodeNumber: Math.round(episodeNumber),
      translation: parts[3] || null,
      quality: parts[4] || null,
      positionMs,
      durationMs,
      watched: durationMs > 0 && positionMs >= durationMs * WATCHED_SHARE,
      updatedAt,
    };
    const id = `${row.sourceId}\n${row.titleId}\n${row.episodeId}`;
    const previous = latest.get(id);
    if (!previous || previous.updatedAt < row.updatedAt) latest.set(id, row);
  }
  return [...latest.values()];
}

/** Writes the old app's library and positions in, beside (never over) what this app already has. */
export async function importLegacyData(data: LegacyAppData): Promise<{ library: number; progress: number; skipped: number; sourceIds: string[] }> {
  const db = getPlatform().db.get();
  let libraryCount = 0;
  let skipped = 0;
  const sourceIds = new Set<string>();
  for (const entry of data.library) {
    const key = legacyTitleKey(entry.titleId);
    const category = legacyCategory(entry.categories);
    const anime = key ? legacyAnimeTitle(entry.animeJson, key) : null;
    if (!key || !category || !anime) {
      skipped++;
      logger.warn("migration", `old library entry not carried over: ${entry.titleId}`);
      continue;
    }
    const addedAt = entry.addedAt ?? Date.now();
    const animeJson = JSON.stringify(anime);
    await db.insert(library).values({ sourceId: key.sourceId, animeId: key.animeId, category, addedAt, animeJson, updatedAt: addedAt }).onConflictDoNothing().run();
    await db.insert(cachedAnime).values({ sourceId: key.sourceId, animeId: key.animeId, animeJson, cachedAt: addedAt }).onConflictDoNothing().run();
    sourceIds.add(key.sourceId);
    libraryCount++;
  }
  const progress = legacyProgressRows(data.progress);
  for (const row of progress) {
    await db.insert(watchProgress).values(row).onConflictDoNothing().run();
    sourceIds.add(row.sourceId);
  }
  return { library: libraryCount, progress: progress.length, skipped, sourceIds: [...sourceIds] };
}
