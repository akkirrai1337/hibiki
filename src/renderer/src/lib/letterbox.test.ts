import { describe, expect, it } from "vitest";
import { barsCropScale, barsFromLuma } from "./letterbox";

function frame(width: number, height: number, pixel: (x: number, y: number) => number): Uint8Array {
  const luma = new Uint8Array(width * height);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) luma[y * width + x] = pixel(x, y);
  return luma;
}

describe("barsFromLuma", () => {
  it("finds bars at both sides", () => {
    const luma = frame(100, 50, (x) => (x < 2 || x >= 98 ? 3 : 200));
    expect(barsFromLuma(luma, 100, 50)).toEqual({ x: 0.02, y: 0 });
  });

  it("finds none in a picture that runs to its edges", () => {
    expect(barsFromLuma(frame(100, 50, () => 150), 100, 50)).toEqual({ x: 0, y: 0 });
  });

  it("can't tell from a dark scene", () => {
    expect(barsFromLuma(frame(100, 50, () => 5), 100, 50)).toBeNull();
  });

  it("does not take a dark shape at one side for bars", () => {
    const luma = frame(100, 50, (x) => (x < 6 ? 2 : 180));
    expect(barsFromLuma(luma, 100, 50)).toEqual({ x: 0, y: 0 });
  });
});

describe("barsCropScale", () => {
  it("scales just enough to push 1% side bars off a wider screen", () => {
    // 16:9 video on a 20:9 screen: cover fits the width; bars of 1% each side need 1/0.98 more.
    expect(barsCropScale({ x: 0.01, y: 0 }, 1920, 1080, 2000, 900)).toBeCloseTo(1 / 0.98);
  });

  it("needs nothing when cover already crops the barred sides", () => {
    // Top/bottom bars on a screen wider than the video: cover already cuts more than the bars.
    expect(barsCropScale({ x: 0, y: 0.02 }, 1920, 1080, 2000, 900)).toBe(1);
  });
});
