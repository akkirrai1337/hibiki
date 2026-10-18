import { describe, expect, it } from "vitest";
import { findNew, isFinished, nextCheckAt, nothingNewByDetails, usualInterval, withArrival, type GroupLike, type WatchState } from "./logic";

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const NOW = 1_800_000_000_000;

const group = (id: string, count: number): GroupLike => ({ id, title: id, episodes: Array.from({ length: count }, (_, i) => ({ id: `${id}-${i + 1}`, number: i + 1 })) });
const state = (partial: Partial<WatchState> = {}): WatchState => ({ groupId: null, knownCount: null, knownMax: null, arrivals: [], waitingSince: null, nextEpisodeAt: null, failures: 0, ...partial });

describe("findNew", () => {
  it("only learns where things stand on the first look", () => {
    expect(findNew([group("AniLibria", 5)], "AniLibria", null, new Set())).toEqual({ max: 5, found: null });
  });

  it("finds what came out in the followed dub since last time", () => {
    const { max, found } = findNew([group("AniLibria", 7), group("Other", 9)], "AniLibria", 5, new Set());
    expect(max).toBe(7);
    expect(found?.numbers).toEqual([6, 7]);
    expect(found?.firstEpisodeId).toBe("AniLibria-6");
  });

  it("is not fooled by another dub being ahead", () => {
    expect(findNew([group("AniLibria", 5), group("Other", 6)], "AniLibria", 5, new Set()).found).toBeNull();
  });

  it("takes whichever dub is furthest when none is followed, or the followed one is gone", () => {
    expect(findNew([group("A", 5), group("B", 6)], null, 5, new Set()).found?.group.id).toBe("B");
    expect(findNew([group("B", 6)], "Gone", 5, new Set()).found?.numbers).toEqual([6]);
  });

  it("leaves out episodes already watched", () => {
    expect(findNew([group("A", 6)], "A", 5, new Set([6])).found).toBeNull();
  });
});

describe("nothingNewByDetails", () => {
  it("skips the episode list when the released count has not moved", () => {
    expect(nothingNewByDetails(state({ knownCount: 5 }), 5)).toBe(true);
    expect(nothingNewByDetails(state({ knownCount: 5 }), 6)).toBe(false);
  });

  it("asks anyway when the details give no count, or a dub is being waited for", () => {
    expect(nothingNewByDetails(state({ knownCount: 5 }), null)).toBe(false);
    expect(nothingNewByDetails(state({ knownCount: 6, waitingSince: NOW }), 6)).toBe(false);
  });
});

describe("nextCheckAt", () => {
  it("looks half an hour after the announced airing, then backs off after it", () => {
    expect(nextCheckAt(state({ nextEpisodeAt: NOW + 2 * HOUR }), NOW, "nothing")).toBe(NOW + 2.5 * HOUR);
    expect(nextCheckAt(state({ nextEpisodeAt: NOW - HOUR }), NOW, "nothing")).toBe(NOW + HOUR);
    expect(nextCheckAt(state({ nextEpisodeAt: NOW - 3 * HOUR }), NOW, "nothing")).toBe(NOW + 3 * HOUR);
  });

  it("does not wait weeks for a far announced date", () => {
    expect(nextCheckAt(state({ nextEpisodeAt: NOW + 30 * DAY }), NOW, "nothing")).toBe(NOW + 3 * DAY);
  });

  it("with no date, goes by the usual interval: rarely before it is due, often around it", () => {
    const weekly = [NOW - 21 * DAY, NOW - 14 * DAY, NOW - 7 * DAY + 3 * HOUR];
    expect(usualInterval(weekly)).toBe(7 * DAY + 3 * HOUR);
    expect(nextCheckAt(state({ arrivals: [NOW - 14 * DAY, NOW - 7 * DAY, NOW - 2 * DAY] }), NOW, "nothing")).toBe(NOW + DAY);
    expect(nextCheckAt(state({ arrivals: weekly }), NOW, "nothing")).toBe(NOW + 4 * HOUR);
  });

  it("with nothing to go by, every 12 hours", () => {
    expect(nextCheckAt(state(), NOW, "nothing")).toBe(NOW + 12 * HOUR);
  });

  it("slows down in a long silence", () => {
    expect(nextCheckAt(state({ arrivals: [NOW - 40 * DAY, NOW - 33 * DAY] }), NOW, "nothing")).toBe(NOW + DAY);
    expect(nextCheckAt(state({ arrivals: [NOW - 90 * DAY, NOW - 83 * DAY] }), NOW, "nothing")).toBe(NOW + 3 * DAY);
  });

  it("waits for the dub on its own ladder", () => {
    expect(nextCheckAt(state({ waitingSince: NOW - HOUR }), NOW, "waiting")).toBe(NOW + HOUR);
    expect(nextCheckAt(state({ waitingSince: NOW - 30 * HOUR }), NOW, "waiting")).toBe(NOW + DAY);
  });

  it("retries a failure later each time, up to a day", () => {
    expect(nextCheckAt(state({ failures: 0 }), NOW, "failed")).toBe(NOW + HOUR);
    expect(nextCheckAt(state({ failures: 3 }), NOW, "failed")).toBe(NOW + 8 * HOUR);
    expect(nextCheckAt(state({ failures: 9 }), NOW, "failed")).toBe(NOW + DAY);
  });
});

describe("helpers", () => {
  it("keeps the last eight arrivals", () => {
    let arrivals: number[] = [];
    for (let i = 0; i < 10; i++) arrivals = withArrival(arrivals, i);
    expect(arrivals).toEqual([2, 3, 4, 5, 6, 7, 8, 9]);
  });

  it("knows a finished title", () => {
    expect(isFinished("released", 12, 12)).toBe(true);
    expect(isFinished("ongoing", 12, 12)).toBe(true);
    expect(isFinished("ongoing", 11, 12)).toBe(false);
    expect(isFinished("ongoing", 11, null)).toBe(false);
  });
});
