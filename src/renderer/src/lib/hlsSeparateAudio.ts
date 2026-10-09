// Some sources (Aniyomi extensions built on PlaylistUtils, Re:ANIME for one) split an HLS stream into a
// video-only variant playlist and a separate audio playlist. hls.js plays the variant alone without
// sound, so the player gives it a master playlist that pairs the two as an alternate audio rendition.
import type { HlsConfig, Loader, LoaderCallbacks, LoaderConfiguration, LoaderContext, LoaderStats } from "hls.js";

type LoaderClass = new (config: HlsConfig) => Loader<LoaderContext>;

const quoted = (value: string) => value.replace(/"/g, "%22");

/** A master playlist with `videoUrl` as its only variant and `audioUrl` as that variant's audio. */
export function separateAudioMaster(videoUrl: string, audioUrl: string): string {
  return [
    "#EXTM3U",
    `#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Audio",DEFAULT=YES,AUTOSELECT=YES,URI="${quoted(audioUrl)}"`,
    '#EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO="audio"',
    videoUrl,
    "",
  ].join("\n");
}

/**
 * An hls.js loader that answers the manifest request for `manifestUrl` with `manifest` instead of
 * fetching it. Level, audio and fragment requests, even ones to the same URL, go to `Base`.
 */
export function syntheticManifestLoader(Base: LoaderClass, manifestUrl: string, manifest: string): LoaderClass {
  return class SyntheticManifestLoader extends (Base as new (config: HlsConfig) => Loader<LoaderContext> & {
    load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void;
  }) {
    private synthetic = false;

    load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void {
      if (context.type !== "manifest" || context.url !== manifestUrl) {
        super.load(context, config, callbacks);
        return;
      }
      this.synthetic = true;
      const now = performance.now();
      const stats: LoaderStats = {
        aborted: false, loaded: manifest.length, retry: 0, total: manifest.length, chunkCount: 0, bwEstimate: 0,
        loading: { start: now, first: now, end: now },
        parsing: { start: now, end: now },
        buffering: { start: 0, first: 0, end: 0 },
      };
      // hls.js expects the answer asynchronously, as from a real request.
      queueMicrotask(() => {
        if (!this.synthetic) return;
        callbacks.onSuccess({ url: manifestUrl, data: manifest }, stats, context, null);
      });
    }

    abort(): void {
      this.synthetic = false;
      super.abort();
    }

    destroy(): void {
      this.synthetic = false;
      super.destroy();
    }
  } as unknown as LoaderClass;
}
