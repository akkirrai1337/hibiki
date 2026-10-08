import type { ImgHTMLAttributes } from "react";

/**
 * Use Chromium's compositor for image scaling. A canvas copy per poster looks tempting, but it
 * doubles the large painted surfaces in a catalog and keeps them expensive to scroll even after
 * every image has loaded. Chromium's native `auto` resampling is high quality and stays on the
 * browser's optimized image path.
 */
export function SmoothImage({ className, decoding = "async", style, ...props }: ImgHTMLAttributes<HTMLImageElement>) {
  return (
    <img
      {...props}
      decoding={decoding}
      className={`block object-cover ${className ?? ""}`}
      style={{ imageRendering: "auto", ...style }}
    />
  );
}
