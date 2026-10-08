import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import type Hls from "hls.js";
import type { MediaPlayerClass } from "dashjs";
import {
  ArrowLeft,
  Captions,
  Check,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  FastForward,
  ListVideo,
  Loader2,
  Maximize,
  Minimize,
  Pause,
  PictureInPicture2,
  Play,
  Plus,
  Settings,
  SkipBack,
  SkipForward,
  TriangleAlert,
  Volume1,
  Volume2,
  VolumeX,
} from "lucide-react";
import type { Episode, PlayerLink, VideoSegment } from "@shared/types";
import { audioTrackOptions, pickLinkForAudioTrack, pickLinkForDimension, pickLinkForQuality, playerOptions, qualityOptions, translationOptions } from "@/lib/playerLinks";
import { playbackUrl } from "@/lib/playbackUrl";
import { proxiedHlsLoader, streamRequestUrl, usesStreamProxy } from "@/lib/streamProxy";
import { isGenericDubTitle } from "@/lib/dubTitle";
import { subtitleFormatFromUrl, toVtt } from "@/lib/subtitles";
import { hibiki } from "@/lib/hibiki";
import { cn } from "@/lib/cn";
import { log } from "@/lib/log";
import { PLAYBACK_SPEEDS, usePlayerPrefsStore, type PlayerOrientation } from "@/stores/playerPrefsStore";
import { StreakBadge } from "@/components/StreakBadge";
import { isMobile, useBackHandler } from "@/lib/mobile";

// One entry in the subtitle picker, and what actually backs a rendered <track> - `url` is always
// already a playable WebVTT source (a source's own .vtt passed through as-is, or an SRT/ASS
// conversion's blob URL, see lib/subtitles.ts), never the raw source/file URL.
interface SubtitleOption {
  id: string;
  url: string;
  label: string;
  language?: string;
}

interface VideoPlayerProps {
  // Optional, and that is the point: while the parent is still working out *which* link to play
  // (resolving a remembered EMBED pick - see the watch route's preferencePending) there is no link
  // yet, but the chrome around it must still be on screen. Rendering a bare full-screen spinner
  // there instead used to lock the user out completely: pick a player that resolves slowly or not
  // at all, and the settings menu needed to pick a different one was never mounted, so there was
  // no way back short of clearing the saved preference from outside the app.
  link?: PlayerLink;
  // The other links this episode could also play through - lets the in-player settings menu
  // offer a manual "Player"/"Quality" pick instead of always silently taking `link` as given.
  availableLinks?: PlayerLink[];
  // Playing a downloaded copy rather than a live stream - there's nothing to actually switch to
  // (the file on disk is the file on disk), so the settings menu's quality row shows what it is
  // but doesn't let you tap into a picker for it (see PlayerSettingsMenu's `qualityLocked`).
  offlinePlayback?: boolean;
  // The title's other dubs (its PlaybackGroups), so the settings menu can switch dub without
  // leaving the player - the per-link "translation" row below only ever covers the alternatives
  // *inside* the current group, and most sources put each dub in a group of its own, which left
  // the menu with no dub row at all.
  dubOptions?: { id: string; title: string }[];
  selectedDubId?: string;
  // Switches to the same episode under another dub - navigation, so the watch route owns it.
  onSelectDub?: (groupId: string) => void;
  sourceSwitching?: boolean;
  // The episode has links but none this player can play (an unsupported/unresolvable player page) -
  // said out loud over the still-reachable chrome instead of a spinner that never ends.
  unplayable?: boolean;
  onSelectLink?: (link: PlayerLink) => void;
  onPlaybackFailure?: (link: PlayerLink, reason: string) => boolean;
  startPositionMs?: number;
  onProgress?: (positionMs: number, durationMs: number) => void;
  onPlayStateChange?: (playing: boolean) => void;
  // Fired with a JPEG data URL of the current frame whenever playback pauses, and once more right
  // as this episode is being left (episode switch or navigating away) - see the history page,
  // which shows it instead of a generic poster so each entry reads as "the moment you stopped".
  onCaptureThumbnail?: (dataUrl: string) => void;
  title: string;
  episodeLabel: string;
  onBack: () => void;
  onPrevEpisode?: () => void;
  onNextEpisode?: () => void;
  onOpenEpisodes?: () => void;
  episodesLoading?: boolean;
  // The current group's full episode list, so the in-player episode picker can jump straight to
  // any of them - not just the immediate neighbors onPrevEpisode/onNextEpisode cover.
  episodes?: Episode[];
  currentEpisodeId?: string;
  onSelectEpisode?: (episodeId: string) => void;
  // Set for a few seconds right when the streak count just went up mid-episode - see StreakToast.
  streakToast?: { current: number; best: number } | null;
  // Set for a few seconds right after handlePlaybackFailure silently swaps to a fallback link - see
  // PlayerSwitchToast. Silent auto-recovery used to look, from the outside, indistinguishable from
  // the episode just randomly changing player/quality on its own.
  playerSwitchToast?: { fromLabel: string; toLabel: string } | null;
}

const CONTROLS_HIDE_DELAY_MS = 3000;
const PLAYBACK_LOAD_TIMEOUT_MS = 15_000;
let playbackTraceSequence = 0;

function playbackUrlLabel(raw: string): string {
  try {
    const url = new URL(raw);
    const tail = url.pathname.split("/").filter(Boolean).slice(-2).map((part) =>
      part.length > 24 ? `${part.slice(0, 8)}…` : part,
    ).join("/");
    return `${url.host}/${tail || "…"}${url.search ? "?…" : ""}`;
  } catch {
    return raw.slice(0, 48);
  }
}

function logPlayRequestFailure(origin: string, error: unknown): void {
  const name = error instanceof DOMException ? error.name : "unknown";
  const message = error instanceof Error ? error.message : String(error);
  const write = name === "AbortError" ? log.debug : log.warn;
  write("player", `video.play() rejected from ${origin}: ${name}: ${message}`);
}

// A quick overshoot on the way in (it's an accomplishment, it should feel a little bouncy) and a
// plain ease-in on the way out (it's just tidying up, no reason to draw it out) - same shape as
// the one-off demo this was designed against, now the real thing.
const streakToastVariants = {
  hidden: { y: "-140%", opacity: 0 },
  visible: { y: 0, opacity: 1, transition: { duration: 0.48, ease: [0.16, 0.9, 0.3, 1.15] } },
  exit: { y: "-140%", opacity: 0, transition: { duration: 0.42, ease: [0.5, 0, 0.75, 0] } },
} as const;

// Bottom-right, unlike StreakToast's top-center - a celebration earns the center of attention, an
// error-recovery notice should read as a quiet aside instead of interrupting the video the same way.
function PlayerSwitchToast({ toast }: { toast: { fromLabel: string; toLabel: string } | null | undefined }) {
  const { t } = useTranslation();
  return (
    <AnimatePresence>
      {toast && (
        <motion.div
          key={`${toast.fromLabel}->${toast.toLabel}`}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 12, transition: { duration: 0.2, ease: "easeIn" } }}
          transition={{ type: "spring", stiffness: 380, damping: 34 }}
          className="pointer-events-none absolute bottom-20 right-5 z-10 flex max-w-xs items-center gap-2.5 rounded-xl border border-white/10 bg-black/80 px-3.5 py-2.5 shadow-2xl backdrop-blur-sm"
        >
          <TriangleAlert className="h-4 w-4 shrink-0 text-amber-400" strokeWidth={2} />
          <div className="min-w-0">
            <p className="text-xs font-semibold text-white">{t("watch.playerSwitchedToast")}</p>
            <p className="truncate text-[11px] text-zinc-400">{toast.fromLabel} → {toast.toLabel}</p>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function StreakToast({ streak }: { streak: { current: number; best: number } | null | undefined }) {
  return (
    <AnimatePresence>
      {streak && (
        <motion.div
          key={streak.current}
          variants={streakToastVariants}
          initial="hidden"
          animate="visible"
          exit="exit"
          className="pointer-events-none absolute inset-x-0 top-5 z-10 flex justify-center"
        >
          {/* `playOnMount` because this component only ever mounts as a reaction to the streak
              just changing - unlike the profile page's badge, there's no "just loading the page"
              case here to suppress the pop/burst/rotate animation for. */}
          <StreakBadge current={streak.current} best={streak.best} atRisk={false} playOnMount />
        </motion.div>
      )}
    </AnimatePresence>
  );
}

// Cheap drop-shadow (a black copy offset one pixel behind a white one) rather than a filter/
// text-shadow, matching how the Android app draws its own seek arrow.
function SeekArrowIcon({ direction }: { direction: "back" | "forward" }) {
  const Icon = direction === "back" ? ChevronLeft : ChevronRight;
  return (
    <span className="relative inline-flex h-7 w-7 items-center justify-center">
      <Icon className="absolute h-7 w-7 translate-x-px translate-y-px text-black/70" strokeWidth={2.5} />
      <Icon className="absolute h-7 w-7 text-white" strokeWidth={2.5} />
    </span>
  );
}

function segmentKey(segment: VideoSegment): string {
  return `${segment.type}-${segment.startMs}`;
}

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const ss = String(s).padStart(2, "0");
  if (h > 0) return `${h}:${String(m).padStart(2, "0")}:${ss}`;
  return `${m}:${ss}`;
}

function ToggleRow({ label, checked, onChange }: { label: string; checked: boolean; onChange: () => void }) {
  // Same fix as Settings' own Switch (routes/settings.tsx) - a border on the track always (a
  // white or black custom accent color otherwise blends into this always-dark player UI just as
  // easily as it does the app's own light/dark surfaces), and bg-accent-fg (not a fixed bg-white)
  // on the knob once checked, since --color-accent-fg is already this app's "contrasts against
  // --color-accent" token - a white accent's track gets a dark knob, everything else keeps the
  // classic white one it always had.
  return <button onClick={onChange} className="flex w-full items-center justify-between gap-3 rounded-lg px-2 py-2 text-left transition-colors hover:bg-white/[.06] mobile:px-2.5 mobile:py-3">
    <span className="text-sm text-zinc-200 mobile:text-[15px]">{label}</span>
    {/* Flow layout + padding, not an absolutely-positioned knob: hand-placed `top`/`translate-x`
        offsets have to be re-derived every time the track's size changes, and the previous pair
        left the knob a pixel low and flush against the right edge when on. Sized to the track's
        content box (h-5 minus a 1px border and 2px padding a side = 14px) it lands centered and
        symmetric on its own - same approach as Settings' own Switch. */}
    <span className={cn("inline-flex h-5 w-9 shrink-0 items-center rounded-full border border-white/[.15] p-0.5 transition-colors", checked ? "bg-accent" : "bg-white/[.15]")}>
      <span className={cn("h-[14px] w-[14px] rounded-full shadow ring-1 ring-black/10 transition-[transform,background-color]", checked ? "translate-x-4 bg-accent-fg" : "translate-x-0 bg-white")} />
    </span>
  </button>;
}

// `onClick` omitted entirely (not just disabled) renders this as a plain, non-interactive row -
// used for the quality row during offline playback, where there's nothing to actually switch to
// (see PlayerSettingsMenu's `qualityLocked`): shows what quality the file is without inviting a
// tap into a picker for it.
function MenuRow({ label, value, onClick }: { label: string; value: string; onClick?: () => void }) {
  if (!onClick) {
    return <div className="flex w-full items-center justify-between rounded-lg px-2.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] text-zinc-500">
      <span>{label}</span>
      <span className="max-w-[7rem] truncate text-xs mobile:max-w-[11rem] mobile:text-sm">{value}</span>
    </div>;
  }
  return <button onClick={onClick} className="flex w-full items-center justify-between rounded-lg px-2.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] text-zinc-200 transition-colors hover:bg-white/[.06]">
    <span>{label}</span>
    <span className="flex items-center gap-1 text-zinc-200">
      <span className="max-w-[7rem] truncate text-xs mobile:max-w-[11rem] mobile:text-sm">{value}</span>
      <ChevronRight className="h-4 w-4 shrink-0 text-zinc-500" strokeWidth={2.5} />
    </span>
  </button>;
}

function ListPage({ title, options, selected, onSelect, onBack }: { title: string; options: string[]; selected: string | undefined; onSelect: (value: string) => void; onBack: () => void }) {
  return <div className="w-56 p-1.5 mobile:w-full mobile:p-2">
    <button onClick={onBack} className="mb-1 flex w-full items-center gap-1 rounded-lg px-1.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] font-semibold text-white transition-colors hover:bg-white/[.06]">
      <ChevronLeft className="h-4 w-4 shrink-0" strokeWidth={2.5} />
      {title}
    </button>
    <div className="max-h-64 overflow-y-auto mobile:max-h-none">
      {options.map((option) => (
        <button
          key={option}
          onClick={() => onSelect(option)}
          className="flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] text-zinc-200 transition-colors hover:bg-white/[.06]"
        >
          <span className="truncate">{option}</span>
          {selected === option && <Check className="h-4 w-4 shrink-0 text-accent-text" strokeWidth={2.5} />}
        </button>
      ))}
    </div>
  </div>;
}

// Its own page rather than a plain ListPage: unlike every other picker here, this one needs an
// "off" entry that isn't just one of the options, plus a trailing action row (add a local file)
// that doesn't pick anything at all - two shapes ListPage's plain string-in/string-out contract
// has no room for.
function SubtitleListPage({
  title, offLabel, addLabel, options, selectedId, onSelect, onAdd, onBack,
}: {
  title: string;
  offLabel: string;
  addLabel: string;
  options: { id: string; label: string }[];
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onAdd: () => void;
  onBack: () => void;
}) {
  return <div className="w-56 p-1.5 mobile:w-full mobile:p-2">
    <button onClick={onBack} className="mb-1 flex w-full items-center gap-1 rounded-lg px-1.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] font-semibold text-white transition-colors hover:bg-white/[.06]">
      <ChevronLeft className="h-4 w-4 shrink-0" strokeWidth={2.5} />
      {title}
    </button>
    <div className="max-h-64 overflow-y-auto mobile:max-h-none">
      <button onClick={() => onSelect(null)} className="flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] text-zinc-200 transition-colors hover:bg-white/[.06]">
        <span className="truncate">{offLabel}</span>
        {selectedId === null && <Check className="h-4 w-4 shrink-0 text-accent-text" strokeWidth={2.5} />}
      </button>
      {options.map((option) => (
        <button
          key={option.id}
          onClick={() => onSelect(option.id)}
          className="flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] text-zinc-200 transition-colors hover:bg-white/[.06]"
        >
          <span className="truncate">{option.label}</span>
          {selectedId === option.id && <Check className="h-4 w-4 shrink-0 text-accent-text" strokeWidth={2.5} />}
        </button>
      ))}
    </div>
    <div className="my-1 border-t border-white/[.08]" />
    <button onClick={onAdd} className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] text-zinc-200 transition-colors hover:bg-white/[.06]">
      <Plus className="h-4 w-4 shrink-0" strokeWidth={2.5} />
      <span className="truncate">{addLabel}</span>
    </button>
  </div>;
}

// The episode picker's own panel - a plain scrollable list rather than the settings menu's
// drill-down, since there's only ever this one page of it. Numbered rows (not just titles) so a
// title-less episode ("Episode 7" everywhere) is still distinguishable at a glance.
function EpisodeListPanel({ episodes, currentEpisodeId, onSelect, title, t }: {
  episodes: Episode[];
  currentEpisodeId: string | undefined;
  onSelect: (episodeId: string) => void;
  title: string;
  t: (key: string, options?: Record<string, unknown>) => string;
}) {
  const listRef = useRef<HTMLDivElement>(null);
  // Opens already scrolled to wherever you actually are, not the top of a list that can run into
  // the hundreds - this mounts fresh every time the panel opens (see episodeListOpen below), so
  // there's no stale scroll position to worry about carrying over from a previous open.
  // useLayoutEffect, not useEffect, so this happens before the panel's first paint instead of as a
  // visible jump right after it.
  // Only the list's own vertical scroller moves: scrollIntoView would also scroll every ancestor,
  // and on the phone the panel mounts still slid off to the right, so it scrolled the whole
  // (overflow-hidden) player sideways for a moment - the jerk when the episode list opened.
  useLayoutEffect(() => {
    const item = listRef.current?.querySelector<HTMLElement>('[data-selected="true"]');
    let box = item?.parentElement ?? null;
    // The first box that actually scrolls: on the phone the list has no height cap of its own and
    // the whole side panel scrolls instead.
    while (box && !(/(auto|scroll)/.test(getComputedStyle(box).overflowY) && box.scrollHeight > box.clientHeight)) box = box.parentElement;
    if (!item || !box) return;
    const offset = item.getBoundingClientRect().top - box.getBoundingClientRect().top;
    box.scrollTop += offset - (box.clientHeight - item.offsetHeight) / 2;
  }, []);
  return <div className="w-72 p-1.5 mobile:w-full mobile:p-2">
    <div className="px-2.5 py-1.5 text-sm font-semibold text-white mobile:py-2.5 mobile:text-base">{title}</div>
    <div ref={listRef} className="max-h-72 overflow-y-auto mobile:max-h-none">
      {episodes.map((episode) => {
        const selected = episode.id === currentEpisodeId;
        return (
          <button
            key={episode.id}
            data-selected={selected}
            onClick={() => onSelect(episode.id)}
            className={cn(
              "flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-left text-sm mobile:py-3 mobile:text-[15px] transition-colors",
              selected ? "text-white" : "text-zinc-200 hover:bg-white/[.06]",
            )}
          >
            <span className="truncate">{episode.title || t("detail.episodeFallback", { number: episode.number })}</span>
            {selected && <Check className="h-4 w-4 shrink-0 text-accent-text" strokeWidth={2.5} />}
          </button>
        );
      })}
    </div>
  </div>;
}

function EpisodeListSkeleton({ title }: { title: string }) {
  return <div className="w-72 p-1.5 mobile:w-full mobile:p-2">
    <div className="px-2.5 py-1.5 text-sm font-semibold text-white">{title}</div>
    <div className="space-y-1.5 p-1">
      {Array.from({ length: 7 }, (_, index) => <div key={index} className="h-8 animate-shimmer rounded-lg" />)}
    </div>
  </div>;
}

