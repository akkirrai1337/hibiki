// How many bytes a stream has received so far, counting the requests still in flight. The player's
// startup/buffering watchdog reads this to tell a dead stream from a slow one: on mobile data a
// single segment can take longer than the whole timeout to arrive, and switching away from a
// stream that is still downloading only starts the next one from scratch on the same slow link.
import type { HlsConfig, Loader, LoaderCallbacks, LoaderConfiguration, LoaderContext } from "hls.js";

type LoaderClass = new (config: HlsConfig) => Loader<LoaderContext>;
type LoaderInstance = Loader<LoaderContext> & {
  load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void;
  destroy(): void;
};

export interface LoadActivity {
  /** Bytes received so far - finished requests plus whatever the running ones have read. */
  bytes(): number;
  /** Counts something that arrived outside hls.js (a <video> element's own "progress" event). */
  mark(): void;
  /** An hls.js loader class that reports into this counter. */
  hlsLoader(Base: LoaderClass): LoaderClass;
}

export function createLoadActivity(): LoadActivity {
  let finished = 0;
  // hls.js's XhrLoader only updates `stats.loaded` while a request runs; it calls no callback
  // until the response is complete. So the running loaders themselves are read on each check.
  const running = new Set<LoaderInstance>();
  // Moves a loader's bytes into the finished total once, so the sum never goes backwards.
  const settle = (loader: LoaderInstance) => {
    if (running.delete(loader)) finished += loader.stats?.loaded ?? 0;
  };
  return {
    bytes() {
      let total = finished;
      for (const loader of running) total += loader.stats?.loaded ?? 0;
      return total;
    },
    mark() {
      finished += 1;
    },
    hlsLoader(Base) {
      return class ActivityLoader extends (Base as new (config: HlsConfig) => LoaderInstance) {
        load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void {
          running.add(this);
          super.load(context, config, {
            ...callbacks,
            onSuccess: (...args) => { settle(this); callbacks.onSuccess(...args); },
            onError: (...args) => { settle(this); callbacks.onError(...args); },
            onTimeout: (...args) => { settle(this); callbacks.onTimeout(...args); },
            ...(callbacks.onAbort ? { onAbort: (...args: Parameters<NonNullable<typeof callbacks.onAbort>>) => { settle(this); callbacks.onAbort!(...args); } } : {}),
          });
        }

        destroy(): void {
          settle(this);
          super.destroy();
        }
      } as unknown as LoaderClass;
    },
  };
}
