import { describe, expect, it } from "vitest";
import type { VideoSegment } from "@shared/types";
import { setLoadHold, skipHoldPoint, skipMarginSeconds } from "./loadGate";

const opening: VideoSegment = { type: "OPENING", startMs: 60_000, endMs: 150_000 } as VideoSegment;
const ending: VideoSegment = { type: "ENDING", startMs: 1_300_000, endMs: 1_390_000 } as VideoSegment;
const never = () => false;

describe("skipMarginSeconds", () => {
  it("keeps the margin between 5 and 10 seconds", () => {
    expect(skipMarginSeconds(0)).toBe(5);
    expect(skipMarginSeconds(5)).toBe(8);
    expect(skipMarginSeconds(30)).toBe(10);
  });
});

describe("skipHoldPoint", () => {
  it("stops a margin into the next opening", () => {
    expect(skipHoldPoint([opening, ending], 10, 8, never)).toBe(68);
  });

  it("moves on to the ending once the opening is behind", () => {
    expect(skipHoldPoint([opening, ending], 200, 8, never)).toBe(1308);
  });

  it("still holds while inside the segment, before the hold point", () => {
    expect(skipHoldPoint([opening], 62, 8, never)).toBe(68);
  });

  it("does not hold a dismissed segment", () => {
    expect(skipHoldPoint([opening], 10, 8, (s) => s === opening)).toBeNull();
  });

  it("ignores a segment too short to save anything", () => {
    const short = { type: "OPENING", startMs: 60_000, endMs: 69_000 } as VideoSegment;
    expect(skipHoldPoint([short], 10, 8, never)).toBeNull();
  });

  it("ignores other segment types and missing data", () => {
    const recap = { type: "RECAP", startMs: 0, endMs: 90_000 } as unknown as VideoSegment;
    expect(skipHoldPoint([recap], 0, 8, never)).toBeNull();
    expect(skipHoldPoint(undefined, 0, 8, never)).toBeNull();
  });
});

describe("setLoadHold", () => {
  function loader() {
    const calls: string[] = [];
    return { calls, stopLoad: () => calls.push("stop"), startLoad: (p?: number) => calls.push(`start@${p}`) };
  }

  it("stops once and starts once, however many reasons overlap", () => {
    const l = loader();
    setLoadHold(l, "skip", true, 10, () => {});
    setLoadHold(l, "pause", true, 11, () => {});
    setLoadHold(l, "skip", false, 12, () => {});
    expect(l.calls).toEqual(["stop"]);
    setLoadHold(l, "pause", false, 13, () => {});
    expect(l.calls).toEqual(["stop", "start@13"]);
  });

  it("does nothing when nothing changes", () => {
    const l = loader();
    setLoadHold(l, "skip", false, 0, () => {});
    expect(l.calls).toEqual([]);
  });
});