// A YouTube-style drill-down menu (main list -> tap "Speed" -> its own page with a back arrow)
// rather than dumping every control flat in one panel - the flat version read as a wall of options
// with no hierarchy even at just three settings, and this scales worse the more get added (as
// Player/Quality below just did). "Player"/"Quality" only appear at all once there's more than one
// value to actually choose between - most episodes only ever resolve to a single stream.
function PlayerSettingsMenu({
  playbackSpeed, onSelectSpeed,
  autoSkipSegments, onToggleAutoSkip,
  autoPlayNextEpisode, onToggleAutoPlay,
  dubOptions, selectedDubId, onSelectDub, onOpenDub, dubLoading,
  translationOptions, selectedTranslation, onSelectTranslation,
  playerOptions, selectedPlayerName, onSelectPlayerName,
  qualityOptions, selectedQuality, onSelectQuality, qualityLocked,
  audioTrackOptions, selectedAudioTrack, onSelectAudioTrack,
  subtitleOptions, selectedSubtitleId, onSelectSubtitle, onAddSubtitleFile,
  t,
}: {
  playbackSpeed: number;
  onSelectSpeed: (speed: (typeof PLAYBACK_SPEEDS)[number]) => void;
  autoSkipSegments: boolean;
  onToggleAutoSkip: () => void;
  autoPlayNextEpisode: boolean;
  onToggleAutoPlay: () => void;
  dubOptions: { id: string; title: string }[];
  selectedDubId: string | undefined;
  onSelectDub: (groupId: string) => void;
  onOpenDub: () => void;
  dubLoading: boolean;
  translationOptions: string[];
  selectedTranslation: string | undefined;
  onSelectTranslation: (name: string) => void;
  playerOptions: string[];
  selectedPlayerName: string | undefined;
  onSelectPlayerName: (name: string) => void;
  qualityOptions: string[];
  selectedQuality: string | undefined;
  onSelectQuality: (quality: string) => void;
  // Playing a downloaded copy - see VideoPlayerProps.offlinePlayback. Shows the quality row as a
  // plain (non-clickable) label reading whatever quality it was downloaded in, instead of the
  // usual "> tap to pick from other resolutions" row, since there's nothing else to switch to.
  qualityLocked: boolean;
  // Streams of this dub that differ only in sound (Alloha serves some episodes so).
  audioTrackOptions: string[];
  selectedAudioTrack: string | undefined;
  onSelectAudioTrack: (audioTrack: string) => void;
  subtitleOptions: { id: string; label: string }[];
  selectedSubtitleId: string | null;
  onSelectSubtitle: (id: string | null) => void;
  onAddSubtitleFile: () => void;
  t: (key: string, options?: Record<string, unknown>) => string;
}) {
  const [page, setPage] = useState<"main" | "speed" | "dub" | "translation" | "player" | "quality" | "audio" | "subtitles" | "orientation">("main");
  const selectedDub = dubOptions.find((d) => d.id === selectedDubId);
  // The phone's screen while watching - chosen here, where it is felt, and applied at once (the root
  // layout locks the screen from this same preference while the player is open).
  const playerOrientation = usePlayerPrefsStore((s) => s.playerOrientation);
  const setPlayerOrientation = usePlayerPrefsStore((s) => s.setPlayerOrientation);
  const orientationLabels: Record<PlayerOrientation, string> = {
    landscape: t("watch.settings.orientationLandscape"),
    any: t("watch.settings.orientationAny"),
  };

  if (page === "speed") {
    return <ListPage
      title={t("watch.settings.speed")}
      options={PLAYBACK_SPEEDS.map((speed) => `${speed}×`)}
      selected={`${playbackSpeed}×`}
      onSelect={(value) => { onSelectSpeed(Number.parseFloat(value) as (typeof PLAYBACK_SPEEDS)[number]); setPage("main"); }}
      onBack={() => setPage("main")}
    />;
  }
  if (page === "dub") {
    // Keyed by title, like every other ListPage - two groups sharing a title would be
    // indistinguishable in the list anyway, so picking the first match loses nothing.
    if (dubLoading) return <div className="w-56 p-1.5 mobile:w-full mobile:p-2"><div className="mb-1 flex items-center gap-1 px-1.5 py-2 text-sm font-semibold text-white">{t("watch.settings.dub")}</div><div className="space-y-1.5 p-1">{Array.from({ length: 5 }, (_, index) => <div key={index} className="h-8 animate-shimmer rounded-lg" />)}</div></div>;
    return <ListPage
      title={t("watch.settings.dub")}
      options={dubOptions.map((d) => d.title)}
      selected={selectedDub?.title}
      onSelect={(v) => { const picked = dubOptions.find((d) => d.title === v); if (picked) onSelectDub(picked.id); setPage("main"); }}
      onBack={() => setPage("main")}
    />;
  }
  if (page === "translation") {
    return <ListPage title={t("watch.settings.translation")} options={translationOptions} selected={selectedTranslation} onSelect={(v) => { onSelectTranslation(v); setPage("main"); }} onBack={() => setPage("main")} />;
  }
  if (page === "player") {
    return <ListPage title={t("watch.settings.player")} options={playerOptions} selected={selectedPlayerName} onSelect={(v) => { onSelectPlayerName(v); setPage("main"); }} onBack={() => setPage("main")} />;
  }
  if (page === "quality") {
    return <ListPage title={t("watch.settings.quality")} options={qualityOptions} selected={selectedQuality} onSelect={(v) => { onSelectQuality(v); setPage("main"); }} onBack={() => setPage("main")} />;
  }
  if (page === "audio") {
    return <ListPage title={t("watch.settings.audioTrack")} options={audioTrackOptions} selected={selectedAudioTrack} onSelect={(v) => { onSelectAudioTrack(v); setPage("main"); }} onBack={() => setPage("main")} />;
  }
  if (page === "orientation") {
    return <ListPage
      title={t("watch.settings.orientation")}
      options={Object.values(orientationLabels)}
      selected={orientationLabels[playerOrientation]}
      onSelect={(label) => {
        const picked = (Object.keys(orientationLabels) as PlayerOrientation[]).find((key) => orientationLabels[key] === label);
        if (picked) setPlayerOrientation(picked);
        setPage("main");
      }}
      onBack={() => setPage("main")}
    />;
  }
  if (page === "subtitles") {
    return <SubtitleListPage
      title={t("watch.settings.subtitles")}
      offLabel={t("watch.subtitles.off")}
      addLabel={t("watch.subtitles.addFile")}
      options={subtitleOptions}
      selectedId={selectedSubtitleId}
      onSelect={(id) => { onSelectSubtitle(id); setPage("main"); }}
      onAdd={() => { onAddSubtitleFile(); setPage("main"); }}
      onBack={() => setPage("main")}
    />;
  }

  // A source with no real per-dub grouping names its one-and-only group "Episodes" - a structural
  // placeholder, not an actual dub name (see isGenericDubTitle) - so with nothing else to pick from
  // either, this row would only ever announce that placeholder as if it meant something.
  const hideDubRow = dubOptions.length <= 1 && isGenericDubTitle(dubOptions[0]?.title);

  return <div className="w-56 p-1.5 mobile:w-full mobile:p-2">
    {!hideDubRow && <MenuRow label={t("watch.settings.dub")} value={selectedDub?.title ?? "—"} onClick={() => { onOpenDub(); setPage("dub"); }} />}
    {translationOptions.length > 1 && <MenuRow label={t("watch.settings.translation")} value={selectedTranslation ?? "—"} onClick={() => setPage("translation")} />}
    {playerOptions.length > 0 && <MenuRow label={t("watch.settings.player")} value={selectedPlayerName ?? "—"} onClick={playerOptions.length > 1 ? () => setPage("player") : undefined} />}
    {qualityLocked
      ? selectedQuality && <MenuRow label={t("watch.settings.quality")} value={selectedQuality} />
      : qualityOptions.length > 1 && <MenuRow label={t("watch.settings.quality")} value={selectedQuality ?? "—"} onClick={() => setPage("quality")} />}
    {audioTrackOptions.length > 1 && <MenuRow label={t("watch.settings.audioTrack")} value={selectedAudioTrack ?? "—"} onClick={() => setPage("audio")} />}
    <MenuRow label={t("watch.settings.speed")} value={`${playbackSpeed}×`} onClick={() => setPage("speed")} />
    <MenuRow
      label={t("watch.settings.subtitles")}
      value={subtitleOptions.find((o) => o.id === selectedSubtitleId)?.label ?? t("watch.subtitles.off")}
      onClick={() => setPage("subtitles")}
    />
    {isMobile && <MenuRow label={t("watch.settings.orientation")} value={orientationLabels[playerOrientation]} onClick={() => setPage("orientation")} />}
    <div className="my-1 border-t border-white/[.08]" />
    <ToggleRow label={t("watch.settings.autoSkipSegments")} checked={autoSkipSegments} onChange={onToggleAutoSkip} />
    <ToggleRow label={t("watch.settings.autoPlayNextEpisode")} checked={autoPlayNextEpisode} onChange={onToggleAutoPlay} />
  </div>;
}

/** The picture-in-picture window's button names, in the app's language. */
function pipLabels(t: (key: string) => string) {
  return {
    previous: t("watch.player.previousEpisode"),
    next: t("watch.player.nextEpisode"),
    play: t("watch.player.play"),
    pause: t("watch.player.pause"),
    audioOnly: t("watch.player.audioOnly"),
  };
}

