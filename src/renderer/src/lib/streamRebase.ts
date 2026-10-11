// Moving a playing HLS stream onto a freshly issued URL without restarting it.
//
// CDN tokens live in the stream's URL - Alloha's (vkvideo) about six minutes, Kodik's hours - and
// hls.js keeps requesting segments by the playlist it parsed at the start. Once the token dies every
// next segment answers 403, and reloading the source with a new URL throws away the buffer and
// re-attaches the media: a visible stop. Segment URLs, though, are the playlist's relative names
// under the stream's directory, and a fresh link to the same stream is the same names under a new
// directory. So requests still aimed at the old directory are sent to the new one instead, and
// playback carries on from what it already has buffered.
import type { HlsConfig, Loader, LoaderCallbacks, LoaderConfiguration, LoaderContext } from "hls.js";

type LoaderClass = new (config: HlsConfig) => Loader<LoaderContext>;
type LoaderInstance = Loader<LoaderContext> & {
  load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void;
};

/** Everything up to and including the last "/" of the path - no query, no fragment. */
export function directoryOf(url: string): string {
  const path = url.split(/[?#]/, 1)[0];
  return path.slice(0, path.lastIndexOf("/") + 1);
}

export interface StreamRebase {
  /** From now on, requests under `originalUrl`'s directory go under `freshUrl`'s. */
  rebase(originalUrl: string, freshUrl: string): void;
  /** Where a request for `url` actually goes. */
  rewrite(url: string): string;
  /** An hls.js loader class that sends its requests where rewrite() says. */
  hlsLoader(Base: LoaderClass): LoaderClass;
}

export function createStreamRebase(): StreamRebase {
  let from: string | null = null;
  let to: string | null = null;
  const rewrite = (url: string) => (from && to && url.startsWith(from) ? to + url.slice(from.length) : url);
  return {
    rebase(originalUrl, freshUrl) {
      // Always from the original: that is what hls.js's parsed playlist keeps asking for, however
      // many times the token has been renewed since.
      from = directoryOf(originalUrl);
      to = directoryOf(freshUrl);
    },
    rewrite,
    hlsLoader(Base) {
      return class RebasedLoader extends (Base as new (config: HlsConfig) => LoaderInstance) {
        load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void {
          context.url = rewrite(context.url);
          super.load(context, config, callbacks);
        }
      } as unknown as LoaderClass;
    },
  };
}
