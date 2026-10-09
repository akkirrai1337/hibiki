import { eq, and, or } from "drizzle-orm";
import type { AnimeTitle, CachedAnimeEntry, CachedPlaybackGroupsEntry, DownloadedEpisode, DownloadedEpisodeFile, DownloadedParts, DownloadedSubtitle, PlaybackGroup } from "@shared/types";
import { getPlatform } from "./platform";
import { cachedAnime, cachedPlaybackGroups, downloadedEpisodes } from "./db/schema";
import { chronologicalGroups } from "./extensions/episodeOrder";

const getDb = () => getPlatform().db.get();

/** Strips the parts of an AnimeTitle that are only useful for navigating to *other* titles
 * (related/franchise/similar anime) before it goes into cachedAnime - offline, there's nothing to
 * navigate to (those titles' own pages likely aren't cached), so this would just be dead weight in
 * the table for every title that ever has one episode downloaded. */
function stripForCache(anime: AnimeTitle): AnimeTitle {
  const { relatedAnime: _related, franchiseAnime: _franchise, similarAnime: _similar, ...rest } = anime;
  return rest;
}

/**
 * Everything already on disk for the given titles, keyed "sourceId:animeId".
 *
 * This table has been written on every successful getById since it was added, but until now it was
 * only ever *read* in the catch branch - a fallback for when the source is unreachable. So a home
 * screen full of titles the app had already fetched a dozen times still waited on the network to
 * draw a name and a poster it had on disk the whole time. Reading all of it costs about a
 * millisecond; one getById costs a few hundred.
 *
 * Deliberately keyed by request rather than "give me everything": the table grows with every title
 * ever opened, and a screen only ever needs the handful it is about to draw.
 */
export async function getCachedAnimeMany(
  keys: Array<{ sourceId: string; animeId: string }>,
): Promise<Record<string, CachedAnimeEntry>> {
  if (keys.length === 0) return {};
  const result: Record<string, CachedAnimeEntry> = {};
  // Two bound values per title. Keep comfortably below SQLite's common 999-variable limit while
  // turning the normal 12-card screen from twelve statements into one.
  const unique = [...new Map(keys.map((key) => [`${key.sourceId}:${key.animeId}`, key])).values()];
  const BATCH_SIZE = 400;
  for (let offset = 0; offset < unique.length; offset += BATCH_SIZE) {
    const batch = unique.slice(offset, offset + BATCH_SIZE);
    const rows = await getDb()
      .select()
      .from(cachedAnime)
      .where(or(...batch.map(({ sourceId, animeId }) => and(eq(cachedAnime.sourceId, sourceId), eq(cachedAnime.animeId, animeId)))))
      .all();
    // `cachedAt` travels with the title on purpose: it is what lets a caller answer "is this still
    // good enough" without a round trip. Without it the only options are to trust the cache
    // forever or to refetch every time, and this data sits squarely between the two.
    for (const row of rows) {
      result[`${row.sourceId}:${row.animeId}`] = { title: JSON.parse(row.animeJson) as AnimeTitle, cachedAt: row.cachedAt };
    }
  }
  return result;
}

export async function cacheAnime(sourceId: string, animeId: string, anime: AnimeTitle): Promise<void> {
  await getDb()
    .insert(cachedAnime)
    .values({ sourceId, animeId, animeJson: JSON.stringify(stripForCache(anime)), cachedAt: Date.now() })
    .onConflictDoUpdate({
      target: [cachedAnime.sourceId, cachedAnime.animeId],
      set: { animeJson: JSON.stringify(stripForCache(anime)), cachedAt: Date.now() },
    })
    .run();
}

export async function getCachedAnime(sourceId: string, animeId: string): Promise<AnimeTitle | null> {
  const row = await getDb().select().from(cachedAnime).where(and(eq(cachedAnime.sourceId, sourceId), eq(cachedAnime.animeId, animeId))).get();
  return row ? (JSON.parse(row.animeJson) as AnimeTitle) : null;
}

export async function cachePlaybackGroups(sourceId: string, animeId: string, groups: PlaybackGroup[]): Promise<void> {
  await getDb()
    .insert(cachedPlaybackGroups)
    .values({ sourceId, animeId, groupsJson: JSON.stringify(groups), cachedAt: Date.now() })
    .onConflictDoUpdate({
      target: [cachedPlaybackGroups.sourceId, cachedPlaybackGroups.animeId],
      set: { groupsJson: JSON.stringify(groups), cachedAt: Date.now() },
    })
    .run();
}

export async function getCachedPlaybackGroups(sourceId: string, animeId: string): Promise<PlaybackGroup[] | null> {
  return (await getCachedPlaybackGroupsEntry(sourceId, animeId))?.groups ?? null;
}

