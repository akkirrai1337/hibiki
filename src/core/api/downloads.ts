import type { HibikiApi } from "@shared/hibikiApi";
import { IPC } from "@shared/ipc";
import { subtitleTextToVtt } from "@shared/subtitles";
import type { DownloadedParts, DownloadedSubtitle, DownloadProgress, DownloadRequest, PlayerLink } from "@shared/types";
import type { ExtensionRuntime } from "../extensions/runtime";
import {
  cacheAnime,
  cachePlaybackGroups,
  deleteDownloadedEpisodeRow,
  getDownloadedEpisode,
  listDownloadedEpisodes,
  recordDownloadedEpisode,
} from "../offlineCache";
import { logger } from "../logger";
import { getPlatform } from "../platform";

// Same ordering as the watch page's own selectPlayerLink (see the route file) - a direct,
// playable link over a third-party embed page, since only the former can actually be fetched and
// saved to disk from here.
const LINK_TYPE_PRIORITY: Record<string, number> = { DIRECT_HLS: 0, DIRECT_MP4: 0, DIRECT_DASH: 1, EMBED: 2 };

function selectPlayerLink(links: PlayerLink[], preferredQuality?: string | null, preferredPlayer?: string | null): PlayerLink | undefined {
  if (links.length === 0) return undefined;
  const sorted = [...links].sort((a, b) => (LINK_TYPE_PRIORITY[a.type] ?? 0) - (LINK_TYPE_PRIORITY[b.type] ?? 0));
  // Only ever a preference, not a hard requirement - a quality the picker offered when the dialog
  // was open (e.g. a source whose availability shifts between requests) not being present in this
  // fetch just falls back to the same type-priority pick as if nothing had been requested.
  if (preferredQuality) {
    // The exact pick first (two players can both offer a "1080p"), then the quality from any player.
    const match =
      (preferredPlayer ? sorted.find((l) => l.quality === preferredQuality && l.playerName === preferredPlayer) : undefined) ??
      sorted.find((l) => l.quality === preferredQuality);
    if (match) return match;
  }
  return sorted[0];
}

