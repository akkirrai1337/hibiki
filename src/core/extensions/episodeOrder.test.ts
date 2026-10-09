import { describe, expect, it } from "vitest";
import type { PlaybackGroup } from "@shared/types";
import { chronologicalGroups } from "./episodeOrder";

const group = (numbers: number[]): PlaybackGroup => ({ id: "g", title: "Episodes", episodes: numbers.map((number) => ({ id: `e${number}`, number })) }) as PlaybackGroup;
const numbers = (groups: PlaybackGroup[]) => groups[0].episodes.map((episode) => episode.number);

describe("chronologicalGroups", () => {
  it("turns a newest-first list around", () => {
    expect(numbers(chronologicalGroups([group([3, 2, 1])]))).toEqual([1, 2, 3]);
  });

  it("keeps a list that already runs first to last, specials included", () => {
    expect(numbers(chronologicalGroups([group([1, 2, 2.5, 3])]))).toEqual([1, 2, 2.5, 3]);
    expect(numbers(chronologicalGroups([group([1])]))).toEqual([1]);
  });

  it("returns an untouched group as the same object", () => {
    const ordered = group([1, 2]);
    expect(chronologicalGroups([ordered])[0]).toBe(ordered);
  });
});