export function VideoPlayer({ link, availableLinks, offlinePlayback, dubOptions, selectedDubId, onSelectDub, sourceSwitching, unplayable, onSelectLink, onPlaybackFailure, startPositionMs, onProgress, onPlayStateChange, onCaptureThumbnail, title, episodeLabel, onBack, onPrevEpisode, onNextEpisode, onOpenEpisodes, episodesLoading, episodes, currentEpisodeId, onSelectEpisode, streakToast, playerSwitchToast }: VideoPlayerProps) {
  const { t } = useTranslation();
  // Held in a ref, deliberately not read as a prop from inside the effects below. Both the source
  // setup and the media-element wiring would otherwise have to list it as a dependency, and the
  // watch route rebuilds this callback whenever its own inputs change - which would tear down and
  // re-attach hls.js mid-episode, snapping playback back to the last saved checkpoint. Same
  // reasoning the comments on `link`, onProgress and onNextEpisode already spell out.
  const playbackFailureRef = useRef(onPlaybackFailure);
  playbackFailureRef.current = onPlaybackFailure;
  // Which of the three load paths below ends up owning the <video> element's source - read by the
  // element's own "error" handler, which must stay silent while a library is driving playback and
  // reporting its own (richer) errors.
  const elementOwnsSourceRef = useRef(false);
  const autoSkipSegments = usePlayerPrefsStore((s) => s.autoSkipSegments);
  const setAutoSkipSegments = usePlayerPrefsStore((s) => s.setAutoSkipSegments);
  const autoPlayNextEpisode = usePlayerPrefsStore((s) => s.autoPlayNextEpisode);
  const setAutoPlayNextEpisode = usePlayerPrefsStore((s) => s.setAutoPlayNextEpisode);
  const playbackSpeed = usePlayerPrefsStore((s) => s.playbackSpeed);
  const showRemainingTime = usePlayerPrefsStore((s) => s.showRemainingTime);
  const toggleRemainingTime = usePlayerPrefsStore((s) => s.toggleRemainingTime);
  const setPlaybackSpeed = usePlayerPrefsStore((s) => s.setPlaybackSpeed);
  const autoSkipDelaySeconds = usePlayerPrefsStore((s) => s.autoSkipDelaySeconds);
  const skipButtonTimeoutSeconds = usePlayerPrefsStore((s) => s.skipButtonTimeoutSeconds);
  const storedVolume = usePlayerPrefsStore((s) => s.volume);
  const storedMuted = usePlayerPrefsStore((s) => s.muted);
  const setStoredVolume = usePlayerPrefsStore((s) => s.setVolume);
  // Only one of the two is ever actually the countdown in play at a time - which one depends on
  // autoSkipSegments (see the effects below), not something the button itself chooses.
  const skipCountdownSeconds = autoSkipSegments ? autoSkipDelaySeconds : skipButtonTimeoutSeconds;
  const [settingsOpen, setSettingsOpen] = useState(false);
  const settingsRef = useRef<HTMLDivElement>(null);
  // --- subtitles ---
  // A locally picked file (see the settings menu's "add subtitle file" action) stays around
  // regardless of dub/quality/player switches - unlike a source-supplied track it was never tied to
  // `link` in the first place, and its `url` is already a converted, ready-to-play blob.
  const [customSubtitles, setCustomSubtitles] = useState<SubtitleOption[]>([]);
  const customSubtitlesRef = useRef(customSubtitles);
  customSubtitlesRef.current = customSubtitles;
  // Revokes every locally-added subtitle's blob URL once, on the player's actual teardown - not
  // keyed to `customSubtitles` itself, which would revoke (and break) a URL the moment a *second*
  // file gets added right after the first.
  useEffect(() => () => { customSubtitlesRef.current.forEach((subtitle) => URL.revokeObjectURL(subtitle.url)); }, []);
  const [selectedSubtitleId, setSelectedSubtitleId] = useState<string | null>(null);
  const subtitleFileInputRef = useRef<HTMLInputElement>(null);
  const addCustomSubtitleFile = async (file: File) => {
    try {
      const format = subtitleFormatFromUrl(file.name);
      const vtt = toVtt(format, await file.text());
      const url = URL.createObjectURL(new Blob([vtt], { type: "text/vtt" }));
      const id = `custom:${crypto.randomUUID()}`;
      setCustomSubtitles((prev) => [...prev, { id, url, label: file.name.replace(/\.[^./]+$/, "") }]);
      setSelectedSubtitleId(id);
    } catch (error) {
      log.error("player", `failed to load local subtitle "${file.name}":`, error instanceof Error ? error.message : String(error));
    }
  };
  // <track> only ever parses WebVTT - a source (or a WebView extractor, see hibiki-sources)
  // handing over SRT/ASS needs converting to a blob URL first (see lib/subtitles.ts). Most tracks
  // are already .vtt and skip the fetch+convert round trip entirely; only the few that aren't pay
  // for it. Keyed on `link` itself (stable across renders that don't actually change it, same as
  // every other effect in this component that reads off it) rather than `link?.subtitles`, which
  // would be a fresh array on every render and re-run this on every keystroke of unrelated state.
  const [resolvedSourceSubtitles, setResolvedSourceSubtitles] = useState<SubtitleOption[]>([]);
  useEffect(() => {
    let cancelled = false;
    const blobUrls: string[] = [];
    const proxySessions: string[] = [];
    // On a host with a stream proxy (Android) a subtitle file on another origin is out of reach for
    // <track> and fetch() alike (CORS), so each track gets its own proxy session carrying its
    // headers. Desktop loads it directly, as before.
    const subtitleSource = async (subtitle: { url: string; headers?: Record<string, string> | null }): Promise<string> => {
      const url = playbackUrl(subtitle.url);
      if (!usesStreamProxy()) return url;
      const sessionId = await hibiki.player.registerHeaders(url, subtitle.headers ?? link?.headers ?? null);
      if (cancelled) void hibiki.player.unregisterHeaders(sessionId);
      else proxySessions.push(sessionId);
      return streamRequestUrl(sessionId, url);
    };
    void (async () => {
      const resolved = await Promise.all((link?.subtitles ?? []).map(async (subtitle) => {
        const format = subtitleFormatFromUrl(subtitle.url);
        const label = subtitle.label ?? subtitle.language ?? "?";
        if (format === "vtt" || format === "unknown") return { id: subtitle.url, url: await subtitleSource(subtitle), label, language: subtitle.language ?? undefined };
        try {
          const response = await fetch(await subtitleSource(subtitle));
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const url = URL.createObjectURL(new Blob([toVtt(format, await response.text())], { type: "text/vtt" }));
          blobUrls.push(url);
          return { id: subtitle.url, url, label, language: subtitle.language ?? undefined };
        } catch (error) {
          log.error("player", `subtitle "${label}" failed to load/convert:`, error instanceof Error ? error.message : String(error));
          return null;
        }
      }));
      const usable = resolved.filter((entry) => entry !== null);
      if ((link?.subtitles?.length ?? 0) > 0) {
        log.info("player", `subtitles ready: ${usable.length}/${link?.subtitles?.length ?? 0}${usable.length ? ` (${usable.map((entry) => entry.label).join(", ")})` : ""}`);
      }
      if (!cancelled) setResolvedSourceSubtitles(usable);
    })();
    return () => {
      cancelled = true;
      blobUrls.forEach((url) => URL.revokeObjectURL(url));
      proxySessions.forEach((sessionId) => void hibiki.player.unregisterHeaders(sessionId));
    };
  }, [link]);
  // Memoized so the two effects below - one of them syncing native <track> state on every change -
  // don't refire on every unrelated render just because a fresh array literal compares unequal.
  const subtitleOptions = useMemo(() => [...resolvedSourceSubtitles, ...customSubtitles], [resolvedSourceSubtitles, customSubtitles]);
  // A pick stops being valid the moment it's no longer in the list backing it - almost always a
  // dub/quality/player switch replacing `link`'s own subtitle tracks wholesale. A locally added
  // file survives this: it lives in `customSubtitles`, untouched by any of that.
  useEffect(() => {
    if (selectedSubtitleId && !subtitleOptions.some((option) => option.id === selectedSubtitleId)) setSelectedSubtitleId(null);
  }, [subtitleOptions, selectedSubtitleId]);
  // The only way to actually turn a <track> on/off once it's mounted - matched by DOM order against
  // subtitleOptions, which is exactly the order the <track> elements below are rendered in. The
  // selected one goes to "hidden", not "showing" - active enough to load its cues and fire
  // "cuechange" (see the effect below, which renders them itself), but without the browser's own
  // built-in subtitle box, which always sits flush against the video's bottom edge with no way to
  // move or drag it out from under the controls bar sitting right on top of it.
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    for (let i = 0; i < video.textTracks.length; i++) {
      const option = subtitleOptions[i];
      video.textTracks[i].mode = option && option.id === selectedSubtitleId ? "hidden" : "disabled";
    }
  }, [selectedSubtitleId, subtitleOptions]);
  // The lines actually on screen right now, read off the one "hidden" TextTrack above via its own
  // "cuechange" event rather than polled - a cue's start/end is exact, a rAF/interval poll is not,
  // and the whole reason this exists instead of just using "showing" is precise native rendering.
  // A VTT cue's `.text` can carry a handful of simple markup tags (<i>, <b>, <c>, ...) - stripped
  // rather than rendered, same ceiling Android's own plain-text overlay has (see PlayerSubtitleOverlay
  // in PlayerScreen.kt).
  // For the log: which track was turned on, and whether it then loaded - "subtitles show nothing"
  // is otherwise impossible to tell apart from a track that never arrived.
  const subtitleLoggedRef = useRef<string | null>(null);
  useEffect(() => {
    const video = videoRef.current;
    const index = subtitleOptions.findIndex((option) => option.id === selectedSubtitleId);
    const option = subtitleOptions[index];
    if (!video || !option) {
      if (subtitleLoggedRef.current) log.info("player", "subtitles off");
      subtitleLoggedRef.current = null;
      return;
    }
    if (subtitleLoggedRef.current !== option.id) log.info("player", `subtitles on: "${option.label}"`);
    subtitleLoggedRef.current = option.id;
    const element = video.querySelectorAll("track")[index];
    if (!element) return;
    const onLoad = () => log.info("player", `subtitles "${option.label}" loaded: ${element.track.cues?.length ?? 0} cue(s)`);
    const onError = () => log.warn("player", `subtitles "${option.label}" failed to load`);
    if (element.readyState === HTMLTrackElement.LOADED) onLoad();
    else if (element.readyState === HTMLTrackElement.ERROR) onError();
    element.addEventListener("load", onLoad);
    element.addEventListener("error", onError);
    return () => {
      element.removeEventListener("load", onLoad);
      element.removeEventListener("error", onError);
    };
  }, [selectedSubtitleId, subtitleOptions]);

  const [activeSubtitleLines, setActiveSubtitleLines] = useState<string[]>([]);
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !selectedSubtitleId) {
      setActiveSubtitleLines([]);
      return;
    }
    const index = subtitleOptions.findIndex((option) => option.id === selectedSubtitleId);
    const track = index >= 0 ? video.textTracks[index] : undefined;
    if (!track) {
      setActiveSubtitleLines([]);
      return;
    }
    const onCueChange = () => {
      const cues = track.activeCues;
      const lines: string[] = [];
      for (let i = 0; i < (cues?.length ?? 0); i++) {
        const cue = cues![i];
        const text = "text" in cue ? String((cue as VTTCue).text) : "";
        if (text) lines.push(text.replace(/<[^>]+>/g, ""));
      }
      setActiveSubtitleLines(lines);
    };
    track.addEventListener("cuechange", onCueChange);
    onCueChange();
    return () => {
      track.removeEventListener("cuechange", onCueChange);
      setActiveSubtitleLines([]);
    };
  }, [selectedSubtitleId, subtitleOptions]);
  const subtitleOffset = usePlayerPrefsStore((s) => s.subtitleOffset);
  const subtitleOffsetX = usePlayerPrefsStore((s) => s.subtitleOffsetX);
  const setSubtitlePosition = usePlayerPrefsStore((s) => s.setSubtitlePosition);
  // Dragged live in component state during the gesture (writing straight to the persisted store on
  // every pointermove would hit localStorage synchronously each time - zustand's persist has no
  // throttling), committed to the real store only once, on pointerup.
  const subtitleDragRef = useRef<{ pointerId: number; startX: number; startY: number; startOffset: number; startOffsetX: number } | null>(null);
  const [draggingSubtitle, setDraggingSubtitle] = useState<{ y: number; x: number } | null>(null);
  // Where the box is drawn right now - above the controls bar while that is up (see subtitleBottom
  // below) - which is where a drag has to start from, or the box would jump on the first move.
  const subtitleBottomRef = useRef(subtitleOffset);
  const onSubtitleDragStart = (e: React.PointerEvent) => {
    e.stopPropagation();
    // No text selection, no click-through to the picture underneath; the touch side of the same
    // thing is the box's touch-none (a pan the browser claims ends the drag with pointercancel).
    e.preventDefault();
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    const startOffset = subtitleBottomRef.current;
    subtitleDragRef.current = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, startOffset, startOffsetX: subtitleOffsetX };
    setDraggingSubtitle({ y: startOffset, x: subtitleOffsetX });
  };
  const onSubtitleDragMove = (e: React.PointerEvent) => {
    const drag = subtitleDragRef.current;
    if (!drag || drag.pointerId !== e.pointerId) return;
    const containerHeight = containerRef.current?.clientHeight ?? 1;
    const containerWidth = containerRef.current?.clientWidth ?? 1;
    // Half of what's left once the box's own width is taken out - it may reach either edge, never
    // leave the player.
    const maxX = Math.max(0, (containerWidth - (e.currentTarget as HTMLElement).offsetWidth) / 2);
    // Screen Y grows downward, `bottom` grows upward - moving the pointer up must increase it.
    setDraggingSubtitle({
      y: Math.min(containerHeight * 0.85, Math.max(0, drag.startOffset + (drag.startY - e.clientY))),
      x: Math.min(maxX, Math.max(-maxX, drag.startOffsetX + (e.clientX - drag.startX))),
    });
  };
  const onSubtitleDragEnd = (e: React.PointerEvent) => {
    const drag = subtitleDragRef.current;
    if (!drag || drag.pointerId !== e.pointerId) return;
    subtitleDragRef.current = null;
    if (draggingSubtitle) setSubtitlePosition(draggingSubtitle.y, draggingSubtitle.x);
    setDraggingSubtitle(null);
  };
  const [episodeListOpen, setEpisodeListOpen] = useState(false);
  const episodeListRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const playbackStartedAtRef = useRef(0);
  const firstFrameLoggedRef = useRef(false);
  const playbackTraceRef = useRef<{ id: string; write: (message: string, ...details: unknown[]) => void; snapshot?: () => unknown } | null>(null);
  const playbackTimeoutRef = useRef<number | null>(null);
  const hideTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seekBarRef = useRef<HTMLDivElement>(null);

  const [playing, setPlaying] = useState(true);
  const [buffering, setBuffering] = useState(true);
  // A source change is a deliberate loading transition, not ordinary buffering. Keep the old
  // frame paused under the loading veil while an EMBED resolver works, then carry both the exact
  // position and the user's play/pause intent over to the replacement stream.
  const [switchingSource, setSwitchingSource] = useState(false);
  const pendingSourceSwitchRef = useRef<{ fromUrl: string | null; position: number; resume: boolean } | null>(null);
  const [playbackError, setPlaybackError] = useState<string | null>(null);
  const [playbackRetryKey, setPlaybackRetryKey] = useState(0);
  const retryPositionMsRef = useRef<number | null>(null);
  // The current link and the failure fallback, for the startup timeout below (which is set up
  // before either exists in this function, and fires long after the render that armed it).
  const timeoutLinkRef = useRef<PlayerLink | null>(null);
  timeoutLinkRef.current = link ?? null;
  const timeoutFallbackRef = useRef<((failedLink: PlayerLink, reason: string) => boolean) | null>(null);
  const armPlaybackTimeout = useCallback((stage: "startup" | "buffering") => {
    if (playbackTimeoutRef.current !== null) clearTimeout(playbackTimeoutRef.current);
    playbackTimeoutRef.current = window.setTimeout(() => {
      playbackTimeoutRef.current = null;
      const video = videoRef.current;
      // Network startup can outlive the timeout even after the browser has enough data to play.
      // Don't leave a stale fatal overlay on top of media that's already healthy.
      if (stage === "startup" && video && (!video.paused || video.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA)) {
        setPlaybackError(null);
        setBuffering(false);
        return;
      }
      const trace = playbackTraceRef.current;
      if (trace) trace.write(`playback ${stage} timeout after ${PLAYBACK_LOAD_TIMEOUT_MS}ms`, trace.snapshot?.() ?? {});
      else log.error("player", `playback ${stage} timeout after ${PLAYBACK_LOAD_TIMEOUT_MS}ms`);
      // A stream that never starts is as dead as one that errors: a CDN node can hand out the
      // playlist and then never answer for a single segment (Kodik's nova.cloud.solodcdn.com did,
      // the request failing only half a minute later). Move on to the next quality / player the
      // same way an error does, and only say it failed when there is nothing left to try.
      const stalled = timeoutLinkRef.current;
      if (stage === "startup" && stalled && timeoutFallbackRef.current?.(stalled, "playback startup timeout")) return;
      setPlaybackError(t("common.playbackTimeout"));
      setBuffering(false);
      // A switch that never finishes shouldn't leave the clock frozen at wherever it started
      // forever - once it's given up, the element's own (however broken) state is more honest.
      frozenDisplayRef.current = null;
    }, PLAYBACK_LOAD_TIMEOUT_MS);
  }, [t]);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  // Setting a new source on the same <video> element resets its own currentTime/duration to 0/NaN
  // the instant it happens, well before the replacement stream's metadata (and the seek back to
  // where playback was) arrives - long enough that onTimeUpdate/onLoadedMetadata below faithfully
  // report that transient 0:00/0:00 into state. Purely a display glitch (the resume-to-position
  // logic elsewhere already uses `pendingSourceSwitchRef`, not this), so it's fixed the same way:
  // freeze what the clock/seek bar *show* at the values they held right before the switch, for as
  // long as one is in flight, instead of tracking the element through its reset.
  const frozenDisplayRef = useRef<{ time: number; duration: number } | null>(null);
  // A stream failure (hls.js fatal error, dash.js error, the <video> element's own "error" event -
  // see the three call sites below) asking the parent for a fallback link used to skip straight to
  // that ask, with none of beginSourceSwitch's own bookkeeping: `link` still changed once the
  // parent answered, tearing down this stream and setting up the fallback same as any other switch,
  // but with no pendingSourceSwitchRef/frozenDisplayRef set up to go with it. onLoadedMetadata had
  // nothing telling it to resume at the failure's position (so a fallback silently restarted the
  // episode from 0 instead of picking up where the dead stream left off), and the clock/seek bar
  // just went on displaying the failed stream's last real currentTime forever - not frozen by
  // design, just never updated again, on a "playing" video that was, from this component's own
  // point of view, invisible: hls.js/dash.js/<video> were all mid-teardown, so nothing was left to
  // fire further timeupdate events into this stream's now-abandoned state. Reading straight off the
  // <video> element here (never through the `currentTime`/`duration` state) keeps this callback as
  // stable as it already was - those two tick on every frame of playback, and this same identity is
  // part of the source-setup effect's own dependency array below.
  const reportPlaybackFailure = useCallback(
    (failedLink: PlayerLink, reason: string): boolean => {
      const handled = playbackFailureRef.current?.(failedLink, reason) ?? false;
      if (handled) {
        const video = videoRef.current;
        const position = video && Number.isFinite(video.currentTime) ? video.currentTime : 0;
        const mediaDuration = video && Number.isFinite(video.duration) ? video.duration : 0;
        const resume = video ? !video.paused : true;
        pendingSourceSwitchRef.current = { fromUrl: failedLink.url, position, resume };
        frozenDisplayRef.current = { time: position, duration: mediaDuration };
        // Same as beginSourceSwitch: stop the dying stream itself, rather than leaving it running
        // (silently, off both the visible frame and the frozen clock above it) until the replacement
        // is ready to take over.
        video?.pause();
        setSwitchingSource(true);
        armPlaybackTimeout("startup");
      }
      return handled;
    },
    [armPlaybackTimeout],
  );
  timeoutFallbackRef.current = reportPlaybackFailure;
  const [buffered, setBuffered] = useState(0);
  // Seeded from the persisted preference rather than the element's own 1.0 default, so the very
  // first controls render already shows the volume this episode is about to play at.
  const [volume, setVolume] = useState(storedVolume);
  const [muted, setMuted] = useState(storedMuted);
  const [controlsVisible, setControlsVisible] = useState(true);
  // The bottom controls cover the bottom of the picture, which is where subtitles sit by default; while
  // they are up, the subtitles rise above them - readable, and within reach of a finger or the mouse
  // instead of underneath the bar.
  const bottomBarRef = useRef<HTMLDivElement>(null);
  const [bottomBarHeight, setBottomBarHeight] = useState(0);
  useLayoutEffect(() => {
    const bar = bottomBarRef.current;
    if (!bar) return;
    const measure = () => setBottomBarHeight(bar.offsetHeight);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(bar);
    return () => observer.disconnect();
  }, [isMobile]);
  const [volumeHover, setVolumeHover] = useState(false);
  // Initialised from the document: moving to another episode remounts the whole player (see the
  // keyed WatchPage), and fullscreen outlives that, so a fresh instance can start out inside it.
  const [isFullscreen, setIsFullscreen] = useState(() => !!document.fullscreenElement);
  const [isPip, setIsPip] = useState(false);
  const [seeking, setSeeking] = useState(false);
  const [hoverRatio, setHoverRatio] = useState<number | null>(null);
  // Mirrors the Android app's hold-to-fast-forward chip (top-center, "2×" + a fast-forward icon
  // in a dark rounded pill) - shown for as long as space stays held past the threshold below.
  const [fastForwardActive, setFastForwardActive] = useState(false);
  // Set on pointerup when the press had become a hold; consumed (and cleared) by the click that
  // the browser fires immediately after - see onVideoPointerUp.
  const pointerHoldWasActiveRef = useRef(false);

  // YouTube-style momentary feedback for actions that otherwise happen invisibly (space/click
  // toggling play, arrow-key seeking) - a `key`'d id (not just the kind/direction) so triggering
  // the same one twice in a row restarts its fade-out animation instead of the second press being
  // a no-op against React's own state-diffing.
  const [centerFlash, setCenterFlash] = useState<{ id: number; icon: "play" | "pause" } | null>(null);
  // Mirrors Android's PlayerDoubleTapSeekController: rapid presses in the same direction accumulate
  // into ONE overlay showing the running total ("+15" after three +5 taps) instead of each press
  // spawning its own independent flash - stacking those (the earlier bug: spamming arrow keys piled
  // up overlapping "+5"/"+5"/"+5" instances, all fading out on their own schedule) reads as a smear,
  // not feedback. `streakId` keys the outer fade (stable for the whole accumulation streak, so it
  // doesn't refade on every press); `pulse` keys just the arrow icon's own slide-in "nudge" so *that*
  // still replays on every press within the streak, same as Android's separate `arrowPulse` counter.
  const [seekFlash, setSeekFlash] = useState<{ streakId: number; direction: "back" | "forward"; totalSeconds: number; pulse: number } | null>(null);
  const seekFlashRef = useRef<{ direction: "back" | "forward"; totalSeconds: number; pulse: number; lastAtMs: number; streakId: number } | null>(null);
  const flashIdRef = useRef(0);
  const flashCenterIcon = useCallback((icon: "play" | "pause") => {
    flashIdRef.current += 1;
    setCenterFlash({ id: flashIdRef.current, icon });
  }, []);
  const SEEK_ACCUMULATION_WINDOW_MS = 700;
  const flashSeek = useCallback((direction: "back" | "forward", stepSeconds: number) => {
    const now = Date.now();
    const prev = seekFlashRef.current;
    const accumulating = !!prev && prev.direction === direction && now - prev.lastAtMs <= SEEK_ACCUMULATION_WINDOW_MS;
    const next = {
      direction,
      totalSeconds: accumulating ? prev!.totalSeconds + stepSeconds : stepSeconds,
      pulse: accumulating ? prev!.pulse + 1 : 0,
      lastAtMs: now,
      streakId: accumulating ? prev!.streakId : ++flashIdRef.current,
    };
    seekFlashRef.current = next;
    setSeekFlash(next);
  }, []);
  const FLASH_DURATION_MS = 550;
  useEffect(() => {
    if (!centerFlash) return;
    const timer = setTimeout(() => setCenterFlash(null), FLASH_DURATION_MS);
    return () => clearTimeout(timer);
  }, [centerFlash]);
  useEffect(() => {
    if (!seekFlash) return;
    const timer = setTimeout(() => {
      setSeekFlash(null);
      seekFlashRef.current = null;
    }, FLASH_DURATION_MS);
    return () => clearTimeout(timer);
  }, [seekFlash]);

  // The skip button (see PlayerSkipSegmentOverlay below) - not the auto-skip toggle itself, which
  // only decides what happens once the countdown below reaches zero.
  const [activeSegment, setActiveSegment] = useState<VideoSegment | null>(null);
  const [skipCountdown, setSkipCountdown] = useState<number>(skipCountdownSeconds);
  const [dismissedSegmentKey, setDismissedSegmentKey] = useState<string | null>(null);
  const activeSegmentKeyRef = useRef<string | null>(null);

  // Mirrors the Android app's decision (PlayerScreen.kt): trust the resolved link's own `type`
  // outright rather than sniffing the URL - a source can (and Miruro does) hand back an HLS
  // stream at a URL with no ".m3u8" in sight. There is no EMBED playback: a third-party player
  // page is never loaded into this player (see withoutUnplayableEmbeds in the main process and the
  // watch route), so `link` is always a stream this player can drive itself.

  // Translation/player/quality picking: distinct, non-empty values across every link this episode
  // could play through - PlayerSettingsMenu only shows a picker for whichever of these actually
  // has more than one option (some episodes only ever resolve to a single stream). A source can
  // vary any of the three independently per link (confirmed against yummy-anime.js: every link
  // carries its own `translation` - dub studio - *and* `playerName`, decoupled from each other).
  const [pendingSelection, setPendingSelection] = useState<Partial<Pick<PlayerLink, "translation" | "playerName" | "quality" | "audioTrack">> | null>(null);
  const links = availableLinks ?? (link ? [link] : []);
  // Which link each of these maps to lives in lib/playerLinks - see the note at the top of that
  // file. What stays here is only the UI's own concern: reflecting the pick immediately.
  const translationValues = translationOptions(links);
  const playerValues = playerOptions(links);
  const qualityValues = link ? qualityOptions(links, link) : [];
  const audioTrackValues = link ? audioTrackOptions(links, link) : [];
  const beginSourceSwitch = useCallback((target?: PlayerLink): boolean => {
    if (target && link && target.type === link.type && target.url === link.url) return false;

    const video = videoRef.current;
    const position = video && Number.isFinite(video.currentTime) ? video.currentTime : currentTime;
    pendingSourceSwitchRef.current = {
      fromUrl: link?.url ?? null,
      position,
      resume: video ? !video.paused : playing,
    };
    frozenDisplayRef.current = { time: position, duration };
    video?.pause();
    setSettingsOpen(false);
    setBuffering(true);
    armPlaybackTimeout("startup");
    setSwitchingSource(true);
    return true;
  }, [currentTime, duration, link, playing, armPlaybackTimeout]);
  /** Whether the pick actually moved playback somewhere. */
  const selectDimension = (changed: Partial<Pick<PlayerLink, "translation" | "playerName" | "quality">>): boolean => {
    // With no link resolved yet there is nothing to keep the other two dimensions *close* to, so
    // the pick is just "the first link carrying what was asked for" - which is exactly what this
    // menu is for in that state: getting off a player that isn't coming back.
    const next = link
      ? pickLinkForDimension(links, link, changed)
      : links.find((candidate) =>
          (Object.entries(changed) as [keyof typeof changed, string | null | undefined][])
            .every(([key, value]) => candidate[key] === value));
    if (!next) {
      log.info("player", `no link carries ${JSON.stringify(changed)}, leaving playback where it is`);
      return false;
    }
    if (!beginSourceSwitch(next)) return false;
    onSelectLink?.(next);
    return true;
  };
  // The menu is only told a pick took effect once it has. Announcing it up front - which is what
  // selectQuality below has always been careful not to do - left the menu showing a dub that was
  // never switched to when no link carried it, and nothing afterwards to correct it: the effect
  // that clears a pending pick keys off the link and the switching flags, none of which move when
  // nothing happened.
  const selectTranslation = (translation: string) => { if (selectDimension({ translation })) setPendingSelection({ translation }); };
  const selectPlayerName = (playerName: string) => { if (selectDimension({ playerName })) setPendingSelection({ playerName }); };
  const selectQuality = (quality: string) => {
    const candidate = link ? pickLinkForQuality(links, link, quality) : undefined;
    if (candidate && beginSourceSwitch(candidate)) { setPendingSelection({ quality }); onSelectLink?.(candidate); }
  };
  const selectAudioTrack = (audioTrack: string) => {
    const candidate = link ? pickLinkForAudioTrack(links, link, audioTrack) : undefined;
    if (candidate && beginSourceSwitch(candidate)) { setPendingSelection({ audioTrack }); onSelectLink?.(candidate); }
  };
  // What the settings menu should *say* is selected. An EMBED pick isn't a `link` swap: the parent
  // has to resolve it over the network first (see the watch route's selectLink), so reading these
  // straight off `link` left the menu showing the old player/quality for as long as that took,
  // making the pick feel like it hadn't registered. Show the pick right away and let the real link
  // catch up - the effect below drops it again as soon as it does.
  const shownTranslation = pendingSelection?.translation ?? link?.translation ?? undefined;
  const shownPlayerName = pendingSelection?.playerName ?? link?.playerName ?? undefined;
  const shownQuality = pendingSelection?.quality ?? link?.quality ?? undefined;
  const shownAudioTrack = pendingSelection?.audioTrack ?? link?.audioTrack ?? undefined;

  // Drop the optimistic pick once it can no longer differ from reality: either `link` itself
  // changed (a non-EMBED pick applies synchronously) or the parent finished resolving the EMBED
  // one. Both cases end with `link` being the source of truth again, including when resolution
  // failed and the pick simply didn't take.
  useEffect(() => {
    if (!sourceSwitching && !switchingSource) setPendingSelection(null);
  }, [link, sourceSwitching, switchingSource]);

  // --- source setup (hls.js / direct mp4) ---
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !link) return;

    let hls: Hls | null = null;
    let dash: MediaPlayerClass | null = null;
    let cancelled = false;
    let headerSessionId: string | null = null;
    const subtitleSessionIds: string[] = [];
    let networkRetryTimer: number | null = null;
    let manifestTimer: number | null = null;
    let fragmentTimer: number | null = null;
    let bufferedTimer: number | null = null;
    const traceId = `p${(++playbackTraceSequence).toString(36)}`;
    const traceStartedAt = performance.now();
    const trace = (message: string, ...details: unknown[]) => {
      log.info("player", `[${traceId} +${Math.round(performance.now() - traceStartedAt)}ms] ${message}`, ...details);
    };
    playbackTraceRef.current = { id: traceId, write: trace };
    // Providers occasionally return `//cdn…` URLs. They are remote HTTPS streams, not local
    // files; make that explicit before a packaged renderer resolves them relative to `file:`.
    // Downloaded episodes already use the explicit `hibiki-download:` scheme and remain local.
    const streamUrl = playbackUrl(link.url);
    playbackStartedAtRef.current = Date.now();
    firstFrameLoggedRef.current = false;
    const isHls = link.type === "DIRECT_HLS";
    // Aksor (and other resolvers - see extractors/*.js in hibiki-sources) sometimes only has a
    // DASH rendition available, not HLS/MP4 - Android's ExoPlayer handles this via its DASH
    // module (DashMediaSource, see PlayerScreen.kt), so this mirrors that with dash.js rather
    // than falling back to the EMBED iframe just because the direct stream happens to be DASH.
    const isDash = link.type === "DIRECT_DASH";
    trace(`selected ${link.type} ${link.translation ?? "?"}/${link.playerName ?? "?"} ${link.quality ?? "?"} at ${playbackUrlLabel(streamUrl)}; subtitles=${link.subtitles?.length ?? 0}; headers=${Object.keys(link.headers ?? {}).length}`);
    setPlaybackError(null);
    // Nothing owns the element until one of the paths below claims it - an error arriving in
    // between belongs to the stream being torn down, not to this one.
    elementOwnsSourceRef.current = false;
    // This effect also runs when switching between two already-resolved qualities/providers. The
    // old stream may still have left `buffering` false, so reset it explicitly before loading the
    // replacement to provide immediate feedback and suppress controls tied to the previous media.
    setBuffering(true);
    armPlaybackTimeout("startup");

    // Referer/User-Agent can't be set from renderer JS (forbidden headers on XHR/fetch, and a
    // plain <video src> has no header hook at all) — register them with the main process, which
    // injects them at the session level for every request to this URL's origin (playlist +
    // segments alike), then start playback once that's in place.
    const setupStartedAt = performance.now();
    hibiki.player.registerHeaders(streamUrl, link.headers).then(async (sessionId) => {
      trace(`header session ready in ${Math.round(performance.now() - setupStartedAt)}ms`);
      if (cancelled) {
        void hibiki.player.unregisterHeaders(sessionId);
        return;
      }
      headerSessionId = sessionId;
      // `<track src>` is fetched by Chromium outside hls.js, so it does not inherit the stream
      // request's headers automatically. Register every subtitle origin in the playback session;
      // tracks that carry their own headers get a small dedicated session instead.
      await Promise.all((link.subtitles ?? []).map(async (track) => {
        const subtitleUrl = playbackUrl(track.url);
        if (track.headers && Object.keys(track.headers).length > 0) {
          const subtitleSessionId = await hibiki.player.registerHeaders(subtitleUrl, track.headers);
          subtitleSessionIds.push(subtitleSessionId);
          return;
        }
        await hibiki.player.registerHeaderOrigin(sessionId, subtitleUrl);
      }));
      trace(`subtitle/header origins ready in ${Math.round(performance.now() - setupStartedAt)}ms`);
      if (cancelled) return;
      if (isHls) {
        // HLS/DASH are large libraries and the catalog never needs them. Import only the engine
        // selected by this stream, keeping both out of the application's startup bundle.
        const { default: HlsEngine } = await import("hls.js");
        trace(`hls.js import ready in ${Math.round(performance.now() - setupStartedAt)}ms`);
        if (cancelled) return;
        elementOwnsSourceRef.current = !HlsEngine.isSupported();
        if (!HlsEngine.isSupported()) {
          video.src = streamUrl;
          return;
        }
        // Which stream this player instance is about to own. A switch that silently kept the old
        // stream, or a torn-down instance still loading, is otherwise invisible in an exported log.
        trace(`creating hls.js; nativeHls=${!HlsEngine.isSupported()}; attach target=${playbackUrlLabel(streamUrl)}`);
        // hls.js's defaults keep every segment it has ever played (backBufferLength is Infinity) and
        // read ahead up to 10 minutes when the network allows, so a long session's buffered media
        // grew the renderer's memory for as long as the episode played. Thirty seconds behind the
        // playhead is plenty for a seek back; sixty ahead keeps playback smooth on a slow source.
        hls = new HlsEngine({
          backBufferLength: 30,
          maxBufferLength: 40,
          maxMaxBufferLength: 60,
          maxBufferSize: 60 * 1000 * 1000,
          // A host with a stream proxy (Android) gets every request routed through it; desktop keeps
          // hls.js's own loader.
          ...(usesStreamProxy() ? { loader: proxiedHlsLoader(HlsEngine.DefaultConfig.loader, sessionId) } : {}),
        });
        let manifestLoaded = false;
        let firstFragmentLoaded = false;
        let firstFragmentBuffered = false;
        manifestTimer = window.setTimeout(() => {
          if (!manifestLoaded && !cancelled) log.warn("player", `[${traceId} +${Math.round(performance.now() - traceStartedAt)}ms] still waiting for HLS manifest response`);
        }, 5000);
        fragmentTimer = window.setTimeout(() => {
          if (!firstFragmentLoaded && !cancelled) log.warn("player", `[${traceId} +${Math.round(performance.now() - traceStartedAt)}ms] manifest not followed by first HLS fragment response`);
        }, 12000);
        bufferedTimer = window.setTimeout(() => {
          if (!firstFragmentBuffered && !cancelled) log.warn("player", `[${traceId} +${Math.round(performance.now() - traceStartedAt)}ms] first HLS fragment has not reached buffer`);
        }, 18000);
        hls.on(HlsEngine.Events.MANIFEST_LOADING, (_event, data) => {
          trace(`manifest request started: ${playbackUrlLabel(data.url)}`);
        });
        hls.on(HlsEngine.Events.MEDIA_ATTACHED, () => trace("hls.js media source attached to video element"));
        hls.on(HlsEngine.Events.BUFFER_CREATED, (_event, data) => {
          trace("media source buffers created", Object.entries(data.tracks).map(([name, track]) => `${name}:${track?.container ?? "?"}/${track?.codec ?? "?"}`).join(", "));
        });
        hls.on(HlsEngine.Events.MANIFEST_LOADED, (_event, data) => {
          manifestLoaded = true;
          if (manifestTimer !== null) window.clearTimeout(manifestTimer);
          trace(`manifest response received in ${Math.round(data.stats.loading.end - data.stats.loading.start)}ms; levels=${data.levels.length}; url=${playbackUrlLabel(data.url)}`);
        });
        hls.on(HlsEngine.Events.LEVEL_LOADED, (_event, data) => {
          trace(`level playlist loaded: level=${data.level}, fragments=${data.details.fragments.length}, live=${data.details.live}, url=${playbackUrlLabel(data.details.url)}`);
        });
        hls.on(HlsEngine.Events.FRAG_LOADING, (_event, data) => {
          if (data.frag.sn === "initSegment" || (typeof data.frag.sn === "number" && data.frag.sn < 3)) {
            trace(`fragment request started: sn=${data.frag.sn}, type=${data.frag.type}, level=${data.frag.level}, url=${playbackUrlLabel(data.frag.url)}`);
          }
        });
        hls.on(HlsEngine.Events.FRAG_LOADED, (_event, data) => {
          if (firstFragmentLoaded) return;
          firstFragmentLoaded = true;
          if (fragmentTimer !== null) window.clearTimeout(fragmentTimer);
          const stats = data.frag.stats;
          trace(`first fragment response in ${stats ? Math.round(stats.loading.end - stats.loading.start) : "?"}ms; bytes=${stats?.loaded ?? data.payload.byteLength}; sn=${data.frag.sn}; url=${playbackUrlLabel(data.frag.url)}`);
        });
        hls.on(HlsEngine.Events.FRAG_BUFFERED, (_event, data) => {
          if (!firstFragmentBuffered) {
            firstFragmentBuffered = true;
            if (bufferedTimer !== null) window.clearTimeout(bufferedTimer);
            trace(`first fragment buffered; sn=${data.frag.sn}; buffer=${video.buffered.length ? `${video.buffered.start(0).toFixed(2)}-${video.buffered.end(video.buffered.length - 1).toFixed(2)}s` : "empty"}`);
          }
          networkRetries = 0;
          mediaRetries = 0;
        });
        let appendedSegmentsLogged = 0;
        hls.on(HlsEngine.Events.BUFFER_APPENDED, (_event, data) => {
          if (appendedSegmentsLogged++ >= 5) return;
          const ranges = Object.entries(data.timeRanges).flatMap(([name, range]) => {
            if (!range || range.length === 0) return [];
            return [`${name}:${Array.from({ length: range.length }, (_, index) => `${range.start(index).toFixed(2)}-${range.end(index).toFixed(2)}s`).join(",")}`];
          });
          const pending = (data as typeof data & { pending?: number }).pending ?? "?";
          trace(`buffer append completed: parent=${data.parent}, sn=${data.frag.sn}, pending=${pending}`, ranges.join("; "));
        });
        // Without this, a failed manifest/segment load (CORS, a dead CDN host, ...) just leaves
        // the "buffering" spinner turning forever with nothing in the console to explain why -
        // network/media errors are usually transient (hls.js's own recommended recovery), but a
        // fatal, unrecoverable one should at least surface instead of spinning silently.
        //
        // A bare hls?.startLoad() on every NETWORK_ERROR (hls.js's own suggested pattern) assumes
        // the failure is transient - fine for a blip, but a manifest URL that's just plain dead
        // (an expired token from a browser-runtime resolver, a 403/404) fails the exact same way
        // on every retry, forever: error → startLoad() → same error → startLoad() → ... with
        // nothing ever reaching the user but an eternal spinner (seen live: exactly this, on a
        // YummyAnime stream resolved through the Alloha browser-resolver). Capping retries and
        // giving up into the visible error overlay after a few tries fixes that without losing
        // the recovery behavior for genuinely transient blips.
        let networkRetries = 0;
        const MAX_NETWORK_RETRIES = 3;
        // A manifest that came back with an HTTP status has been answered: the server has looked
        // for this rendition and said what it found. Retrying it three times on a backoff spends
        // twenty-odd seconds of the user's time before reaching the fallback link that was sitting
        // there the whole while (seen live: Kodik listing a 720p that resolves to a file its CDN
        // does not have, answered with a 500 every time). Only the statuses that actually mean
        // "ask again later" are worth the wait; everything else goes straight to the fallback.
        // 500 and Cloudflare's own 52x are in here for the same reason 502/503/504 are: a CDN edge
        // hiccuping on one segment is not the server saying this stream is gone, and dropping the
        // whole link over it would move a playable stream to the back of the fallback list.
        const RETRYABLE_MANIFEST_STATUSES = new Set([408, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524, 525, 526, 527]);
        // recoverMediaError() was uncapped - a stream with a genuinely broken fragment (not a
        // transient decode hiccup) just re-throws the same fatal MEDIA_ERROR immediately after
        // every recovery attempt, forever: error → recover → same error → recover → ... which
        // looks exactly like a freeze/lag from the outside (seen live: repeated fragParsingError/
        // bufferStalledError/bufferAppendNoProgress with playback stuck). Capped the same way
        // NETWORK_ERROR already was, giving up into the visible error overlay after a few tries.
        let mediaRetries = 0;
        const MAX_MEDIA_RETRIES = 3;
        let emptyFragment = { sn: "", count: 0 };
        const MAX_EMPTY_FRAGMENT_REPEATS = 4;
        hls.on(HlsEngine.Events.MANIFEST_PARSED, (_event, data) => {
          networkRetries = 0;
          trace(`manifest parsed; levels=${data.levels.length}; selectedLevel=${hls?.currentLevel ?? "auto"}`);
        });
        // A successful buffer append is the real signal that recovery actually worked - resetting
        // only on MANIFEST_PARSED (which fires once, near the very start) would let one recovered
        // error early in playback silently use up the whole retry budget for a later, unrelated one.
        hls.on(HlsEngine.Events.ERROR, (_event, data) => {
          // Non-fatal is hls.js saying it has handled this itself. Recording it is worth doing,
          // reporting it as an error is not - a single dub switch produces a burst of them as the
          // segments in flight are cancelled, which buried the one line that mattered.
          const write = data.fatal ? log.error : log.debug;
          const stats = data.stats;
          const request = data.networkDetails as XMLHttpRequest | undefined;
          const requestDuration = stats?.loading?.start != null && stats.loading.end > stats.loading.start
            ? `${Math.round(stats.loading.end - stats.loading.start)}ms`
            : "incomplete";
          const failedUrl = data.response?.url ?? request?.responseURL ?? data.url ?? data.frag?.url ?? streamUrl;
          write("player", `[${traceId} +${Math.round(performance.now() - traceStartedAt)}ms] hls.js ${data.fatal ? "fatal" : "non-fatal"} error:`, data.type, data.details, data.reason ?? "", data.response ? `http ${data.response.code}` : "", `url=${playbackUrlLabel(failedUrl)}`, `fragment=${data.frag?.sn ?? "?"}`, `level=${data.level ?? data.frag?.level ?? "?"}`, `context=${data.context?.type ?? "?"}`, `buffer=${data.buffer ?? data.bufferInfo?.len ?? "?"}`, `appendNoProgress=${data.appendsWithoutProgress ?? "?"}`, `requestStatus=${request?.status ?? "?"}`, request?.getResponseHeader?.("X-Hibiki-Error") ? `proxy=${request.getResponseHeader("X-Hibiki-Error")}` : "", `loaded=${stats?.loaded ?? "?"}`, `requestDuration=${requestDuration}`);
          // hls.js treats a fragment that parses to no media as non-fatal and asks for it again, without
          // end: seen live with Kodik after a seek, the same segment came back empty for fifteen seconds
          // until the startup timeout moved on. The CDN answers that segment the same way each time, so
          // a few repeats are enough to call the stream broken and go to the next link.
          if (!data.fatal && data.details === HlsEngine.ErrorDetails.FRAG_PARSING_ERROR && data.frag) {
            const sn = String(data.frag.sn);
            emptyFragment = emptyFragment.sn === sn ? { sn, count: emptyFragment.count + 1 } : { sn, count: 1 };
            if (emptyFragment.count >= MAX_EMPTY_FRAGMENT_REPEATS) {
              const reason = `fragment ${sn} has no media`;
              log.warn("player", `${reason} after ${emptyFragment.count} tries; giving up on this stream`);
              if (!reportPlaybackFailure(link, reason)) setPlaybackError(reason);
              hls?.destroy();
              return;
            }
          }
          if (!data.fatal) return;
          switch (data.type) {
            case HlsEngine.ErrorTypes.NETWORK_ERROR: {
              const isManifestFailure = data.details === HlsEngine.ErrorDetails.MANIFEST_LOAD_ERROR ||
                data.details === HlsEngine.ErrorDetails.MANIFEST_LOAD_TIMEOUT;
              const status = data.response?.code;
              // A 4xx/5xx on a level/fragment is just as definitive as one on the master. The old
              // manifest-only check retried every MegaPlay 403 three times before trying the next
              // mirror, turning a finite fallback list into minutes of apparent endless loading.
              const answered = typeof status === "number" && status > 0 &&
                !RETRYABLE_MANIFEST_STATUSES.has(status);
              networkRetries += 1;
              if (answered || networkRetries > MAX_NETWORK_RETRIES) {
                const reason = data.details || "playback failed";
                if (answered) log.info("player", `manifest answered http ${status}, not retrying`);
                if (!reportPlaybackFailure(link, reason)) setPlaybackError(reason);
                hls?.destroy();
                break;
              }
              // startLoad() resumes fragment loading, but does not reliably recreate the loader
              // after the initial manifest itself failed. Reload that URL explicitly; later
              // fragment failures can resume from the current position. A short backoff prevents
              // a dead edge host from being hammered while still recovering quickly from a blip.
              if (networkRetryTimer !== null) window.clearTimeout(networkRetryTimer);
              networkRetryTimer = window.setTimeout(() => {
                networkRetryTimer = null;
                if (cancelled || !hls) return;
                log.info("player", `retrying ${isManifestFailure ? "manifest" : "stream"} load (${networkRetries}/${MAX_NETWORK_RETRIES})`);
                if (!isManifestFailure) {
                  hls.startLoad();
                  return;
                }
                // Retrying the *same* URL is a coin flip when what failed is the CDN's own
                // load-balancing redirect (Kodik hands out p14.solodcdn.com and 302s to p13 -
                // verified live). A redirected XHR has to pass the CORS check on the redirect hop
                // too, and these CDNs send no Access-Control-Allow-Origin of their own, so the
                // retry only started working once Chromium had cached the redirect and stopped
                // making one. Ask the main process where the URL actually lands (no CORS there)
                // and load that instead, so the retry has nothing left to redirect through.
                // Relative segment URLs resolve against it as well, keeping the rest of the
                // stream on the host that answered.
                void hibiki.player.resolveStreamUrl(streamUrl, link.headers).then((finalUrl) => {
                  if (cancelled || !hls) return;
                if (finalUrl !== streamUrl) trace(`retrying manifest at redirect target ${playbackUrlLabel(finalUrl)}`);
                  hls.loadSource(finalUrl);
                });
              }, 500 * (2 ** (networkRetries - 1)));
              break;
            }
            case HlsEngine.ErrorTypes.MEDIA_ERROR:
              mediaRetries += 1;
              if (mediaRetries > MAX_MEDIA_RETRIES) {
                setPlaybackError(data.details || "playback failed");
                hls?.destroy();
                break;
              }
              hls?.recoverMediaError();
              break;
            default:
              setPlaybackError(data.details || "playback failed");
              hls?.destroy();
          }
        });
        hls.loadSource(streamUrl);
        hls.attachMedia(video);
        trace("loadSource() and attachMedia() called");
      } else if (isDash) {
        const { MediaPlayer: DashMediaPlayer } = await import("dashjs");
        if (cancelled) return;
        elementOwnsSourceRef.current = false;
        dash = DashMediaPlayer().create();
        if (usesStreamProxy()) {
          // Requests go through the proxy, but each response is reported under its upstream URL (the
          // final one after redirects, X-Hibiki-Final-Url): dash.js resolves the manifest's relative
          // segment URLs against that, and against the proxy they would point nowhere.
          const upstreamByRequest = new Map<string, string>();
          dash.addRequestInterceptor(async (request) => {
            const proxied = streamRequestUrl(sessionId, request.url);
            if (proxied !== request.url) upstreamByRequest.set(proxied, request.url);
            request.url = proxied;
            return request;
          });
          dash.addResponseInterceptor(async (response) => {
            const headers = (response.headers ?? {}) as Record<string, string>;
            const upstream = headers["x-hibiki-final-url"] ?? headers["X-Hibiki-Final-Url"] ?? upstreamByRequest.get(response.request.url);
            upstreamByRequest.delete(response.request.url);
            if (upstream) response.url = upstream;
            return response;
          });
        }
        // Same reasoning as hls.js above: dash.js retries transient errors on its own, but a
        // manifest that's simply dead keeps re-erroring forever with nothing surfaced unless
        // this caps it and gives up into the visible error overlay.
        let dashErrors = 0;
        const MAX_DASH_RETRIES = 3;
        dash.on(DashMediaPlayer.events.STREAM_INITIALIZED, () => {
          dashErrors = 0;
        });
        dash.on(DashMediaPlayer.events.ERROR, (e) => {
          log.error("player", "dash.js error:", e);
          dashErrors += 1;
          if (dashErrors > MAX_DASH_RETRIES) {
            const detail = typeof e.error === "object" && e.error ? e.error.message : e.error;
            const reason = detail || "playback failed";
            if (!reportPlaybackFailure(link, reason)) setPlaybackError(reason);
            dash?.destroy();
          }
        });
        dash.initialize(video, streamUrl, true);
      } else {
        elementOwnsSourceRef.current = true;
        video.src = streamRequestUrl(sessionId, streamUrl);
      }
    }).catch((error) => {
      if (cancelled) return;
      log.error("player", `[${traceId} +${Math.round(performance.now() - traceStartedAt)}ms] playback setup failed at header/subtitle/player initialization:`, error);
      setPlaybackError(error instanceof Error ? error.message : "playback setup failed");
    });

    return () => {
      cancelled = true;
      trace(`teardown; stage=${firstFrameLoggedRef.current ? "playing" : "not-playing"}`);
      // A superseded player instance may still own outstanding network requests. Distinguish that
      // from a stream that independently failed so logs don't misdiagnose user-driven switches.
      if (manifestTimer !== null) window.clearTimeout(manifestTimer);
      if (fragmentTimer !== null) window.clearTimeout(fragmentTimer);
      if (bufferedTimer !== null) window.clearTimeout(bufferedTimer);
      if (networkRetryTimer !== null) window.clearTimeout(networkRetryTimer);
      if (playbackTimeoutRef.current !== null) clearTimeout(playbackTimeoutRef.current);
      playbackTimeoutRef.current = null;
      if (playbackTraceRef.current?.id === traceId) playbackTraceRef.current = null;
      if (hls) trace(`detaching hls: ${playbackUrlLabel(streamUrl)}`);
      hls?.destroy();
      dash?.destroy();
      if (headerSessionId) void hibiki.player.unregisterHeaders(headerSessionId);
      for (const sessionId of subtitleSessionIds) void hibiki.player.unregisterHeaders(sessionId);
    };
  }, [link, armPlaybackTimeout, playbackRetryKey]);

  // --- media element event wiring ---
  // Capture while the playback surface still exists, never during unmount.
  // A late capture response must not save pixels from the destination page.
  useEffect(() => {
    let cancelled = false;
    let pending = false;
    let lastCapture = 0;
    const video = videoRef.current;
    const capture = async () => {
      const surface = video;
      if (cancelled || pending || !onCaptureThumbnail || !surface?.isConnected || document.hidden) return;
      if (!video || video.readyState < 2 || video.seeking) return;
      if (Date.now() - lastCapture < 2000) return;
      const rect = surface.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      pending = true;
      lastCapture = Date.now();
      try {
        const dataUrl = await hibiki.player.captureFrame({ x: rect.x, y: rect.y, width: rect.width, height: rect.height });
        if (!cancelled && surface.isConnected && dataUrl) onCaptureThumbnail(dataUrl);
      } catch (error) {
        log.warn("player", "thumbnail capture failed:", error);
      } finally {
        pending = false;
      }
    };
    const timer = window.setInterval(capture, 5000);
    video?.addEventListener("playing", capture);
    video?.addEventListener("seeked", capture);
    video?.addEventListener("pause", capture);
    return () => {
      cancelled = true;
      clearInterval(timer);
      video?.removeEventListener("playing", capture);
      video?.removeEventListener("seeked", capture);
      video?.removeEventListener("pause", capture);
    };
  }, [link, onCaptureThumbnail]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    let waitingSince: number | null = null;
    let lastTimeUpdateLogAt = 0;
    let lastLoggedBufferedEnd = -1;
    const mediaState = () => {
      const ranges: string[] = [];
      try {
        for (let index = 0; index < video.buffered.length; index += 1) {
          ranges.push(`${video.buffered.start(index).toFixed(2)}-${video.buffered.end(index).toFixed(2)}`);
        }
      } catch { /* MediaSource can invalidate ranges during a source switch. */ }
      return {
        currentSrc: video.currentSrc ? playbackUrlLabel(video.currentSrc) : "",
        readyState: `${video.readyState}(${["HAVE_NOTHING", "HAVE_METADATA", "HAVE_CURRENT_DATA", "HAVE_FUTURE_DATA", "HAVE_ENOUGH_DATA"][video.readyState] ?? "?"})`,
        networkState: `${video.networkState}(${["NETWORK_EMPTY", "NETWORK_IDLE", "NETWORK_LOADING", "NETWORK_NO_SOURCE"][video.networkState] ?? "?"})`,
        currentTime: Number.isFinite(video.currentTime) ? Number(video.currentTime.toFixed(2)) : null,
        duration: Number.isFinite(video.duration) ? Number(video.duration.toFixed(2)) : null,
        paused: video.paused,
        seeking: video.seeking,
        playbackRate: video.playbackRate,
        buffered: ranges,
        error: video.error ? { code: video.error.code, message: video.error.message } : null,
      };
    };
    const activeTrace = playbackTraceRef.current;
    if (activeTrace) activeTrace.snapshot = mediaState;
    const observe = (event: string, extra?: Record<string, unknown>) => {
      playbackTraceRef.current?.write(`video.${event}`, { ...mediaState(), ...extra });
    };
    const mediaEvents = ["loadstart", "loadeddata", "canplaythrough", "stalled", "suspend", "emptied", "seeking", "seeked", "abort"] as const;
    const mediaEventHandlers = mediaEvents.map((event) => {
      const handler = () => observe(event);
      video.addEventListener(event, handler);
      return [event, handler] as const;
    });

    const onLoadedMetadata = () => {
      setDuration(video.duration);
      observe("loadedmetadata", { elapsedMs: playbackStartedAtRef.current > 0 ? Date.now() - playbackStartedAtRef.current : null });
      const pendingSwitch = pendingSourceSwitchRef.current;
      const isReplacementStream = !!pendingSwitch && pendingSwitch.fromUrl !== link?.url;
      if (isReplacementStream && video.duration) {
        video.currentTime = Math.min(pendingSwitch.position, Math.max(0, video.duration - 1));
        if (!pendingSwitch.resume) video.pause();
      } else if (retryPositionMsRef.current !== null && video.duration) {
        video.currentTime = Math.min(retryPositionMsRef.current / 1000, video.duration - 1);
        retryPositionMsRef.current = null;
      } else if (startPositionMs && video.duration) {
        video.currentTime = Math.min(startPositionMs / 1000, video.duration - 1);
      }
      video.playbackRate = playbackSpeed;
    };
    // Mirrors Android's segment-skip (PlayerScreen.kt): a currentTime that lands inside an
    // OPENING/ENDING window surfaces the skip button/countdown effect below - checked every
    // timeupdate tick rather than scheduled up front, since seeking backward into a segment (or a
    // segment starting exactly at 0) needs to surface it too, not just the first crossing into it.
    // Only updates state on an actual change (entering/leaving a segment), not every tick.
    const onTimeUpdate = () => {
      setCurrentTime(video.currentTime);
      if (onProgress && Number.isFinite(video.duration)) {
        onProgress(video.currentTime * 1000, video.duration * 1000);
      }
      const nowMs = video.currentTime * 1000;
      const segment = link?.segments?.find((s) => (s.type === "OPENING" || s.type === "ENDING") && nowMs >= s.startMs && nowMs < s.endMs) ?? null;
      const key = segment ? segmentKey(segment) : null;
      if (key !== activeSegmentKeyRef.current) {
        activeSegmentKeyRef.current = key;
        setActiveSegment(segment);
        setSkipCountdown(skipCountdownSeconds);
      }
    };
    const onEnded = () => {
      observe("ended");
      if (autoPlayNextEpisode && onNextEpisode) log.info("player", "episode ended; playing the next one");
      if (autoPlayNextEpisode) onNextEpisode?.();
    };
    const onProgressEvent = () => {
      if (video.buffered.length > 0) {
        const bufferedEnd = video.buffered.end(video.buffered.length - 1);
        setBuffered(bufferedEnd);
        if (bufferedEnd - lastLoggedBufferedEnd >= 2) {
          lastLoggedBufferedEnd = bufferedEnd;
          observe("progress", { bufferedEnd: Number(bufferedEnd.toFixed(2)) });
        }
      }
    };
    const onPlay = () => {
      setPlaying(true);
      observe("play (playback requested)", { elapsedMs: playbackStartedAtRef.current > 0 ? Date.now() - playbackStartedAtRef.current : null });
      onPlayStateChange?.(true);
      if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "playing";
    };
    const onPause = () => {
      setPlaying(false);
      observe("pause");
      onPlayStateChange?.(false);
      if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "paused";
    };
    const onWaiting = () => {
      if (waitingSince === null) waitingSince = performance.now();
      observe("waiting", { waitingForMs: 0 });
      setBuffering(true);
      armPlaybackTimeout("buffering");
    };
    const onCanPlay = (event: "canplay" | null = "canplay") => {
      if (event) observe(event);
      if (playbackTimeoutRef.current !== null) clearTimeout(playbackTimeoutRef.current);
      playbackTimeoutRef.current = null;
      setPlaybackError(null);
      setBuffering(false);
      const pendingSwitch = pendingSourceSwitchRef.current;
      if (!pendingSwitch || pendingSwitch.fromUrl === link?.url) return;

      // Explicitly synced afterwards rather than left to the element's own "play"/"pause" events:
      // this <video> can carry leftover listeners/state from the stream that just failed (an
      // automatic fallback - see reportPlaybackFailure - never paused the dying stream the way a
      // user-initiated switch does), and a play() that silently resolves without ever actually
      // starting playback fires no "play" event to correct `playing` with. Reading `video.paused`
      // straight after settling is the one thing that can't disagree with what's really happening,
      // whichever of those left it stuck showing "playing" over audio that was actually paused.
      if (pendingSwitch.resume) {
        video.play().catch((error: unknown) => logPlayRequestFailure("source switch", error)).finally(() => setPlaying(!video.paused));
      } else {
        video.pause();
        setPlaying(false);
      }
      pendingSourceSwitchRef.current = null;
      frozenDisplayRef.current = null;
      setSwitchingSource(false);
    };
    const onCanPlayEvent = () => onCanPlay();
    const onPlaying = () => {
      const waitedMs = waitingSince === null ? null : Math.round(performance.now() - waitingSince);
      waitingSince = null;
      if (!firstFrameLoggedRef.current && playbackStartedAtRef.current > 0) {
        firstFrameLoggedRef.current = true;
        observe("first playing", { elapsedMs: Date.now() - playbackStartedAtRef.current, waitedMs });
      } else {
        observe("playing", { waitedMs });
      }
      onCanPlay(null);
    };
    const onTimeUpdateDiagnostic = () => {
      const now = performance.now();
      if (now - lastTimeUpdateLogAt < 5000) return;
      lastTimeUpdateLogAt = now;
      observe("timeupdate");
    };
    const onVolumeChange = () => { setVolume(video.volume); setMuted(video.muted); setStoredVolume(video.volume, video.muted); };
    // Only meaningful for the direct-<video src> path (hls.js has its own error events, wired up
    // where the source is set up) - a dead/CORS-blocked direct MP4 link would otherwise leave the
    // buffering spinner turning forever with no console trace at all.
    // Only when the element itself is what loaded the stream: hls.js and dash.js attach to this
    // same <video>, report their own errors and recover from some of them, so letting this fire
    // alongside them would double-report and pre-empt their retries.
    //
    // Keying that on "the link is DIRECT_MP4" was close but not equivalent - a DIRECT_HLS link
    // falls through to `video.src` too whenever Hls.isSupported() is false, and that path would
    // then have had no error reporting at all: no overlay, no fallback, just a spinner forever.
    const onError = () => {
      observe("error");
      if (!link || !elementOwnsSourceRef.current) return;
      log.error("player", "<video> element error:", `code ${video.error?.code}`, video.error?.message ?? "");
      const reason = video.error?.message || "playback failed";
      if (!reportPlaybackFailure(link, reason)) setPlaybackError(reason);
    };

    video.addEventListener("loadedmetadata", onLoadedMetadata);
    video.addEventListener("timeupdate", onTimeUpdate);
    video.addEventListener("timeupdate", onTimeUpdateDiagnostic);
    video.addEventListener("progress", onProgressEvent);
    video.addEventListener("play", onPlay);
    video.addEventListener("pause", onPause);
    video.addEventListener("waiting", onWaiting);
    video.addEventListener("canplay", onCanPlayEvent);
    video.addEventListener("playing", onPlaying);
    video.addEventListener("volumechange", onVolumeChange);
    video.addEventListener("error", onError);
    video.addEventListener("ended", onEnded);

    return () => {
      if (playbackTraceRef.current === activeTrace && activeTrace) activeTrace.snapshot = undefined;
      video.removeEventListener("loadedmetadata", onLoadedMetadata);
      video.removeEventListener("timeupdate", onTimeUpdate);
      video.removeEventListener("timeupdate", onTimeUpdateDiagnostic);
      video.removeEventListener("progress", onProgressEvent);
      video.removeEventListener("play", onPlay);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("waiting", onWaiting);
      video.removeEventListener("canplay", onCanPlayEvent);
      video.removeEventListener("playing", onPlaying);
      video.removeEventListener("volumechange", onVolumeChange);
      video.removeEventListener("ended", onEnded);
      video.removeEventListener("error", onError);
      for (const [event, handler] of mediaEventHandlers) video.removeEventListener(event, handler);
    };
  }, [link, startPositionMs, onProgress, onPlayStateChange, reportPlaybackFailure, autoPlayNextEpisode, playbackSpeed, onNextEpisode, setStoredVolume, armPlaybackTimeout]);

  // Push the remembered volume onto the element itself. A fresh <video> (new episode, new stream
  // after a player switch) always comes up at 1.0 unmuted, so this has to re-run per `link`, not
  // just once on mount. Reading the live store instead of the `volume`/`muted` state keeps this
  // off the render loop: the element's own volumechange is what feeds those back.
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const { volume: preferred, muted: preferredMuted } = usePlayerPrefsStore.getState();
    video.volume = preferred;
    video.muted = preferredMuted;
  }, [link]);

  // Applied separately (not just via loadedmetadata above) so changing the speed in the in-player
  // settings menu takes effect immediately on whatever's already playing, not just next episode.
  useEffect(() => {
    const video = videoRef.current;
    if (video) video.playbackRate = playbackSpeed;
  }, [playbackSpeed]);

  // A new episode's segments start from a clean slate - a dismissed key from the previous episode
  // has no business suppressing this one's button, even in the unlikely case the keys collide.
  // Keyed on the URL, not the `link` object itself - selectPlayerLink() (WatchPage) rebuilds that
  // object on every render even for the same episode, which would otherwise reset this on every
  // unrelated re-render (a progress-query refetch, ...), not just on an actual episode change.
  useEffect(() => {
    activeSegmentKeyRef.current = null;
    setActiveSegment(null);
    setDismissedSegmentKey(null);
  }, [link?.url]);

  // Mirrors Android's PlayerSkipSegmentOverlay/SKIP_SEGMENT_COUNTDOWN_SECONDS: the button (and its
  // countdown) is shown for every OPENING/ENDING segment regardless of the auto-skip setting - that
  // setting only decides what happens once the countdown reaches zero (jump automatically vs. just
  // let the button quietly disappear), so a title with segment data but auto-skip turned off still
  // gets a clickable "Пропустить" button instead of nothing at all.
  useEffect(() => {
    if (!activeSegment) return;
    const key = segmentKey(activeSegment);
    if (dismissedSegmentKey === key) return;
    setSkipCountdown(skipCountdownSeconds);
    const interval = setInterval(() => {
      setSkipCountdown((seconds) => {
        if (seconds > 1) return seconds - 1;
        clearInterval(interval);
        if (autoSkipSegments) {
          const video = videoRef.current;
          if (video) video.currentTime = activeSegment.endMs / 1000;
        }
        setDismissedSegmentKey(key);
        return 0;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [activeSegment, dismissedSegmentKey, autoSkipSegments, skipCountdownSeconds]);

  const skipSegmentNow = useCallback(() => {
    if (!activeSegment) return;
    const video = videoRef.current;
    if (video) video.currentTime = activeSegment.endMs / 1000;
    setDismissedSegmentKey(segmentKey(activeSegment));
  }, [activeSegment]);

  const dismissSegmentPrompt = useCallback(() => {
    if (!activeSegment) return;
    setDismissedSegmentKey(segmentKey(activeSegment));
  }, [activeSegment]);

  // Closing on outside click (not just re-toggling the gear) matches every other dropdown in the
  // app; keeping controls from auto-hiding while it's open avoids the menu floating over faded-out
  // controls with no gear left to close it.
  useEffect(() => {
    if (!settingsOpen) return;
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    const onPointerDown = (e: PointerEvent) => {
      if (e.target instanceof Node && settingsRef.current?.contains(e.target)) return;
      setSettingsOpen(false);
    };
    window.addEventListener("pointerdown", onPointerDown, { capture: true });
    return () => window.removeEventListener("pointerdown", onPointerDown, { capture: true });
  }, [settingsOpen]);

  // Same reasoning as the settings menu's outside-click effect above, for the episode list panel.
  useEffect(() => {
    if (!episodeListOpen) return;
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    const onPointerDown = (e: PointerEvent) => {
      if (e.target instanceof Node && episodeListRef.current?.contains(e.target)) return;
      setEpisodeListOpen(false);
    };
    window.addEventListener("pointerdown", onPointerDown, { capture: true });
    return () => window.removeEventListener("pointerdown", onPointerDown, { capture: true });
  }, [episodeListOpen]);

  // --- fullscreen tracking ---
  useEffect(() => {
    const onChange = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", onChange);
    return () => document.removeEventListener("fullscreenchange", onChange);
  }, []);

  // --- picture-in-picture tracking (native fallback path only - see togglePip) ---
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const onEnter = () => setIsPip(true);
    const onLeave = () => setIsPip(false);
    video.addEventListener("enterpictureinpicture", onEnter);
    video.addEventListener("leavepictureinpicture", onLeave);
    return () => {
      video.removeEventListener("enterpictureinpicture", onEnter);
      video.removeEventListener("leavepictureinpicture", onLeave);
    };
  }, []);

  // The browser's native picture-in-picture window (and the OS media-key overlay) only grows the
  // ±seek buttons and a scrubbing timeline once a Media Session with those actions actually
  // exists - without this, PiP falls back to a bare video frame with nothing but a generic
  // "back to tab" link, which is exactly what a plain requestPictureInPicture() call gets you on
  // its own.
  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    navigator.mediaSession.metadata = new MediaMetadata({ title: episodeLabel || title, artist: title });
    return () => { navigator.mediaSession.metadata = null; };
  }, [title, episodeLabel]);

  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    const video = videoRef.current;
    if (!video) return;
    // The PiP window's own skip icons are a fixed 15s, independent of this player's in-app
    // keyboard/double-tap seek step - two different surfaces, no reason to tie them together.
    const PIP_SEEK_STEP_SECONDS = 15;
    const seekBy = (deltaSeconds: number) => {
      video.currentTime = Math.min(Math.max(0, video.currentTime + deltaSeconds), video.duration || Infinity);
    };
    navigator.mediaSession.setActionHandler("play", () => void video.play().catch((error: unknown) => logPlayRequestFailure("media session", error)));
    navigator.mediaSession.setActionHandler("pause", () => video.pause());
    // Electron's Chromium build renders "previoustrack"/"nexttrack" as clickable icons flanking
    // play/pause in the native PiP window, but not "seekbackward"/"seekforward" (confirmed by
    // hand: registering only the seek actions left the PiP window with no flanking buttons at
    // all) - so the ±15s skip has to ride on the icon slots that actually render, rather than the
    // ones semantically meant for it.
    navigator.mediaSession.setActionHandler("previoustrack", () => seekBy(-PIP_SEEK_STEP_SECONDS));
    navigator.mediaSession.setActionHandler("nexttrack", () => seekBy(PIP_SEEK_STEP_SECONDS));
    return () => {
      navigator.mediaSession.setActionHandler("play", null);
      navigator.mediaSession.setActionHandler("pause", null);
      navigator.mediaSession.setActionHandler("previoustrack", null);
      navigator.mediaSession.setActionHandler("nexttrack", null);
    };
  }, []);

  // --- auto-hide controls while playing ---
  const scheduleHide = useCallback(() => {
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    hideTimerRef.current = setTimeout(() => setControlsVisible(false), CONTROLS_HIDE_DELAY_MS);
  }, []);
  const wake = useCallback(() => {
    setControlsVisible(true);
    if (playing) scheduleHide();
  }, [playing, scheduleHide]);
  useEffect(() => {
    if (playing) scheduleHide();
    else if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    return () => { if (hideTimerRef.current) clearTimeout(hideTimerRef.current); };
  }, [playing, scheduleHide]);
  // Phone: nothing moves a mouse to keep the controls awake, so an open menu holds them up itself;
  // shown controls (a tap) start the hide countdown the mouse would have started.
  const menuOpenOnPhone = isMobile && (settingsOpen || episodeListOpen);
  useEffect(() => {
    if (!isMobile) return;
    if (menuOpenOnPhone) {
      if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
      setControlsVisible(true);
    } else if (controlsVisible && playing) scheduleHide();
  }, [menuOpenOnPhone, controlsVisible, playing, scheduleHide]);
  useBackHandler(settingsOpen || episodeListOpen, () => { setSettingsOpen(false); setEpisodeListOpen(false); });

  // --- the phone's picture in picture (as the Kotlin app has it) ---
  // The window shows only the picture (and subtitles); its buttons are previous / play-pause / next,
  // plus "audio only" (the window goes, the sound goes on). Leaving the app otherwise pauses, as
  // the Kotlin player does, and coming back resumes what was playing.
  const pip = hibiki.device?.pip;
  const [pipActive, setPipActive] = useState(false);
  const pipActiveRef = useRef(false);
  const audioOnlyRef = useRef(false);
  const pipHandlers = useRef({ togglePlay: () => {}, previous: () => {}, next: () => {} });
  pipHandlers.current = {
    togglePlay: () => {
      const video = videoRef.current;
      if (!video) return;
      if (video.paused) void video.play().catch((error: unknown) => logPlayRequestFailure("picture in picture", error));
      else video.pause();
    },
    previous: () => onPrevEpisode?.(),
    next: () => onNextEpisode?.(),
  };
  useEffect(() => {
    if (!pip) return;
    const offMode = pip.onModeChange((active) => {
      pipActiveRef.current = active;
      setPipActive(active);
      if (active) { setSettingsOpen(false); setEpisodeListOpen(false); }
    });
    const offAction = pip.onAction((action) => {
      if (action === "toggle") pipHandlers.current.togglePlay();
      else if (action === "previous") pipHandlers.current.previous();
      else if (action === "next") pipHandlers.current.next();
      else if (action === "audioOnly") {
        audioOnlyRef.current = true;
        void videoRef.current?.play().catch(() => undefined);
        hibiki.device?.minimize();
      }
    });
    let resumeOnReturn = false;
    const onVisibility = () => {
      const video = videoRef.current;
      if (!video) return;
      if (document.hidden) {
        // The small window closed, or the app left without one: pause - unless it's audio only.
        if (pipActiveRef.current || audioOnlyRef.current) return;
        resumeOnReturn = !video.paused;
        video.pause();
      } else {
        audioOnlyRef.current = false;
        if (resumeOnReturn) void video.play().catch(() => undefined);
        resumeOnReturn = false;
      }
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      offMode();
      offAction();
      document.removeEventListener("visibilitychange", onVisibility);
      // Off the player: no window to go to on leaving the app.
      pip.update({ enabled: false, playing: false, hasPrevious: false, hasNext: false, labels: pipLabels(t) });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- handlers are read through pipHandlers
  }, [pip]);
  useEffect(() => {
    if (!pip) return;
    const video = videoRef.current;
    pip.update({
      enabled: !!link && !unplayable,
      playing,
      hasPrevious: !!onPrevEpisode,
      hasNext: !!onNextEpisode,
      width: video?.videoWidth || undefined,
      height: video?.videoHeight || undefined,
      labels: pipLabels(t),
    });
  }, [pip, link, unplayable, playing, onPrevEpisode, onNextEpisode, t, buffering]);
  const enterPip = () => {
    if (!pip) return;
    setControlsVisible(false);
    void pip.enter();
  };

  // Shared by the space-bar hold below and the mouse-hold handlers - same threshold and speed, so
  // holding the left button reads exactly like holding space.
  const HOLD_TO_FAST_FORWARD_MS = 350;
  const FAST_FORWARD_SPEED = 2;
  // A press that turned into a hold has to swallow the click the browser fires on release,
  // otherwise letting go would also pause the video through the container's own onClick.
  const pointerHoldRef = useRef<{ timer: ReturnType<typeof setTimeout> | null; active: boolean }>({ timer: null, active: false });
  const endPointerHold = useCallback(() => {
    const hold = pointerHoldRef.current;
    if (hold.timer) { clearTimeout(hold.timer); hold.timer = null; }
    if (!hold.active) return;
    hold.active = false;
    setFastForwardActive(false);
    const video = videoRef.current;
    if (video) video.playbackRate = playbackSpeed;
  }, [playbackSpeed]);
  // Only a plain left-press on the video itself starts a hold - the controls sit on top of this
  // container, and a press that begins on the seek bar or a button is that control's business.
  const onVideoPointerDown = useCallback((e: React.PointerEvent) => {
    if (e.button !== 0) return;
    if (e.target !== videoRef.current && e.target !== containerRef.current) return;
    const hold = pointerHoldRef.current;
    if (hold.timer) clearTimeout(hold.timer);
    hold.timer = setTimeout(() => {
      hold.active = true;
      const video = videoRef.current;
      if (video) video.playbackRate = FAST_FORWARD_SPEED;
      setFastForwardActive(true);
    }, HOLD_TO_FAST_FORWARD_MS);
  }, []);
  const onVideoPointerUp = useCallback(() => {
    // Read before endPointerHold() clears it - the click event that follows this release still has
    // to know whether it belonged to a hold.
    pointerHoldWasActiveRef.current = pointerHoldRef.current.active;
    endPointerHold();
  }, [endPointerHold]);

  // --- the phone's taps: one shows or hides the controls; two quick ones on the left or right part
  // of the screen seek 10s back or forward, and every further tap on that side while the "+10" is
  // still up adds another 10s (YouTube's and Android's own players behave the same way). A lone
  // tap waits out the double-tap window before it acts, or a double tap would flash the controls.
  // The taps only add up: the video jumps once, by the total, when the streak ends - one seek and
  // one load instead of a fetch from every intermediate position along the way.
  const MOBILE_SEEK_SECONDS = 10;
  const DOUBLE_TAP_MS = 280;
  const mobileTapRef = useRef<{
    timer: ReturnType<typeof setTimeout> | null;
    at: number;
    side: "back" | "forward" | null;
    seekUntil: number;
    pendingSeconds: number;
    commit: ReturnType<typeof setTimeout> | null;
  }>({ timer: null, at: 0, side: null, seekUntil: 0, pendingSeconds: 0, commit: null });
  const commitMobileSeek = useCallback(() => {
    const tap = mobileTapRef.current;
    if (tap.commit) { clearTimeout(tap.commit); tap.commit = null; }
    const video = videoRef.current;
    const delta = tap.pendingSeconds;
    tap.pendingSeconds = 0;
    if (!video || delta === 0) return;
    video.currentTime = Math.min(Math.max(0, video.currentTime + delta), video.duration || Infinity);
  }, []);
  useEffect(() => () => {
    const tap = mobileTapRef.current;
    if (tap.timer) clearTimeout(tap.timer);
    if (tap.commit) clearTimeout(tap.commit);
  }, []);
  const onMobileTap = (e: React.MouseEvent) => {
    const box = containerRef.current?.getBoundingClientRect();
    if (!box) return;
    const x = (e.clientX - box.left) / box.width;
    const side = x < 0.4 ? "back" : x > 0.6 ? "forward" : null;
    const tap = mobileTapRef.current;
    const now = Date.now();
    const seek = (direction: "back" | "forward") => {
      if (tap.timer) { clearTimeout(tap.timer); tap.timer = null; }
      tap.seekUntil = now + SEEK_ACCUMULATION_WINDOW_MS;
      tap.side = direction;
      tap.pendingSeconds += direction === "back" ? -MOBILE_SEEK_SECONDS : MOBILE_SEEK_SECONDS;
      if (tap.commit) clearTimeout(tap.commit);
      tap.commit = setTimeout(commitMobileSeek, SEEK_ACCUMULATION_WINDOW_MS);
      flashSeek(direction, MOBILE_SEEK_SECONDS);
    };
    if (side && side === tap.side && (now < tap.seekUntil || (tap.timer && now - tap.at < DOUBLE_TAP_MS))) {
      seek(side);
      return;
    }
    // Anything else ends a streak that is still adding up: it lands now.
    commitMobileSeek();
    if (tap.timer) clearTimeout(tap.timer);
    tap.at = now;
    tap.side = side;
    tap.seekUntil = 0;
    tap.timer = setTimeout(() => {
      tap.timer = null;
      setControlsVisible((visible) => !visible);
    }, DOUBLE_TAP_MS);
  };

  const togglePlay = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      void video.play().catch((error: unknown) => logPlayRequestFailure("player controls", error));
      flashCenterIcon("play");
    } else {
      video.pause();
      flashCenterIcon("pause");
    }
  }, [flashCenterIcon]);

  const toggleMute = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !video.muted;
  }, []);

  const toggleFullscreenNow = useCallback(() => {
    if (document.fullscreenElement) document.exitFullscreen();
    // The app's player wrapper rather than the player itself: switching episode remounts the player,
    // and an element that leaves the document takes fullscreen with it. The wrapper outlives it.
    else (containerRef.current?.closest<HTMLElement>("[data-player-fullscreen-root]") ?? containerRef.current)?.requestFullscreen();
  }, []);

  const togglePip = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    const video = videoRef.current;
    if (!video) return;
    if (document.pictureInPictureElement) {
      document.exitPictureInPicture().catch((err) => log.error("player", "failed to exit picture-in-picture:", err));
    } else {
      video.requestPictureInPicture().catch((err) => log.error("player", "failed to enter picture-in-picture:", err));
    }
  }, []);

  // --- keyboard shortcuts: space play/pause (hold to fast-forward at 2x, TikTok/YouTube-style),
  // f fullscreen, escape leaves, left/right seek ±5s, up/down volume, m mutes ---
  const SEEK_STEP_SECONDS = 5;
  const VOLUME_STEP = 0.05;
  useEffect(() => {
    // Refs, not state - this only ever needs to be read/written from key handlers, and re-running
    // the whole effect (which state would trigger) on every hold-start/hold-end would keep
    // tearing down and re-attaching both listeners for no reason.
    const spaceHold = { timer: null as ReturnType<typeof setTimeout> | null, active: false };
    const onKeyDown = (e: KeyboardEvent) => {
      const video = videoRef.current;
      switch (e.code) {
        case "Space":
          e.preventDefault();
          if (e.repeat) break; // the browser auto-repeats keydown while held - only the first matters
          wake();
          spaceHold.timer = setTimeout(() => {
            spaceHold.active = true;
            if (video) video.playbackRate = FAST_FORWARD_SPEED;
            setFastForwardActive(true);
          }, HOLD_TO_FAST_FORWARD_MS);
          break;
        case "KeyF":
          e.preventDefault();
          toggleFullscreenNow();
          wake();
          break;
        case "Escape":
          // If we're in fullscreen, let the browser's own Escape handling exit that first —
          // calling onBack() too would navigate away in the same keystroke as leaving fullscreen.
          if (!document.fullscreenElement) {
            e.preventDefault();
            onBack();
          }
          break;
        case "ArrowLeft":
          if (!video) break;
          e.preventDefault();
          video.currentTime = Math.max(0, video.currentTime - SEEK_STEP_SECONDS);
          flashSeek("back", SEEK_STEP_SECONDS);
          wake();
          break;
        case "ArrowRight":
          if (!video) break;
          e.preventDefault();
          video.currentTime = Math.min(video.duration || Infinity, video.currentTime + SEEK_STEP_SECONDS);
          flashSeek("forward", SEEK_STEP_SECONDS);
          wake();
          break;
        case "ArrowUp":
          if (!video) break;
          e.preventDefault();
          video.volume = Math.min(1, video.volume + VOLUME_STEP);
          video.muted = false;
          wake();
          break;
        case "ArrowDown":
          if (!video) break;
          e.preventDefault();
          video.volume = Math.max(0, video.volume - VOLUME_STEP);
          wake();
          break;
        case "KeyM":
          e.preventDefault();
          toggleMute();
          wake();
          break;
      }
    };
    const onKeyUp = (e: KeyboardEvent) => {
      if (e.code !== "Space") return;
      e.preventDefault();
      if (spaceHold.timer) {
        clearTimeout(spaceHold.timer);
        spaceHold.timer = null;
      }
      if (spaceHold.active) {
        // Was a hold, not a tap - playback was never paused, just sped up, so release just
        // restores the normal speed rather than also toggling play.
        spaceHold.active = false;
        setFastForwardActive(false);
        const video = videoRef.current;
        if (video) video.playbackRate = playbackSpeed;
      } else {
        togglePlay();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      if (spaceHold.timer) clearTimeout(spaceHold.timer);
    };
  }, [togglePlay, toggleMute, toggleFullscreenNow, onBack, wake, playbackSpeed, flashSeek]);

  const ratioFromClientX = useCallback((clientX: number) => {
    const bar = seekBarRef.current;
    if (!bar) return 0;
    const rect = bar.getBoundingClientRect();
    return Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
  }, []);

  const seekToClientX = useCallback((clientX: number) => {
    const video = videoRef.current;
    if (!video || !duration) return;
    const ratio = ratioFromClientX(clientX);
    video.currentTime = ratio * duration;
    setCurrentTime(ratio * duration);
    setHoverRatio(ratio);
  }, [duration, ratioFromClientX]);

  const onSeekPointerDown = (e: React.PointerEvent) => {
    e.stopPropagation();
    setSeeking(true);
    seekToClientX(e.clientX);
  };
  useEffect(() => {
    if (!seeking) return;
    const onMove = (e: PointerEvent) => seekToClientX(e.clientX);
    const onUp = () => setSeeking(false);
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    return () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
  }, [seeking, seekToClientX]);

  const onSeekAreaMouseMove = (e: React.MouseEvent) => setHoverRatio(ratioFromClientX(e.clientX));
  const onSeekAreaMouseLeave = () => { if (!seeking) setHoverRatio(null); };

  const toggleFullscreen = (e: React.MouseEvent) => {
    e.stopPropagation();
    toggleFullscreenNow();
  };

  const onMuteButtonClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    toggleMute();
  };

  const onVolumeInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.stopPropagation();
    const video = videoRef.current;
    if (!video) return;
    const next = Number(e.target.value);
    video.volume = next;
    video.muted = next === 0;
  };

  const stop = (e: React.SyntheticEvent) => e.stopPropagation();
  // The phone's bars span the full width with a gradient behind them, so a tap on the empty part of
  // one is a tap on the picture: it must reach the container (to hide the controls), while a tap on a
  // button or the seek strip stays with that control.
  const stopOnControl = (e: React.SyntheticEvent) => {
    const target = e.target as Element;
    if (target.closest("button, a, input") || seekBarRef.current?.contains(target)) e.stopPropagation();
  };

  // A pick that turned out unplayable never swaps `link`, so nothing else ends the switch the menu
  // began (paused, frozen clock, spinner): hand the stream that was already playing back its state.
  useEffect(() => {
    if (!unplayable || !switchingSource || !link) return;
    const video = videoRef.current;
    const resume = pendingSourceSwitchRef.current?.resume ?? false;
    pendingSourceSwitchRef.current = null;
    frozenDisplayRef.current = null;
    setSwitchingSource(false);
    setBuffering(false);
    if (video && resume) video.play().catch((error: unknown) => logPlayRequestFailure("abandoned switch", error)).finally(() => setPlaying(!video.paused));
  }, [unplayable, switchingSource, link]);

  const VolumeIcon = muted || volume === 0 ? VolumeX : volume < 0.5 ? Volume1 : Volume2;
  // What the clock and seek bar actually render - the frozen snapshot while a switch is still
  // settling (see frozenDisplayRef above), the element's real state otherwise. Every other use of
  // currentTime/duration in this file (segment detection, onProgress, the seek ratio math) keeps
  // reading the live values - only what's drawn on screen is held still.
  const isSwitching = sourceSwitching || switchingSource;
  const displayCurrentTime = isSwitching && frozenDisplayRef.current ? frozenDisplayRef.current.time : currentTime;
  const displayDuration = isSwitching && frozenDisplayRef.current ? frozenDisplayRef.current.duration : duration;
  const playedPercent = displayDuration ? (displayCurrentTime / displayDuration) * 100 : 0;
  const bufferedPercent = displayDuration ? (buffered / displayDuration) * 100 : 0;
  // The phone's time labels keep one width for the whole episode: room for the longest value it can
  // show (at least m:ss up to 99:59, plus the remaining-time minus on the right), so the seek bar
  // between them doesn't jump when the duration arrives or the minutes gain a digit.
  const timeLabelChars = Math.max(5, formatTime(displayDuration).length);

  // The two menus the controls open - one copy each, shown from the desktop bar or the phone's.
  const episodeListPanel = episodesLoading || !episodes
    ? <EpisodeListSkeleton title={t("detail.episodes")} />
    : <EpisodeListPanel
        episodes={episodes}
        currentEpisodeId={currentEpisodeId}
        onSelect={(episodeId) => {
          setEpisodeListOpen(false);
          if (episodeId === currentEpisodeId) return;
          onSelectEpisode?.(episodeId);
        }}
        title={t("detail.episodes")}
        t={t}
      />;
  const settingsMenu = <PlayerSettingsMenu
    playbackSpeed={playbackSpeed}
    onSelectSpeed={setPlaybackSpeed}
    autoSkipSegments={autoSkipSegments}
    onToggleAutoSkip={() => setAutoSkipSegments(!autoSkipSegments)}
    autoPlayNextEpisode={autoPlayNextEpisode}
    onToggleAutoPlay={() => setAutoPlayNextEpisode(!autoPlayNextEpisode)}
    dubOptions={dubOptions ?? []}
    selectedDubId={selectedDubId}
    onOpenDub={() => onOpenEpisodes?.()}
    dubLoading={episodesLoading ?? false}
    onSelectDub={(id) => {
      setSettingsOpen(false);
      if (id === selectedDubId) return;
      beginSourceSwitch();
      onSelectDub?.(id);
    }}
    translationOptions={translationValues}
    selectedTranslation={shownTranslation}
    onSelectTranslation={selectTranslation}
    playerOptions={playerValues}
    selectedPlayerName={shownPlayerName}
    onSelectPlayerName={selectPlayerName}
    qualityOptions={qualityValues}
    selectedQuality={shownQuality}
    onSelectQuality={selectQuality}
    qualityLocked={!!offlinePlayback}
    audioTrackOptions={offlinePlayback ? [] : audioTrackValues}
    selectedAudioTrack={shownAudioTrack}
    onSelectAudioTrack={selectAudioTrack}
    subtitleOptions={subtitleOptions}
    selectedSubtitleId={selectedSubtitleId}
    onSelectSubtitle={setSelectedSubtitleId}
    onAddSubtitleFile={() => subtitleFileInputRef.current?.click()}
    t={t}
  />;

  // Subtitles, episodes and settings sit up top beside the title, where a phone player keeps them;
  // their menus open as a panel down the right side of the screen.
  const mobileTopActions = <div className="ml-auto flex shrink-0 items-center gap-1">
    {pip && (
      <button onClick={(e) => { stop(e); enterPip(); }} disabled={!link || !!unplayable} aria-label={t("watch.player.pictureInPicture")} className={cn("flex h-10 w-10 items-center justify-center rounded-full", !link || unplayable ? "text-white/25" : "text-white active:bg-white/10")}>
        <PictureInPicture2 className="h-[22px] w-[22px]" strokeWidth={2} />
      </button>
    )}
    <button
      onClick={(e) => { stop(e); setSelectedSubtitleId(selectedSubtitleId ? null : subtitleOptions[0].id); }}
      disabled={subtitleOptions.length === 0}
      aria-pressed={!!selectedSubtitleId}
      className={cn("relative flex h-10 w-10 items-center justify-center rounded-full", subtitleOptions.length === 0 ? "text-white/25" : "text-white active:bg-white/10")}
    >
      <Captions className="h-[22px] w-[22px]" strokeWidth={2} />
      <span className={cn("absolute bottom-1.5 h-[2px] w-4 rounded-full bg-accent transition-opacity", selectedSubtitleId ? "opacity-100" : "opacity-0")} />
    </button>
    {(episodesLoading || !episodes || episodes.length > 1) && (
      <button onClick={(e) => { stop(e); onOpenEpisodes?.(); setSettingsOpen(false); setEpisodeListOpen(true); }} className="flex h-10 w-10 items-center justify-center rounded-full text-white active:bg-white/10">
        <ListVideo className="h-[22px] w-[22px]" strokeWidth={2} />
      </button>
    )}
    <button onClick={(e) => { stop(e); setEpisodeListOpen(false); setSettingsOpen(true); }} className="flex h-10 w-10 items-center justify-center rounded-full text-white active:bg-white/10">
      <Settings className="h-[22px] w-[22px]" strokeWidth={2} />
    </button>
  </div>;

  // The phone's controls. The middle of the screen holds play/pause with the previous and next
  // episode beside it; seeking ±10s is a double tap on either half (see onMobileTap), so it needs
  // no buttons. The bottom is one line: the time, the seek bar the thumb can grab anywhere along,
  // and the length (tap it for what is left). Volume is the phone's own keys.
  const busy = isSwitching || buffering || !link || !!playbackError || !!unplayable;
  const mobileControls = <>
    {/* Gone while an error is up: the error's own message and retry take the middle of the screen. */}
    <div className={cn("pointer-events-none absolute inset-0 z-[5] flex items-center justify-center gap-12 transition-opacity duration-300", controlsVisible ? "opacity-100" : "opacity-0", (playbackError || unplayable) && "hidden")}>
      <button
        onClick={(e) => { stop(e); onPrevEpisode?.(); }}
        disabled={!onPrevEpisode}
        aria-label={t("watch.player.previousEpisode", { defaultValue: "Previous episode" })}
        className={cn("flex h-12 w-12 items-center justify-center rounded-full bg-black/40", controlsVisible && "pointer-events-auto", onPrevEpisode ? "text-white active:bg-black/60" : "text-white/25")}
      >
        <SkipBack className="h-6 w-6 fill-current" strokeWidth={1.5} />
      </button>
      {/* The spinner takes this spot while there is nothing to play yet. */}
      <button
        onClick={(e) => { stop(e); togglePlay(); wake(); }}
        className={cn("flex h-16 w-16 items-center justify-center rounded-full bg-black/45 text-white active:bg-black/65", controlsVisible && !busy && "pointer-events-auto", busy && "invisible")}
      >
        {playing ? <Pause className="h-8 w-8 fill-current" strokeWidth={0} /> : <Play className="ml-1 h-8 w-8 fill-current" strokeWidth={0} />}
      </button>
      <button
        onClick={(e) => { stop(e); onNextEpisode?.(); }}
        disabled={!onNextEpisode}
        aria-label={t("watch.player.nextEpisode", { defaultValue: "Next episode" })}
        className={cn("flex h-12 w-12 items-center justify-center rounded-full bg-black/40", controlsVisible && "pointer-events-auto", onNextEpisode ? "text-white active:bg-black/60" : "text-white/25")}
      >
        <SkipForward className="h-6 w-6 fill-current" strokeWidth={1.5} />
      </button>
    </div>
    <div
      ref={bottomBarRef}
      className={cn("absolute inset-x-0 bottom-0 z-[6] bg-gradient-to-t from-black/80 to-transparent pt-10 transition-opacity duration-300", controlsVisible ? "opacity-100" : "pointer-events-none opacity-0")}
      style={{ paddingLeft: "max(1.25rem, var(--safe-left))", paddingRight: "max(1.25rem, var(--safe-right))", paddingBottom: "max(0.375rem, var(--safe-bottom))" }}
      onClick={stopOnControl}
    >
      <div className="flex items-center gap-3">
        <span className="shrink-0 text-left text-[13px] font-medium tabular-nums text-zinc-100" style={{ minWidth: `${timeLabelChars}ch` }}>{formatTime(displayCurrentTime)}</span>
        {/* A tall strip to land a thumb on; the visible track stays thin, the thumb always shown. */}
        <div className="relative min-w-0 flex-1">
          {seeking && hoverRatio !== null && duration > 0 && (
            <div className="absolute bottom-full mb-1 -translate-x-1/2 rounded-md bg-red-600 px-2 py-1 text-xs font-bold tabular-nums text-white shadow-lg" style={{ left: `${hoverRatio * 100}%` }}>
              {formatTime(hoverRatio * duration)}
            </div>
          )}
          <div ref={seekBarRef} onPointerDown={onSeekPointerDown} className="relative flex h-9 touch-none items-center">
            <div className="relative h-[3px] w-full overflow-hidden rounded-full bg-white/25 shadow-[0_1px_3px_rgba(0,0,0,0.7)]">
              <div className="absolute inset-y-0 left-0 bg-white/45" style={{ width: `${bufferedPercent}%` }} />
              <div className="absolute inset-y-0 left-0 bg-red-600" style={{ width: `${playedPercent}%` }} />
            </div>
            <div className={cn("absolute -translate-x-1/2 rounded-full bg-red-600 shadow-[0_1px_4px_rgba(0,0,0,0.8)] transition-[width,height]", seeking ? "h-4 w-4" : "h-3 w-3")} style={{ left: `${playedPercent}%` }} />
          </div>
        </div>
        <button onClick={(e) => { stop(e); toggleRemainingTime(); }} className="shrink-0 py-2 text-right text-[13px] font-medium tabular-nums text-zinc-100" style={{ minWidth: `${timeLabelChars + 1}ch` }}>
          {showRemainingTime ? `-${formatTime(Math.max(0, displayDuration - displayCurrentTime))}` : formatTime(displayDuration)}
        </button>
      </div>
    </div>
    <AnimatePresence>
      {(settingsOpen || episodeListOpen) && (
        <motion.div key="panel" className="absolute inset-0 z-30" onClick={stop} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
          <div className="absolute inset-0 bg-black/40" onClick={() => { setSettingsOpen(false); setEpisodeListOpen(false); }} />
          <motion.div
            // The menus' own refs, so their outside-click closing (see the effects above) knows taps
            // inside the panel from taps on the dimmed video beside it.
            ref={settingsOpen ? settingsRef : episodeListRef}
            initial={{ x: "100%" }}
            animate={{ x: 0 }}
            exit={{ x: "100%" }}
            transition={{ type: "spring", stiffness: 420, damping: 42 }}
            className="absolute inset-y-0 right-0 w-[min(400px,48vw)] overflow-y-auto overscroll-contain bg-[#141418]/[.97] shadow-[-12px_0_40px_rgba(0,0,0,0.5)]"
            style={{ paddingRight: "max(0.5rem, var(--safe-right))", paddingTop: "0.5rem", paddingBottom: "0.5rem" }}
          >
            {settingsOpen ? settingsMenu : episodeListPanel}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  </>;

  // The bar's own top padding is a fade over the picture, not controls: the subtitles may overlap it.
  const SUBTITLE_BAR_FADE_PX = 28;
  const subtitleBottom = draggingSubtitle?.y
    ?? (controlsVisible && bottomBarHeight > 0 ? Math.max(subtitleOffset, bottomBarHeight - SUBTITLE_BAR_FADE_PX) : subtitleOffset);
  subtitleBottomRef.current = subtitleBottom;

  return (
    <div
      ref={containerRef}
      // The cursor goes with the controls (they only ever hide while playing), including over children
      // that set their own, like the draggable subtitle box.
      className={cn("group/player relative h-full w-full select-none overflow-hidden bg-black", !controlsVisible && "[&_*]:!cursor-none cursor-none")}
      data-pip={pipActive ? "" : undefined}
      // Not on the phone: a tap sends a compatibility mousemove first, which would show the controls
      // just before the tap itself toggles them off again.
      onMouseMove={isMobile ? undefined : wake}
      onPointerDown={onVideoPointerDown}
      onPointerUp={onVideoPointerUp}
      // A pointer that leaves the player (or gets cancelled by the OS) never sends pointerup here,
      // which would otherwise strand playback at 2x with the chip still showing.
      onPointerLeave={endPointerHold}
      onPointerCancel={endPointerHold}
      onClick={(e) => {
        if (pointerHoldWasActiveRef.current) { pointerHoldWasActiveRef.current = false; return; }
        if (isMobile) onMobileTap(e);
        else togglePlay();
      }}
    >
      <video ref={videoRef} crossOrigin="anonymous" autoPlay className="h-full w-full object-contain">
        {/* Order matches subtitleOptions exactly - the mode-sync effect above matches this element's
            resulting TextTrack back to its option purely by DOM/textTracks index. */}
        {subtitleOptions.map((option) => (
          <track key={option.id} kind="subtitles" src={option.url} srcLang={option.language} label={option.label} />
        ))}
      </video>
      <input
        ref={subtitleFileInputRef}
        type="file"
        accept=".vtt,.srt,.ass,.ssa,text/vtt"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (file) void addCustomSubtitleFile(file);
        }}
      />

      {/* Rendered by hand from the "hidden" TextTrack's own cues (see the cuechange effect above),
          not the browser's built-in subtitle box - that one is always flush against the video's
          bottom edge, which is exactly what put it underneath this app's own controls bar in the
          first place, and native rendering has no drag handle to fix that with. No z-index on purpose:
          it sits under the controls bar and every other piece of player UI, which come later in the
          DOM. `bottom` and the horizontal shift are player preferences (see playerPrefsStore), dragged live via the pointer handlers on the
          box itself and only committed to the store on release. */}
      {activeSubtitleLines.length > 0 && (
        <div
          data-subtitles
          // Above the bars (z-[6]) so the box can always be taken hold of; it stays clear of their
          // buttons by rising over them (subtitleBottom) rather than by sitting underneath.
          className={cn("pointer-events-none absolute inset-x-0 z-[7] flex justify-center px-6", !draggingSubtitle && "transition-[bottom] duration-300")}
          style={{ bottom: subtitleBottom }}
        >
          <div
            onPointerDown={onSubtitleDragStart}
            onPointerMove={onSubtitleDragMove}
            onPointerUp={onSubtitleDragEnd}
            onPointerCancel={onSubtitleDragEnd}
            onClick={(e) => e.stopPropagation()}
            title={t("watch.subtitles.dragHint")}
            style={{ transform: `translateX(${draggingSubtitle?.x ?? subtitleOffsetX}px)` }}
            className={cn(
              "pointer-events-auto max-w-[85%] cursor-grab touch-none select-none whitespace-pre-line rounded-md bg-black/75 px-3 py-1.5 text-center text-lg font-medium leading-snug text-white shadow-lg active:cursor-grabbing",
              draggingSubtitle && "ring-1 ring-white/40",
            )}
          >
            {activeSubtitleLines.join("\n")}
          </div>
        </div>
      )}

      {/* Sits outside the controlsVisible-gated top bar below on purpose - a streak update is a
          one-off announcement, not part of the persistent chrome, so it shows up (and fades back
          out on its own) whether or not the controls happen to be visible right now. */}
      <StreakToast streak={streakToast} />
      <PlayerSwitchToast toast={playerSwitchToast} />

      {/* Mirrors the Android app's hold-to-fast-forward chip exactly: top-center, a dark rounded
          pill with "2×" then a fast-forward icon, fade+scale in/out. */}
      <AnimatePresence>
        {fastForwardActive && (
          <motion.div
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.96 }}
            transition={{ duration: 0.15, ease: "easeOut" }}
            className="pointer-events-none absolute inset-x-0 top-7 z-10 flex justify-center"
          >
            <div className="flex items-center gap-[3px] rounded-2xl bg-black/70 py-[7px] pl-3 pr-2.5">
              <span className="text-base font-medium text-white">2×</span>
              <FastForward className="h-[19px] w-[19px] fill-white text-white" strokeWidth={0} />
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Not on the phone: there the big play/pause button itself already says what happened. */}
      <AnimatePresence>
        {!isMobile && centerFlash && (
          <motion.div
            key={centerFlash.id}
            initial={{ opacity: 0.9, scale: 0.7 }}
            animate={{ opacity: 0, scale: 1.35 }}
            transition={{ duration: FLASH_DURATION_MS / 1000, ease: "easeOut" }}
            className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center"
          >
            <div className="flex h-20 w-20 items-center justify-center rounded-full bg-black/60">
              {centerFlash.icon === "play" ? (
                <Play className="ml-1 h-9 w-9 fill-white text-white" strokeWidth={0} />
              ) : (
                <Pause className="h-9 w-9 fill-white text-white" strokeWidth={0} />
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Matches the Android app's double-tap seek overlay: a plain arrow (drawn twice, offset
          black copy behind a white one for a cheap drop-shadow) next to a signed "+5"/"-5", no
          pill background. The arrow sits in its own fixed-size, absolutely-positioned slot with
          its own nested AnimatePresence keyed by `pulse` - each additional press within the
          accumulation window swaps in a fresh arrow that slides in from center as the previous
          one slides out to the edge, all in that same slot, which is what makes spamming the key
          read as one continuously "nudged" arrow instead of a pile of separately-timed icons each
          fading out on their own (the previous bug). The outer fade is keyed by `streakId`
          instead, which only changes when a new streak starts - it stays mounted (and its own
          auto-hide timer keeps getting pushed back, see the effect above) across every pulse in
          the same streak. */}
      {/* Phone: the double tap's answer fills the side that was tapped - a soft arc with the arrows and
          the running total centred in it, the way a phone's own video players show it. */}
      <AnimatePresence>
        {isMobile && seekFlash && (
          <motion.div
            key={seekFlash.streakId}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className={cn(
              "pointer-events-none absolute inset-y-0 z-10 flex w-[36%] flex-col items-center justify-center gap-1.5 bg-white/[.07]",
              seekFlash.direction === "back" ? "left-0 rounded-r-[50%]" : "right-0 rounded-l-[50%]",
            )}
          >
            <motion.span key={seekFlash.pulse} initial={{ opacity: 0.4, scale: 0.85 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.18 }}>
              {seekFlash.direction === "back" ? <ChevronsLeft className="h-9 w-9 text-white" strokeWidth={2.25} /> : <ChevronsRight className="h-9 w-9 text-white" strokeWidth={2.25} />}
            </motion.span>
            <span className="text-[15px] font-semibold tabular-nums text-white [text-shadow:0_1px_6px_rgba(0,0,0,0.6)]">
              {seekFlash.direction === "back" ? "−" : "+"}{seekFlash.totalSeconds}
            </span>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {!isMobile && seekFlash && (
          <motion.div
            key={seekFlash.streakId}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className={cn(
              "pointer-events-none absolute top-1/2 z-10 flex -translate-y-1/2 items-center gap-1",
              seekFlash.direction === "back" ? "left-14" : "right-14",
            )}
          >
            {seekFlash.direction === "back" && (
              <span className="relative h-7 w-7">
                <AnimatePresence initial={false}>
                  <motion.span
                    key={seekFlash.pulse}
                    className="absolute inset-0"
                    initial={{ opacity: 0, x: 16 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -16 }}
                    transition={{ duration: 0.18, ease: "easeOut" }}
                  >
                    <SeekArrowIcon direction="back" />
                  </motion.span>
                </AnimatePresence>
              </span>
            )}
            {/* Measured directly against the real rendered lucide icon (React DevTools-mounted in
                a live copy of this page, not a hand-copied SVG approximation): the digit's ink
                sits lower than the arrow's, box-centering notwithstanding - font metrics, not
                something line-height/leading fixes. The exact offset shifts slightly with the
                window's device pixel ratio (font hinting snaps to the device grid, SVG doesn't),
                so this errs a little past the 1x measurement rather than under-correcting again. */}
            <span className="[transform:translateY(-4px)] text-2xl leading-none font-bold tabular-nums text-white [text-shadow:2px_2px_10px_rgba(0,0,0,0.7)]">
              {seekFlash.direction === "back" ? "−" : "+"}
              {seekFlash.totalSeconds}
            </span>
            {seekFlash.direction === "forward" && (
              <span className="relative h-7 w-7">
                <AnimatePresence initial={false}>
                  <motion.span
                    key={seekFlash.pulse}
                    className="absolute inset-0"
                    initial={{ opacity: 0, x: -16 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 16 }}
                    transition={{ duration: 0.18, ease: "easeOut" }}
                  >
                    <SeekArrowIcon direction="forward" />
                  </motion.span>
                </AnimatePresence>
              </span>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {playbackError ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/60 px-8 text-center">
          <TriangleAlert className="h-10 w-10 text-rose-400" strokeWidth={1.75} />
          <p className="select-text text-sm text-zinc-300">{t("common.loadFailed", { message: playbackError })}</p>
          {link && (
            <button
              type="button"
              onClick={() => {
                const video = videoRef.current;
                retryPositionMsRef.current = video && Number.isFinite(video.currentTime) ? video.currentTime * 1000 : null;
                setPlaybackError(null);
                setBuffering(true);
                setPlaybackRetryKey((attempt) => attempt + 1);
              }}
              className="rounded-full bg-rose-500 px-5 py-2 text-sm font-semibold text-white transition-colors hover:bg-rose-400"
            >
              {t("common.retry")}
            </button>
          )}
        </div>
      ) : unplayable ? (
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/60 px-8 text-center">
          <TriangleAlert className="h-10 w-10 text-rose-400" strokeWidth={1.75} />
          <p className="text-sm text-zinc-300">{t("watch.unsupportedPlayer")}</p>
        </div>
      ) : (sourceSwitching || switchingSource || buffering || !link) && (
        // `!link` is the "still deciding what to play" case - the spinner sits over the chrome
        // rather than replacing it, so the settings menu stays reachable throughout.
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/55 backdrop-blur-[1px]">
          <Loader2 className="h-12 w-12 animate-spin text-white/80" strokeWidth={2} />
        </div>
      )}

      {link && !sourceSwitching && !switchingSource && !buffering && activeSegment && dismissedSegmentKey !== segmentKey(activeSegment) && (
        <div
          onClick={stop}
          className={cn(
            "absolute right-6 z-10 flex items-center gap-2 transition-[bottom] duration-300",
            controlsVisible ? "bottom-[136px]" : "bottom-8",
            // Phone: just above the seek bar while the controls are up, low on the screen otherwise.
            isMobile && (controlsVisible ? "mobile:bottom-16" : "mobile:bottom-6"),
          )}
          style={isMobile ? { right: "max(1.25rem, var(--safe-right))" } : undefined}
        >
          {autoSkipSegments && (
            <button onClick={dismissSegmentPrompt} className="rounded-full bg-black/60 px-4 py-2.5 text-sm font-semibold text-white shadow-lg transition-colors hover:bg-black/75">
              {t("watch.player.watch")}
            </button>
          )}
          <button onClick={skipSegmentNow} className="rounded-full bg-white/[.92] px-4 py-2.5 text-sm font-semibold text-zinc-900 shadow-lg transition-colors hover:bg-white">
            {t("watch.player.skip")} ({skipCountdown})
          </button>
        </div>
      )}

      {/* Top bar: back + title */}
      <div
        className={cn("absolute inset-x-0 top-0 z-[6] flex items-center gap-4 bg-gradient-to-b from-black/80 to-transparent px-6 pb-10 pt-5 transition-opacity duration-300 mobile:pt-3", controlsVisible ? "opacity-100" : "pointer-events-none opacity-0")}
        style={isMobile ? { paddingLeft: "max(1.25rem, var(--safe-left))", paddingRight: "max(1.25rem, var(--safe-right))" } : undefined}
        onClick={isMobile ? stopOnControl : stop}
      >
        <button onClick={(e) => { stop(e); onBack(); }} className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20">
          <ArrowLeft className="h-[18px] w-[18px]" strokeWidth={2} />
        </button>
        <div className="min-w-0">
          <p className="select-text truncate text-base font-bold text-white">{title}</p>
          <p className="select-text truncate text-xs text-zinc-300">{episodeLabel}</p>
        </div>
        {isMobile && mobileTopActions}
      </div>

      {/* Bottom control cluster */}
      {isMobile ? mobileControls : <div ref={bottomBarRef} className={cn("absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent px-6 pb-4 pt-10 transition-opacity duration-300", controlsVisible ? "opacity-100" : "pointer-events-none opacity-0")} onClick={stop}>
        {/* Seek bar: transparent track, white = buffered, red = played */}
        <div className="relative" onMouseMove={onSeekAreaMouseMove} onMouseLeave={onSeekAreaMouseLeave}>
          {hoverRatio !== null && duration > 0 && (
            <div
              className="absolute bottom-full mb-2.5 -translate-x-1/2 select-text rounded-md bg-red-600 px-2 py-1 text-xs font-bold tabular-nums text-white shadow-lg"
              style={{ left: `${hoverRatio * 100}%` }}
              onClick={stop}
            >
              {formatTime(hoverRatio * duration)}
            </div>
          )}
          <div
            ref={seekBarRef}
            onPointerDown={onSeekPointerDown}
            className="group/seek relative flex h-4 cursor-pointer items-center"
          >
            {/* The bar sits on top of the video itself, so its own contrast cannot be assumed:
                over a bright scene a thin red line on a translucent track disappears. The track
                gets a dark edge beneath it and the played part a faint glow of its own colour,
                which reads on light and dark frames alike without making the bar heavier. */}
            <div className="relative h-[3px] w-full overflow-hidden rounded-full bg-white/20 shadow-[0_1px_3px_rgba(0,0,0,0.7)] transition-[height] group-hover/seek:h-[5px]">
              <div className="absolute inset-y-0 left-0 bg-white/45" style={{ width: `${bufferedPercent}%` }} />
              <div
                className="absolute inset-y-0 left-0 bg-red-600 shadow-[0_0_8px_rgba(239,68,68,0.75)]"
                style={{ width: `${playedPercent}%` }}
              />
            </div>
            <div
              className="absolute h-3 w-3 -translate-x-1/2 rounded-full bg-red-600 opacity-0 shadow-[0_1px_4px_rgba(0,0,0,0.8)] ring-2 ring-black/25 transition-opacity group-hover/seek:opacity-100"
              style={{ left: `${playedPercent}%` }}
            />
          </div>
        </div>

        <div className="mt-1 flex items-center">
          <div className="flex flex-1 items-center">
            {/* Click to switch between elapsed and what is left. Both readings answer different
                questions - how far in am I, and can I finish this before I have to go - and which
                one someone wants is a habit, so the choice is remembered. */}
            <button
              onClick={(e) => { stop(e); toggleRemainingTime(); }}
              title={t("watch.toggleTimeDisplay")}
              className="shrink-0 rounded px-1 py-0.5 text-xs font-medium tabular-nums text-zinc-300 transition-colors hover:bg-white/10 hover:text-white"
            >
              {showRemainingTime
                ? `-${formatTime(Math.max(0, displayDuration - displayCurrentTime))}`
                : `${formatTime(displayCurrentTime)} / ${formatTime(displayDuration)}`}
            </button>
          </div>

          <div className="flex items-center justify-center gap-3">
            <button
              onClick={(e) => { stop(e); onPrevEpisode?.(); }}
              disabled={!onPrevEpisode}
              className={cn("flex h-10 w-10 items-center justify-center rounded-full transition-colors", onPrevEpisode ? "text-white/80 hover:bg-white/10 hover:text-white" : "cursor-default text-white/20")}
            >
              <SkipBack className="h-5 w-5" strokeWidth={2} />
            </button>
            <button onClick={(e) => { stop(e); togglePlay(); }} className="flex h-12 w-12 items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20">
              {playing ? <Pause className="h-6 w-6 fill-current" strokeWidth={0} /> : <Play className="ml-0.5 h-6 w-6 fill-current" strokeWidth={0} />}
            </button>
            <button
              onClick={(e) => { stop(e); onNextEpisode?.(); }}
              disabled={!onNextEpisode}
              className={cn("flex h-10 w-10 items-center justify-center rounded-full transition-colors", onNextEpisode ? "text-white/80 hover:bg-white/10 hover:text-white" : "cursor-default text-white/20")}
            >
              <SkipForward className="h-5 w-5" strokeWidth={2} />
            </button>
          </div>

          <div className="flex flex-1 items-center justify-end gap-1">
            {/* Fixed-size box so the slider expands via absolute positioning — hovering it must
                never reflow the mute button or the settings/fullscreen buttons next to it. */}
            {/* 48px of hover zone around a 32px button, the negative margin giving the extra
                back to the layout - crossing the gap between the button and the slider must not
                count as leaving, or the slider collapses on the way to it. */}
            <div
              className="relative -m-2 flex h-12 w-12 shrink-0 items-center justify-center"
              onMouseEnter={() => setVolumeHover(true)}
              onMouseLeave={() => setVolumeHover(false)}
              onWheel={(event) => {
                event.preventDefault();
                event.stopPropagation();
                const video = videoRef.current;
                if (!video || event.deltaY === 0) return;
                video.volume = Math.min(1, Math.max(0, video.volume + (event.deltaY < 0 ? VOLUME_STEP : -VOLUME_STEP)));
                video.muted = video.volume === 0;
              }}
            >
              {/* h-8, not the input's own height: a range input is a ~16px band, and aiming at
                  16px of a track that only appears on hover means the slightest vertical drift
                  collapses it mid-drag. The taller box is transparent, so nothing looks different
                  - there is just somewhere to be. */}
              <div className={cn("absolute right-full top-1/2 -mr-2 flex h-8 -translate-y-1/2 items-center overflow-hidden transition-[width] duration-200 ease-out", volumeHover ? "w-20" : "w-0")}>
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.01}
                  value={muted ? 0 : volume}
                  onChange={onVolumeInput}
                  onClick={stop}
                  className="h-6 w-20 shrink-0 cursor-pointer accent-red-600"
                />
              </div>
              <button onClick={onMuteButtonClick} className="relative z-10 flex h-8 w-8 items-center justify-center text-white/80 transition-colors hover:text-white">
                <VolumeIcon className="h-[18px] w-[18px]" strokeWidth={2} />
              </button>
            </div>
            {/* YouTube's CC button: one click on/off, and "on" is simply the first track in the list -
                picking a specific one (or adding a file) lives in the settings menu. Always there,
                greyed out and inert while there is nothing to turn on. */}
            <button
              onClick={(e) => { stop(e); setSelectedSubtitleId(selectedSubtitleId ? null : subtitleOptions[0].id); }}
              disabled={subtitleOptions.length === 0}
              title={t("watch.settings.subtitles")}
              aria-pressed={!!selectedSubtitleId}
              className={cn(
                "relative flex h-8 w-8 shrink-0 items-center justify-center transition-colors",
                subtitleOptions.length === 0 ? "cursor-default text-white/25" : selectedSubtitleId ? "text-white" : "text-white/80 hover:text-white",
              )}
            >
              <Captions className="h-[18px] w-[18px]" strokeWidth={2} />
              <span className={cn("absolute bottom-0.5 h-[2px] w-4 rounded-full bg-accent transition-opacity", selectedSubtitleId ? "opacity-100" : "opacity-0")} />
            </button>
            {(episodesLoading || !episodes || episodes.length > 1) && (
              <div ref={episodeListRef} className="relative">
                <button
                  onClick={(e) => { stop(e); onOpenEpisodes?.(); setEpisodeListOpen((v) => !v); }}
                  className={cn("flex h-8 w-8 shrink-0 items-center justify-center transition-colors", episodeListOpen ? "text-white" : "text-white/80 hover:text-white")}
                >
                  <ListVideo className="h-[18px] w-[18px]" strokeWidth={2} />
                </button>
                {episodeListOpen && (
                  <div onClick={stop} className="absolute bottom-full right-0 z-20 mb-3 overflow-hidden rounded-xl border border-white/10 bg-[#1d1c22] shadow-2xl">
                    {episodeListPanel}
                  </div>
                )}
              </div>
            )}
            <button
              onClick={togglePip}
              className={cn("flex h-8 w-8 shrink-0 items-center justify-center transition-colors", isPip ? "text-white" : "text-white/80 hover:text-white")}
            >
              <PictureInPicture2 className="h-[18px] w-[18px]" strokeWidth={2} />
            </button>
            <div ref={settingsRef} className="relative">
              <button
                onClick={(e) => { stop(e); setSettingsOpen((v) => !v); }}
                className={cn("flex h-8 w-8 shrink-0 items-center justify-center transition-colors", settingsOpen ? "text-white" : "text-white/80 hover:text-white")}
              >
                <Settings className="h-[18px] w-[18px]" strokeWidth={2} />
              </button>
              {settingsOpen && (
                <div onClick={stop} className="absolute bottom-full right-0 z-20 mb-3 overflow-hidden rounded-xl border border-white/10 bg-[#1d1c22] shadow-2xl">
                  {settingsMenu}
                </div>
              )}
            </div>
            <button onClick={toggleFullscreen} className="flex h-8 w-8 shrink-0 items-center justify-center text-white/80 transition-colors hover:text-white">
              {isFullscreen ? <Minimize className="h-[18px] w-[18px]" strokeWidth={2} /> : <Maximize className="h-[18px] w-[18px]" strokeWidth={2} />}
            </button>
          </div>
        </div>
      </div>}
    </div>
  );
}
