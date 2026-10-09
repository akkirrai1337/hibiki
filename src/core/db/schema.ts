import { sqliteTable, text, integer, primaryKey, index, uniqueIndex } from "drizzle-orm/sqlite-core";

export const library = sqliteTable(
  "library",
  {
    animeId: text("anime_id").notNull(),
    sourceId: text("source_id").notNull(),
    category: text("category").notNull(), // watching | planned | completed | dropped | on_hold | favorite | saved
    addedAt: integer("added_at").notNull(),
    animeJson: text("anime_json").notNull(),
    // When the category last changed - what decides between two devices' versions (sync). Set by a
    // trigger, so no writer has to remember it.
    updatedAt: integer("updated_at").notNull().default(0),
    // Local change counter for device sync (core/sync): bumped by triggers on every write.
    changeSeq: integer("change_seq").notNull().default(0),
  },
  (t) => [primaryKey({ columns: [t.sourceId, t.animeId] }), index("library_change_seq").on(t.changeSeq)],
);

export const watchProgress = sqliteTable(
  "watch_progress",
  {
    sourceId: text("source_id").notNull(),
    titleId: text("title_id").notNull(),
    episodeId: text("episode_id").notNull(),
    episodeNumber: integer("episode_number").notNull(),
    // The playback group this episode was watched under - lets a "continue watching" card jump
    // straight into `/watch/$sourceId/$animeId/$groupId/$episodeId` instead of only ever landing
    // on the anime detail page (which has to be told a group anyway before it can build that same
    // URL itself). Null for progress saved before this column existed.
    groupId: text("group_id"),
    quality: text("quality"),
    // The dub studio/player this episode was last watched through (see watch route's `link`) -
    // resuming picks a matching link over the source's default priority order when one's
    // available, so switching to "Английская озвучка" once actually sticks across episodes/
    // sessions instead of quietly reverting the next time this title's watched.
    translation: text("translation"),
    playerName: text("player_name"),
    positionMs: integer("position_ms").notNull(),
    durationMs: integer("duration_ms").notNull(),
    watched: integer("watched", { mode: "boolean" }).notNull().default(false),
    updatedAt: integer("updated_at").notNull(),
    // A JPEG data URL of the last frame seen for this episode (see VideoPlayer's
    // onCaptureThumbnail) - powers the history page's per-episode thumbnails. Null until the first
    // capture, and for episodes saved before this column existed.
    thumbnailDataUrl: text("thumbnail_data_url"),
    changeSeq: integer("change_seq").notNull().default(0),
  },
  (t) => [primaryKey({ columns: [t.sourceId, t.titleId, t.episodeId] }), index("watch_progress_change_seq").on(t.changeSeq)],
);

export const dailyActivity = sqliteTable(
  "daily_activity",
  {
    date: text("date").notNull(), // YYYY-MM-DD
    // Which device watched: each one counts only its own, and a day's total is their sum - so two
    // devices' minutes add up instead of one overwriting the other when they sync.
    deviceId: text("device_id").notNull().default(""),
    watchedMs: integer("watched_ms").notNull().default(0),
    completedCount: integer("completed_count").notNull().default(0),
    changeSeq: integer("change_seq").notNull().default(0),
  },
  (t) => [primaryKey({ columns: [t.date, t.deviceId] }), index("daily_activity_change_seq").on(t.changeSeq)],
);

export const sourceRepositories = sqliteTable("source_repositories", {
  url: text("url").primaryKey(),
  addedAt: integer("added_at").notNull(),
});

// One row per achievement tier actually cleared (see achievements.ts's tiersClearedInRange) - the
// only genuinely discrete "+N XP" moments in the whole system. The steady per-hour-watched trickle
// (levelProgress.ts) isn't logged here at all - it's a continuous total, not a series of events,
// and would just be noise at one entry every 6 minutes of watching.
export const xpEvents = sqliteTable(
  "xp_events",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    kind: text("kind").notNull(), // an achievement tier id, e.g. "collector_10"
    xp: integer("xp").notNull(),
    createdAt: integer("created_at").notNull(),
    // The same event on every device: the local id differs from one database to another.
    uid: text("uid"),
    changeSeq: integer("change_seq").notNull().default(0),
  },
  (t) => [uniqueIndex("xp_events_uid").on(t.uid), index("xp_events_change_seq").on(t.changeSeq)],
);