function sanitizeFilename(name: string): string {
  return name.replace(/[\\/:*?"<>|]+/g, " ").replace(/\s+/g, " ").trim().slice(0, 120) || "untitled";
}

function isAbortError(err: unknown): boolean {
  return err instanceof Error && err.name === "AbortError";
}

// One entry per episode with a download that's running *or paused* - a paused download keeps its
// entry (and its partial file) around so resume can pick up where it left off; only an explicit
// cancel removes it. `controller` is per attempt, not per download: pausing aborts the current one
// but the episode's entry (and its resume point below) survives for the next `controller` a resume
// creates.
interface DownloadState {
  request: DownloadRequest;
  runtime: ExtensionRuntime;
  link: PlayerLink;
  outFile: string;
  controller: AbortController;
  cancelled: boolean;
  // True only while a fetch for this download is actually in flight - a cancel that arrives while
  // paused (nothing in flight) has nothing for `controller.abort()` to interrupt, so it needs to
  // know to do the cleanup itself instead of relying on runDownload's catch block to get triggered.
  running: boolean;
  // HLS resume point - the streams (the video, plus its audio when that comes separately) only need
  // resolving once, then each just tracks how far through its segments its file already goes.
  hlsTracks?: HlsTrack[];
  // The files saved beside `outFile` (separate audio, fMP4 init segments).
  parts: DownloadedParts;
  // Summed from the playlist's own #EXTINF values the moment it's resolved - the real total
  // duration, for the synthetic single-file HLS playlist the watch page wraps a downloaded .ts in
  // to play it back (see localFileLink in the watch route) instead of a made-up placeholder.
  durationMs?: number;
  // DIRECT_MP4 resume point.
  bytesReceived: number;
  // The last percent reported to the renderer - carried over into a "paused" status so the chip
  // keeps showing where it actually left off instead of resetting to 0 until the next real
  // progress tick arrives after resuming.
  lastPercent: number;
}

async function fetchText(url: string, headers: Record<string, string> | null | undefined, signal: AbortSignal): Promise<string> {
  const res = await getPlatform().http.request({ url, headers: headers ?? undefined, signal });
  if (res.status < 200 || res.status >= 300) throw new Error(`HTTP ${res.status}`);
  return res.body;
}
type Headers = Record<string, string> | null | undefined;

/** One HLS stream being saved into its own file. */
interface HlsTrack {
  kind: "video" | "audio";
  filePath: string;
  headers: Headers;
  playlistUrl: string;
  /** The fMP4 init segment, saved first into a file of its own. */
  init?: { uri: string; filePath: string; done: boolean };
  segmentUris: string[];
  nextIndex: number;
}

/** An attribute of an HLS tag line (`NAME="value"` or `NAME=value`), unquoted. */
function tagAttribute(line: string, name: string): string | undefined {
  const match = new RegExp(`[:,]${name}=("([^"]*)"|[^,]*)`).exec(line);
  return match ? (match[2] ?? match[1]) : undefined;
}

/** Resolves a master playlist down to a single media playlist by taking its first listed variant
 * - a direct PlayerLink's own `quality` was already picked upstream, so this only exists to unwrap
 * the occasional source that still hands back a master playlist instead of the final media one.
 * When that variant's sound is a separate rendition, its URL comes back as `audioUrl`. */
async function resolveMediaPlaylist(url: string, headers: Headers, signal: AbortSignal): Promise<{ url: string; text: string; audioUrl?: string }> {
  let currentUrl = url;
  let text = await fetchText(currentUrl, headers, signal);
  let audioUrl: string | undefined;
  if (text.includes("#EXT-X-STREAM-INF")) {
    const lines = text.split("\n").map((l) => l.trim());
    const infIndex = lines.findIndex((l) => l.startsWith("#EXT-X-STREAM-INF"));
    const variantLine = lines.slice(infIndex + 1).find((l) => l && !l.startsWith("#"));
    if (!variantLine) throw new Error("empty master playlist");
    const group = tagAttribute(lines[infIndex], "AUDIO");
    const renditions = group
      ? lines.filter((l) => l.startsWith("#EXT-X-MEDIA:") && tagAttribute(l, "TYPE") === "AUDIO" && tagAttribute(l, "GROUP-ID") === group && tagAttribute(l, "URI"))
      : [];
    const rendition = renditions.find((l) => tagAttribute(l, "DEFAULT") === "YES") ?? renditions[0];
    if (rendition) audioUrl = new URL(tagAttribute(rendition, "URI")!, currentUrl).toString();
    currentUrl = new URL(variantLine, currentUrl).toString();
    text = await fetchText(currentUrl, headers, signal);
  }
  return { url: currentUrl, text, audioUrl };
}

/** The segments of a media playlist, its fMP4 init segment and its total #EXTINF duration. */
function parseMediaPlaylist(text: string): { segmentUris: string[]; initUri?: string; durationSeconds: number } {
  if (/#EXT-X-KEY:METHOD=(?!NONE)/i.test(text)) throw new Error("an encrypted stream isn't supported");
  if (/#EXT-X-BYTERANGE/i.test(text)) throw new Error("a byte-range playlist isn't supported");
  const segmentUris: string[] = [];
  let initUri: string | undefined;
  let durationSeconds = 0;
  let pendingExtinf = 0;
  for (const rawLine of text.split("\n")) {
    const line = rawLine.trim();
    if (line.startsWith("#EXT-X-MAP:")) {
      if (initUri !== undefined) throw new Error("a playlist with several init segments isn't supported");
      if (tagAttribute(line, "BYTERANGE")) throw new Error("a byte-range playlist isn't supported");
      initUri = tagAttribute(line, "URI");
      continue;
    }
    const extinf = /^#EXTINF:([\d.]+)/.exec(line);
    if (extinf) {
      pendingExtinf = Number(extinf[1]);
      continue;
    }
    if (!line || line.startsWith("#")) continue;
    segmentUris.push(line);
    durationSeconds += pendingExtinf;
    pendingExtinf = 0;
  }
  if (segmentUris.length === 0) throw new Error("the playlist has no segments");
  return { segmentUris, initUri, durationSeconds };
}

/** Resolves one stream and decides the files it lands in: `<base><suffix>.<ext>`, plus an
 * `.init.mp4` beside it for fMP4. */
async function prepareHlsTrack(kind: HlsTrack["kind"], url: string, headers: Headers, base: string, signal: AbortSignal) {
  const resolved = await resolveMediaPlaylist(url, headers, signal);
  const media = parseMediaPlaylist(resolved.text);
  const suffix = kind === "audio" ? ".audio" : "";
  // fMP4 fragments only play behind their init segment; packed audio (.aac) keeps its own type.
  const firstSegmentPath = new URL(media.segmentUris[0], resolved.url).pathname.toLowerCase();
  const ext = media.initUri ? "m4s" : kind === "audio" && firstSegmentPath.endsWith(".aac") ? "aac" : "ts";
  const track: HlsTrack = {
    kind,
    filePath: `${base}${suffix}.${ext}`,
    headers,
    playlistUrl: resolved.url,
    init: media.initUri ? { uri: media.initUri, filePath: `${base}${suffix}.init.mp4`, done: false } : undefined,
    segmentUris: media.segmentUris,
    nextIndex: 0,
  };
  return { track, durationSeconds: media.durationSeconds, audioUrl: resolved.audioUrl };
}

/** Downloads whatever's left of an HLS stream (from each track's `nextIndex` on - 0 for a fresh
 * start, wherever a previous pause left off otherwise) and appends each segment's raw bytes to that
 * track's file - MPEG-TS, packed AAC and fMP4 fragments are all made to be concatenated, so the
 * result plays without an ffmpeg remux this app doesn't bundle. A separate audio track (the link's
 * `audioUrl`, or the master playlist's audio rendition) is saved the same way into a file of its
 * own, and an fMP4 init segment into one more; offline playback pairs them up again. AES-encrypted
 * and byte-range streams aren't supported - those surface as a plain thrown error, which the caller
 * reports back as a failed download rather than silently producing an undecodable file.
 *
 * Each segment is written only once it has fully arrived (`buffered`), so a pause in the middle of
 * one leaves the file ending exactly at the previous segment and resume appends it whole. */
async function downloadHls(state: DownloadState, onProgress: (percent: number) => void): Promise<void> {
  const { link, controller } = state;
  if (!state.hlsTracks) {
    const base = state.outFile.replace(/\.[^./\\]+$/, "");
    const video = await prepareHlsTrack("video", link.url, link.headers, base, controller.signal);
    const tracks = [video.track];
    const audioUrl = link.audioUrl ?? video.audioUrl;
    if (audioUrl) tracks.push((await prepareHlsTrack("audio", audioUrl, link.audioHeaders ?? link.headers, base, controller.signal)).track);
    const audio = tracks.find((track) => track.kind === "audio");
    state.outFile = video.track.filePath;
    state.parts = {
      ...(video.track.init ? { videoInit: video.track.init.filePath } : {}),
      ...(audio ? { audio: audio.filePath } : {}),
      ...(audio?.init ? { audioInit: audio.init.filePath } : {}),
    };
    state.hlsTracks = tracks;
    // #EXTINF is mandatory per segment in the HLS spec, so this is normally an exact total: only
    // left unset (falling back to the watch page's own placeholder) if the playlist is malformed
    // enough to have none of them at all.
    if (video.durationSeconds > 0) state.durationMs = Math.round(video.durationSeconds * 1000);
  }
  const tracks = state.hlsTracks;
  const total = tracks.reduce((sum, track) => sum + track.segmentUris.length + (track.init ? 1 : 0), 0);
  const reportProgress = () => {
    const done = tracks.reduce((sum, track) => sum + track.nextIndex + (track.init?.done ? 1 : 0), 0);
    onProgress(Math.round((done / total) * 100));
  };

  for (const track of tracks) {
    const fetchPiece = async (uri: string, filePath: string, append: boolean, what: string) => {
      try {
        await getPlatform().downloads.fetchToFile({
          url: new URL(uri, track.playlistUrl).toString(),
          headers: track.headers ?? undefined,
          filePath,
          append,
          buffered: true,
          signal: controller.signal,
        });
      } catch (error) {
        if (error instanceof Error && /^HTTP \d+$/.test(error.message)) throw new Error(`${track.kind} ${what}: ${error.message}`);
        throw error;
      }
    };
    if (track.init && !track.init.done) {
      await fetchPiece(track.init.uri, track.init.filePath, false, "init segment");
      track.init.done = true;
      reportProgress();
    }
    // A fresh start replaces whatever file is there; a resume continues it.
    for (let i = track.nextIndex; i < track.segmentUris.length; i++) {
      await fetchPiece(track.segmentUris[i], track.filePath, i > 0, `segment ${i + 1}/${track.segmentUris.length}`);
      track.nextIndex = i + 1;
      reportProgress();
    }
  }
}

/** Every file a download writes: the main one and the parts beside it. */
const downloadFiles = (state: { outFile: string; parts: DownloadedParts }) => [state.outFile, ...Object.values(state.parts)];

/** A plain progressive download (DIRECT_MP4), resumed via an HTTP Range request when
 * `state.bytesReceived > 0`. If the server doesn't actually honor the range (some don't, and just
 * send the whole file back with a 200 instead of a 206 Partial Content), this falls back to
 * restarting the file from scratch rather than corrupting it by appending a *second* full copy
 * onto what's already there. */
async function downloadDirect(state: DownloadState, onProgress: (percent: number) => void): Promise<void> {
  const { link, controller } = state;
  const resuming = state.bytesReceived > 0;
  const headers = { ...(link.headers ?? {}), ...(resuming ? { Range: `bytes=${state.bytesReceived}-` } : {}) };
  let rangeHonored = false;
  let base = 0;
  await getPlatform().downloads.fetchToFile({
    url: link.url,
    headers,
    filePath: state.outFile,
    append: (response) => {
      rangeHonored = response.status === 206;
      if (resuming && !rangeHonored) state.bytesReceived = 0;
      base = state.bytesReceived;
      return resuming && rangeHonored;
    },
    signal: controller.signal,
    onProgress: (received, contentLength) => {
      state.bytesReceived = base + received;
      const total = rangeHonored ? base + (contentLength ?? 0) : (contentLength ?? 0);
      if (total > 0) onProgress(Math.round((state.bytesReceived / total) * 100));
    },
  });
}

/** A file-name-safe tag for a track: its language, else its label. */
function subtitleTag(track: { label?: string | null; language?: string | null }): string {
  const raw = (track.language || track.label || "sub").toLowerCase();
  return raw.replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 24) || "sub";
}

/**
 * Every subtitle track of the episode, saved beside the video as WebVTT - the one format the
 * player's <track> reads, so nothing needs converting at playback. Best-effort: a track that fails
 * leaves the episode downloaded without it, and is named in the log.
 */
async function downloadSubtitles(state: DownloadState): Promise<DownloadedSubtitle[]> {
  const tracks = (state.link.subtitles ?? []).filter((track, index, all) => track.url && all.findIndex((other) => other.url === track.url) === index);
  if (tracks.length === 0) return [];
  const { files } = getPlatform();
  const base = state.outFile.replace(/\.[^./\\]+$/, "");
  const label = jobLabel(state.request);
  const saved: DownloadedSubtitle[] = [];
  for (const [index, track] of tracks.entries()) {
    const name = track.label ?? track.language ?? `#${index + 1}`;
    try {
      const text = await fetchText(track.url, track.headers ?? state.link.headers, state.controller.signal);
      // Read by what it holds, the way the player reads it.
      const vtt = subtitleTextToVtt(text);
      if (!vtt) throw new Error("not a subtitle file");
      const filePath = `${base}.${index + 1}.${subtitleTag(track)}.vtt`;
      await files.writeText(filePath, vtt);
      saved.push({ filePath, label: name, language: track.language ?? null });
    } catch (error) {
      if (isAbortError(error)) throw error;
      logger.warn("download", `${label}: subtitle "${name}" not saved: ${errorText(error)}`);
    }
  }
  if (saved.length > 0) logger.info("download", `${label}: ${saved.length}/${tracks.length} subtitle track(s) saved`);
  return saved;
}

/** Everything a downloaded episode's title page needs to still work offline: the episode row
 * itself, plus (best-effort - a hiccup here shouldn't fail a download that otherwise completed
 * fine) a snapshot of the anime's own detail and its playback groups, for whenever the source
 * itself isn't reachable later (see sources.ts's own fallback-to-cache). */
async function cacheForOffline(state: DownloadState, subtitles: DownloadedSubtitle[]): Promise<void> {
  const { sourceId, animeId, groupId, episodeId, episodeNumber, episodeLabel } = state.request;
  try {
    const fileSizeBytes = await downloadedSize(state);
    if (fileSizeBytes === null) throw new Error("downloaded file is missing");
    await recordDownloadedEpisode({ sourceId, animeId, groupId, episodeId, episodeNumber, episodeLabel, filePath: state.outFile, fileSizeBytes, durationMs: state.durationMs ?? null, quality: state.link.quality ?? null, subtitles, parts: state.parts });
    const [anime, groups] = await Promise.all([state.runtime.getById(sourceId, animeId), state.runtime.getPlaybackGroups(sourceId, animeId)]);
    await cacheAnime(sourceId, animeId, anime);
    await cachePlaybackGroups(sourceId, animeId, groups);
  } catch {
    // Downloaded fine either way - offline browsing for this title just won't have a description/
    // episode list ready until the next successful (online) visit happens to cache it instead.
  }
}

const removeQuietly = (filePath: string) => getPlatform().files.remove(filePath).catch(() => {});
const removeDownloadFiles = (state: { outFile: string; parts: DownloadedParts }) => Promise.all(downloadFiles(state).map(removeQuietly));

/** The bytes on disk across every file of a download; null when one of them is missing. */
async function downloadedSize(state: { outFile: string; parts: DownloadedParts }): Promise<number | null> {
  const stats = await Promise.all(downloadFiles(state).map((filePath) => getPlatform().files.stat(filePath)));
  return stats.every(Boolean) ? stats.reduce((sum, stat) => sum + stat!.size, 0) : null;
}

// At most this many transfers actually run at once - queuing the rest instead of firing off
// everything a user selects at once keeps a "download all 10" from saturating bandwidth/disk I/O
// (and hammering the source with that many simultaneous getPlayerLinks + segment requests) the
// same way one download at a time never did.
const MAX_CONCURRENT_DOWNLOADS = 2;

// A queued job is either a fresh download (still needs its link resolved - that happens lazily,
// right when it's actually dequeued, not up front for the whole batch at once) or a paused one
// waiting for a slot to resume into - both go through the same slot-limited pipe.
type QueuedJob = { kind: "start"; request: DownloadRequest } | { kind: "resume"; episodeId: string };

function jobEpisodeId(job: QueuedJob): string {
  return job.kind === "start" ? job.request.episodeId : job.episodeId;
}

/** How a download is named in the log: which title and episode, not its opaque ids. */
function jobLabel(request: DownloadRequest): string {
  return `${request.sourceId} "${request.animeTitle}" ${request.episodeLabel}`;
}

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

/** The episode-download part of `window.hibiki`. Progress (including the final
 * done/error/paused/cancelled) goes out as `downloads:progress` events. */
export function createDownloadsApi(runtime: ExtensionRuntime): Omit<HibikiApi["downloads"], "onProgress"> {
  const downloads = new Map<string, DownloadState>();
  let activeCount = 0;
  const jobQueue: QueuedJob[] = [];
  const send = (progress: DownloadProgress) => getPlatform().events.emit(IPC.downloadsProgress, progress);

  async function runDownload(state: DownloadState): Promise<void> {
    const episodeId = state.request.episodeId;
    const label = jobLabel(state.request);
    const startedAt = Date.now();
    state.running = true;
    const resuming = state.hlsTracks?.some((track) => track.nextIndex > 0 || track.init?.done) || state.bytesReceived > 0;
    logger.info("download", `${label}: ${resuming ? `resumed at ${state.lastPercent}%` : "started"} (${state.link.type}, ${state.link.quality ?? "?"}, ${state.link.playerName ?? "?"})`);
    try {
      send({ episodeId, status: "downloading", percent: state.lastPercent });
      const onProgress = (percent: number) => {
        state.lastPercent = percent;
        send({ episodeId, status: "downloading", percent });
      };
      if (state.link.type === "DIRECT_HLS") await downloadHls(state, onProgress);
      else await downloadDirect(state, onProgress);

      const subtitles = await downloadSubtitles(state);
      downloads.delete(episodeId);
      await cacheForOffline(state, subtitles);
      const size = (await downloadedSize(state)) ?? 0;
      logger.info("download", `${label}: done in ${Math.round((Date.now() - startedAt) / 1000)}s, ${(size / 1024 / 1024).toFixed(1)} MB`);
      send({ episodeId, status: "done", filePath: state.outFile });
    } catch (err) {
      if (!isAbortError(err)) {
        logger.warn("download", `${label}: failed at ${state.lastPercent}%: ${errorText(err)}`);
        downloads.delete(episodeId);
        send({ episodeId, status: "error", message: err instanceof Error ? err.message : String(err) });
        return;
      }
      if (state.cancelled) {
        downloads.delete(episodeId);
        // A half-written file left behind by a cancelled download isn't useful to anyone - clean it
        // up rather than leaving a truncated video sitting in the downloads folder.
        await removeDownloadFiles(state);
        logger.info("download", `${label}: cancelled at ${state.lastPercent}%`);
        send({ episodeId, status: "cancelled" });
      } else {
        logger.info("download", `${label}: paused at ${state.lastPercent}%`);
        // Just paused - the entry (and the partial file on disk) stays put for resume to continue.
        // `percent: state.lastPercent` so the chip keeps showing where it actually left off instead
        // of resetting to 0 (DownloadProgress.percent just wouldn't be there at all otherwise).
        send({ episodeId, status: "paused", percent: state.lastPercent });
      }
    } finally {
      state.running = false;
    }
  }

  async function resolveAndRunDownload(request: DownloadRequest): Promise<void> {
    // Wrapped in its own try/catch (distinct from runDownload's) since this part - resolving the
    // link, making the anime's folder - has no AbortController/state of its own yet to report
    // through consistently, and this now runs from inside pumpQueue's fire-and-forget dispatch,
    // where an uncaught rejection here would otherwise surface as an unhandled promise rejection
    // instead of a normal "error" status on the episode's own card.
    try {
      const links = await runtime.getPlayerLinks(request.sourceId, request.animeId, request.groupId, request.episodeId);
      const link = selectPlayerLink(links, request.quality, request.playerName);
      if (!link || link.type === "EMBED" || link.type === "DIRECT_DASH" || (link.audioUrl && link.type !== "DIRECT_HLS")) {
        const why = !link ? `no link among ${links.length}` : link.audioUrl && link.type !== "DIRECT_HLS" ? `separate audio beside ${link.type}` : link.type;
        logger.info("download", `${jobLabel(request)}: can't be downloaded (${why})`);
        send({ episodeId: request.episodeId, status: "unsupported" });
        return;
      }

      const { files, paths } = getPlatform();
      const animeDir = files.join(paths.downloads, sanitizeFilename(request.animeTitle));
      await files.mkdir(animeDir);
      const ext = link.type === "DIRECT_MP4" ? "mp4" : "ts";
      const outFile = files.join(animeDir, `${sanitizeFilename(request.episodeLabel)}.${ext}`);

      const state: DownloadState = { request, runtime, link, outFile, controller: new AbortController(), cancelled: false, running: false, parts: {}, bytesReceived: 0, lastPercent: 0 };
      downloads.set(request.episodeId, state);
      await runDownload(state);
    } catch (err) {
      logger.warn("download", `${jobLabel(request)}: could not start: ${errorText(err)}`);
      send({ episodeId: request.episodeId, status: "error", message: errorText(err) });
    }
  }

  function pumpQueue(): void {
    while (activeCount < MAX_CONCURRENT_DOWNLOADS && jobQueue.length > 0) {
      const job = jobQueue.shift()!;
      activeCount++;
      const settle = () => {
        activeCount--;
        pumpQueue();
      };
      if (job.kind === "start") {
        void resolveAndRunDownload(job.request).finally(settle);
      } else {
        const state = downloads.get(job.episodeId);
        if (!state) {
          settle();
          continue;
        }
        state.controller = new AbortController();
        void runDownload(state).finally(settle);
      }
    }
  }

  return {
    // Resolves as soon as the job is queued - a download can run for a while (potentially hundreds
    // of HLS segment requests), and the renderer only needs this call to *start* the job, not block
    // on its completion.
    async start(request: DownloadRequest): Promise<void> {
      if (downloads.has(request.episodeId) || jobQueue.some((j) => jobEpisodeId(j) === request.episodeId)) return;
      jobQueue.push({ kind: "start", request });
      send({ episodeId: request.episodeId, status: "queued" });
      pumpQueue();
    },

    async pause(episodeId: string): Promise<void> {
      // Aborting is what actually stops the in-flight request - `runDownload`'s catch block sees
      // that abort, notices `cancelled` is still false, and reports "paused" instead of tearing the
      // entry down, leaving it ready to resume into later. The freed slot (via the `.finally` in
      // pumpQueue, once the abort actually unwinds runDownload) lets the next queued job start.
      downloads.get(episodeId)?.controller.abort();
    },

    async resume(episodeId: string): Promise<void> {
      const state = downloads.get(episodeId);
      if (!state || state.running || jobQueue.some((j) => jobEpisodeId(j) === episodeId)) return;
      jobQueue.push({ kind: "resume", episodeId });
      send({ episodeId, status: "queued", percent: state.lastPercent });
      pumpQueue();
    },

    async cancel(episodeId: string): Promise<void> {
      // Still just waiting in line, never actually started - nothing for an abort to interrupt, so
      // this is the one case cancel doesn't go through runDownload's own cleanup path at all.
      const queuedIndex = jobQueue.findIndex((j) => jobEpisodeId(j) === episodeId);
      if (queuedIndex !== -1) {
        jobQueue.splice(queuedIndex, 1);
        const state = downloads.get(episodeId);
        downloads.delete(episodeId);
        if (state) await removeDownloadFiles(state);
        send({ episodeId, status: "cancelled" });
        return;
      }
      const state = downloads.get(episodeId);
      if (!state) return;
      state.cancelled = true;
      if (state.running) {
        // The abort below is what triggers the actual cleanup, via runDownload's catch block.
        state.controller.abort();
        return;
      }
      // Nothing's in flight (the download is currently paused) - there's no fetch for abort() to
      // interrupt, and so nothing that would otherwise reach runDownload's catch block to do the
      // cleanup. Do it here instead.
      downloads.delete(episodeId);
      await removeDownloadFiles(state);
      send({ episodeId, status: "cancelled" });
    },

    list: () => listDownloadedEpisodes(),
    getForEpisode: (sourceId: string, animeId: string, episodeId: string) => getDownloadedEpisode(sourceId, animeId, episodeId),

    async remove(sourceId: string, animeId: string, episodeId: string): Promise<void> {
      const row = await getDownloadedEpisode(sourceId, animeId, episodeId);
      await deleteDownloadedEpisodeRow(sourceId, animeId, episodeId);
      if (row) {
        await removeDownloadFiles({ outFile: row.filePath, parts: row.parts });
        await Promise.all(row.subtitles.map((subtitle) => removeQuietly(subtitle.filePath)));
      }
    },
  };
}
