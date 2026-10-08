import { describe, expect, it } from "vitest";
import { isAgeRatingFilter, isConnectedToggle, optionIcon } from "./filterVisuals";
import type { SearchFilterDef } from "@shared/types";

const opts = (...titles: string[]) => titles.map((t) => ({ id: t.toLowerCase(), title: t }));
const def = (id: string, title: string, options: ReturnType<typeof opts>, type: SearchFilterDef["type"] = "multi"): SearchFilterDef => ({ id, title, type, options });

describe("filter visuals", () => {
  it("draws seasons, statuses and sub/dub as connected buttons", () => {
    expect(isConnectedToggle(def("season", "Season", opts("Fall", "Summer", "Spring", "Winter")))).toBe(true);
    expect(isConnectedToggle(def("status", "Status", opts("Finished Airing", "Currently Airing", "Not Yet Aired")))).toBe(true);
    expect(isConnectedToggle(def("language", "Language", opts("sub", "dub")))).toBe(true);
  });

  it("keeps everything else as chips: unknown names, one icon missing, or too many options", () => {
    expect(isConnectedToggle(def("genres", "Genres", opts("Action", "Drama")))).toBe(false);
    expect(isConnectedToggle(def("season", "Season", opts("Fall", "Summer", "Spooky")))).toBe(false);
    expect(isConnectedToggle(def("x", "X", opts("Fall", "Summer", "Spring", "Winter", "Fall2", "Fall3", "Fall4")))).toBe(false);
  });

  it("recognises an age rating only when the name and every option say so", () => {
    expect(isAgeRatingFilter(def("rating", "Rating", opts("PG", "PG-13", "G", "R", "R+", "Rx")))).toBe(true);
    expect(isAgeRatingFilter(def("rating", "Rating", opts("PG", "Great", "G")))).toBe(false);
    expect(isAgeRatingFilter(def("genres", "Genres", opts("PG", "PG-13", "G")))).toBe(false);
  });

  it("matches an icon by the option's id when its title is a translation", () => {
    expect(optionIcon({ id: "finished-airing", title: "Завершён" })).not.toBeNull();
  });
});

import { yearOptions } from "./filterVisuals";

describe("year lists", () => {
  it("turns a list of years into a slider, oldest first", () => {
    const years = yearOptions({ id: "year", title: "Year", type: "multi", options: [{ id: "2026", title: "2026" }, { id: "2024", title: "2024" }, { id: "2025", title: "2025" }] });
    expect(years?.map((y) => y.year)).toEqual([2024, 2025, 2026]);
  });

  it("leaves other filters alone", () => {
    expect(yearOptions({ id: "genres", title: "Genres", type: "multi", options: [{ id: "a", title: "2020" }, { id: "b", title: "2021" }] })).toBeNull();
    expect(yearOptions({ id: "year", title: "Year", type: "range", min: 1990, max: 2026 })).toBeNull();
    expect(yearOptions({ id: "season", title: "Season", type: "multi", options: [{ id: "fall", title: "Fall" }, { id: "winter", title: "Winter" }] })).toBeNull();
  });
});

import { inDisplayOrder } from "./filterVisuals";

describe("display order", () => {
  it("puts sort first, then season, genres, status, language, type, year, and the rest as given", () => {
    const d = (id: string, title = id): SearchFilterDef => ({ id, title, type: "multi", options: [] });
    const ordered = inDisplayOrder([d("source", "Source"), d("type", "Type"), d("rating", "Rating"), d("status", "Status"), d("year", "Year"), d("genres", "Genres"), d("season", "Season"), d("sort", "Sort")]);
    expect(ordered.map((x) => x.id)).toEqual(["sort", "season", "genres", "status", "type", "year", "source", "rating"]);
  });
});

import { withoutUnknown } from "./filterVisuals";

describe("unknown option", () => {
  it("is never offered, whatever the filter", () => {
    const [d] = withoutUnknown([{ id: "status", title: "Status", type: "multi", options: [{ id: "finished", title: "Finished" }, { id: "unknown", title: "Unknown" }] }]);
    expect(d.options?.map((o) => o.id)).toEqual(["finished"]);
  });
});

describe("status icons", () => {
  it("covers YummyAnime's statuses (announcement, not announced) so they become buttons", () => {
    const d = def("status", "Status", [{ id: "released", title: "Завершён" }, { id: "ongoing", title: "Онгоинг" }, { id: "announcement", title: "Анонс" }]);
    expect(isConnectedToggle(d)).toBe(true);
  });
});

import { chipIconFor } from "./filterVisuals";

describe("type icons", () => {
  it("find an icon for Ukrainian and Russian type names, by title or id", () => {
    const type = def("type", "Type", []);
    const icon = chipIconFor(type)!;
    for (const [id, title] of [["tv", "ТБ-серіал"], ["movie", "Фільм"], ["special", "Спешл"], ["ova", "OVA"], ["ona", "ONA"], ["tv", "ТВ"]]) {
      expect(icon({ id, title }), `${id}/${title}`).not.toBeNull();
    }
  });
});
