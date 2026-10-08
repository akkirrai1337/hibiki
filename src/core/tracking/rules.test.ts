import { describe, expect, it } from "vitest";
import { parseAniListRedirect } from "./anilist";
import { categoryChange, favouriteChange, importedCategory, progressChange } from "./rules";

describe("progressChange", () => {
  it("never lowers progress, and writes nothing when there is nothing new", () => {
    expect(progressChange(3, { status: "watching", progress: 5 }, 12)).toBeNull();
    expect(progressChange(5, { status: "watching", progress: 5 }, 12)).toBeNull();
    expect(progressChange(0, null, 12)).toBeNull();
  });

  it("raises it, keeping a status that already says watching", () => {
    expect(progressChange(6, { status: "watching", progress: 5 }, 12)).toEqual({ progress: 6 });
  });

  it("starts a title that was planned or on no list", () => {
    expect(progressChange(1, { status: "planned", progress: 0 }, 12)).toEqual({ progress: 1, status: "watching" });
    expect(progressChange(2, null, null)).toEqual({ progress: 2, status: "watching" });
  });

  it("completes a title on its last episode, and never counts past it", () => {
    expect(progressChange(12, { status: "watching", progress: 11 }, 12)).toEqual({ progress: 12, status: "completed" });
    expect(progressChange(14, { status: "watching", progress: 11 }, 12)).toEqual({ progress: 12, status: "completed" });
  });

  it("leaves a rewatch a rewatch", () => {
    expect(progressChange(12, { status: "rewatching", progress: 4 }, 12)).toEqual({ progress: 12 });
  });

  it("does not finish a show whose length the tracker does not know yet", () => {
    expect(progressChange(30, { status: "watching", progress: 29 }, null)).toEqual({ progress: 30 });
  });
});

describe("categoryChange", () => {
  it("sends a status only when it differs", () => {
    expect(categoryChange("planned", { status: "planned", progress: 0 }, 12)).toBeNull();
    expect(categoryChange("dropped", { status: "watching", progress: 3 }, 12)).toEqual({ status: "dropped" });
    expect(categoryChange("watching", null, 12)).toEqual({ status: "watching" });
  });

  it("has no status for favourites", () => {
    expect(categoryChange("favorite", null, 12)).toBeNull();
  });

  it("does not turn a rewatch back into a first watch", () => {
    expect(categoryChange("watching", { status: "rewatching", progress: 2 }, 12)).toBeNull();
  });

  it("fills the progress in on completing", () => {
    expect(categoryChange("completed", { status: "watching", progress: 5 }, 12)).toEqual({ status: "completed", progress: 12 });
    expect(categoryChange("completed", { status: "watching", progress: 5 }, null)).toEqual({ status: "completed" });
  });
});

describe("favouriteChange", () => {
  it("marks a favourite going into favourites, once", () => {
    expect(favouriteChange("watching", "favorite", false)).toBe(true);
    expect(favouriteChange("watching", "favorite", true)).toBeNull();
  });

  it("unmarks it when the title leaves favourites for another category", () => {
    expect(favouriteChange("favorite", "watching", true)).toBe(false);
  });

  it("leaves alone a favourite that was never one here", () => {
    expect(favouriteChange("planned", "watching", true)).toBeNull();
    expect(favouriteChange(null, "completed", true)).toBeNull();
  });
});

describe("importedCategory", () => {
  it("files a status under its category, a rewatch under watching", () => {
    expect(importedCategory("on_hold", false)).toBe("on_hold");
    expect(importedCategory("rewatching", true)).toBe("watching");
  });

  it("files a favourite on no list under favourites, and skips the rest", () => {
    expect(importedCategory(null, true)).toBe("favorite");
    expect(importedCategory(null, false)).toBeNull();
  });
});

describe("parseAniListRedirect", () => {
  it("reads the token and its lifetime out of the fragment", () => {
    expect(parseAniListRedirect("hibiki://anilist-auth#access_token=abc.def&token_type=Bearer&expires_in=31536000")).toEqual({
      ok: true,
      accessToken: "abc.def",
      expiresInSeconds: 31536000,
    });
  });

  it("reports the error AniList sent instead", () => {
    expect(parseAniListRedirect("hibiki://anilist-auth?error=access_denied&error_description=The+user+denied")).toEqual({
      ok: false,
      error: "The user denied",
    });
  });

  it("ignores every other link", () => {
    expect(parseAniListRedirect("hibiki://watch/a/b/c/d")).toBeNull();
  });
});
