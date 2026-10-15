// HEVC and AV1 carry the same picture as H.264 in roughly 30-50% fewer bytes. When a master
// playlist offers a rendition in one of them next to an H.264 one of the same height, the H.264 one
// is dropped, so hls.js can't pick it - same resolution, same bitrate ladder position, less traffic.
// Nothing changes for a stream that has only H.264 (most of them), and nothing is ever dropped that
// would leave a height without a rendition.
//
// hls.js has already removed renditions this browser can't decode at all. What is left to decide is
// whether decoding is cheap: HEVC plays in Chromium only through the GPU, so it is; AV1 may be
// software-decoded on older machines, so it is preferred only where the browser says it is
// power-efficient.

export interface CodecLevel {
  height: number;
  videoCodec?: string;
}

export type CodecFamily = "avc" | "hevc" | "av1" | "other";

export function codecFamily(codec: string | undefined): CodecFamily {
  const c = (codec ?? "").toLowerCase();
  if (c.startsWith("avc1") || c.startsWith("avc3")) return "avc";
  if (c.startsWith("hvc1") || c.startsWith("hev1")) return "hevc";
  if (c.startsWith("av01")) return "av1";
  return "other";
}

/** Indices of H.264 renditions that an efficient-codec rendition of the same height replaces,
 * highest first (the order to remove them in without shifting the rest). */
export function redundantAvcLevels(levels: CodecLevel[], efficient: ReadonlySet<CodecFamily>): number[] {
  const efficientHeights = new Set(
    levels.filter((level) => level.height > 0 && efficient.has(codecFamily(level.videoCodec))).map((level) => level.height),
  );
  return levels
    .map((level, index) => ({ level, index }))
    .filter(({ level }) => codecFamily(level.videoCodec) === "avc" && efficientHeights.has(level.height))
    .map(({ index }) => index)
    .sort((a, b) => b - a);
}

/** Which efficient codecs to prefer on this device. */
export async function preferredEfficientCodecs(levels: CodecLevel[]): Promise<Set<CodecFamily>> {
  const preferred = new Set<CodecFamily>(["hevc"]);
  const av1 = levels.find((level) => codecFamily(level.videoCodec) === "av1");
  if (av1?.videoCodec && typeof navigator !== "undefined" && navigator.mediaCapabilities) {
    try {
      const info = await navigator.mediaCapabilities.decodingInfo({
        type: "media-source",
        video: { contentType: `video/mp4; codecs="${av1.videoCodec}"`, width: Math.round((av1.height * 16) / 9), height: av1.height, bitrate: 2_000_000, framerate: 24 },
      });
      if (info.supported && info.powerEfficient) preferred.add("av1");
    } catch {
      // No verdict: H.264 stays, which always works.
    }
  }
  return preferred;
}
