import { describe, expect, it } from "vitest";
import type { AnimeTitle } from "@shared/types";
import { findListedTitle } from "./listedTitles";

function title(sourceId: string, id: string, name = "Some title"): AnimeTitle {
  return { id, sourceId, russianName: name } as unknown as AnimeTitle;
}

describe("findListedTitle", () => {
  it("finds a title in a plain list result", () => {
    const payloads = [[title("yummy-anime", "1"), title("yummy-anime", "2")]];
    expect(findListedTitle(payloads, "yummy-anime", "2")?.id).toBe("2");
  });

  // The catalog is a useInfiniteQuery, so its data is { pages: [[...], [...]] } rather than an
  // array - a title from page three has to be findable too.
  it("finds a title inside an infinite query's pages", () => {
    const payloads = [{ pages: [[title("yummy-anime", "1")], [title("yummy-anime", "9")]], pageParams: [1, 2] }];
    expect(findListedTitle(payloads, "yummy-anime", "9")?.id).toBe("9");
  });

  it("never matches the same id from a different source", () => {
    // Sources number their titles independently, so an id collision across two of them is ordinary
    // rather than exotic - matching on id alone would show one source's poster on another's page.
    const payloads = [[title("ani-liberty", "463", "Wrong one")]];
    expect(findListedTitle(payloads, "yummy-anime", "463")).toBeUndefined();
  });

  it("returns undefined when nothing has listed it", () => {
    expect(findListedTitle([[title("yummy-anime", "1")]], "yummy-anime", "404")).toBeUndefined();
  });

  it("walks past query data that isn't a list at all", () => {
    // The cache holds every query in the app - library rows, progress, downloads, an update check.
    // This has to step over all of it without throwing.
    const payloads: unknown[] = [
      undefined,
      null,
      42,
      "a string",
      { some: "object" },
      { pages: "not an array" },
      { pages: [null, 7, [title("yummy-anime", "5")]] },
      [null, undefined, { id: "5" }],
    ];
    expect(findListedTitle(payloads, "yummy-anime", "5")?.id).toBe("5");
  });

  it("takes the first match when several lists hold the title", () => {
    const fresh = title("yummy-anime", "1", "Fresh");
    const stale = title("yummy-anime", "1", "Stale");
    expect(findListedTitle([[fresh], [stale]], "yummy-anime", "1")?.russianName).toBe("Fresh");
  });
});
