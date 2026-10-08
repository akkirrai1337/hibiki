import { describe, expect, it } from "vitest";
import { genreFilterFor } from "./genreLink";
import type { SearchFilterDef } from "@shared/types";

const genres = (type: SearchFilterDef["type"]): { filters: SearchFilterDef[] } => ({
  filters: [
    { id: "status", title: "Status", type: "select", options: [{ id: "done", title: "Comedy" }] },
    { id: "genres", title: "Genres", type, options: [{ id: "g1", title: "Comedy" }, { id: "g2", title: "Drama" }] },
  ],
});

describe("genreFilterFor", () => {
  it("finds the option of the source's genre filter by name, in the shape of its type", () => {
    expect(genreFilterFor(genres("tristate"), " comedy ")).toEqual({ filterId: "genres", value: { include: ["g1"], exclude: [] } });
    expect(genreFilterFor(genres("multi"), "Drama")).toEqual({ filterId: "genres", value: ["g2"] });
    expect(genreFilterFor(genres("select"), "Drama")).toEqual({ filterId: "genres", value: "g2" });
  });
  it("is null when the source has no such genre", () => {
    expect(genreFilterFor(genres("multi"), "Mecha")).toBeNull();
    expect(genreFilterFor(undefined, "Comedy")).toBeNull();
  });
});
