import { describe, expect, it } from "vitest";
import type { HlsConfig, LoaderCallbacks, LoaderContext } from "hls.js";
import { createLoadActivity } from "./loadActivity";

// Stands in for hls.js's XhrLoader: `stats.loaded` grows while the request runs, and a callback
// only comes once it is over.
class FakeLoader {
  stats = { loaded: 0 };
  callbacks: LoaderCallbacks<LoaderContext> | null = null;
  constructor(_config: HlsConfig) {}
  load(_context: LoaderContext, _config: unknown, callbacks: LoaderCallbacks<LoaderContext>) {
    this.callbacks = callbacks;
  }
  destroy() {}
}

function start(Loader: ReturnType<ReturnType<typeof createLoadActivity>["hlsLoader"]>) {
  const loader = new Loader({} as HlsConfig) as unknown as FakeLoader;
  const noop = () => {};
  loader.load({} as LoaderContext, {}, { onSuccess: noop, onError: noop, onTimeout: noop });
  return loader;
}

describe("createLoadActivity", () => {
  it("counts bytes of a request that is still running", () => {
    const activity = createLoadActivity();
    const loader = start(activity.hlsLoader(FakeLoader as never));
    loader.stats.loaded = 4096;
    expect(activity.bytes()).toBe(4096);
  });

  it("never goes backwards once a request finishes or is destroyed", () => {
    const activity = createLoadActivity();
    const Loader = activity.hlsLoader(FakeLoader as never);
    const done = start(Loader);
    done.stats.loaded = 100;
    done.callbacks!.onSuccess({ url: "", data: "" }, done.stats as never, {} as LoaderContext, null);
    const aborted = start(Loader);
    aborted.stats.loaded = 50;
    (aborted as unknown as { destroy(): void }).destroy();
    expect(activity.bytes()).toBe(150);
  });

  it("counts progress reported from outside hls.js", () => {
    const activity = createLoadActivity();
    activity.mark();
    expect(activity.bytes()).toBeGreaterThan(0);
  });
});
