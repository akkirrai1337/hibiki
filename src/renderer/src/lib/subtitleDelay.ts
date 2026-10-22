// Moving a subtitle track in time, for one that runs ahead of or behind the speech - a file made
// for another release (with or without an intro, a cut recap) is off by the same amount throughout.
// A positive delay shows the lines later, a negative one earlier. Kept per track for the player's
// lifetime rather than saved: the next episode's track may well be in sync.

/** What the -/+ keys (Z and X, as in mpv) move it by. */
export const SUBTITLE_DELAY_STEP_MS = 100;
export const SUBTITLE_DELAY_STEPS_MS = [-500, -100, 100, 500] as const;
const MAX_DELAY_MS = 10 * 60_000;

export function clampSubtitleDelay(ms: number): number {
  if (!Number.isFinite(ms)) return 0;
  // Whole steps only: adding 0.1 s ten times must come back to exactly 1 s, not 0.9999999.
  return Math.max(-MAX_DELAY_MS, Math.min(MAX_DELAY_MS, Math.round(ms / SUBTITLE_DELAY_STEP_MS) * SUBTITLE_DELAY_STEP_MS));
}

/** "+1,2 s" / "−0,5 s" / "0 s", in the locale's own decimal mark. */
export function formatSubtitleDelay(ms: number, locale: string, secondsUnit: string): string {
  if (ms === 0) return `0 ${secondsUnit}`;
  const value = (Math.abs(ms) / 1000).toLocaleString(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `${ms > 0 ? "+" : "−"}${value} ${secondsUnit}`;
}

/** Moves every cue of a track by `deltaMs`. Cues are shifted where they are, so a later change
 * only has to move them by the difference. */
export function shiftCues(cues: ArrayLike<{ startTime: number; endTime: number }>, deltaMs: number): void {
  if (deltaMs === 0) return;
  const delta = deltaMs / 1000;
  for (let i = 0; i < cues.length; i++) {
    const cue = cues[i];
    // End before start: a cue shifted the other way must not briefly end before it begins.
    if (delta > 0) {
      cue.endTime += delta;
      cue.startTime += delta;
    } else {
      cue.startTime += delta;
      cue.endTime += delta;
    }
  }
}
