// Opening and ending times from AniSkip (api.aniskip.com), for episodes whose source marks none.
// AniSkip knows shows by their MyAnimeList id, which no source here gives, so it is found the way
// tracking finds a title on AniList: the AniList entry the title is linked to, or else AniList's
// public search with the same strict match the import uses (core/tracking/matching.ts) - AniList
// entries carry their MAL id. Both answers are kept for the session; the renderer keeps the MAL id
// across sessions and hands it back, so a title is looked up on AniList once per device.
// Asked only by the player, only when the link has no segments and data saver is off.
import { and, eq } from "drizzle-orm";
import type { AnimeTitle, SkipTimesResult, VideoSegment } from "@shared/types";
import { trackerLinks } from "./db/schema";
import { logger } from "./logger";
import { getPlatform } from "./platform";
import { anilistQuery, aniListNames, aniListYear, searchMedia } from "./tracking/anilist";
import { anilistFormatToType, pickConfident, searchQueriesFor, trackerNamesOf } from "./tracking/matching";

const getDb = () => getPlatform().db.get();
const ANISKIP_URL = "https://api.aniskip.com/v2/skip-times";
const REQUEST_TIMEOUT_MS = 8_000;
/**
 * How sure the AniList match has to be. Lower than tracking's (which writes to an account): the
 * same name and type with no year to compare, which several sources' details leave out, is enough
 * here - nothing is written, and AniSkip itself drops times measured on an episode of another length.
 */
const SKIP_TIMES_MATCH = 0.8;

/** MAL id by "sourceId\nanimeId"; null once looked for and not found. */
const malIds = new Map<string, number | null>();
/** Segments by "malId:episode:seconds". */
const skipCache = new Map<string, VideoSegment[]>();

interface AniSkipResult {
  interval?: { startTime?: number; endTime?: number };
  skipType?: string;
}

/** AniSkip's answer as segments: openings and endings (a "mixed" one is either), nothing else. */
export function parseAniSkip(body: string): VideoSegment[] {
  let parsed: { found?: boolean; results?: AniSkipResult[] };
  try {
    parsed = JSON.parse(body) as typeof parsed;
  } catch {
    return [];
  }
  if (!parsed.found || !Array.isArray(parsed.results)) return [];
  const segments: VideoSegment[] = [];
  for (const result of parsed.results) {
    const start = result.interval?.startTime;
    const end = result.interval?.endTime;
    if (typeof start !== "number" || typeof end !== "number" || !(end > start) || start < 0) continue;
    const type = result.skipType === "op" || result.skipType === "mixed-op" ? "OPENING" : result.skipType === "ed" || result.skipType === "mixed-ed" ? "ENDING" : null;
    if (!type) continue;
    segments.push({ type, startMs: Math.round(start * 1000), endMs: Math.round(end * 1000) });
  }
  return segments.sort((a, b) => a.startMs - b.startMs);
}

async function linkedMalId(sourceId: string, animeId: string): Promise<number | null | undefined> {
  const row = await getDb()
    .select()
    .from(trackerLinks)
    .where(and(eq(trackerLinks.tracker, "anilist"), eq(trackerLinks.sourceId, sourceId), eq(trackerLinks.animeId, animeId)))
    .get();
  if (row?.remoteId == null) return undefined;
  const data = await anilistQuery<{ Media: { idMal?: number | null } | null }>(null, "query ($id: Int) { Media(id: $id) { idMal } }", { id: row.remoteId });
  return data.Media?.idMal ?? null;
}

async function searchedMalId(anime: AnimeTitle): Promise<number | null> {
  const names = trackerNamesOf(anime);
  if (names.length === 0) return null;
  const wanted = { names, year: anime.year ?? null, type: anime.type ?? null };
  const candidates = [];
  for (const query of searchQueriesFor(names, 2)) {
    for (const media of await searchMedia(null, query, 8)) {
      candidates.push({ key: media.idMal ?? null, comparable: { names: aniListNames(media), year: aniListYear(media), type: anilistFormatToType(media.format) } });
    }
  }
  const picked = pickConfident(wanted, candidates.filter((candidate) => candidate.key != null), SKIP_TIMES_MATCH);
  return picked?.key ?? null;
}

async function findMalId(sourceId: string, animeId: string, anime: () => Promise<AnimeTitle | null>): Promise<number | null> {
  const key = `${sourceId}\n${animeId}`;
  if (malIds.has(key)) return malIds.get(key) ?? null;
  let malId: number | null = null;
  try {
    const linked = await linkedMalId(sourceId, animeId);
    if (linked !== undefined) malId = linked;
    else {
      const title = await anime();
      if (title) malId = await searchedMalId(title);
    }
  } catch (error) {
    // Not remembered: AniList being down now says nothing about the next try.
    logger.warn("skip", `${sourceId}/${animeId}: no MAL id (${error instanceof Error ? error.message : String(error)})`);
    return null;
  }
  malIds.set(key, malId);
  logger.info("skip", `${sourceId}/${animeId}: MAL id ${malId ?? "not found"}`);
  return malId;
}

/**
 * Opening/ending segments for one episode from AniSkip. `knownMalId` is what the renderer kept from
 * an earlier answer; `durationMs` filters out times measured on a differently cut release.
 */
export async function skipTimesFor(
  sourceId: string,
  animeId: string,
  episodeNumber: number,
  durationMs: number,
  knownMalId: number | null | undefined,
  anime: () => Promise<AnimeTitle | null>,
): Promise<SkipTimesResult> {
  if (knownMalId != null) malIds.set(`${sourceId}\n${animeId}`, knownMalId);
  const malId = await findMalId(sourceId, animeId, anime);
  if (malId == null || !(episodeNumber > 0)) return { malId, segments: [] };
  const seconds = Math.round(durationMs / 1000);
  const cacheKey = `${malId}:${episodeNumber}:${seconds}`;
  const cached = skipCache.get(cacheKey);
  if (cached) return { malId, segments: cached };
  try {
    const url = `${ANISKIP_URL}/${malId}/${episodeNumber}?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed&episodeLength=${seconds}`;
    const response = await getPlatform().http.request({ url, method: "GET", headers: { Accept: "application/json" }, timeoutMs: REQUEST_TIMEOUT_MS });
    // 404 is AniSkip's "no times for this episode": an answer, kept like any other.
    const segments = response.status === 200 ? parseAniSkip(response.body) : [];
    if (response.status === 200 || response.status === 404) skipCache.set(cacheKey, segments);
    logger.info("skip", `MAL ${malId} ep ${episodeNumber} (${seconds}s): ${segments.length ? segments.map((s) => `${s.type} ${s.startMs / 1000}-${s.endMs / 1000}s`).join(", ") : `none (http ${response.status})`}`);
    return { malId, segments };
  } catch (error) {
    logger.warn("skip", `AniSkip MAL ${malId} ep ${episodeNumber}: ${error instanceof Error ? error.message : String(error)}`);
    return { malId, segments: [] };
  }
}
