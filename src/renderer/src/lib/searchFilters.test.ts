import { describe, expect, it } from "vitest";
import { EMPTY_SEARCH_FILTERS, activeFilterCount, cycleTristate, toSearchRequestFilters, tristateOf, withFilterValue } from "./searchFilters";

describe("source-defined filters", () => {
  it("counts one per set filter, whatever its shape, and ignores empty ones", () => {
    let filters = withFilterValue(EMPTY_SEARCH_FILTERS, "genres", { include: ["a"], exclude: ["b"] });
    filters = withFilterValue(filters, "studio", "Actas");
    filters = withFilterValue(filters, "year", { from: 2015 });
    filters = withFilterValue(filters, "notes", "   ");
    filters = withFilterValue(filters, "tags", []);
    expect(activeFilterCount(filters)).toBe(3);
  });

  it("drops a key when its value is cleared, so 'off' equals 'never touched'", () => {
    const on = withFilterValue(EMPTY_SEARCH_FILTERS, "years", ["2020"]);
    expect(withFilterValue(on, "years", [])).toEqual(EMPTY_SEARCH_FILTERS);
    expect(withFilterValue(on, "years", { include: [], exclude: [] })).toEqual(EMPTY_SEARCH_FILTERS);
  });

  it("hands values to the source untouched, and sends nothing when nothing is set", () => {
    const filters = withFilterValue(EMPTY_SEARCH_FILTERS, "genres", ["Action"]);
    expect(toSearchRequestFilters(filters)).toEqual({ filters: { genres: ["Action"] } });
    expect(toSearchRequestFilters(EMPTY_SEARCH_FILTERS)).toEqual({});
  });

  it("cycles a tristate option none -> include -> exclude -> none", () => {
    const one = cycleTristate(undefined, "x");
    expect(tristateOf(one, "x")).toBe("include");
    const two = cycleTristate(one, "x");
    expect(tristateOf(two, "x")).toBe("exclude");
    expect(tristateOf(cycleTristate(two, "x"), "x")).toBe("none");
  });

  it("does not mutate the previous filters", () => {
    const before = withFilterValue(EMPTY_SEARCH_FILTERS, "a", "1");
    withFilterValue(before, "b", "2");
    expect(before).toEqual({ a: "1" });
  });
});
