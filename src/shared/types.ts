// Shared IPC contract types, mirrored from the Android app's Kotlin models
// (hibiki/parsers/.../model/Models.kt) so extension output needs no reshaping
// between main and renderer.

export type AnimeType = "tv" | "ova" | "ona" | "movie" | "special" | string;
export type AnimeStatus = "ongoing" | "released" | "announced" | string;

// A lightweight cross-reference to another title on the *same* source (no sourceId of its own -
// Android's DetailsScreen resolves it against whichever source is currently active, see
// RelatedAnimeTitle in Models.kt). Used for both relatedAnime/franchiseAnime (prequels, sequels,
// side stories - merged into one "related" section, see anime.$sourceId.$animeId.tsx) and
// similarAnime (recommendations).
export interface RelatedAnimeTitle {
  id: string;
  title: string;
  posterUrl?: string | null;
  type?: AnimeType | null;
  year?: number | null;
  episodeCount?: number | null;
  status?: AnimeStatus | null;
}

// One title's rating from one particular ratings site (Shikimori, MAL, Kinopoisk, ...) - a source
// can carry several at once (see toRatings() in e.g. yummy-anime.js), same shape as Android's own
// TitleRating in Models.kt.
export interface TitleRating {
  source: string;
  value: number;
  votes?: number | null;
}

export interface AnimeTitle {
  id: string;
  sourceId: string;
  russianName?: string | null;
  englishName?: string | null;
  originalName?: string | null;
  synonyms?: string[];
  year?: number | null;
  type?: AnimeType | null;
  episodeCount?: number | null;
  availableEpisodeCount?: number | null;
  posterUrl?: string | null;
  /** Stills from the title as a whole - not from any one episode. Only some sources have them. */
  screenshots?: string[];
  /** This title's own page on the source's website, when the source supplies one. Only it can: an
   * id here is whatever that source identifies titles by, which is often not what its URLs use. */
  pageUrl?: string | null;
  status?: AnimeStatus | null;
  description?: string | null;
  nextEpisodeAt?: number | null;
  genres?: string[];
  ratings?: TitleRating[];
  ageRating?: string | null;
  relatedAnime?: RelatedAnimeTitle[];
  franchiseAnime?: RelatedAnimeTitle[];
  similarAnime?: RelatedAnimeTitle[];
}

export interface Episode {
  id: string;
  number: number;
  title?: string | null;
}

export interface PlaybackGroup {
  id: string;
  title: string;
  episodes: Episode[];
  qualityLabel?: string | null;
}

// "DIRECT_DASH" has no Android equivalent in PlayerType (source extensions never return it
// directly) - Android instead carries an internal PlaybackStreamType.DASH assigned only *after*
// a resolver resolves an EMBED link (see PlayerScreen.kt), decoupled from the network-facing
// PlayerLink model. This app reuses PlayerLink end-to-end for both raw and resolved links (see
// runtime.ts's resolveEmbedLinks), so the same distinction is made here instead: "DIRECT_DASH"
// only ever appears on a link a resolver produced (e.g. extractors/aksor.js), never one a source's
// own getPlayerLinks() returns.
export type PlayerLinkType = "DIRECT_HLS" | "DIRECT_MP4" | "DIRECT_DASH" | "EMBED";

// `type`, not `kind` - matches what every source/resolver actually emits (yummy-anime.js,
// kodik.js, ...) and Android's own PlaybackSegment (WatchModels.kt) - confirmed live against a
// real getPlayerLinks() response, where segments come back as {"type":"ENDING",...}.
export interface VideoSegment {
  type: "OPENING" | "ENDING" | "UNKNOWN";
  startMs: number;
  endMs: number;
}

export interface SubtitleTrack {
  url: string;
  label?: string | null;
  language?: string | null;
  headers?: Record<string, string> | null;
}

