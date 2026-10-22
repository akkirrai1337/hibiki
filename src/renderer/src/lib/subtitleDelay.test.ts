import { describe, expect, it } from "vitest";
import { clampSubtitleDelay, formatSubtitleDelay, shiftCues } from "./subtitleDelay";

describe("subtitle delay", () => {
  it("keeps whole steps, so ten small moves add up exactly", () => {
    let delay = 0;
    for (let i = 0; i < 10; i++) delay = clampSubtitleDelay(delay + 100);
    expect(delay).toBe(1000);
    expect(clampSubtitleDelay(149)).toBe(100);
    expect(clampSubtitleDelay(Number.NaN)).toBe(0);
    expect(clampSubtitleDelay(1e9)).toBe(600_000);
  });

  it("writes the sign and the locale's decimal mark", () => {
    expect(formatSubtitleDelay(1200, "ru", "с")).toBe("+1,2 с");
    expect(formatSubtitleDelay(-500, "en", "s")).toBe("−0.5 s");
    expect(formatSubtitleDelay(0, "ru", "с")).toBe("0 с");
  });

  it("moves every cue by the difference, both ways", () => {
    const cues = [{ startTime: 1, endTime: 2 }, { startTime: 5, endTime: 7.5 }];
    shiftCues(cues, 1500);
    expect(cues).toEqual([{ startTime: 2.5, endTime: 3.5 }, { startTime: 6.5, endTime: 9 }]);
    shiftCues(cues, -2000);
    expect(cues[0].startTime).toBeCloseTo(0.5);
    expect(cues[1].endTime).toBeCloseTo(7);
  });
});