// One row per episode a download actually finished for (see main/ipc/downloads.ts) - the
// foundation offline viewing is built on. `filePath` points at wherever it landed under this
// app's own userData/downloads folder.
export const downloadedEpisodes = sqliteTable(
  "downloaded_episodes",
  {
    sourceId: text("source_id").notNull(),
    animeId: text("anime_id").notNull(),
    groupId: text("group_id").notNull(),
    episodeId: text("episode_id").notNull(),
    episodeNumber: integer("episode_number").notNull(),
    episodeLabel: text("episode_label").notNull(),
    filePath: text("file_path").notNull(),
    fileSizeBytes: integer("file_size_bytes").notNull(),
    // Summed from the source HLS playlist's own #EXTINF values while downloading (see
    // downloadHls) - null for a DIRECT_MP4 download (that container reports its own real duration
    // natively, no synthetic playlist/wrapper ever needed for it) or for an episode downloaded
    // before this column existed.
    durationMs: integer("duration_ms"),
    // The PlayerLink's own `quality` at the time it was downloaded (e.g. "720p") - lets offline
    // playback show what quality the file actually is instead of a dash, and mark that row as
    // fixed/non-switchable rather than offering picks from the live source's quality list, which
    // doesn't apply to a file already on disk. Null for an episode downloaded before this column
    // existed, or from a source whose links never carried a quality label to begin with.
    quality: text("quality"),
    // The episode's subtitle tracks saved beside the video, converted to WebVTT: a JSON array of
    // { filePath, label, language } (DownloadedSubtitle). Null when there were none.
    subtitles: text("subtitles"),
    // Files beside `filePath` that playback also needs: a separate audio track and the fMP4 init
    // segments of either stream. A JSON DownloadedParts; null when the episode is one file.
    parts: text("parts"),
    downloadedAt: integer("downloaded_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.sourceId, t.animeId, t.episodeId] })],
);

// A snapshot of an anime's own detail (description, genres, poster, ...) taken the moment one of
// its episodes finishes downloading - lets the title page still show something meaningful (short
// of playback of anything not itself downloaded) when the source it came from isn't reachable.
// Deliberately excludes relatedAnime/franchiseAnime/similarAnime (see sources.ts's own stripping
// before this gets written) - those aren't useful offline anyway (nothing to navigate to) and
// would otherwise be the single biggest thing bloating this table.
export const cachedAnime = sqliteTable(
  "cached_anime",
  {
    sourceId: text("source_id").notNull(),
    animeId: text("anime_id").notNull(),
    animeJson: text("anime_json").notNull(),
    cachedAt: integer("cached_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.sourceId, t.animeId] })],
);

// Same idea as cachedAnime, but for its playback groups/episode list - the title page's episode
// grid needs this to render offline too, not just the description above it.
export const cachedPlaybackGroups = sqliteTable(
  "cached_playback_groups",
  {
    sourceId: text("source_id").notNull(),
    animeId: text("anime_id").notNull(),
    groupsJson: text("groups_json").notNull(),
    cachedAt: integer("cached_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.sourceId, t.animeId] })],
);

// What the user rated a title, on the source's own scale (YummyAnime and every other site here
// rate out of 10).
//
// Its own table rather than a column on `library`, because rating a title and keeping it in a list
// are separate acts on every source that has both: rating something is not a reason to add it to a
// library, and removing it from the library is not a reason to forget what it was rated.
export const titleRatings = sqliteTable(
  "title_ratings",
  {
    sourceId: text("source_id").notNull(),
    animeId: text("anime_id").notNull(),
    rating: integer("rating").notNull(),
    ratedAt: integer("rated_at").notNull(),
    changeSeq: integer("change_seq").notNull().default(0),
  },
  (t) => [primaryKey({ columns: [t.sourceId, t.animeId] }), index("title_ratings_change_seq").on(t.changeSeq)],
);

// --- Device sync (core/sync) ----------------------------------------------------------------------

/** This database's own facts for sync: its device id and its change counter ("seq"). */
export const syncState = sqliteTable("sync_state", {
  key: text("key").primaryKey(),
  value: text("value").notNull(),
});

/** Rows deleted from synced tables, so the deletion reaches the other devices too. Written by triggers. */
export const syncTombstones = sqliteTable(
  "sync_tombstones",
  {
    tbl: text("tbl").notNull(),
    key: text("key").notNull(),
    deletedAt: integer("deleted_at").notNull(),
    changeSeq: integer("change_seq").notNull().default(0),
  },
  (t) => [primaryKey({ columns: [t.tbl, t.key] }), index("sync_tombstones_change_seq").on(t.changeSeq)],
);

/** Devices this one is paired with, and how far each side has seen the other's changes. */
export const syncPeers = sqliteTable("sync_peers", {
  deviceId: text("device_id").primaryKey(),
  name: text("name").notNull(),
  // The shared key, encrypted with the platform's secure store (base64 ciphertext).
  keyCiphertext: text("key_ciphertext").notNull(),
  // Our own change counter as of the last batch the peer acknowledged.
  sentSeq: integer("sent_seq").notNull().default(0),
  // The peer's change counter as of the last batch applied here.
  receivedSeq: integer("received_seq").notNull().default(0),
  lastAddress: text("last_address"),
  lastSyncAt: integer("last_sync_at"),
  pairedAt: integer("paired_at").notNull(),
  // This device reaches out to that one (it paired by entering that one's code), rather than waiting
  // to be reached. Only these are synced from here; the others sync with this one themselves.
  connects: integer("connects", { mode: "boolean" }).notNull().default(false),
  // A replacement picked at pairing, until the first exchange carries it out: "send" - that device's
  // data is replaced by this one's; "take" - this device's data is replaced by that one's.
  pendingReplace: text("pending_replace"),
});

// Which entry on a tracker (AniList) a title of a source is. One row per title and tracker, also for
// a title that was searched for and not found (remoteId null), so it is not searched again on every
// library change - see core/tracking. Only identifiers and the name shown on the title page: what a
// tracker says about the show is never stored as the title's own data.
export const trackerLinks = sqliteTable(
  "tracker_links",
  {
    tracker: text("tracker").notNull(),
    sourceId: text("source_id").notNull(),
    animeId: text("anime_id").notNull(),
    remoteId: integer("remote_id"),
    remoteTitle: text("remote_title"),
    // "auto" (matched by the app) or "user" (picked by hand, never replaced automatically).
    linkedBy: text("linked_by").notNull(),
    checkedAt: integer("checked_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tracker, t.sourceId, t.animeId] })],
);