export interface PlayerLink {
  url: string;
  type: PlayerLinkType;
  quality?: string | null;
  headers?: Record<string, string> | null;
  playerName?: string | null;
  translation?: string | null;
  // Which sound this stream has, when a player serves one episode of one dub as several streams
  // that differ only in audio (Alloha's audio tracks). Picked in the player's settings.
  audioTrack?: string | null;
  segments?: VideoSegment[];
  videoId?: string | null;
  audioUrl?: string | null;
  audioHeaders?: Record<string, string> | null;
  subtitles?: SubtitleTrack[];
}

export interface DownloadRequest {
  sourceId: string;
  animeId: string;
  groupId: string;
  episodeId: string;
  episodeNumber: number;
  animeTitle: string;
  episodeLabel: string;
  // Matched exactly against a candidate PlayerLink's own `quality` (see downloads.ts's
  // selectPlayerLink) - unset falls back to the previous "best available" pick.
  quality?: string | null;
  // The link's player (an APK source's hoster, a site's mirror), matched together with `quality`:
  // two players can each offer a "1080p".
  playerName?: string | null;
}

/** A subtitle track saved beside a downloaded episode, already converted to WebVTT. */
export interface DownloadedSubtitle {
  filePath: string;
  label: string;
  language: string | null;
}

/**
 * The other files of a downloaded episode whose stream was more than one file: the separate audio
 * track, and the fMP4 init segment each stream's fragments need in front of them.
 */
export interface DownloadedParts {
  videoInit?: string;
  audio?: string;
  audioInit?: string;
}

/** What playback needs to know about a downloaded episode. */
export interface DownloadedEpisodeFile {
  filePath: string;
  durationMs: number | null;
  quality: string | null;
  subtitles: DownloadedSubtitle[];
  parts: DownloadedParts;
}

// A finished download, as listed on the "Downloaded episodes" screen - `animeTitle`/`animePosterUrl`
// come along from cachedAnime (see main/offlineCache.ts) so that screen doesn't need a live,
// per-title round trip to the source just to render a poster grid of stuff already on disk.
export interface DownloadedEpisode {
  sourceId: string;
  animeId: string;
  groupId: string;
  episodeId: string;
  episodeNumber: number;
  episodeLabel: string;
  filePath: string;
  fileSizeBytes: number;
  durationMs: number | null;
  downloadedAt: number;
  animeTitle: string;
  animePosterUrl: string | null;
}

export interface DownloadProgress {
  episodeId: string;
  status: "queued" | "downloading" | "paused" | "done" | "error" | "unsupported" | "cancelled";
  percent?: number;
  filePath?: string;
  message?: string;
}

export type SourceCapability =
  | "LATEST_RELEASES"
  | "PLAYBACK"
  | "RELATED_TITLES"
  | "SIMILAR_TITLES"
  // What a source can do once someone is signed in to it. Each one gates a piece of UI, so a
  // source that declares nothing here looks exactly as it does today.
  | "ACCOUNT"
  | "COMMENTS"
  | "REVIEWS"
  | "LIBRARY_SYNC"
  // Reports watching itself - an episode counted and the minutes actually spent in it - to the
  // account, rather than only what is in a list.
  | "ACTIVITY_SYNC";

/**
 * One row on a source's settings page, as the source itself declares it.
 *
 * The app renders these without knowing which source it is looking at - it understands field
 * types, not source names. "ACCOUNT" is the one that is not a value at all: it stands for the
 * sign-in block, which the app draws itself from the source's login/logout/getAccount methods.
 */
export type SourceSettingType =
  | "ACCOUNT"
  // Like TOGGLE, but the app knows what it means: turning it on is the moment two libraries that
  // already disagree have to be reconciled, and that is a question only the person can answer. A
  // type rather than a well-known key, so the screen still knows nothing about any one source.
  | "LIBRARY_SYNC"
  // Like TOGGLE, and known to the app for the same reason as LIBRARY_SYNC: it decides whether
  // watching is reported at all, which the player has to be able to ask without knowing the
  // source.
  | "ACTIVITY_SYNC"
  | "TOGGLE"
  | "TEXT"
  | "SELECT";

