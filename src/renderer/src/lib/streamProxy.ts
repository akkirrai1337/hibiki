// Playback through a host stream proxy (Android: the native proxy behind hibiki.player.streamUrl).
// Desktop has no such method - Electron injects headers at the session level - so everything here
// is a no-op there and the player loads stream URLs as it always has.
import type { HlsConfig, Loader, LoaderCallbacks, LoaderConfiguration, LoaderContext } from "hls.js";
import { hibiki } from "./hibiki";

/** The URL a request for `url` should actually go to under the playback `sessionId`. */
export function streamRequestUrl(sessionId: string, url: string): string {
  if (!hibiki.player.streamUrl || !/^https?:/i.test(url) || url.startsWith(`${location.origin}/`)) return url;
  return hibiki.player.streamUrl(sessionId, url);
}

export function usesStreamProxy(): boolean {
  return typeof hibiki.player.streamUrl === "function";
}

type LoaderClass = new (config: HlsConfig) => Loader<LoaderContext>;

/**
 * An hls.js loader that sends every request through the proxy but reports each response under its
 * upstream URL - the final one after redirects, which the proxy returns as X-Hibiki-Final-Url - so
 * hls.js keeps resolving relative playlist and segment URLs against the real stream, not the proxy.
 */
export function proxiedHlsLoader(Base: LoaderClass, sessionId: string): LoaderClass {
  return class ProxiedLoader extends (Base as new (config: HlsConfig) => Loader<LoaderContext> & {
    load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void;
  }) {
    load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void {
      const upstream = context.url;
      context.url = streamRequestUrl(sessionId, upstream);
      super.load(context, config, {
        ...callbacks,
        onSuccess: (response, stats, ctx, networkDetails) => {
          const request = networkDetails as XMLHttpRequest | undefined;
          response.url = request?.getResponseHeader?.("X-Hibiki-Final-Url") || upstream;
          ctx.url = upstream;
          callbacks.onSuccess(response, stats, ctx, networkDetails);
        },
      });
    }
  } as unknown as LoaderClass;
}
