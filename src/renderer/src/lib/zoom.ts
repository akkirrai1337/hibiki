// A fixed ladder rather than multiplying by a constant, matching what Chrome itself steps through.
// Repeated multiplication drifts into values like 1.3310000000000004 and, worse, never lands back
// exactly on 1 - so "zoom in twice, zoom out twice" would leave the UI imperceptibly off its
// default forever, and the reset below would be the only way back.
//
// The range stops at half and double on purpose. This is app chrome, not a document: the sidebar,
// the player controls and the settings rows all have px-based minimums, and past roughly these
// bounds they start colliding rather than just looking small or large.
export const ZOOM_STEPS = [0.5, 0.67, 0.75, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2] as const;
export const DEFAULT_ZOOM = 1;

/** The ladder entry a stored factor corresponds to - nearest match, so a value saved by an older
 * build (or hand-edited in localStorage) still lands somewhere sane instead of pinning to an end. */
function stepIndexFor(factor: number): number {
  let nearest = 0;
  for (let i = 1; i < ZOOM_STEPS.length; i++) {
    if (Math.abs(ZOOM_STEPS[i] - factor) < Math.abs(ZOOM_STEPS[nearest] - factor)) nearest = i;
  }
  return nearest;
}

/** One step up the ladder, or the same value when already at the top. */
export function zoomIn(factor: number): number {
  return ZOOM_STEPS[Math.min(stepIndexFor(factor) + 1, ZOOM_STEPS.length - 1)];
}

/** One step down the ladder, or the same value when already at the bottom. */
export function zoomOut(factor: number): number {
  return ZOOM_STEPS[Math.max(stepIndexFor(factor) - 1, 0)];
}

/** Anything outside the ladder - a corrupted or hand-edited stored value - snapped back onto it. */
export function normalizeZoom(factor: unknown): number {
  if (typeof factor !== "number" || !Number.isFinite(factor)) return DEFAULT_ZOOM;
  return ZOOM_STEPS[stepIndexFor(factor)];
}