export interface SourceSetting {
  key: string;
  type: SourceSettingType;
  /** The fallback wording, in whatever language the source author wrote it. */
  title: string;
  description?: string | null;
  /**
   * The same two, per language tag, so a source's own rows are not stuck in one language while
   * the app around them is translated. Keyed by the tags the app uses ("ru", "uk", ...); a missing
   * one falls back to `title`/`description` above, which is why those stay required.
   */
  titleI18n?: Record<string, string> | null;
  descriptionI18n?: Record<string, string> | null;
  /** SELECT only. */
  options?: SearchFilterOption[];
  /** TOGGLE and TEXT. Absent means off / empty. */
  default?: boolean | string;
  /**
   * ACCOUNT only, and all optional - a source whose site doesn't call its own two fields "login"
   * and "password" (an email address, say) can say so instead of the app's generic wording
   * showing up in the login form. Same per-language shape as title/descriptionI18n above.
   */
  loginLabel?: string | null;
  loginLabelI18n?: Record<string, string> | null;
  passwordLabel?: string | null;
  passwordLabelI18n?: Record<string, string> | null;
  /**
   * ACCOUNT only. The scale this source's own rating actually runs on (its site's real 1-N) -
   * defaults to 10 when absent, but that was never a fact about every source, only the one the
   * host used to assume for all of them regardless of what RatingButton actually sent back.
   */
  ratingScale?: number;
  /**
   * ACCOUNT only, and both optional together - a source whose site has no simple login+password
   * API of its own (VK/Discord/Telegram buttons, a passkey, a CAPTCHA on the login form, whatever
   * that page actually asks for) points these at it instead. The host opens `webLoginUrl` in a
   * real window and, once a cookie named `webLoginSuccessCookie` shows up for it, hands that
   * domain's cookies to the source's own `loginWeb(cookiesJson)` - what they mean is entirely its
   * business. A source with these set gets a single "sign in on the site" button in place of the
   * login/password fields, not alongside them.
   */
  webLoginUrl?: string;
  webLoginSuccessCookie?: string;
}

/** Sources the data here (library, history, ratings) refers to but which are not installed. */
export interface MissingSources {
  /** Installable from a configured repository; one entry per APK package. */
  available: Array<{ extension: MarketplaceExtension; repositoryUrl: string }>;
  /** Ids no configured repository has. */
  unavailable: string[];
}

/** Who is signed in to a source, as far as the source is concerned. */
export interface SourceAccount {
  id: string;
  name: string;
  avatarUrl?: string | null;
  profileUrl?: string | null;
}

/** One row of a source account's own library, as the source reports it. */
export interface SourceLibraryEntry {
  animeId: string;
  title?: string | null;
  posterUrl?: string | null;
  category: LibraryCategory;
  rating?: number | null;
}

export interface SourceComment {
  id: string;
  authorName: string;
  authorAvatarUrl?: string | null;
  text: string;
  createdAt: number;
  /** Both sides separately, the way a site shows them - a host given only the difference could not
   * get back to "4 up, 1 down". */
  likes?: number;
  dislikes?: number;
  /** What the signed-in account already voted on this comment: 1, -1 or 0. Null while signed out,
   * which is not the same as having voted nothing. */
  viewerVote?: number | null;
  replyCount?: number;
  /** Set on replies, so a flat list can still be drawn as threads. */
  parentId?: string | null;
}

export interface SourceReview {
  id: string;
  authorName: string;
  authorAvatarUrl?: string | null;
  text: string;
  createdAt: number;
  rating?: number | null;
  likes?: number;
}

export interface SourceInfo {
  id: string;
  name: string;
  version: string;
  iconUrl?: string | null;
  lang?: string | null;
  /** Whether this installed source is marked 18+ by its manifest. */
  isNsfw: boolean;
  capabilities: SourceCapability[];
  runtime?: "NODE" | "BROWSER";
  /** Empty for every source that does not declare any - which is all of them until one does. */
  settings: SourceSetting[];
  /** The source's own homepage, from its manifest - the fallback for a title page's "open on site"
   * button when the source doesn't supply that specific title's own pageUrl (see AnimeTitle). */
  website?: string | null;
}

// A source-declared (id, display title) pair - options come from the source itself (e.g. its own
// genre list), never hardcoded on the client. Mirrors Android's SearchFilterOption.
export interface SearchFilterOption {
  id: string;
  title: string;
}

