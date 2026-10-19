import { describe, expect, it } from "vitest";
import type { AnimeTitle, RelatedAnimeTitle } from "@shared/types";
import { forCache, mergeKnownExtras, withExtras } from "./titleParts";

const similar = (...ids: string[]): RelatedAnimeTitle[] => ids.map((id) => ({ id, title: `Title ${id}` }));
const core = (extra: Partial<AnimeTitle> = {}): AnimeTitle => ({ id: "1", sourceId: "src", russianName: "One", similarAnime: [], extrasPending: true, ...extra });

describe("mergeKnownExtras", () => {
  it("leaves a title that came whole alone", () => {
    const whole = core({ extrasPending: undefined, similarAnime: similar("a") });
    expect(mergeKnownExtras(whole, core({ similarAnime: similar("b"), extrasPending: undefined }))).toEqual({ title: whole, complete: true });
  });

  it("puts back the similar titles an earlier copy held, still pending for the page", () => {
    const { title, complete } = mergeKnownExtras(core({ russianName: "One (new)" }), core({ extrasPending: undefined, similarAnime: similar("a", "b") }));
    expect(title).toMatchObject({ russianName: "One (new)", extrasPending: true });
    expect(title.similarAnime?.map((entry) => entry.id)).toEqual(["a", "b"]);
    expect(complete).toBe(true);
  });

  it("has nothing to put back for a first visit, or when the earlier copy waited for its extras too", () => {
    expect(mergeKnownExtras(core(), null)).toEqual({ title: core(), complete: false });
    expect(mergeKnownExtras(core(), core({ similarAnime: similar("a") }))).toEqual({ title: core(), complete: false });
  });
});

describe("forCache", () => {
  it("drops the waiting mark only when the extras are in", () => {
    expect(forCache(core({ similarAnime: similar("a") }), true)).not.toHaveProperty("extrasPending");
    expect(forCache(core(), false)).toHaveProperty("extrasPending", true);
  });
});

describe("withExtras", () => {
  it("puts the extras in and the title is done waiting", () => {
    const done = withExtras(core({ russianName: "One" }), { similarAnime: similar("x") });
    expect(done).not.toHaveProperty("extrasPending");
    expect(done).toMatchObject({ id: "1", russianName: "One" });
    expect(done.similarAnime?.map((entry) => entry.id)).toEqual(["x"]);
  });
});
