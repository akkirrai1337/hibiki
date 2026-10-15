// Stops hls.js downloading where nothing will be watched, and starts it again the moment that
// changes. Two reasons share one switch, so lifting one never restarts what the other still holds:
//  - "skip": auto-skip is on and the buffer already reaches a few seconds into an opening/ending.
//    The rest of it would be downloaded only to be jumped over.
//  - "pause": the episode has been paused for a while. The buffer is full anyway, and a player left
//    paused overnight shouldn't keep a connection busy.
import type { VideoSegment } from "@shared/types";

export type LoadHoldReason = "skip" | "pause";

interface Loader {
  stopLoad(): void;
  startLoad(startPosition?: number): void;
}

const holds = new WeakMap<Loader, Set<LoadHoldReason>>();

/** Sets whether `reason` holds loading; starts or stops the loader only when the overall state flips. */
export function setLoadHold(loader: Loader, reason: LoadHoldReason, hold: boolean, position: number, log: (message: string) => void): void {
  let reasons = holds.get(loader);
  if (!reasons) holds.set(loader, (reasons = new Set()));
  const wasHeld = reasons.size > 0;
  if (hold) reasons.add(reason);
  else reasons.delete(reason);
  const isHeld = reasons.size > 0;
  if (isHeld === wasHeld) return;
  if (isHeld) {
    loader.stopLoad();
    log(`loading held (${[...reasons].join(", ")}) at ${position.toFixed(1)}s`);
  } else {
    loader.startLoad(position);
    log(`loading resumed at ${position.toFixed(1)}s`);
  }
}

/** Seconds past an opening/ending's start the buffer still goes: segment times are often a little
 * early, and the countdown runs this long before it jumps. Between 5 and 10 seconds. */
export function skipMarginSeconds(countdownSeconds: number): number {
  return Math.min(10, Math.max(5, countdownSeconds + 3));
}

/**
 * Where loading should stop for the next opening/ending ahead of `now`, or null when it shouldn't:
 * a segment already dismissed ("Watch" pressed, or skipped) is watched in full, and a segment too
 * short to save anything beyond the margin isn't worth a stop.
 */
export function skipHoldPoint(
  segments: VideoSegment[] | undefined,
  now: number,
  margin: number,
  isDismissed: (segment: VideoSegment) => boolean,
): number | null {
  const ahead = (segments ?? [])
    .filter((s) => (s.type === "OPENING" || s.type === "ENDING") && s.endMs / 1000 > now)
    .sort((a, b) => a.startMs - b.startMs);
  const next = ahead[0];
  if (!next || isDismissed(next)) return null;
  const holdAt = next.startMs / 1000 + margin;
  return holdAt < next.endMs / 1000 - 2 ? holdAt : null;
}

/** The end of the buffered range the playhead is in (or the playhead, outside any). */
export function bufferedEnd(video: HTMLVideoElement): number {
  const now = video.currentTime;
  for (let i = 0; i < video.buffered.length; i++) {
    if (video.buffered.start(i) <= now + 0.5 && video.buffered.end(i) >= now) return video.buffered.end(i);
  }
  return now;
}
