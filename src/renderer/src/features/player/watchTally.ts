// Progress ticks arrive several times a second while playing, so anything past a couple of seconds
// is a jump rather than elapsed playback.
export const PLAYED_TICK_MAX_MS = 2500;

/**
 * What one visit to an episode actually watched, from the player's progress ticks: time that played
 * (a seek is not watching - skipping to the end of a film is not having seen it), the distinct seconds
 * that played (a source's account counts seconds seen, so rewatching the same minute is one minute
 * there), and whether the episode counts as watched (sticky: reaching the end and seeking back is
 * still having watched it). Written out once, when the episode is left (the watch page's saveProgress).
 */
export class WatchTally {
  private lastPositionMs: number | null = null;
  private playedMs = 0;
  private seconds = new Set<number>();
  private watched = false;

  /** One progress tick. `threshold` is the share of the episode (0..1) that counts as watched. */
  tick(positionMs: number, durationMs: number, threshold: number): void {
    const previous = this.lastPositionMs;
    this.lastPositionMs = positionMs;
    if (previous !== null) {
      const tickMs = positionMs - previous;
      if (tickMs > 0 && tickMs <= PLAYED_TICK_MAX_MS) {
        this.playedMs += tickMs;
        for (let second = Math.floor(previous / 1000); second <= Math.floor(positionMs / 1000); second++) this.seconds.add(second);
      }
    }
    if (durationMs > 0 && positionMs / durationMs >= threshold) this.watched = true;
  }

  get isWatched(): boolean {
    return this.watched;
  }

  /** The time played since the last take, which starts over. */
  takePlayedMs(): number {
    const played = this.playedMs;
    this.playedMs = 0;
    return played;
  }

  /** The seconds played and not yet reported; they are gone until given back. */
  takeSeconds(): number[] {
    const seconds = [...this.seconds].sort((a, b) => a - b);
    this.seconds = new Set();
    return seconds;
  }

  /** Seconds a report did not get through with: sent again with the next one. */
  giveBack(seconds: number[]): void {
    for (const second of seconds) this.seconds.add(second);
  }
}
