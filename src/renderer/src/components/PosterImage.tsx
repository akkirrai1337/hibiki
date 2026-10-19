import { useCallback, useState, type ImgHTMLAttributes, type SyntheticEvent } from "react";
import { useNearViewport } from "@/lib/useNearViewport";
import { originalOfResizedImage } from "@/lib/imageProxy";
import type { PosterVariant } from "@shared/types";

/**
 * A source that has its posters resized by an image proxy (images.weserv.nl, to send a 500KB PNG as
 * a 30KB WebP) keeps working when the proxy does not: the picture falls back to the original.
 * Returns the address to show and an onError that does the switch, passing other failures on.
 */
export function useResizeProxyFallback(
  src: string | undefined,
  onError?: (event: SyntheticEvent<HTMLImageElement>) => void,
): [string | undefined, (event: SyntheticEvent<HTMLImageElement>) => void] {
  // Keyed by the src it failed for, so a new src starts from the proxy again.
  const [failedFor, setFailedFor] = useState<string | null>(null);
  const original = src ? originalOfResizedImage(src) : null;
  const shown = src && failedFor === src && original ? original : src;
  return [shown, (event) => {
    if (src && original && failedFor !== src) setFailedFor(src);
    else onError?.(event);
  }];
}

export interface PosterImageProps extends ImgHTMLAttributes<HTMLImageElement> {
  /** Download only once the image comes near the screen (see useNearViewport) - `loading="lazy"`
   * starts 1250-2500px early, which in a sideways strip is every picture in it. */
  lazy?: boolean;
}

/** `posterVariants` as an srcset ("url 250w, url 570w"), or undefined when there are none. */
export function posterSrcSet(variants: PosterVariant[] | null | undefined): string | undefined {
  const usable = (variants ?? []).filter((variant) => variant.url && variant.width > 0 && !variant.url.includes(" ") && !variant.url.includes(","));
  if (usable.length === 0) return undefined;
  return [...usable].sort((a, b) => a.width - b.width).map((variant) => `${variant.url} ${variant.width}w`).join(", ");
}

/** Sharper than this many image pixels per CSS pixel is not visible on a poster this small. */
const MAX_POSTER_DENSITY = 2;

/** The `sizes` for a picture laid out `width` CSS pixels wide, so the browser - which multiplies it
 * by the screen's pixel density - asks for at most MAX_POSTER_DENSITY image pixels per CSS pixel. */
export function posterSizes(width: number, devicePixelRatio: number): string {
  const ratio = Math.max(1, devicePixelRatio);
  return `${Math.ceil((width * Math.min(ratio, MAX_POSTER_DENSITY)) / ratio)}px`;
}

/**
 * A plain <img> for artwork from a source: optionally lazy, and see useResizeProxyFallback.
 *
 * With `srcSet` (posterSrcSet) the browser downloads only one variant: the smallest that is sharp
 * at the width the image is laid out at, up to MAX_POSTER_DENSITY. On a phone (density ~3) a catalog
 * card ~115px wide takes Yummy's 250px poster (~25 KB) instead of the 570px one (~55 KB) - a plain
 * sizes="auto" would have asked for 400px. A variant that fails drops the srcset, so src - and from
 * there the resize-proxy fallback - takes over.
 */
export function PosterImage({ src, srcSet, lazy = false, alt, onError, ...props }: PosterImageProps) {
  const [nearRef, near] = useNearViewport<HTMLImageElement>();
  const [srcSetFailed, setSrcSetFailed] = useState<string | null>(null);
  const usingSrcSet = srcSet && srcSetFailed !== srcSet ? srcSet : undefined;
  // Measured once the element is laid out; until then (or at width 0) the browser's own guess.
  const [layoutWidth, setLayoutWidth] = useState<number | null>(null);
  const ref = useCallback((element: HTMLImageElement | null) => {
    if (lazy) nearRef(element);
    if (element && srcSet) {
      const width = element.getBoundingClientRect().width;
      if (width > 0) setLayoutWidth(width);
    }
  }, [lazy, nearRef, srcSet]);
  const [wanted, handleError] = useResizeProxyFallback(src, onError);
  const shown = lazy && !near ? undefined : wanted;
  return (
    <img
      {...props}
      ref={ref}
      src={shown}
      srcSet={shown ? usingSrcSet : undefined}
      // Unmeasured: sizes="auto" (the laid-out width, needs loading="lazy"), then a rough guess for an
      // engine without it.
      sizes={usingSrcSet ? (layoutWidth ? posterSizes(layoutWidth, window.devicePixelRatio || 1) : "auto, (max-width: 640px) 34vw, 220px") : undefined}
      loading={usingSrcSet ? "lazy" : props.loading}
      alt={shown ? alt : ""}
      onError={(event) => {
        if (usingSrcSet) setSrcSetFailed(usingSrcSet);
        else handleError(event);
      }}
    />
  );
}
