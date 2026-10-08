import { describe, expect, it } from "vitest";
import type { AnimeTitle } from "@shared/types";
import { findContinuations, genreProfile, rankCandidates, type Candidate, type Seed } from "./scoring";

function title(id: string, extra: Partial<AnimeTitle> = {}): AnimeTitle {
  return { id, sourceId: "src", russianName: `Title ${id}`, ...extra };
}
const seed = (id: string, extra: Partial<AnimeTitle> = {}): Seed => ({ anime: title(id, extra), watchedEpisodes: 5 });
const candidate = (id: string, similarTo: string[] = [], extra: Partial<AnimeTitle> = {}): Candidate => ({ anime: title(id, extra), similarTo: new Set(similarTo) });

const SEEDS = [
  seed("a", { genres: ["Fantasy", "Adventure"], type: "tv" }),
  seed("b", { genres: ["Fantasy", "Drama"], type: "tv" }),
  seed("c", { genres: ["fantasy", "Comedy"], type: "tv" }),
];

describe("genreProfile", () => {
  it("weighs a genre by the share of seeds that have it, whatever its case", () => {
    const profile = genreProfile(SEEDS);
    expect(profile.get("fantasy")?.weight).toBe(1);
    expect(profile.get("drama")?.weight).toBeCloseTo(1 / 3);
  });
});

describe("rankCandidates", () => {
  it("puts a title two seeds agree on above one a single seed lists, and above genre-only finds", () => {
    const ranked = rankCandidates(SEEDS, [
      candidate("genre-only", [], { genres: ["Fantasy", "Adventure"], type: "tv" }),
      candidate("one", ["a"]),
      candidate("two", ["a", "b"]),
    ], new Set(), 10);
    expect(ranked.map((pick) => pick.anime.id)).toEqual(["two", "one", "genre-only"]);
  });

  it("among equally recommended titles, prefers the one closer to the seeds' genres", () => {
    const ranked = rankCandidates(SEEDS, [
      candidate("off", ["a"], { genres: ["Sports"] }),
      candidate("on", ["a"], { genres: ["Fantasy"] }),
    ], new Set(), 10);
    expect(ranked[0].anime.id).toBe("on");
  });

  it("leaves out what is excluded and says why the rest are there", () => {
    const ranked = rankCandidates(SEEDS, [
      candidate("seen", ["a"]),
      candidate("similar", ["b", "a"]),
      candidate("genres", [], { genres: ["Comedy", "Fantasy", "Sports"] }),
    ], new Set(["seen"]), 10);
    expect(ranked.map((pick) => pick.anime.id)).toEqual(["similar", "genres"]);
    // Seeds in the order given - most recently watched first.
    expect(ranked[0].reason).toEqual({ kind: "similar", to: ["Title a", "Title b"] });
    expect(ranked[1].reason).toEqual({ kind: "genres", genres: ["Fantasy", "Comedy"] });
  });
});

describe("findContinuations", () => {
  it("takes later entries of a seed's franchise, once each, skipping what is excluded", () => {
    const seeds = [
      seed("s1", { year: 2020, relatedAnime: [
        { id: "prequel", title: "Before", year: 2018 },
        { id: "sequel", title: "After", year: 2022 },
        { id: "watched", title: "Seen", year: 2023 },
      ] }),
      seed("s2", { year: 2021, franchiseAnime: [{ id: "sequel", title: "After", year: 2022 }] }),
    ];
    const found = findContinuations(seeds, new Set(["watched"]), 10);
    expect(found.map((item) => item.anime.id)).toEqual(["sequel"]);
    expect(found[0].reason).toEqual({ kind: "continues", of: "Title s1" });
  });
});
