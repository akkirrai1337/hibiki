import { describe, expect, it } from "vitest";
import { headersForImageRequest } from "@shared/imageRequestHeaders";

const anitube = "https://anitube.in.ua/uploads/slider/slide.png";
const animelib = "https://cover.cdnlibs.org/uploads/anime/16133/cover/x.jpg";

describe("headersForImageRequest", () => {
  // The app's own page as the Referer is what AniTube's CDN rejects, and no Referer at all is what
  // AnimeLib's rejects. The image's own origin is what a page on that site would send.
  it("replaces this app's referer with the image's own origin", () => {
    expect(headersForImageRequest("image", anitube, { Referer: "http://localhost:5173/", Accept: "image/*" }))
      .toEqual({ Accept: "image/*", Referer: "https://anitube.in.ua/" });
  });

  it("supplies a referer for a request that carried none", () => {
    expect(headersForImageRequest("image", animelib, { Accept: "image/*" }))
      .toEqual({ Accept: "image/*", Referer: "https://cover.cdnlibs.org/" });
  });

  it("matches the headers it replaces however they are capitalised", () => {
    const headers = headersForImageRequest("image", anitube, { referer: "x", ORIGIN: "y", Accept: "image/*" });
    expect(headers).toEqual({ Accept: "image/*", Referer: "https://anitube.in.ua/" });
  });

  it("leaves everything that is not an image alone", () => {
    const headers = { Referer: "https://example.test/", Accept: "*/*" };
    expect(headersForImageRequest("xhr", anitube, headers)).toBe(headers);
    expect(headersForImageRequest("media", anitube, headers)).toBe(headers);
  });

  it("leaves an image that is not fetched over HTTP alone", () => {
    const headers = { Accept: "image/*" };
    expect(headersForImageRequest("image", "hibiki-download://frame/1", headers)).toBe(headers);
    expect(headersForImageRequest("image", "data:image/png;base64,AAAA", headers)).toBe(headers);
  });
});
