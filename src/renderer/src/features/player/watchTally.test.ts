import { describe, expect, it } from "vitest";
import { WatchTally } from "./watchTally";

function play(tally: WatchTally, fromMs: number, toMs: number, durationMs = 1_440_000, threshold = 0.85) {
  for (let position = fromMs; position <= toMs; position += 250) tally.tick(position, durationMs, threshold);
}

describe("watch tally", () => {
  it("counts the time that played", () => {
    const tally = new WatchTally();
    play(tally, 0, 10_000);
    expect(tally.takePlayedMs()).toBe(10_000);
    expect(tally.takePlayedMs()).toBe(0);
  });

  it("a seek is not watching", () => {
    const tally = new WatchTally();
    play(tally, 0, 5_000);
    tally.tick(1_300_000, 1_440_000, 0.85);
    expect(tally.takePlayedMs()).toBe(5_000);
  });

  it("does not count time when the position goes back", () => {
    const tally = new WatchTally();
    play(tally, 10_000, 12_000);
    tally.tick(11_000, 1_440_000, 0.85);
    expect(tally.takePlayedMs()).toBe(2_000);
  });

  it("rewatching the same seconds reports them once", () => {
    const tally = new WatchTally();
    play(tally, 0, 3_000);
    tally.tick(0, 1_440_000, 0.85);
    play(tally, 0, 3_000);
    expect(tally.takeSeconds()).toEqual([0, 1, 2, 3]);
    expect(tally.takePlayedMs()).toBe(6_000);
  });

  it("seconds a report lost go out with the next one", () => {
    const tally = new WatchTally();
    play(tally, 0, 2_000);
    const lost = tally.takeSeconds();
    play(tally, 2_000, 3_000);
    tally.giveBack(lost);
    expect(tally.takeSeconds()).toEqual([0, 1, 2, 3]);
  });

  it("watched stays watched after seeking back", () => {
    const tally = new WatchTally();
    tally.tick(1_300_000, 1_440_000, 0.85);
    expect(tally.isWatched).toBe(true);
    tally.tick(10_000, 1_440_000, 0.85);
    expect(tally.isWatched).toBe(true);
  });

  it("an unknown duration never counts as watched", () => {
    const tally = new WatchTally();
    tally.tick(10_000, 0, 0.85);
    expect(tally.isWatched).toBe(false);
  });
});