export async function getCachedPlaybackGroupsEntry(sourceId: string, animeId: string): Promise<CachedPlaybackGroupsEntry | null> {
  const row = await getDb()
    .select()
    .from(cachedPlaybackGroups)
    .where(and(eq(cachedPlaybackGroups.sourceId, sourceId), eq(cachedPlaybackGroups.animeId, animeId)))
    .get();
  return row ? { groups: chronologicalGroups(JSON.parse(row.groupsJson) as PlaybackGroup[]), cachedAt: row.cachedAt } : null;
}

export async function recordDownloadedEpisode(entry: {
  sourceId: string;
  animeId: string;
  groupId: string;
  episodeId: string;
  episodeNumber: number;
  episodeLabel: string;
  filePath: string;
  fileSizeBytes: number;
  durationMs: number | null;
  quality: string | null;
  subtitles: DownloadedSubtitle[];
  parts: DownloadedParts;
}): Promise<void> {
  const subtitles = entry.subtitles.length > 0 ? JSON.stringify(entry.subtitles) : null;
  const parts = Object.keys(entry.parts).length > 0 ? JSON.stringify(entry.parts) : null;
  await getDb()
    .insert(downloadedEpisodes)
    .values({ ...entry, subtitles, parts, downloadedAt: Date.now() })
    .onConflictDoUpdate({
      target: [downloadedEpisodes.sourceId, downloadedEpisodes.animeId, downloadedEpisodes.episodeId],
      set: {
        groupId: entry.groupId,
        episodeNumber: entry.episodeNumber,
        episodeLabel: entry.episodeLabel,
        filePath: entry.filePath,
        fileSizeBytes: entry.fileSizeBytes,
        durationMs: entry.durationMs,
        quality: entry.quality,
        subtitles,
        parts,
        downloadedAt: Date.now(),
      },
    })
    .run();
}

function titleFor(anime: AnimeTitle | null, fallbackId: string): string {
  return anime?.russianName || anime?.englishName || anime?.originalName || fallbackId;
}

// Joined with cachedAnime in-memory after one batched lookup. Keeping the JSON decode here is
// simpler than a SQL join while avoiding the old extra indexed statement for every episode row.
export async function listDownloadedEpisodes(): Promise<DownloadedEpisode[]> {
  const rows = await getDb()
    .select()
    .from(downloadedEpisodes)
    .all();
  const cached = await getCachedAnimeMany(rows.map((row) => ({ sourceId: row.sourceId, animeId: row.animeId })));
  return rows.map((r) => {
    const anime = cached[`${r.sourceId}:${r.animeId}`]?.title ?? null;
    return {
      sourceId: r.sourceId,
      animeId: r.animeId,
      groupId: r.groupId,
      episodeId: r.episodeId,
      episodeNumber: r.episodeNumber,
      episodeLabel: r.episodeLabel,
      filePath: r.filePath,
      fileSizeBytes: r.fileSizeBytes,
      durationMs: r.durationMs,
      downloadedAt: r.downloadedAt,
      animeTitle: titleFor(anime, r.animeId),
      animePosterUrl: anime?.posterUrl ?? null,
    };
  });
}

/** The stored subtitle list; anything unreadable (a row written by hand, a future shape) is none. */
function parseSubtitles(raw: string | null): DownloadedSubtitle[] {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((entry): entry is DownloadedSubtitle => typeof entry?.filePath === "string" && typeof entry?.label === "string");
  } catch {
    return [];
  }
}

/** The stored extra files; anything unreadable is none, leaving the episode its main file. */
function parseParts(raw: string | null): DownloadedParts {
  if (!raw) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return {};
    const parts: DownloadedParts = {};
    for (const key of ["videoInit", "audio", "audioInit"] as const) {
      const value = (parsed as Record<string, unknown>)[key];
      if (typeof value === "string") parts[key] = value;
    }
    return parts;
  } catch {
    return {};
  }
}

export async function getDownloadedEpisode(sourceId: string, animeId: string, episodeId: string): Promise<DownloadedEpisodeFile | null> {
  const row = await getDb()
    .select({ filePath: downloadedEpisodes.filePath, durationMs: downloadedEpisodes.durationMs, quality: downloadedEpisodes.quality, subtitles: downloadedEpisodes.subtitles, parts: downloadedEpisodes.parts })
    .from(downloadedEpisodes)
    .where(and(eq(downloadedEpisodes.sourceId, sourceId), eq(downloadedEpisodes.animeId, animeId), eq(downloadedEpisodes.episodeId, episodeId)))
    .get();
  return row ? { ...row, subtitles: parseSubtitles(row.subtitles), parts: parseParts(row.parts) } : null;
}

export async function deleteDownloadedEpisodeRow(sourceId: string, animeId: string, episodeId: string): Promise<void> {
  await getDb()
    .delete(downloadedEpisodes)
    .where(and(eq(downloadedEpisodes.sourceId, sourceId), eq(downloadedEpisodes.animeId, animeId), eq(downloadedEpisodes.episodeId, episodeId)))
    .run();
}

