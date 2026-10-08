import { create } from "zustand";
import { persist } from "zustand/middleware";

// Mirrors the player-related fields of Android's AppPreferencesState. Lives in its own store
// (rather than uiStore) since most of these are set from the in-player quick settings menu, not
// the app's Settings page - the two skip-timer values below are the exception (Settings page).
export const PLAYBACK_SPEEDS = [0.75, 1, 1.25, 1.5, 2] as const;
export type PlaybackSpeed = (typeof PLAYBACK_SPEEDS)[number];

// Bounds for the two skip-timer settings below - a slider needs a fixed range, and letting either
// go to 0 or absurdly high defeats the point (an instant, unannounced auto-skip; or a button that
// outlives the segment it's for).
export const SKIP_TIMER_MIN_SECONDS = 1;
export const SKIP_TIMER_MAX_SECONDS = 30;

function clampSkipTimer(seconds: number): number {
  if (!Number.isFinite(seconds)) return SKIP_TIMER_MIN_SECONDS;
  return Math.round(Math.min(SKIP_TIMER_MAX_SECONDS, Math.max(SKIP_TIMER_MIN_SECONDS, seconds)));
}

// Bounds for the auto-watched threshold slider - below 50% would mark something "watched" after
// barely starting it, and above 95% leaves almost no room for stopping a few seconds before the
// credits without it staying stuck as unwatched forever.
export const WATCHED_THRESHOLD_MIN_PERCENT = 50;
export const WATCHED_THRESHOLD_MAX_PERCENT = 95;

function clampWatchedThreshold(percent: number): number {
  if (!Number.isFinite(percent)) return WATCHED_THRESHOLD_MIN_PERCENT;
  return Math.round(Math.min(WATCHED_THRESHOLD_MAX_PERCENT, Math.max(WATCHED_THRESHOLD_MIN_PERCENT, percent)));
}

/** The phone's screen while watching: always landscape, or any way up (rotating with the phone). */
export type PlayerOrientation = "landscape" | "any";

interface PlayerPrefsState {
  playerOrientation: PlayerOrientation;
  setPlayerOrientation: (orientation: PlayerOrientation) => void;
  autoSkipSegments: boolean;
  autoPlayNextEpisode: boolean;
  playbackSpeed: PlaybackSpeed;
  // How long the skip-segment button (VideoPlayer's PlayerSkipSegmentOverlay-style UI) waits
  // before acting - two independent values, not one shared countdown, since "how long before it
  // skips for you" and "how long a button stays clickable before quietly going away" are different
  // trade-offs (the first is about not skipping content you wanted to see; the second is about not
  // leaving stale UI on screen) that don't need to move together. Only one is ever actually in play
  // at a time, depending on autoSkipSegments.
  autoSkipDelaySeconds: number;
  skipButtonTimeoutSeconds: number;
  // How far into an episode (as a percent of its duration) counts as "finished" for the sticky
  // `watched` flag (see the watch route's own onProgress) - was a hardcoded 90% before this
  // existed; now a Settings slider.
  watchedThresholdPercent: number;
  // Playback volume, kept across episodes and app restarts - a <video> element always starts at
  // 1.0, so without persisting it every episode reset whatever the user had set.
  volume: number;
  muted: boolean;
  // Whether the time label counts down to the end instead of up from the start. A preference about
  // how someone reads a player, not about one episode, so it outlives both.
  showRemainingTime: boolean;
  // How far up from the video's bottom edge (in pixels) the subtitle overlay sits - see
  // VideoPlayer's own draggable subtitle box. Persisted like every other player preference here:
  // someone who drags it once to clear their window chrome shouldn't have to redo that every
  // episode. The default clears this app's own controls bar (see the bottom gradient bar in
  // VideoPlayer.tsx) with a little room to spare.
  subtitleOffset: number;
  // Horizontal shift from centre, in pixels (negative = left) - dragged the same way.
  subtitleOffsetX: number;
  setAutoSkipSegments: (enabled: boolean) => void;
  setAutoPlayNextEpisode: (enabled: boolean) => void;
  setPlaybackSpeed: (speed: PlaybackSpeed) => void;
  setAutoSkipDelaySeconds: (seconds: number) => void;
  setSkipButtonTimeoutSeconds: (seconds: number) => void;
  setWatchedThresholdPercent: (percent: number) => void;
  setVolume: (volume: number, muted: boolean) => void;
  toggleRemainingTime: () => void;
  setSubtitlePosition: (offset: number, offsetX: number) => void;
}

export const usePlayerPrefsStore = create<PlayerPrefsState>()(
  persist(
    (set) => ({
      playerOrientation: "landscape",
      setPlayerOrientation: (playerOrientation) => set({ playerOrientation }),
      autoSkipSegments: false,
      autoPlayNextEpisode: true,
      playbackSpeed: 1,
      autoSkipDelaySeconds: 5,
      skipButtonTimeoutSeconds: 5,
      watchedThresholdPercent: 85,
      volume: 1,
      muted: false,
      showRemainingTime: false,
      subtitleOffset: 72,
      subtitleOffsetX: 0,
      setAutoSkipSegments: (autoSkipSegments) => set({ autoSkipSegments }),
      setAutoPlayNextEpisode: (autoPlayNextEpisode) => set({ autoPlayNextEpisode }),
      setPlaybackSpeed: (playbackSpeed) => set({ playbackSpeed }),
      setAutoSkipDelaySeconds: (seconds) => set({ autoSkipDelaySeconds: clampSkipTimer(seconds) }),
      setSkipButtonTimeoutSeconds: (seconds) => set({ skipButtonTimeoutSeconds: clampSkipTimer(seconds) }),
      setWatchedThresholdPercent: (percent) => set({ watchedThresholdPercent: clampWatchedThreshold(percent) }),
      setVolume: (volume, muted) => set({ volume: Number.isFinite(volume) ? Math.min(1, Math.max(0, volume)) : 1, muted }),
      toggleRemainingTime: () => set((state) => ({ showRemainingTime: !state.showRemainingTime })),
      setSubtitlePosition: (offset, offsetX) =>
        set({ subtitleOffset: Number.isFinite(offset) ? Math.max(0, offset) : 72, subtitleOffsetX: Number.isFinite(offsetX) ? offsetX : 0 }),
    }),
    { name: "hibiki-player-prefs" },
  ),
);
