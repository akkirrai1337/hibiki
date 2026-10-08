import { describe, expect, it } from "vitest";
import { subtitleTracksFrom } from "@shared/resolverSubtitles";

const EMBED = "https://megaplay.buzz/stream/s-2/12345/sub";

describe("subtitleTracksFrom", () => {
  it("keeps each http(s) track once, fetched as the embed page fetched it", () => {
    const tracks = subtitleTracksFrom(
      [
        { url: "https://cdn.example/eng-2.vtt", label: "English", language: "en" },
        { url: "https://cdn.example/eng-2.vtt", label: null, language: null },
        { url: "https://cdn.example/spa.vtt", label: null, language: "es" },
      ],
      EMBED,
      "UA/1",
    );
    expect(tracks.map((track) => [track.url, track.label, track.language])).toEqual([
      ["https://cdn.example/eng-2.vtt", "English", "en"],
      ["https://cdn.example/spa.vtt", "es", "es"],
    ]);
    expect(tracks[0].headers).toEqual({ Referer: EMBED, Origin: "https://megaplay.buzz", "User-Agent": "UA/1" });
  });

  it("drops what cannot be read: page-local URLs and the seek-bar thumbnail sheet", () => {
    const tracks = subtitleTracksFrom(
      [
        { url: "blob:https://megaplay.buzz/abc", label: "English" },
        { url: "https://cdn.example/thumbnails.vtt", label: null },
        { url: "https://cdn.example/x.vtt", label: "Thumbnails" },
        { url: "", label: "Empty" },
        { url: null },
      ],
      EMBED,
    );
    expect(tracks).toEqual([]);
  });

  it("labels an unnamed track so the picker never shows an empty row", () => {
    expect(subtitleTracksFrom([{ url: "https://cdn.example/a.vtt" }], EMBED)[0].label).toBe("Track 1");
  });
});
