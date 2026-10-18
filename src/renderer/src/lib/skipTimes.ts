// AniSkip openings/endings for an episode whose source marks none (core/skipTimes.ts), asked for
// only while data saver is not holding the player down: on a slow line or with data saver on the
// player does without, as before. The MAL id a title was found under is kept here, per device, so
// later episodes and later sessions go straight to AniSkip without asking AniList again.
import type { VideoSegment } from "@shared/types";
import { hibiki } from "@/lib/hibiki";
import { isDataSaverActive } from "@/lib/connectionQuality";

const STORAGE_KEY = "hibiki-mal-ids";
const LIMIT = 500;

function read(): Record<string, number> {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}") as Record<string, number>;
    return saved && typeof saved === "object" ? saved : {};
  } catch {
    return {};
  }
}

function remember(key: string, malId: number): void {
  const { [key]: _previous, ...rest } = read();
  const next = { ...rest, [key]: malId };
  const keys = Object.keys(next);
  for (const stale of keys.slice(0, Math.max(0, keys.length - LIMIT))) delete next[stale];
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // Not kept: AniList is asked again next session, nothing worse.
  }
}

/** The segments AniSkip has for this episode, or none (data saver, unknown show, no times). */
export async function fetchSkipTimes(sourceId: string, animeId: string, episodeNumber: number, durationMs: number): Promise<VideoSegment[]> {
  if (isDataSaverActive() || !hibiki.sources.skipTimes) return [];
  const key = `${sourceId}\n${animeId}`;
  const known = read()[key];
  const result = await hibiki.sources.skipTimes(sourceId, animeId, episodeNumber, durationMs, known ?? null);
  if (result.malId != null && result.malId !== known) remember(key, result.malId);
  return result.segments;
}

/** Whether a link already carries its own opening or ending - then AniSkip is not asked. */
export function hasOwnSkipSegments(segments: VideoSegment[] | null | undefined): boolean {
  return !!segments?.some((segment) => segment.type === "OPENING" || segment.type === "ENDING");
}
