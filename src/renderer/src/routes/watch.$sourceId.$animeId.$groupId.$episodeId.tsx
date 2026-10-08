import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createFileRoute, useNavigate, useRouter } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { ArrowLeft, Loader2, TriangleAlert } from "lucide-react";
import type { DownloadedEpisodeFile, PlaybackGroup, PlayerLink } from "@shared/types";
import { pickDefaultLink, pickPlaybackFallback, pickPreferredLink, pickResolvedLink } from "@/lib/playerLinks";
import { usePlaybackGroups } from "@/lib/playbackGroups";
import { isGenericDubTitle } from "@/lib/dubTitle";
import { hibiki, downloadFileUrl } from "@/lib/hibiki";
import { log } from "@/lib/log";
import { usePlayerPrefsStore } from "@/stores/playerPrefsStore";
import { usePlayerSelectionStore } from "@/stores/playerSelectionStore";
import { useUiStore } from "@/stores/uiStore";
import { VideoPlayer } from "@/features/player/VideoPlayer";
import { CloudflareCheckButton } from "@/components/CloudflareCheck";
import { cloudflareCheckOf } from "@shared/cloudflare";
import { animeTitle } from "@/components/AnimeCard";
import { ACTIVITY_DAYS, buildActivitySeries, computeStreaks } from "@/components/StreakBadge";

// How long to hold the toast visible before clearing it back to null - matches StreakToast's own
// enter (480ms) + the 2s hold the user asked for. Clearing the state doesn't cut its exit
// animation short: AnimatePresence in VideoPlayer keeps it mounted through the exit variant on its
// own, so this only needs to cover enter+hold, not the full round trip.
const STREAK_TOAST_HOLD_MS = 2000;

// Longer than the streak toast's hold - this one carries two link labels worth of actual reading,
// not just a number ticking up.
const PLAYER_SWITCH_TOAST_HOLD_MS = 4500;

// What PlayerSwitchToast shows for one end of the switch - playerName is the only field every link
// reliably has; quality is worth adding when it's there since two links from the same player most
// often differ by quality, not by identity.
function playerSwitchLabel(link: PlayerLink): string {
  return link.quality ? `${link.playerName ?? "?"} (${link.quality})` : (link.playerName ?? "?");
}

export const Route = createFileRoute("/watch/$sourceId/$animeId/$groupId/$episodeId")({
  component: WatchRoute,
});

/**
 * Opening a different episode or dub is a fresh visit to the player, not a re-render of the one
 * already open.
 *
 * Arriving from the title page is a route change, so this page mounts clean. Switching dub from
 * inside the player only changes the route's params, so the same instance stayed mounted and had
 * to unwind itself: a request counter, a resolve in flight, a preference gate and several refs all
 * had to agree, in effect order, about which episode they now belonged to. When they disagreed the
 * player waited forever - visible only as a spinner, and only when switching from inside, never
 * when coming from the title page.
 *
 * Keying on the episode makes the two paths the same thing. React unmounts and remounts, so every
 * piece of that state starts where it starts on a first visit, and the ordering it depended on
 * stops existing rather than being reasoned about.
 */
function WatchRoute() {
  const { groupId, episodeId } = Route.useParams();
  // Above the key on purpose: leaving the player should stop announcing an episode, but moving to
  // the next one should not blink the presence off and straight back on. This unmounts only when
  // the route itself is left.
  useEffect(() => () => {
    hibiki.discord.clearPresence();
    // Fullscreen belongs to a wrapper that outlives the player (so it survives a switch of episode),
    // which means it no longer ends with the player on its own when the route is left.
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
  }, []);
  return <WatchPage key={`${groupId}/${episodeId}`} />;
}

// Progress ticks arrive several times a second while playing, so anything past a couple of seconds
// is a jump rather than elapsed playback.
const PLAYED_TICK_MAX_MS = 2500;

/** One episode's progress as last seen, waiting to be written when the episode is left. */
interface PendingSave {
  sourceId: string;
  titleId: string;
  episodeId: string;
  episodeNumber: number;
  groupId: string;
  translation: string | null;
  playerName: string | null;
  videoId: string | null;
  thumbnail: { dataUrl: string | null };
  positionMs: number;
  durationMs: number;
  watched: boolean;
}
// Matches Android's own DiscordRpcManager.MIN_PUBLISH_INTERVAL_MS - no need to hit the local
// Discord IPC socket every progress tick, timestamps already convey a moving playhead on their own.
const DISCORD_UPDATE_INTERVAL_MS = 16_000;

/** A downloaded HLS episode is saved as one raw MPEG-TS file (see main/ipc/downloads.ts's
 * downloadHls) - Chromium's <video> has no native demuxer for that container outside of Media
 * Source Extensions, so handing it to `video.src` directly fails immediately with
 * "FFmpegDemuxer: open context failed" (confirmed: the file itself is a perfectly valid H.264
 * stream, ffprobe reads it fine - this is purely about how a bare <video> element is willing to
 * open it). hls.js already solves exactly this for live HLS by transmuxing TS into fragmented MP4
 * for MSE - this just gives it a one-segment, single-file "playlist" (this app's downloads only
 * ever produce one output file per episode, so there's nothing to actually list) referencing the
 * local file through this app's own download-serving protocol, so hls.js's existing pipeline
 * handles a downloaded episode exactly the way it already handles a live stream's segments,
 * instead of a second parallel playback path needing its own testing/maintenance. A real
 * MP4 download (DIRECT_MP4 sources) skips all of this - Chromium plays that container natively,
 * so it's handed to `video.src` as-is, same as before.
 *
 * `durationSeconds` drives the playlist's own `#EXTINF`/`#EXT-X-TARGETDURATION` - it's the real
 * total, summed from the source playlist's own #EXTINF values while downloading (see
 * downloads.ts's downloadHls), not a placeholder: a made-up value here is exactly what showed up
 * on the timeline as a nonsense multi-hour duration before, since hls.js/the <video> element take
 * it at face value for the displayed duration and the 90%-watched threshold alike. An episode
 * downloaded before that was tracked falls back to a permissive placeholder (still wrong, but
 * only discovered as such once real data arrives - see the `ended` handling this leans on). */
