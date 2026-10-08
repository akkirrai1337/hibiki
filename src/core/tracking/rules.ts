// What a change here means for the account's entry on a tracker - the decisions tracker.ts acts on,
// kept pure so the rules that protect someone's account are tested on their own.
import type { LibraryCategory, TrackerStatus } from "@shared/types";

export interface RemoteEntry {
  status: TrackerStatus | null;
  progress: number;
}

/** What to write, or null to write nothing. An omitted field stays as it is on the tracker. */
export interface EntryChange {
  status?: TrackerStatus;
  progress?: number;
}

/**
 * Episodes watched to the end here, against the entry there. Only ever forward: a lower count is
 * never sent (watching an old episode again is not un-watching the rest). Starting a title moves it
 * to "watching", reaching its last episode to "completed"; a rewatch keeps its status.
 */
export function progressChange(watched: number, entry: RemoteEntry | null, totalEpisodes: number | null): EntryChange | null {
  if (watched <= 0) return null;
  const progress = totalEpisodes != null && totalEpisodes > 0 ? Math.min(watched, totalEpisodes) : watched;
  if (entry && progress <= entry.progress) return null;
  if (entry?.status === "rewatching") return { progress };
  if (totalEpisodes != null && totalEpisodes > 0 && progress >= totalEpisodes) return { progress, status: "completed" };
  return entry?.status === "watching" ? { progress } : { progress, status: "watching" };
}

const CATEGORY_TO_STATUS: Partial<Record<LibraryCategory, TrackerStatus>> = {
  watching: "watching",
  planned: "planned",
  completed: "completed",
  dropped: "dropped",
  on_hold: "on_hold",
};

/**
 * A category picked here, as a status there. "favorite" is not a status (the caller marks the
 * favourite instead). Completing fills the progress in, as AniList's own site does.
 */
export function categoryChange(category: LibraryCategory, entry: RemoteEntry | null, totalEpisodes: number | null): EntryChange | null {
  const status = CATEGORY_TO_STATUS[category];
  if (!status || entry?.status === status) return null;
  // Watching again what the tracker has as a rewatch is the same thing; its rewatch count stays.
  if (status === "watching" && entry?.status === "rewatching") return null;
  if (status === "completed" && totalEpisodes != null && totalEpisodes > 0 && (entry?.progress ?? 0) < totalEpisodes) {
    return { status, progress: totalEpisodes };
  }
  return { status };
}

/**
 * Whether the tracker's favourite should change with a category move here: on when the title goes
 * into favourites, off when it leaves them for another category. The library holds one category
 * per title, so leaving favourites here is leaving them there. Null leaves it as it is - anything
 * else, including a favourite set on the website for a title that was never one here.
 */
export function favouriteChange(previous: LibraryCategory | null, category: LibraryCategory, favourite: boolean): boolean | null {
  if (category === "favorite") return favourite ? null : true;
  if (previous === "favorite" && favourite) return false;
  return null;
}

const STATUS_TO_CATEGORY: Record<TrackerStatus, LibraryCategory> = {
  watching: "watching",
  rewatching: "watching",
  planned: "planned",
  completed: "completed",
  dropped: "dropped",
  on_hold: "on_hold",
};

/** Where an entry of the account's lists goes in the library: its status's category, or favourites
 * for a favourite that is on no list. Null for neither. */
export function importedCategory(status: TrackerStatus | null, favourite: boolean): LibraryCategory | null {
  if (status) return STATUS_TO_CATEGORY[status];
  return favourite ? "favorite" : null;
}
