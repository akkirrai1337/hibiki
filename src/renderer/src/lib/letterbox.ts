// Black bars baked into the video itself - many anime encodes carry a few pixels of black at the
// sides (or top and bottom) inside the picture. Filling the screen (lib/pinchZoom) crops the video's
// frame, not what is drawn inside it, so those bars stayed on screen. Read from a frame: how much of
// each side is bar, so the fill can crop that away too.

/** Per side, the fraction of the picture's width (x) or height (y) that is bar. */
export interface Bars {
  x: number;
  y: number;
}

// A bar is close to pure black all along; the picture next to it is clearly not. Bars past this
// fraction are no longer bars but a dark scene, and are not trusted.
const BAR_MAX_MEAN = 20;
const BAR_MAX_PEAK = 48;
const CONTENT_MIN_MEAN = 32;
const MAX_BAR_FRACTION = 0.15;

function lineStats(luma: Uint8Array, width: number, height: number, index: number, vertical: boolean) {
  let sum = 0;
  let peak = 0;
  const count = vertical ? height : width;
  for (let i = 0; i < count; i++) {
    const value = vertical ? luma[i * width + index] : luma[index * width + i];
    sum += value;
    if (value > peak) peak = value;
  }
  return { mean: sum / count, peak };
}

/** How many lines from one edge are bar, or null when it can't be told (a dark edge with no bright picture after it). */
function edgeBar(luma: Uint8Array, width: number, height: number, vertical: boolean, fromEnd: boolean): number | null {
  const lines = vertical ? width : height;
  const limit = Math.floor(lines * MAX_BAR_FRACTION);
  for (let n = 0; n <= limit; n++) {
    const { mean, peak } = lineStats(luma, width, height, fromEnd ? lines - 1 - n : n, vertical);
    if (mean <= BAR_MAX_MEAN && peak <= BAR_MAX_PEAK) continue;
    return mean >= CONTENT_MIN_MEAN ? n : null;
  }
  return null;
}

/**
 * The bars in one frame, from its luma (one byte per pixel, rows top to bottom), or null when the
 * frame can't tell - mostly a dark scene. Both sides of an axis must agree within a line, or that
 * axis is taken as having none: bars come in pairs, a dark shape at one side does not.
 */
export function barsFromLuma(luma: Uint8Array, width: number, height: number): Bars | null {
  const left = edgeBar(luma, width, height, true, false);
  const right = edgeBar(luma, width, height, true, true);
  const top = edgeBar(luma, width, height, false, false);
  const bottom = edgeBar(luma, width, height, false, true);
  if (left === null || right === null || top === null || bottom === null) return null;
  const x = Math.abs(left - right) <= 1 ? Math.max(left, right) / width : 0;
  const y = Math.abs(top - bottom) <= 1 ? Math.max(top, bottom) / height : 0;
  return { x, y };
}

const SAMPLE_WIDTH = 192;
let canvas: HTMLCanvasElement | null = null;

/** The bars in what the video shows now, or null when there's no frame, it can't be read, or it can't tell. */
export function readBars(video: HTMLVideoElement): Bars | null {
  if (!video.videoWidth || !video.videoHeight || video.readyState < 2) return null;
  const width = SAMPLE_WIDTH;
  const height = Math.max(1, Math.round((SAMPLE_WIDTH * video.videoHeight) / video.videoWidth));
  canvas ??= document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) return null;
  try {
    context.drawImage(video, 0, 0, width, height);
    const { data } = context.getImageData(0, 0, width, height);
    const luma = new Uint8Array(width * height);
    for (let i = 0; i < luma.length; i++) luma[i] = (data[i * 4] * 299 + data[i * 4 + 1] * 587 + data[i * 4 + 2] * 114) / 1000;
    return barsFromLuma(luma, width, height);
  } catch {
    // A frame from another origin can't be read back; its bars stay.
    return null;
  }
}

/**
 * How much more than object-fit: cover the video has to be scaled so the picture inside the bars
 * covers the box. 1 when cover already hides them.
 */
export function barsCropScale(bars: Bars, videoWidth: number, videoHeight: number, boxWidth: number, boxHeight: number): number {
  if (!videoWidth || !videoHeight || !boxWidth || !boxHeight) return 1;
  const cover = Math.max(boxWidth / videoWidth, boxHeight / videoHeight);
  const inner = Math.max(boxWidth / (videoWidth * (1 - 2 * bars.x)), boxHeight / (videoHeight * (1 - 2 * bars.y)));
  return Math.max(1, inner / cover);
}
