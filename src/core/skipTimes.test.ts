import { describe, expect, it } from "vitest";
import { parseAniSkip } from "./skipTimes";

describe("parseAniSkip", () => {
  it("turns AniSkip's answer into opening and ending segments", () => {
    const body = JSON.stringify({
      found: true,
      results: [
        { interval: { startTime: 1290.5, endTime: 1380 }, skipType: "ed", episodeLength: 1420 },
        { interval: { startTime: 62.3, endTime: 152.1 }, skipType: "op", episodeLength: 1420 },
      ],
    });
    expect(parseAniSkip(body)).toEqual([
      { type: "OPENING", startMs: 62300, endMs: 152100 },
      { type: "ENDING", startMs: 1290500, endMs: 1380000 },
    ]);
  });

  it("reads a mixed opening or ending as one, and leaves a recap out", () => {
    const body = JSON.stringify({
      found: true,
      results: [
        { interval: { startTime: 0, endTime: 90 }, skipType: "mixed-op" },
        { interval: { startTime: 100, endTime: 160 }, skipType: "recap" },
        { interval: { startTime: 1300, endTime: 1390 }, skipType: "mixed-ed" },
      ],
    });
    expect(parseAniSkip(body).map((s) => s.type)).toEqual(["OPENING", "ENDING"]);
  });

  it("gives nothing for no times, a broken body or a nonsense interval", () => {
    expect(parseAniSkip(JSON.stringify({ found: false, results: [] }))).toEqual([]);
    expect(parseAniSkip("<html>")).toEqual([]);
    expect(parseAniSkip(JSON.stringify({ found: true, results: [{ interval: { startTime: 90, endTime: 30 }, skipType: "op" }] }))).toEqual([]);
  });
});
