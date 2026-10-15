import { describe, expect, it } from "vitest";
import { codecFamily, redundantAvcLevels } from "./efficientCodecs";

describe("codecFamily", () => {
  it("recognises the codec strings HLS playlists use", () => {
    expect(codecFamily("avc1.64001f")).toBe("avc");
    expect(codecFamily("hvc1.1.6.L93.B0")).toBe("hevc");
    expect(codecFamily("hev1.1.6.L120.90")).toBe("hevc");
    expect(codecFamily("av01.0.08M.08")).toBe("av1");
    expect(codecFamily(undefined)).toBe("other");
  });
});

describe("redundantAvcLevels", () => {
  const levels = [
    { height: 480, videoCodec: "avc1.64001e" },
    { height: 720, videoCodec: "avc1.64001f" },
    { height: 720, videoCodec: "hvc1.1.6.L93.B0" },
    { height: 1080, videoCodec: "avc1.640028" },
    { height: 1080, videoCodec: "av01.0.08M.08" },
  ];

  it("drops H.264 only where a preferred codec covers the same height, highest index first", () => {
    expect(redundantAvcLevels(levels, new Set(["hevc"]))).toEqual([1]);
    expect(redundantAvcLevels(levels, new Set(["hevc", "av1"]))).toEqual([3, 1]);
  });

  it("leaves an H.264-only stream alone", () => {
    expect(redundantAvcLevels(levels.filter((l) => l.videoCodec.startsWith("avc")), new Set(["hevc", "av1"]))).toEqual([]);
  });
});
