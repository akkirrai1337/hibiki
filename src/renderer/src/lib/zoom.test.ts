import { describe, expect, it } from "vitest";
import { DEFAULT_ZOOM, normalizeZoom, ZOOM_STEPS, zoomIn, zoomOut } from "./zoom";

describe("zoom stepping", () => {
  it("moves one step at a time in both directions", () => {
    expect(zoomIn(1)).toBe(1.1);
    expect(zoomIn(1.1)).toBe(1.25);
    expect(zoomOut(1)).toBe(0.9);
    expect(zoomOut(0.9)).toBe(0.8);
  });

  // The reason for a fixed ladder instead of multiplying by a constant: repeated multiplication
  // never lands back exactly on 1, so the UI would sit imperceptibly off its default forever.
  it("returns exactly to the default after equal steps out and back", () => {
    let factor: number = DEFAULT_ZOOM;
    for (let i = 0; i < 4; i++) factor = zoomIn(factor);
    for (let i = 0; i < 4; i++) factor = zoomOut(factor);
    expect(factor).toBe(DEFAULT_ZOOM);

    for (let i = 0; i < 3; i++) factor = zoomOut(factor);
    for (let i = 0; i < 3; i++) factor = zoomIn(factor);
    expect(factor).toBe(DEFAULT_ZOOM);
  });

  it("stops at the ends instead of running away", () => {
    const max = ZOOM_STEPS[ZOOM_STEPS.length - 1];
    const min = ZOOM_STEPS[0];
    expect(zoomIn(max)).toBe(max);
    expect(zoomOut(min)).toBe(min);
    // Holding the key down must not accumulate past the bound and then need the same number of
    // presses to come back.
    let factor: number = max;
    for (let i = 0; i < 10; i++) factor = zoomIn(factor);
    expect(zoomOut(factor)).toBe(ZOOM_STEPS[ZOOM_STEPS.length - 2]);
  });

  it("steps from a value that isn't on the ladder", () => {
    // A factor saved by an older build, or edited by hand in localStorage.
    expect(zoomIn(1.05)).toBe(1.1);
    expect(zoomOut(1.05)).toBe(0.9);
  });
});

describe("normalizeZoom", () => {
  it("keeps values that are already on the ladder", () => {
    for (const step of ZOOM_STEPS) expect(normalizeZoom(step)).toBe(step);
  });

  it("snaps an off-ladder value to the nearest step", () => {
    expect(normalizeZoom(1.04)).toBe(1);
    expect(normalizeZoom(1.2)).toBe(1.25);
  });

  it("clamps beyond the ends rather than applying an unusable zoom", () => {
    expect(normalizeZoom(12)).toBe(2);
    expect(normalizeZoom(0.01)).toBe(0.5);
    // Negative and zero factors would blank or invert the window if handed to Chromium.
    expect(normalizeZoom(0)).toBe(0.5);
    expect(normalizeZoom(-1)).toBe(0.5);
  });

  it("falls back to the default for anything that isn't a usable number", () => {
    // localStorage is plain text: a corrupted or older-shaped value arrives as whatever it is.
    expect(normalizeZoom(Number.NaN)).toBe(DEFAULT_ZOOM);
    expect(normalizeZoom(Number.POSITIVE_INFINITY)).toBe(DEFAULT_ZOOM);
    expect(normalizeZoom(undefined)).toBe(DEFAULT_ZOOM);
    expect(normalizeZoom("1.5")).toBe(DEFAULT_ZOOM);
    expect(normalizeZoom(null)).toBe(DEFAULT_ZOOM);
  });
});