// What populates the filter panel for one source, fetched from the extension's getSettings().
// Filters are wholly the source's: the host has no notion of "genre" or "year", it draws whatever
// the source declares and hands the values back untouched.
export interface SearchFilterCatalog {
  sortOptions: SearchFilterOption[];
  filters: SearchFilterDef[];
}

/**
 * One source-defined filter; the control is drawn from `type`, and the chosen value goes back to the
 * source in SearchRequest.filters[id] with the shape below.
 *
 *   select    one option or none      -> "option-id"
 *   multi     any number of options   -> ["option-id", ...]
 *   tristate  include and/or exclude  -> { include: [...], exclude: [...] }
 *   text      free text               -> "typed text"
 *   range     numeric bounds          -> { from?: number, to?: number }   (min/max on the def)
 *
 * An unset filter is absent from the request, never an empty value.
 */
export interface SearchFilterDef {
  id: string;
  title: string;
  type: "select" | "multi" | "tristate" | "text" | "range";
  options?: SearchFilterOption[];
  min?: number;
  max?: number;
  /** `select` only: the options are sort orders that can also run backwards. The value is then
   * { include: [id] } for ascending and { exclude: [id] } for descending, instead of a plain id. */
  directional?: boolean;
}

export type FilterValue = string | string[] | { include: string[]; exclude: string[] } | { from?: number; to?: number };
export type FilterValues = Record<string, FilterValue>;

// A source not yet installed, as listed by a repository's index.json (see hibiki-sources'
// repository/index.json / extensions/<id>.manifest.json convention). Mirrors the Android app's
// MarketplaceExtension so both clients can talk to the same repositories.
export interface MarketplaceExtension {
  id: string;
  name: string;
  version: string;
  author?: string | null;
  website?: string | null;
  iconUrl?: string | null;
  lang: string;
  capabilities: SourceCapability[];
  resolverDependencies: string[];
  isNsfw: boolean;
  type: string; // "source" | "player-resolver"
  /** For a JS source its manifest; for an APK source (Android) the APK file itself. */
  manifestUrl: string;
  /** Set for an APK source (an Aniyomi extension, Android only): the package it comes in. One
   * package can hold several sources, each its own entry; they install and uninstall together. */
  apkPackage?: string;
}

export type RepositoryFetchResult =
  | { url: string; ok: true; extensions: MarketplaceExtension[] }
  | { url: string; ok: false; error: string };

/** A device this one syncs with (see core/sync). */
export interface SyncDevice {
  deviceId: string;
  name: string;
  lastSyncAt: number | null;
  pairedAt: number;
  /** This device syncs with that one itself (it entered that one's code); otherwise that one does. */
  connects: boolean;
}

/**
 * Whose data stays when two devices pair: both, joined ("merge"); this device's, replacing the other
 * one's entirely ("keep-here"); or the other one's, replacing this one's ("take-there").
 */
export type SyncPairMode = "merge" | "keep-here" | "take-there";

/** A device found on the network that this one can pair with. */
export interface SyncCandidate {
  deviceId: string;
  name: string;
  host: string;
  port: number;
  kind: "computer" | "phone";
}

/** Why a title is recommended - shown under its card. */
export type RecommendationReason =
  | { kind: "similar"; to: string[] }
  | { kind: "genres"; genres: string[] }
  | { kind: "continues"; of: string };

export interface RecommendedTitle {
  anime: AnimeTitle;
  reason: RecommendationReason;
}

/** Recommendations from one source for what was watched on it (see core/recommendations). */
export interface SourceRecommendations {
  /** Titles on this source with enough episodes watched to count as liked. */
  seedCount: number;
  /** How many such titles recommendations need; `picks` stays empty below it. */
  neededSeeds: number;
  picks: RecommendedTitle[];
  /** Later entries of watched titles' franchises: the next season, a sequel. */
  continuations: RecommendedTitle[];
}

export interface SearchRequest {
  query?: string;
  offset?: number;
  limit?: number;
  sort?: string;
  /** Values of the source's filters (SearchFilterDef), keyed by filter id. */
  filters?: FilterValues;
}

