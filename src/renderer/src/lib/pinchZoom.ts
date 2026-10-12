// The phone player's pinch, as in YouTube and a little past it: fingers a little apart fill the
// screen with the picture (cropped at the edges), well apart zoom in on it under the fingers;
// together go back - out of the zoom, then to the whole picture with bars.
//
// Positions are in pixels from the player's centre; `x`/`y` is how far the zoomed picture is moved,
// `scale` how much larger than the filled screen it is (1 = not zoomed).

export interface ZoomView {
  fill: boolean;
  scale: number;
  x: number;
  y: number;
}

export const FIT_VIEW: ZoomView = { fill: false, scale: 1, x: 0, y: 0 };
export const FILL_VIEW: ZoomView = { fill: true, scale: 1, x: 0, y: 0 };

/** From the whole picture: this much further apart fills the screen... */
const FILL_RATIO = 1.15;
/** ...and this much starts zooming. */
const ZOOM_FROM_FIT_RATIO = 1.7;
/** From a filled screen, the fingers have to go this far apart before it zooms. */
const ZOOM_FROM_FILL_RATIO = 1.25;
/** Together to this fraction of the start goes back to the whole picture. */
const FIT_RATIO = 0.85;
export const MAX_ZOOM = 4;

interface Point {
  x: number;
  y: number;
}

/** Keeps a zoomed picture covering the screen: it can be moved only as far as it overhangs. */
export function clampView(view: ZoomView, width: number, height: number): ZoomView {
  if (!view.fill || view.scale <= 1) return { ...view, scale: 1, x: 0, y: 0 };
  const maxX = ((view.scale - 1) * width) / 2;
  const maxY = ((view.scale - 1) * height) / 2;
  return { ...view, x: Math.min(maxX, Math.max(-maxX, view.x)), y: Math.min(maxY, Math.max(-maxY, view.y)) };
}

/**
 * The view while two fingers are down: `base` is what was on screen when they touched, `ratio` how
 * far apart they are now against then, `startMid`/`mid` the point between them then and now.
 */
export function pinchView(base: ZoomView, ratio: number, startMid: Point, mid: Point, width: number, height: number): ZoomView {
  const baseScale = base.fill ? base.scale : 1;
  let scale: number;
  if (!base.fill) {
    if (ratio < FILL_RATIO) return FIT_VIEW;
    if (ratio < ZOOM_FROM_FIT_RATIO) return FILL_VIEW;
    scale = ratio / ZOOM_FROM_FIT_RATIO;
  } else if (base.scale > 1) {
    scale = base.scale * ratio;
    if (scale < FIT_RATIO) return FIT_VIEW;
    if (scale <= 1) return FILL_VIEW;
  } else {
    if (ratio < FIT_RATIO) return FIT_VIEW;
    if (ratio <= ZOOM_FROM_FILL_RATIO) return FILL_VIEW;
    scale = ratio / ZOOM_FROM_FILL_RATIO;
  }
  scale = Math.min(MAX_ZOOM, scale);
  // The spot of the picture that was between the fingers stays between them.
  const baseX = base.fill ? base.x : 0;
  const baseY = base.fill ? base.y : 0;
  const x = mid.x - (scale * (startMid.x - baseX)) / baseScale;
  const y = mid.y - (scale * (startMid.y - baseY)) / baseScale;
  return clampView({ fill: true, scale, x, y }, width, height);
}

/** The video element's transform for a view. */
export function viewTransform(view: ZoomView): string {
  return view.fill && view.scale > 1 ? `translate(${view.x}px, ${view.y}px) scale(${view.scale})` : "";
}
