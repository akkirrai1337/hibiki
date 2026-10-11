import { useState, type ImgHTMLAttributes, type SyntheticEvent } from "react";
import { useNearViewport } from "@/lib/useNearViewport";
import { originalOfResizedImage } from "@/lib/imageProxy";

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

/** A plain <img> for artwork from a source: optionally lazy, and see useResizeProxyFallback. */
export function PosterImage({ src, lazy = false, alt, onError, ...props }: PosterImageProps) {
  const [ref, near] = useNearViewport<HTMLImageElement>();
  const [wanted, handleError] = useResizeProxyFallback(src, onError);
  const shown = lazy && !near ? undefined : wanted;
  return <img {...props} ref={lazy ? ref : undefined} src={shown} alt={shown ? alt : ""} onError={handleError} />;
}