export type LibraryCategory = "watching" | "planned" | "completed" | "dropped" | "on_hold" | "favorite";

/**
 * What became of a rating beyond this machine.
 *
 * A score is always kept locally; whether it reached the source's account is a separate answer, and
 * one worth showing - a rating that never left looked exactly like one that did.
 */
export interface RatingSyncResult {
  synced: boolean;
  /** "unsupported": the source has no account library to rate into. "signed-out": it has one and
   * nobody is signed in. "failed": it was tried and the source refused or could not be reached. */
  reason?: "unsupported" | "signed-out" | "failed";
}

export interface LibraryEntry {
  animeId: string;
  sourceId: string;
  category: LibraryCategory;
  addedAt: number;
  anime: AnimeTitle;
}

export interface WatchProgress {
  sourceId: string;
  titleId: string;
  episodeId: string;
  episodeNumber: number;
  // The playback group this episode was watched under - null for progress saved before this field
  // existed. Lets a "continue watching" card build a full `/watch/...` URL (which needs a group)
  // on its own instead of only ever linking to the anime detail page.
  groupId?: string | null;
  quality?: string | null;
  // The dub studio/player this episode was last watched through - preferred over the source's own
  // default link ordering when resuming, so a deliberately-picked dub sticks across episodes.
  translation?: string | null;
  playerName?: string | null;
  positionMs: number;
  durationMs: number;
  watched: boolean;
  updatedAt: number;
  // A JPEG data URL of the video frame at the moment playback last stopped (pause, or leaving the
  // episode) - see VideoPlayer's onCaptureThumbnail. Null until the first capture happens for this
  // episode, and always null for EMBED playback (no <video> element of our own to draw from).
  thumbnailDataUrl?: string | null;
  // How much of this save actually played, measured by the player rather than inferred from how
  // far the position moved. Absent for callers that only move the position (marking an episode
  // watched from a list), which fall back to that distance - see progressUpsert.
  watchedDeltaMs?: number;
}

export interface DailyActivity {
  date: string; // YYYY-MM-DD, local time
  watchedMs: number;
  completedCount: number;
}

// One achievement tier cleared - see achievements.ts's tiersClearedInRange for how `kind` (a tier
// id, e.g. "collector_10") maps back to an icon/title for display.
export interface XpEvent {
  id: number;
  kind: string;
  xp: number;
  createdAt: number;
}

// Mirrors the fields Android's DiscordRpcManager.DiscordPlaybackPresence puts on the Rich Presence
// card (title as `details`, dub as `state`, timeline via timestamps) - see discordRpc.ts for why
// the desktop transport (local IPC to the Discord client) can't carry a per-title cover image the
// way Android's gateway-based one does.
export interface DiscordPresence {
  animeTitle: string;
  translation: string | null;
  episodeNumber: number | null;
  positionMs: number;
  durationMs: number;
  isPlaying: boolean;
  posterUrl: string | null;
  /** Exactly what a "hibiki://watch/..." deep link needs to reopen this same episode/dub - see the
   * Rich Presence "Watch" button in discordRpc.ts and DeepLinkHandler on the receiving end. */
  sourceId: string;
  animeId: string;
  groupId: string;
  episodeId: string;
}

/** What a "hibiki://watch/<sourceId>/<animeId>/<groupId>/<episodeId>" deep link resolves to - see
 * main/deepLink.ts (parsing) and components/DeepLinkHandler.tsx (acting on it). */
export interface DeepLinkWatchTarget {
  sourceId: string;
  animeId: string;
  groupId: string;
  episodeId: string;
}

/** A GitHub release newer than the running build - see main/appUpdates.ts. */
export interface AppUpdate {
  /** Without the leading "v" of the tag. */
  version: string;
  releaseUrl: string;
  downloadUrl: string;
  fileName: string;
  sizeBytes: number;
  notes: string;
  publishedAt: string | null;
}

export interface UpdateDownloadProgress {
  receivedBytes: number;
  totalBytes: number;
}