function localFileLink({ filePath, durationMs, quality, subtitles: saved }: DownloadedEpisodeFile): PlayerLink {
  // Saved beside the episode as WebVTT when it was downloaded.
  const subtitles = saved.map((subtitle) => ({ url: downloadFileUrl(subtitle.filePath), label: subtitle.label, language: subtitle.language }));
  if (!filePath.toLowerCase().endsWith(".ts")) return { url: downloadFileUrl(filePath), type: "DIRECT_MP4", quality, subtitles };
  const durationSeconds = durationMs ? Math.ceil(durationMs / 1000) : 100_000;
  const playlist = [
    "#EXTM3U",
    "#EXT-X-VERSION:3",
    `#EXT-X-TARGETDURATION:${durationSeconds}`,
    "#EXT-X-PLAYLIST-TYPE:VOD",
    `#EXTINF:${durationSeconds},`,
    downloadFileUrl(filePath),
    "#EXT-X-ENDLIST",
    "",
  ].join("\n");
  return { url: `data:application/vnd.apple.mpegurl;base64,${btoa(unescape(encodeURIComponent(playlist)))}`, type: "DIRECT_HLS", quality, subtitles };
}

/** 83_000 -> "1:23", for the log. */
function clock(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = String(total % 60).padStart(2, "0");
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, "0")}:${seconds}` : `${minutes}:${seconds}`;
}

function WatchPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const router = useRouter();
  const { sourceId, animeId, groupId, episodeId } = Route.useParams();
  const discordIgnoreNsfwSources = useUiStore((s) => s.discordIgnoreNsfwSources);
  // RootLayout already asks for this exact query on startup, so normally this is a cache hit. Keep
  // sharing blocked until it answers when the privacy switch is on: an unknown source must not
  // briefly leak its title into Discord while its 18+ flag is still loading.
  const sourcesQuery = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const sourceIsNsfw = sourcesQuery.data?.some((source) => source.id === sourceId && source.isNsfw) ?? false;
  const canShareDiscordPresence = !discordIgnoreNsfwSources || (sourcesQuery.isSuccess && !sourceIsNsfw);
  const canShareDiscordPresenceRef = useRef(canShareDiscordPresence);
  useEffect(() => {
    canShareDiscordPresenceRef.current = canShareDiscordPresence;
    if (!canShareDiscordPresence) hibiki.discord.clearPresence();
  }, [canShareDiscordPresence]);
  useEffect(() => {
    log.info("player", `episode opened: ${sourceId}/${animeId} group=${groupId} episode=${episodeId}`);
    return () => log.info("player", `episode left: ${sourceId}/${animeId} episode=${episodeId}`);
  }, [sourceId, animeId, groupId, episodeId]);
  // What the next save writes: the latest tick of this episode, unsaved yet. See saveProgress.
  const pendingSaveRef = useRef<PendingSave | null>(null);
  const lastDiscordUpdateRef = useRef(0);
  const lastPlaybackRef = useRef({ positionMs: 0, durationMs: 0 });
  // Time that actually played since the last save, and the position the previous progress tick
  // reported. A tick that moved further than PLAYED_TICK_MAX_MS is a seek, not playback, and adds
  // nothing - which is the whole point: skipping to the end of a film is not watching it.
  const playedMsRef = useRef(0);
  const lastTickPositionRef = useRef<number | null>(null);
  // The individual seconds of this episode that actually played, not yet reported to the source's
  // account. A count would not do: the account counts distinct seconds seen, so rewatching the
  // same minute twice is one minute there, and only the offsets themselves can say that.
  const unreportedSecondsRef = useRef(new Set<number>());
  const queryClient = useQueryClient();
  // Starting playback only needs the route ids. Fetch the full groups payload when the user
  // opens the dub or episode picker, not on every player entry.
  const [groupsRequested, setGroupsRequested] = useState(false);
  const requestGroups = useCallback(() => setGroupsRequested(true), []);

  // Same query key as the profile page's own activity query - watching here and then checking the
  // profile page reads from (and refetches into) the same cache entry instead of two independent
  // copies drifting apart.
  const activityQuery = useQuery({ queryKey: ["dailyActivity", ACTIVITY_DAYS], queryFn: () => hibiki.progress.listDailyActivity(ACTIVITY_DAYS) });
  const streak = useMemo(() => computeStreaks(buildActivitySeries(activityQuery.data ?? [], ACTIVITY_DAYS)), [activityQuery.data]);

  // Announces the moment the streak count itself goes up while an episode is playing - not just
  // "you watched something", the actual day-over-day increment (today flipping from inactive to
  // active, extending the run). `prevStreakRef` starts unset so the very first successful load
  // (whatever the streak already was before this session) never fires the toast on its own; only a
  // later increase, caused by watching just now, does.
  const [streakToast, setStreakToast] = useState<{ current: number; best: number } | null>(null);
  const prevStreakRef = useRef<number | null>(null);
  useEffect(() => {
    if (!activityQuery.isSuccess) return;
    const prev = prevStreakRef.current;
    prevStreakRef.current = streak.current;
    if (prev === null || streak.current <= prev) return;
    setStreakToast({ current: streak.current, best: streak.best });
    const timer = setTimeout(() => setStreakToast(null), STREAK_TOAST_HOLD_MS);
    return () => clearTimeout(timer);
  }, [streak.current, streak.best, activityQuery.isSuccess]);

  // The detail page's episode chips / continue button read cached progress queries that would
  // otherwise sit stale (staleTime: 60s) until well after the user has already navigated back.
  useEffect(() => () => {
    queryClient.invalidateQueries({ queryKey: ["progress-all", sourceId, animeId] });
    queryClient.invalidateQueries({ queryKey: ["progress", sourceId, animeId, episodeId] });
  }, [queryClient, sourceId, animeId, episodeId]);

  // staleTime: Infinity + refetchOnWindowFocus: false - a resolved episode's links don't change
  // while you're actively watching it, but the global default (60s staleTime, refetch on focus)
  // would otherwise silently refetch this in the background on any window blur/refocus during a
  // watch session longer than a minute (alt-tab, opening DevTools, ...). That refetch returns a
  // fresh array *reference* even when the content is identical, which - through pickDefaultLink()
  // below and VideoPlayer's `link` prop - looked exactly like "the player randomly resets/reloads
  // itself mid-episode": the source-setup effect keys off `link`, so a new reference re-triggers
  // the whole hls.js (re)attach, snapping playback back near the last saved progress checkpoint.
  const linksQuery = useQuery({
    queryKey: ["playerLinks", sourceId, animeId, groupId, episodeId],
    queryFn: async () => {
      const startedAt = performance.now();
      log.info("player", `link discovery started: source=${sourceId}, anime=${animeId}, group=${groupId}, episode=${episodeId}`);
      try {
        const links = await hibiki.sources.playerLinks(
          sourceId,
          animeId,
          groupId,
          episodeId,
          usePlayerSelectionStore.getState().get(sourceId, animeId, groupId),
        );
        log.info("player", `link discovery finished in ${Math.round(performance.now() - startedAt)}ms: ${links.length} link(s); ${links.map((item) => `${item.type}:${item.playerName ?? "?"}/${item.translation ?? "?"}/${item.quality ?? "?"}`).join(", ") || "none"}`);
        return links;
      } catch (error) {
        log.error("player", `link discovery failed after ${Math.round(performance.now() - startedAt)}ms:`, error);
        throw error;
      }
    },
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  // A downloaded copy always wins over resolving a live link - there's no network dependency to
  // fail while offline, no ads/embed page, and no reason to re-download bandwidth you already
  // spent once. `staleTime: Infinity` since a file that's already on disk doesn't need re-checking
  // mid-episode any more than the queries above do.
  const downloadedQuery = useQuery({
    queryKey: ["downloadedEpisode", sourceId, animeId, episodeId],
    queryFn: () => hibiki.downloads.getForEpisode(sourceId, animeId, episodeId),
    staleTime: Infinity,
  });

  // A manual pick (from the in-player "Player"/"Quality" menu) only applies to the episode it was
  // made on - a new episode goes back to the auto-picked default rather than carrying forward a
  // choice that might not even exist for it.
  const [manualLink, setManualLink] = useState<PlayerLink | null>(null);
  const [sourceSwitching, setSourceSwitching] = useState(false);
  const selectionRequestRef = useRef(0);
  const failedPlaybackUrlsRef = useRef(new Set<string>());
  useEffect(() => {
    selectionRequestRef.current += 1;
    failedPlaybackUrlsRef.current.clear();
    setManualLink(null);
    setSourceSwitching(false);
    // The new episode starts wherever it starts; carrying the previous one's last tick over would
    // read the jump between them as either playback or a seek, and neither is true.
    playedMsRef.current = 0;
    lastTickPositionRef.current = null;
    unreportedSecondsRef.current = new Set();
  }, [episodeId]);

  // A pick whose player page could not be turned into a stream - shown as an error, never as the
  // page itself. Cleared by the next pick or episode.
  const [resolveFailed, setResolveFailed] = useState(false);
  useEffect(() => { setResolveFailed(false); }, [episodeId]);
  const selectLink = useCallback(async (selected: PlayerLink) => {
    const requestId = ++selectionRequestRef.current;
    setResolveFailed(false);
    if (selected.type !== "EMBED") {
      setSourceSwitching(false);
      setManualLink(selected);
      return;
    }

    setSourceSwitching(true);
    const startedAt = performance.now();
    log.info("player", `embed resolve started: player=${selected.playerName ?? "?"}, translation=${selected.translation ?? "?"}`);
    try {
      const resolved = await hibiki.sources.resolvePlayerLink(selected);
      log.info("player", `embed resolve finished in ${Math.round(performance.now() - startedAt)}ms: player=${selected.playerName ?? "?"}, links=${resolved.length}; ${resolved.map((item) => `${item.type}/${item.quality ?? "?"}`).join(", ") || "none"}`);
      // Superseded while it was resolving. Whatever superseded it owns the outcome now, and that
      // is worth saying out loud: from the outside this is indistinguishable from a resolve that
      // simply never came back, and the screen sits on a spinner either way.
      if (requestId !== selectionRequestRef.current) {
        log.info("player", `discarding stale resolve ${requestId} (current ${selectionRequestRef.current})`);
        return;
      }
      const playable = resolved.filter((candidate) => candidate.type !== "EMBED");
      // An EMBED link resolves into a whole set of renditions, so the pick that got us here has to
      // survive that expansion: taking pickDefaultLink()'s own default outright is what made an
      // explicit quality choice look like it silently snapped back to whatever the source lists
      // first. Keep the requested quality when the resolved set actually offers it.
      const next = pickResolvedLink(playable, selected);
      if (!next) {
        // Nothing direct came out of the resolve. There is no embed-page fallback: the player only
        // plays streams of its own, so this pick simply isn't supported right now.
        log.warn("player", `no playable link from ${selected.playerName ?? "?"}`);
        setResolveFailed(true);
        return;
      }

      queryClient.setQueryData<PlayerLink[]>(
        ["playerLinks", sourceId, animeId, groupId, episodeId],
        (current) => current?.flatMap((candidate) =>
          candidate.url === selected.url &&
          candidate.playerName === selected.playerName &&
          candidate.translation === selected.translation
            ? playable
            : [candidate]
        ),
      );
      setManualLink(next);
    } catch (error) {
      if (requestId !== selectionRequestRef.current) {
        log.info("player", `discarding stale failed resolve ${requestId} (current ${selectionRequestRef.current})`);
        return;
      }
      log.warn("player", `embed resolve failed for ${selected.playerName ?? "?"} after ${Math.round(performance.now() - startedAt)}ms:`, error);
      setResolveFailed(true);
    } finally {
      if (requestId === selectionRequestRef.current) setSourceSwitching(false);
    }
  }, [queryClient, sourceId, animeId, groupId, episodeId]);

  // Only the *explicit* picks (the in-player settings menu) get remembered - selectLink itself is
  // also called by the resume logic below, and letting that write back would just re-save what it
  // had already read, turning a one-off fallback into a sticky preference.
  const rememberSelection = usePlayerSelectionStore((s) => s.remember);
  const selectLinkManually = useCallback((selected: PlayerLink) => {
    log.info("player", `picked by hand: ${selected.translation ?? "?"}/${selected.playerName ?? "?"} ${selected.quality ?? "?"}`);
    rememberSelection(sourceId, animeId, groupId, { playerName: selected.playerName ?? null, translation: selected.translation ?? null });
    void selectLink(selected);
  }, [rememberSelection, selectLink, sourceId, animeId, groupId]);

  // A silent auto-recovery (see handlePlaybackFailure below) used to be indistinguishable, from the
  // outside, from the episode just randomly changing player/quality on its own mid-watch - this is
  // the only thing that tells the person watching that it was deliberate, not a glitch.
  const [playerSwitchToast, setPlayerSwitchToast] = useState<{ fromLabel: string; toLabel: string } | null>(null);
  const handlePlaybackFailure = useCallback((failed: PlayerLink, reason: string): boolean => {
    if (downloadedQuery.data) return false;
    failedPlaybackUrlsRef.current.add(failed.url);
    const candidates = queryClient.getQueryData<PlayerLink[]>(["playerLinks", sourceId, animeId, groupId, episodeId]) ?? [];
    const fallback = pickPlaybackFallback(candidates, failed, failedPlaybackUrlsRef.current);
    if (!fallback) return false;
    log.warn(
      "player",
      `stream failed (${reason}); falling back ${failed.playerName ?? "?"}/${failed.quality ?? "?"} -> ${fallback.playerName ?? "?"}/${fallback.quality ?? "?"}`,
    );
    setPlayerSwitchToast({ fromLabel: playerSwitchLabel(failed), toLabel: playerSwitchLabel(fallback) });
    void selectLink(fallback);
    return true;
  }, [downloadedQuery.data, queryClient, sourceId, animeId, groupId, episodeId, selectLink]);
  useEffect(() => {
    if (!playerSwitchToast) return;
    const timer = setTimeout(() => setPlayerSwitchToast(null), PLAYER_SWITCH_TOAST_HOLD_MS);
    return () => clearTimeout(timer);
  }, [playerSwitchToast]);

  // Same reasoning as linksQuery above: this is only ever read once, for the initial resume-seek
  // (VideoPlayer's `startPositionMs`) - a background refetch pulling back the position *we
  // ourselves* just saved a moment ago (via onProgress below) would otherwise change that number
  // and, through the source-setup effect's dependency on it, reset/reload the player mid-episode.
  const progressQuery = useQuery({
    queryKey: ["progress", sourceId, animeId, episodeId],
    queryFn: () => hibiki.progress.get(sourceId, animeId, episodeId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
  // Resuming a title should keep the dub/player you actually picked last time, not silently
  // revert to pickDefaultLink()'s own default ordering - once both the saved progress and the
  // live link list are in, look for a link matching what was last watched through and adopt it as
  // this episode's own starting pick (still a plain `manualLink`, so an explicit pick from the
  // settings menu during this session overrides it exactly the same way as always).
  //
  // This has to settle *before* anything starts playing: `link` below used to fall back to
  // pickDefaultLink()'s default the moment the link list arrived, so resuming an Alloha episode
  // visibly loaded Kodik first and only then swapped over once the saved preference was applied.
  // `preferencePending` keeps `link` undefined (plain loading spinner) until either the saved
  // pick has been adopted or there turns out to be nothing to adopt.
  const [preferencePending, setPreferencePending] = useState(true);
  useEffect(() => { setPreferencePending(true); }, [episodeId]);
  useEffect(() => {
    if (manualLink) { setPreferencePending(false); return; }
    // A downloaded copy wins over any link list anyway, so there's nothing to wait for.
    if (downloadedQuery.data) { setPreferencePending(false); return; }
    // Still loading: stay pending rather than reading "no saved pick" out of a query that simply
    // hasn't answered yet (progress legitimately resolves to null for a first watch).
    if (!progressQuery.isFetched || !linksQuery.data) return;
    // The saved per-title/dub pick wins over this episode's own watch_progress row: both describe
    // a deliberate choice, but the stored one is by definition the most recent (it's written on
    // every pick, while progress only records what an episode happened to be watched through).
    // Read imperatively - subscribing to the store here would re-run this effect on every pick.
    const remembered = usePlayerSelectionStore.getState().get(sourceId, animeId, groupId);
    const translation = remembered?.translation ?? progressQuery.data?.translation;
    const playerName = remembered?.playerName ?? progressQuery.data?.playerName;
    const preferred = pickPreferredLink(linksQuery.data, { translation, playerName });
    // Nothing saved at all, or no link matches it any more (source reshuffled its players, the dub
    // is gone) - release the gate and let the default pick play.
    if (!preferred) {
      log.debug("player", "no saved pick to adopt, releasing the preference gate");
      setPreferencePending(false);
      return;
    }
    log.debug("player", `adopting saved pick ${preferred.translation ?? "?"}/${preferred.playerName ?? "?"}`);
    void selectLink(preferred);
  }, [manualLink, downloadedQuery.data, progressQuery.isFetched, progressQuery.data, linksQuery.data, selectLink, sourceId, animeId, groupId, episodeId]);

  // Reused from the title-detail page's own queries (same queryKeys) so these are normally cache
  // hits, not extra network calls. Same staleTime/refetchOnWindowFocus reasoning as linksQuery -
  // this feeds prevEpisode/nextEpisode below, and those flow into onPrevEpisode/onNextEpisode
  // props that VideoPlayer's own effects key off, so a background refetch here is just as capable
  // of resetting the player mid-episode as one on linksQuery/progressQuery was.
  const animeQuery = useQuery({ queryKey: ["anime", sourceId, animeId], queryFn: () => hibiki.sources.getById(sourceId, animeId), staleTime: Infinity, refetchOnWindowFocus: false });
  const groupsQuery = usePlaybackGroups(sourceId, animeId, Infinity, groupsRequested);
  // The detail screen normally populated this cache immediately before navigation. Read it while
  // the picker stays lazy, so title/episode text and prev/next paint without another source call.
  const cachedGroups = queryClient.getQueryData<PlaybackGroup[]>(["playbackGroups", sourceId, animeId]);
  const playerGroups = groupsQuery.data ?? cachedGroups;
  const group = playerGroups?.find((g) => g.id === groupId);
  const episodeIndex = group?.episodes.findIndex((e) => e.id === episodeId) ?? -1;
  const episode = episodeIndex >= 0 ? group!.episodes[episodeIndex] : undefined;
  const prevEpisode = episodeIndex > 0 ? group!.episodes[episodeIndex - 1] : undefined;
  const nextEpisode = episodeIndex >= 0 && episodeIndex < (group?.episodes.length ?? 0) - 1 ? group!.episodes[episodeIndex + 1] : undefined;
  const episodeNumber = episode?.number ?? 0;

  // Only checked at all while the *current* episode is itself a downloaded copy - if this episode
  // came from a live link, prev/next should behave exactly as they always have (no reason to
  // second-guess a source that's clearly reachable). Playing from a downloaded copy usually means
  // deliberately watching offline, so silently falling through to a live fetch for whichever
  // neighboring episode isn't downloaded too would just be a needless network attempt at best and
  // a dead-end error screen at worst - see localFileLink above for the matching player-side half
  // of downloaded playback.
  const neighborsDownloadedQuery = useQuery({
    queryKey: ["downloadedEpisode", "neighbors", sourceId, animeId, prevEpisode?.id, nextEpisode?.id],
    queryFn: () =>
      Promise.all([
        prevEpisode ? hibiki.downloads.getForEpisode(sourceId, animeId, prevEpisode.id) : null,
        nextEpisode ? hibiki.downloads.getForEpisode(sourceId, animeId, nextEpisode.id) : null,
      ]),
    enabled: !!downloadedQuery.data && (!!prevEpisode || !!nextEpisode),
  });
  const [prevDownloaded, nextDownloaded] = neighborsDownloadedQuery.data ?? [null, null];

  // `replace`, not a push: moving between episodes is staying in the player, not travelling
  // somewhere new. Pushing meant an evening of five episodes left five entries behind it, so
  // leaving the player walked back through every one of them instead of returning to the page the
  // player was opened from. Same reasoning for the dub switch below.
  const goToEpisode = useCallback(
    (targetEpisodeId: string) => {
      log.info("player", `switching to episode ${targetEpisodeId}`);
      return navigate({ to: "/watch/$sourceId/$animeId/$groupId/$episodeId", params: { sourceId, animeId, groupId, episodeId: targetEpisodeId }, replace: true });
    },
    [navigate, sourceId, animeId, groupId],
  );
  // Switching dub means switching PlaybackGroup, and groups number their episodes independently
  // (different ids, sometimes a different count) - match on episode *number* first so "episode 7"
  // stays episode 7.
  //
  // When that misses, the dub is behind the one being watched: it has not released this episode
  // yet. The nearest episode it does have at or below the current number is a near miss; its
  // *first* episode, which is where this used to land, is a teleport to the start of the show
  // dressed up as a dub switch - and that is what made switching dub on a just-released episode
  // look broken while every earlier episode worked. The positional fallback that used to sit
  // between the two was a guess either way: a group that lists specials or recaps has every
  // position shifted, so index 15 is not episode 16.
  const selectDub = useCallback(
    (targetGroupId: string) => {
      const target = playerGroups?.find((g) => g.id === targetGroupId);
      if (!target || target.episodes.length === 0) return;
      const nearestBelow = target.episodes
        .filter((e) => e.number <= episodeNumber)
        .sort((a, b) => b.number - a.number)[0];
      const targetEpisode = target.episodes.find((e) => e.number === episodeNumber) ?? nearestBelow ?? target.episodes[0];
      if (targetEpisode.number !== episodeNumber) {
        log.info("player", `${target.title} has no episode ${episodeNumber}, opening episode ${targetEpisode.number} instead`);
      }
      log.info("player", `switching dub to "${target.title}" (${targetGroupId}), episode ${targetEpisode.number}`);
      navigate({ to: "/watch/$sourceId/$animeId/$groupId/$episodeId", params: { sourceId, animeId, groupId: targetGroupId, episodeId: targetEpisode.id }, replace: true });
    },
    [navigate, playerGroups, sourceId, animeId, episodeNumber],
  );
  // A real history.back() (not a push to the detail route) so the titlebar's back/forward arrows
  // stay consistent with "Escape" - otherwise this would push a *new* detail-page entry, leaving
  // the actual previous page one back-click further away than pressing the titlebar's own arrow.
  const goBack = useCallback(() => router.history.back(), [router]);

  // Memoized, not recomputed inline - pickDefaultLink() builds a fresh array/object every call,
  // so without this `link` (and the onPrevEpisode/onNextEpisode callbacks below) got a new
  // identity on *every* WatchPage render regardless of whether the underlying data actually
  // changed, which - through VideoPlayer's effects keying off them - reset/reloaded the player on
  // basically any unrelated re-render, not just background refetches (see the queries above).
  const link = useMemo((): PlayerLink | undefined => {
    if (downloadedQuery.data) return localFileLink(downloadedQuery.data);
    if (manualLink) return manualLink;
    if (preferencePending) return undefined;
    return pickDefaultLink(linksQuery.data);
  }, [downloadedQuery.data, manualLink, preferencePending, linksQuery.data]);
  // Why this link: the engine's own trace (VideoPlayer) says what it is, not where it came from.
  useEffect(() => {
    if (!link) return;
    const origin = downloadedQuery.data ? "downloaded copy" : manualLink ? "picked" : "default pick";
    log.info("player", `playing the ${origin}: ${link.type} ${link.translation ?? "?"}/${link.playerName ?? "?"} ${link.quality ?? "?"}, subtitles=${link.subtitles?.length ?? 0}`);
    // Only on a new link; where it came from is read at that moment.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [link]);
  // With a stream already playing the message is a passing notice, not a state to stay in.
  useEffect(() => {
    if (!resolveFailed || !link) return;
    const timer = setTimeout(() => setResolveFailed(false), 4000);
    return () => clearTimeout(timer);
  }, [resolveFailed, link]);
  // Currently playing a downloaded copy, and this particular neighbor isn't one itself.
  const prevBlockedOffline = !!prevEpisode && !!downloadedQuery.data && !prevDownloaded;
  const nextBlockedOffline = !!nextEpisode && !!downloadedQuery.data && !nextDownloaded;
  const onPrevEpisode = useMemo(
    () => (prevEpisode && !prevBlockedOffline ? () => goToEpisode(prevEpisode.id) : undefined),
    [prevEpisode, goToEpisode, prevBlockedOffline],
  );
  const onNextEpisode = useMemo(
    () => (nextEpisode && !nextBlockedOffline ? () => goToEpisode(nextEpisode.id) : undefined),
    [nextEpisode, goToEpisode, nextBlockedOffline],
  );
  // A stable identity across renders (not an inline arrow prop) - VideoPlayer's own capture effect
  // has this in its dependency list, and a new identity every render would re-trigger that whole
  // effect (rebinding every <video> listener) on every unrelated render, the exact class of bug
  // this file's other memoized callbacks above were already written to avoid.
  const thumbnailCache = useMemo(() => ({ dataUrl: null as string | null }), [sourceId, animeId, episodeId]);
  const onCaptureThumbnail = useCallback(
    (dataUrl: string) => {
      thumbnailCache.dataUrl = dataUrl;
      // The history page's own query (["recent-progress", limit]) has a 60s staleTime (see
      // main.tsx's QueryClient) and, being one of PERSISTED_PAGES, never actually remounts on a
      // later visit to re-trigger a fetch on its own - without this, a thumbnail saved just now
      // wouldn't show up there until that 60s window happened to lapse on its own.
      hibiki.progress.saveThumbnail(sourceId, animeId, episodeId, dataUrl).then(() => {
        queryClient.invalidateQueries({ queryKey: ["recent-progress"] });
      }).catch((error) => log.warn("player", "thumbnail save failed:", error));
    },
    [sourceId, animeId, episodeId, queryClient, thumbnailCache],
  );
  const anime = animeQuery.data;
  const title = anime ? animeTitle(anime) : "";
  const episodeLabel = episode ? (episode.title || t("detail.episodeFallback", { number: episode.number })) : "";
  // A link's own `translation` (see runtime.ts's resolveEmbedLinks carrying it over from the
  // source EMBED link) is the actual dub playing right now, more precise than the playback
  // group's title where a source's groups don't map 1:1 to dub studios. The group's title is a
  // structural placeholder ("Episodes") rather than a dub name at all for a source with no real
  // per-dub grouping - that's not something to show as the dub anywhere, Discord's Rich Presence
  // included (see hibiki.discord.updatePresence below).
  const dubName = link?.translation || (group && !isGenericDubTitle(group.title) ? group.title : null);

  // title/dubName/posterUrl each depend on their own async query (anime, playback groups) and can
  // legitimately still be loading - or arrive a beat late - right when the player first mounts.
  // Reading them through a ref (kept fresh by the effect below) rather than closing over them
  // directly keeps onProgress/onPlayStateChange's own identity stable regardless of when that data
  // shows up, so a poster loading in doesn't also re-trigger VideoPlayer's source-setup effect
  // (which keys off onProgress) - the same reset-on-unrelated-change class of bug fixed earlier for
  // `link`, just for these two callbacks instead.
  const discordMetaRef = useRef({ title: "", dubName: null as string | null, posterUrl: null as string | null, groupId, episodeId });
  useEffect(() => {
    discordMetaRef.current = { title, dubName, posterUrl: anime?.posterUrl ?? null, groupId, episodeId };
    // The very first update after an episode change (see the episodeId effect below) can easily
    // fire before this data has loaded at all - once it does load, correct the card right away
    // instead of leaving it poster-less/dub-less until the next throttled tick (up to 16s later).
    if (!title || !canShareDiscordPresence) return;
    lastDiscordUpdateRef.current = Date.now();
    hibiki.discord.updatePresence({
      animeTitle: title,
      translation: dubName,
      episodeNumber: episodeNumber || null,
      positionMs: lastPlaybackRef.current.positionMs,
      durationMs: lastPlaybackRef.current.durationMs,
      isPlaying: true,
      posterUrl: anime?.posterUrl ?? null,
      sourceId,
      animeId,
      groupId,
      episodeId,
    });
  }, [title, dubName, anime?.posterUrl, episodeNumber, canShareDiscordPresence, groupId, episodeId]);

  const watchedThreshold = usePlayerPrefsStore((s) => s.watchedThresholdPercent) / 100;
  // Plain primitives (not `link` itself) in onProgress's dependency list - same reasoning as
  // `link`/onPrevEpisode/onNextEpisode above: `link` gets a fresh object identity on effectively
  // every render once pickDefaultLink()/localFileLink() are involved, and this callback's own
  // identity feeds VideoPlayer's effects the exact same way theirs do.
  const linkTranslation = link?.translation ?? null;
  const linkPlayerName = link?.playerName ?? null;
  // The source's own id for this episode's video, which is how an account is told what was
  // watched. Absent for sources that do not report activity, and for links that never had one.
  const linkVideoId = link?.videoId ?? null;
  const onProgress = useCallback(
    (positionMs: number, durationMs: number) => {
      const previousTickPosition = lastTickPositionRef.current;
      lastTickPositionRef.current = positionMs;
      if (previousTickPosition !== null) {
        const tickMs = positionMs - previousTickPosition;
        if (tickMs > 0 && tickMs <= PLAYED_TICK_MAX_MS) {
          playedMsRef.current += tickMs;
          for (let second = Math.floor(previousTickPosition / 1000); second <= Math.floor(positionMs / 1000); second++) {
            unreportedSecondsRef.current.add(second);
          }
        }
      }
      lastPlaybackRef.current = { positionMs, durationMs };
      // Only remembered here. The progress is written once, when the episode is left (see
      // saveProgress below): a row rewritten every few seconds meant every screen and every account
      // saw a moving target - the AniList count went up at the credits while still watching.
      const watched = durationMs > 0 && positionMs / durationMs >= watchedThreshold;
      pendingSaveRef.current = {
        sourceId,
        titleId: animeId,
        episodeId,
        episodeNumber,
        groupId,
        translation: linkTranslation,
        playerName: linkPlayerName,
        videoId: linkVideoId,
        thumbnail: thumbnailCache,
        positionMs,
        durationMs,
        // Sticky for the visit, as it is in the database: reaching the end and seeking back is
        // still having watched it.
        watched: watched || (pendingSaveRef.current?.watched ?? false),
      };
      const now = Date.now();
      const meta = discordMetaRef.current;
      if (canShareDiscordPresenceRef.current && meta.title && now - lastDiscordUpdateRef.current >= DISCORD_UPDATE_INTERVAL_MS) {
        lastDiscordUpdateRef.current = now;
        hibiki.discord.updatePresence({
          animeTitle: meta.title,
          translation: meta.dubName,
          episodeNumber: episodeNumber || null,
          positionMs,
          durationMs,
          isPlaying: true,
          posterUrl: meta.posterUrl,
          sourceId,
          animeId,
          groupId,
          episodeId,
        });
      }
    },
    [sourceId, animeId, episodeId, episodeNumber, groupId, linkTranslation, linkPlayerName, linkVideoId, watchedThreshold, thumbnailCache],
  );

  // The one write of this visit's progress: when the episode is left (another episode is a new
  // page - WatchPage is keyed by it - so that is an unmount too), and whenever the app might not get
  // another chance - sent to the background, where Android may end it, or its window closing.
  // Reads only refs, so it can run from an unmount and from window events alike.
  const saveProgress = useCallback((reason: string) => {
    const pending = pendingSaveRef.current;
    if (!pending) return;
    pendingSaveRef.current = null;
    const watchedDeltaMs = playedMsRef.current;
    playedMsRef.current = 0;
    const percent = pending.durationMs > 0 ? Math.round((pending.positionMs / pending.durationMs) * 100) : 0;
    log.info(
      "player",
      `saving progress (${reason}): episode ${pending.episodeNumber} at ${clock(pending.positionMs)}/${clock(pending.durationMs)} (${percent}%)${pending.watched ? ", watched" : ""}, played ${clock(watchedDeltaMs)} this time`,
    );
    const upserted = hibiki.progress.upsert({
      ...(pending.thumbnail.dataUrl ? { thumbnailDataUrl: pending.thumbnail.dataUrl } : {}),
      sourceId: pending.sourceId,
      titleId: pending.titleId,
      episodeId: pending.episodeId,
      episodeNumber: pending.episodeNumber,
      groupId: pending.groupId,
      translation: pending.translation,
      playerName: pending.playerName,
      positionMs: pending.positionMs,
      durationMs: pending.durationMs,
      watched: pending.watched,
      updatedAt: Date.now(),
      watchedDeltaMs,
    });

    // The same seconds, told to the source's account: an episode counted and the minutes really
    // spent in it, which is what fills the day squares on a YummyAnime profile. Fire and forget - a
    // website being unreachable must not matter here, and seconds that were not accepted go back
    // to be sent with the next save.
    const seconds = [...unreportedSecondsRef.current];
    if (pending.videoId && seconds.length > 0) {
      unreportedSecondsRef.current = new Set();
      void hibiki.sources
        .reportPlayback(pending.sourceId, {
          videoId: pending.videoId,
          positionSeconds: Math.floor(pending.positionMs / 1000),
          durationSeconds: Math.round(pending.durationMs / 1000),
          watchedSeconds: seconds,
        })
        .catch(() => {
          for (const second of seconds) unreportedSecondsRef.current.add(second);
        });
    }
    // Whatever shows progress is behind until these refetch - the title page and the continue-watching
    // row are usually the very next screen.
    void upserted.catch((error: unknown) => log.error("player", `progress not saved for episode ${pending.episodeNumber}:`, error));
    void upserted.then(() => {
      void queryClient.invalidateQueries({ queryKey: ["dailyActivity", ACTIVITY_DAYS] });
      void queryClient.invalidateQueries({ queryKey: ["progress-all", pending.sourceId, pending.titleId] });
      void queryClient.invalidateQueries({ queryKey: ["progress", pending.sourceId, pending.titleId, pending.episodeId] });
      void queryClient.invalidateQueries({ queryKey: ["recent-progress"] });
    });
  }, [queryClient]);

  useEffect(() => {
    const onHidden = () => {
      if (document.visibilityState === "hidden") saveProgress("app hidden");
    };
    const onPageHide = () => saveProgress("window closing");
    document.addEventListener("visibilitychange", onHidden);
    window.addEventListener("pagehide", onPageHide);
    return () => {
      document.removeEventListener("visibilitychange", onHidden);
      window.removeEventListener("pagehide", onPageHide);
      saveProgress("episode left");
    };
  }, [saveProgress]);

  // timeupdate (the only thing that drives onProgress above) simply stops firing while paused, so
  // without this a pause would leave Discord showing the last isPlaying:true timestamp, which
  // keeps counting up on its own even though nothing is actually playing - this reacts to the
  // play/pause edge directly instead of waiting for (or missing) the next throttled progress tick.
  const onPlayStateChange = useCallback(
    (playing: boolean) => {
      const meta = discordMetaRef.current;
      if (!canShareDiscordPresenceRef.current || !meta.title) return;
      lastDiscordUpdateRef.current = Date.now();
      hibiki.discord.updatePresence({
        animeTitle: meta.title,
        translation: meta.dubName,
        episodeNumber: episodeNumber || null,
        positionMs: lastPlaybackRef.current.positionMs,
        durationMs: lastPlaybackRef.current.durationMs,
        isPlaying: playing,
        posterUrl: meta.posterUrl,
        sourceId,
        animeId,
        groupId: meta.groupId,
        episodeId: meta.episodeId,
      });
    },
    [episodeNumber, sourceId, animeId],
  );

  // A fresh episode should show up on the Discord card right away, not whenever the throttled
  // onProgress tick above next happens to fire.
  useEffect(() => {
    lastDiscordUpdateRef.current = 0;
  }, [episodeId]);

  // Whether VideoPlayer is about to mount below and draw its own back/title bar - while it isn't
  // (still waiting on linksQuery's very first answer, or that answer was an error/empty result),
  // none of that chrome exists yet, and the screen used to be nothing but a bare spinner on black:
  // no way back except knowing a keyboard shortcut, no sense of what was even being loaded.
  const videoPlayerMounted = !!(link || (linksQuery.data && linksQuery.data.length > 0));

  return (
    <div className="relative h-full w-full bg-black">
      {!videoPlayerMounted && (
        <div className="absolute inset-x-0 top-0 z-10 flex items-center gap-4 bg-gradient-to-b from-black/80 to-transparent px-6 pb-10 pt-5">
          <button onClick={goBack} className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20">
            <ArrowLeft className="h-[18px] w-[18px]" strokeWidth={2} />
          </button>
          <div className="min-w-0">
            <p className="select-text truncate text-base font-bold text-white">{title}</p>
            {episodeLabel && <p className="select-text truncate text-xs text-zinc-300">{episodeLabel}</p>}
          </div>
        </div>
      )}
      {/* Gated on `!link` too, not just the query's own state - once a downloaded copy makes `link`
          available, whatever linksQuery is doing (still loading, or failed because there's no
          network to resolve a live link with) no longer matters, since it isn't what's playing. */}
      {/* `preferencePending` covers the window where the links are in but the saved player/dub pick
          is still being applied (or its EMBED link resolved) - the spinner belongs there too. */}
      {!link && !linksQuery.data && !linksQuery.isError && (
        <div className="flex h-full items-center justify-center">
          <Loader2 className="h-10 w-10 animate-spin text-white/70" strokeWidth={2} />
        </div>
      )}
      {!link && linksQuery.isError && (
        <div className="flex h-full flex-col items-center justify-center gap-3 px-8 text-center">
          <TriangleAlert className="h-8 w-8 text-rose-400" strokeWidth={2} />
          <p className="text-sm text-rose-300">
            {cloudflareCheckOf(linksQuery.error)
              ? t("common.cloudflareBlocked", { host: cloudflareCheckOf(linksQuery.error)!.host })
              : t("watch.linkError", { message: (linksQuery.error as Error).message })}
          </p>
          <div className="mt-2 flex flex-wrap items-center justify-center gap-2">
            <CloudflareCheckButton error={linksQuery.error} className="rounded-lg bg-white px-4 py-2 text-sm font-semibold text-black hover:bg-white/90" />
            <button onClick={goBack} className="rounded-lg bg-white/10 px-4 py-2 text-sm font-medium text-white hover:bg-white/20">{t("detail.back")}</button>
          </div>
        </div>
      )}
      {/* The request itself succeeded, it just came back empty - nothing above (isError) or below
          (VideoPlayer's own `!link` spinner) ever catches this, so without it this episode spun
          forever with no way out but the back button in the corner. */}
      {!link && linksQuery.isSuccess && linksQuery.data.length === 0 && downloadedQuery.isSuccess && !downloadedQuery.data && (
        <div className="flex h-full flex-col items-center justify-center gap-3 px-8 text-center">
          <TriangleAlert className="h-8 w-8 text-rose-400" strokeWidth={2} />
          <p className="text-sm text-rose-300">{t("watch.noLinks")}</p>
          <button onClick={goBack} className="mt-2 rounded-lg bg-white/10 px-4 py-2 text-sm font-medium text-white hover:bg-white/20">{t("detail.back")}</button>
        </div>
      )}
      {/* Mounted as soon as there is *something* to choose between, not only once a link has been
          settled on. While `preferencePending` is still resolving a remembered EMBED pick there is
          no `link` yet, and the player shows its own spinner over the normal chrome (see
          VideoPlayer's optional `link` prop) - which is the difference between "wait a moment" and
          "you are locked out": picking a player that resolves slowly, or never, used to leave a
          bare full-screen spinner with no settings menu to pick a different one from. Excludes the
          empty-and-done case above, which owns the screen instead of a player with nothing to play. */}
      {videoPlayerMounted && (
        <VideoPlayer
          link={link}
          availableLinks={linksQuery.data}
          offlinePlayback={!!downloadedQuery.data}
          dubOptions={playerGroups}
          selectedDubId={groupId}
          onSelectDub={selectDub}
          sourceSwitching={sourceSwitching || (!link && preferencePending)}
          unplayable={!link && !preferencePending && !sourceSwitching && !!linksQuery.data && linksQuery.data.length > 0 || resolveFailed}
          onSelectLink={selectLinkManually}
          onPlaybackFailure={handlePlaybackFailure}
          startPositionMs={progressQuery.data?.positionMs}
          onProgress={onProgress}
          onPlayStateChange={onPlayStateChange}
          onCaptureThumbnail={onCaptureThumbnail}
          title={title}
          episodeLabel={episodeLabel}
          onBack={goBack}
          onPrevEpisode={onPrevEpisode}
          onNextEpisode={onNextEpisode}
          onOpenEpisodes={requestGroups}
          episodesLoading={groupsRequested && groupsQuery.isFetching && !groupsQuery.data}
          episodes={group?.episodes}
          currentEpisodeId={episodeId}
          onSelectEpisode={goToEpisode}
          streakToast={streakToast}
          playerSwitchToast={playerSwitchToast}
        />
      )}
    </div>
  );
}
