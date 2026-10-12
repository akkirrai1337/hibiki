import { describe, expect, it } from "vitest";
import { FILL_VIEW, FIT_VIEW, MAX_ZOOM, clampView, pinchView } from "./pinchZoom";

const centre = { x: 0, y: 0 };
const W = 800;
const H = 400;

describe("pinchView", () => {
  it("from the whole picture: a little apart fills, well apart zooms", () => {
    expect(pinchView(FIT_VIEW, 1.05, centre, centre, W, H)).toEqual(FIT_VIEW);
    expect(pinchView(FIT_VIEW, 1.3, centre, centre, W, H)).toEqual(FILL_VIEW);
    const zoomed = pinchView(FIT_VIEW, 3.4, centre, centre, W, H);
    expect(zoomed.fill).toBe(true);
    expect(zoomed.scale).toBeCloseTo(2);
  });

  it("from a filled screen: apart zooms past a dead zone, together goes back to the whole picture", () => {
    expect(pinchView(FILL_VIEW, 1.2, centre, centre, W, H)).toEqual(FILL_VIEW);
    expect(pinchView(FILL_VIEW, 2.5, centre, centre, W, H).scale).toBeCloseTo(2);
    expect(pinchView(FILL_VIEW, 0.7, centre, centre, W, H)).toEqual(FIT_VIEW);
  });

  it("out of a zoom: first back to the filled screen, then to the whole picture", () => {
    const zoomed = { fill: true, scale: 2, x: 0, y: 0 };
    expect(pinchView(zoomed, 0.75, centre, centre, W, H).scale).toBeCloseTo(1.5);
    expect(pinchView(zoomed, 0.45, centre, centre, W, H)).toEqual(FILL_VIEW);
    expect(pinchView(zoomed, 0.3, centre, centre, W, H)).toEqual(FIT_VIEW);
  });

  it("keeps the spot between the fingers under them, never past the picture's edge", () => {
    const zoomed = pinchView(FILL_VIEW, 2.5, { x: 100, y: 50 }, { x: 100, y: 50 }, W, H);
    // Spot (100, 50) of the picture: 2 * 100 - 100 = 100 to the left of where it would sit.
    expect(zoomed.x).toBeCloseTo(-100);
    expect(zoomed.y).toBeCloseTo(-50);
    expect(pinchView(FILL_VIEW, 100, centre, centre, W, H).scale).toBe(MAX_ZOOM);
    expect(clampView({ fill: true, scale: 2, x: 1000, y: -1000 }, W, H)).toEqual({ fill: true, scale: 2, x: 400, y: -200 });
  });
});
