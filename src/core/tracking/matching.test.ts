import { describe, expect, it } from "vitest";
import { normalizeTitleForMatch, pickConfident, scoreMatch, searchQueriesFor, trackerNamesOf } from "./matching";

describe("normalizeTitleForMatch", () => {
  it("collapses the season wording sites disagree about", () => {
    expect(normalizeTitleForMatch("Mushoku Tensei II: 2nd Season")).toBe(normalizeTitleForMatch("Mushoku Tensei II - Season 2"));
  });

  it("keeps non-latin scripts", () => {
    expect(normalizeTitleForMatch("葬送のフリーレン")).toBe("葬送のフリーレン");
  });

  it("reads a trailing Roman season numeral as its number, and nothing else", () => {
    expect(normalizeTitleForMatch("Classroom of the Elite IV")).toBe("classroom of the elite 4");
    expect(normalizeTitleForMatch("X")).toBe("x");
    expect(normalizeTitleForMatch("Ergo Proxy")).toBe("ergo proxy");
  });
});

describe("searchQueriesFor", () => {
  it("strips a tag the source appended for its own catalog", () => {
    expect(searchQueriesFor(["Jujutsu Kaisen (TV)"])).toEqual(["Jujutsu Kaisen (TV)", "Jujutsu Kaisen"]);
  });

  it("flattens dashes used as brackets, then drops the season suffix", () => {
    expect(searchQueriesFor(["Re:ZERO -Starting Life in Another World- Season 3"])).toEqual([
      "Re:ZERO -Starting Life in Another World- Season 3",
      "Re:ZERO Starting Life in Another World Season 3",
      "Re:ZERO Starting Life in Another World",
    ]);
  });

  it("falls back to the other names a title carries, once each", () => {
    expect(searchQueriesFor(["Death Note", "Death Note"])).toEqual(["Death Note"]);
    expect(searchQueriesFor(["Frieren", "Sousou no Frieren"])).toEqual(["Frieren", "Sousou no Frieren"]);
  });
});

describe("trackerNamesOf", () => {
  it("leaves the Russian name out, since no tracker knows it", () => {
    expect(trackerNamesOf({ englishName: "Frieren", originalName: "Sousou no Frieren", synonyms: ["葬送のフリーレン"] })).toEqual([
      "Frieren",
      "Sousou no Frieren",
      "葬送のフリーレン",
    ]);
    expect(trackerNamesOf({ englishName: null, originalName: " ", synonyms: [] })).toEqual([]);
  });
});

describe("scoreMatch", () => {
  const frieren = { names: ["Frieren: Beyond Journey's End"], year: 2023, type: "tv" };

  it("scores an exact name with a matching year and type at the top", () => {
    expect(scoreMatch(frieren, { names: ["Frieren: Beyond Journey's End"], year: 2023, type: "tv" })).toBe(1);
  });

  it("ignores a one-year disagreement between sites, not a bigger one", () => {
    expect(scoreMatch(frieren, { names: ["Frieren: Beyond Journey's End"], year: 2024, type: "tv" })).toBe(1);
    expect(scoreMatch(frieren, { names: ["Frieren: Beyond Journey's End"], year: 2018, type: "tv" })).toBeLessThan(0.86);
  });

  it("matches the same season written two different ways", () => {
    expect(
      scoreMatch({ names: ["Classroom of the Elite IV"], year: 2025 }, { names: ["Classroom of the Elite 4th Season: Second Year, First Semester"], year: 2025 }),
    ).toBeGreaterThanOrEqual(0.86);
  });

  it("refuses a sequel offered for its own first season", () => {
    expect(scoreMatch(frieren, { names: ["Sousou no Frieren 2nd Season"], year: 2026, type: "tv" })).toBe(0);
  });

  it("refuses a candidate that shares no name", () => {
    expect(scoreMatch(frieren, { names: ["Bocchi the Rock!"], year: 2023, type: "tv" })).toBe(0);
  });
});

describe("pickConfident", () => {
  const candidate = (key: number, names: string[], year?: number | null, type?: string | null) => ({ key, comparable: { names, year, type } });

  it("separates a season from its movie by year and type", () => {
    const picked = pickConfident({ names: ["Mushoku Tensei"], year: 2023, type: "tv" }, [
      candidate(10, ["Mushoku Tensei"], 2021, "tv"),
      candidate(11, ["Mushoku Tensei"], 2023, "tv"),
      candidate(12, ["Mushoku Tensei"], 2023, "movie"),
    ]);
    expect(picked?.key).toBe(11);
  });

  it("does not act on a name alone, which is what a sequel looks like too", () => {
    expect(pickConfident({ names: ["Mushoku Tensei"] }, [candidate(10, ["Mushoku Tensei"])])).toBeNull();
  });

  it("refuses to choose between two entries that fit equally well", () => {
    // Two cours of one name in one year: either would be a guess written to someone's account.
    expect(
      pickConfident({ names: ["Oshi no Ko"], year: 2023, type: "tv" }, [
        candidate(1, ["Oshi no Ko"], 2023, "tv"),
        candidate(2, ["Oshi no Ko"], 2023, "tv"),
      ]),
    ).toBeNull();
  });

  it("counts one entry found by two queries once", () => {
    const picked = pickConfident({ names: ["Death Note"], year: 2006, type: "tv" }, [
      candidate(1535, ["Death Note"], 2006, "tv"),
      candidate(1535, ["DEATH NOTE"], 2006, "tv"),
    ]);
    expect(picked?.key).toBe(1535);
  });

  it("returns nothing rather than a weak guess", () => {
    expect(pickConfident({ names: ["Mushoku Tensei"], year: 2023 }, [candidate(20, ["Something Else"], 2023)])).toBeNull();
  });
});
