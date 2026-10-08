// Calls one Provider function the way the host contract defines it: which arguments are JSON
// strings, which results get tagged with their source id, and which functions are optional. Shared
// by every platform's extension worker, so a script answers the same way on each of them.
import type { ExtensionMethod } from "./methods";

function tagSource<T extends { id: string }>(sourceId: string, item: T): T & { sourceId: string } {
  return { ...item, sourceId };
}

export function invokeProvider(provider: Record<string, unknown>, sourceId: string, method: ExtensionMethod, args: unknown[]): unknown {
  switch (method) {
    case "search": {
      const fn = provider.search as (json: string) => Array<{ id: string }>;
      const results = fn.call(provider, JSON.stringify(args[0]));
      return results.map((r) => tagSource(sourceId, r));
    }
    case "latest": {
      const fn = provider.latest as (limit: number) => Array<{ id: string }>;
      const results = fn.call(provider, args[0] as number);
      return results.map((r) => tagSource(sourceId, r));
    }
    case "getById": {
      const fn = provider.getById as (id: string) => { id: string };
      return tagSource(sourceId, fn.call(provider, args[0] as string));
    }
    case "getPlaybackGroups": {
      const fn = provider.getPlaybackGroups as (titleId: string) => unknown[];
      return fn.call(provider, args[0] as string) ?? [];
    }
    case "getPlayerLinks": {
      const fn = provider.getPlayerLinks as (titleId: string, groupId: string, episodeId: string) => unknown[];
      return (
        fn.call(provider, args[0] as string, args[1] as string, args[2] as string) ?? []
      );
    }
    case "getSettings": {
      const fn = provider.getSettings as (() => unknown) | undefined;
      return fn ? (fn.call(provider) ?? {}) : {};
    }
    case "resolve": {
      // Player resolvers (extensions/extractors/*.js in hibiki-sources) expose Provider.resolve
      // instead of the catalog methods above - they turn one EMBED PlayerLink (a third-party
      // player page) into real DIRECT_HLS/DIRECT_MP4 candidates, so their result isn't tagged
      // with a source id the way search/getById results are.
      const fn = provider.resolve as (json: string) => unknown[];
      return fn.call(provider, args[0] as string) ?? [];
    }
    // One shape for all of these: the argument, if any, is a JSON string, and so is the answer's
    // payload - the same convention search/resolve already use, and the one Rhino needs, since it
    // cannot hand a real object across the boundary either.
    case "login":
    case "loginWeb":
    case "logout":
    case "getAccount":
    case "listComments":
    case "postComment":
    case "voteComment":
    case "listReviews":
    case "postReview":
    case "syncLibraryEntry":
    case "listLibrary":
    case "reportPlayback":
    case "pingOnline": {
      const fn = provider[method] as ((json?: string) => unknown) | undefined;
      if (typeof fn !== "function") {
        throw new Error(`Source "${sourceId}" declares ${method} but does not implement it`);
      }
      const argument = args.length > 0 ? JSON.stringify(args[0]) : undefined;
      return fn.call(provider, argument) ?? null;
    }
    case "browserScript": {
      // BROWSER-runtime resolvers (extractors/alloha.js and similar) expose this instead of
      // resolve() - it's a plain string of JS meant to run *inside* the embed page's own browser
      // context (see browserResolveHost.ts), not something this sandbox executes itself.
      const fn = provider.browserScript as (json: string) => string;
      return fn.call(provider, args[0] as string);
    }
    default:
      throw new Error(`Unknown extension method: ${method satisfies never}`);
  }
}
