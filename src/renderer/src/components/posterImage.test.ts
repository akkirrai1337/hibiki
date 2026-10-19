import { describe, expect, it } from "vitest";
import { posterSizes, posterSrcSet } from "./PosterImage";

describe("posterSrcSet", () => {
  it("lists the variants smallest first with their widths", () => {
    expect(posterSrcSet([
      { url: "https://cdn.test/mega/1.avif", width: 570 },
      { url: "https://cdn.test/big/1.webp", width: 250 },
    ])).toBe("https://cdn.test/big/1.webp 250w, https://cdn.test/mega/1.avif 570w");
  });

  it("is undefined without usable variants", () => {
    expect(posterSrcSet(undefined)).toBeUndefined();
    expect(posterSrcSet([{ url: "https://cdn.test/a b.jpg", width: 250 }, { url: "", width: 100 }, { url: "https://cdn.test/x.jpg", width: 0 }])).toBeUndefined();
  });
});

describe("posterSizes", () => {
  it("asks a dense phone screen for at most two image pixels per CSS pixel", () => {
    // A 115px card at density 3: 230 image pixels wanted, so 77 CSS px "times 3".
    expect(posterSizes(115, 3)).toBe("77px");
  });

  it("leaves screens at or under 2x alone", () => {
    expect(posterSizes(180, 1.25)).toBe("180px");
    expect(posterSizes(180, 2)).toBe("180px");
  });
});