/**
 * Everything installed, read in one go.
 *
 * One snapshot rather than two queries because these two halves together answer a single question
 * - "does this source have an update" - and asking for them separately let them drift: the
 * resolver half was read once at app start and never refreshed, so a source stayed marked as
 * updatable forever after its update had actually applied.
 */
export interface InstalledVersions {
  /** Source id -> installed version. */
  sources: Record<string, string>;
  /** Player-resolver id -> installed version. Resolvers never appear in the sources list. */
  resolvers: Record<string, string>;
}

/** A title read back out of the on-disk cache, with when it was last written. */
export interface CachedAnimeEntry {
  title: AnimeTitle;
  cachedAt: number;
}

/** The provider/dub the user last chose, used to order resolution before playback starts. */
export interface PlayerLinkPreference {
  translation?: string | null;
  playerName?: string | null;
}

/** An episode-group list read from the on-disk cache, with when it was last refreshed. */
export interface CachedPlaybackGroupsEntry {
  groups: PlaybackGroup[];
  cachedAt: number;
}

/** One Electron process in a memory snapshot - see main/memoryDiagnostics.ts. */
export interface MemoryProcess {
  pid: number;
  /** Electron's own process type: Browser (main), Tab (a renderer), GPU, Utility, ... */
  type: string;
  /** The Chromium service name for utility processes ("Network Service", ...), when it has one. */
  name: string | null;
  /** What a renderer is showing. The app's own window and the hidden resolver windows differ here. */
  url: string | null;
  /** Working set in MB: resident memory, including pages shared with other processes. */
  workingSetMb: number;
}

export interface MemorySnapshot {
  time: number;
  processes: MemoryProcess[];
  /** Sum of the working sets. Shared pages are counted once per process, so it overstates a little. */
  totalMb: number;
  /** The main process's own JS heap, separate from its working set above. */
  mainHeapUsedMb: number;
  /** Windows the app created that are not the visible main one (hidden resolver/fetch windows). */
  rendererCount: number;
}

// --- Tracking (AniList now, MyAnimeList later) ----------------------------------------------------
// A tracker is a list site the library is mirrored to. Only tracking: a linked entry's names and
// numbers are shown as what the tracker says, never written over the source's own title data.

export type TrackerId = "anilist";

/** A list status on a tracker, in the app's own words (each tracker maps its own onto these). */
export type TrackerStatus = "watching" | "planned" | "completed" | "dropped" | "on_hold" | "rewatching";

export interface TrackerAccount {
  tracker: TrackerId;
  /** Whether this build can sign in at all (AniList needs a client id baked in at build time). */
  configured: boolean;
  user: { id: number; name: string; avatarUrl: string | null } | null;
  /** Epoch ms the sign-in runs out (AniList: a year), null when unknown. */
  expiresAt: number | null;
  /** A sign-in that ran out or was revoked: the account is still shown, and asks to sign in again. */
  needsSignIn: boolean;
  /** Why the last sign-in did not complete, until the next attempt. */
  signInError: string | null;
}

/** One title on the tracker, as its search shows it - for picking a link by hand. */
export interface TrackerMedia {
  id: number;
  title: string;
  /** Other names, for telling candidates apart. */
  altTitle: string | null;
  year: number | null;
  /** The tracker's own format label (TV, MOVIE, OVA...). */
  format: string | null;
  episodes: number | null;
  coverUrl: string | null;
  url: string;
}

/** Where a title of a source stands on a tracker. */
export interface TrackerLink {
  tracker: TrackerId;
  sourceId: string;
  animeId: string;
  /** "auto": matched by the app; "user": picked by hand. */
  linkedBy: "auto" | "user";
  media: TrackerMedia;
  /** The account's list entry for it; null when the title is on no list there. */
  entry: { status: TrackerStatus | null; progress: number; favourite: boolean } | null;
}

export interface TrackerImportReport {
  added: number;
  updated: number;
  /** Tracker titles no title of the source could be confidently matched to. */
  unmatched: string[];
  failed: number;
}

export interface TrackerImportProgress {
  done: number;
  total: number;
}
