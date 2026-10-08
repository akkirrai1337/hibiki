// Every part of `window.hibiki` that is the same on all platforms, assembled from core/api/*. A
// host adds what only it can do (window controls, zoom, Discord, updates, logs on disk) and the
// listener halves of sources.onChanged/downloads.onProgress, which ride on its EventsPort.
import type { HibikiApi } from "@shared/hibikiApi";
import type { ExtensionRuntime } from "../extensions/runtime";
import { createDownloadsApi } from "./downloads";
import { createExtensionsApi } from "./extensions";
import { createLibraryApi } from "./library";
import { createProfileApi } from "./profile";
import { createRepositoriesApi } from "./repositories";
import { findMissingSources, sourcesInData } from "./sourceAutoInstall";
import { createSourcesApi } from "./sources";
import { createTrackingApi, type TrackingApi } from "./tracking";
import { createXpApi } from "./xp";

export interface CoreApi extends Pick<HibikiApi, "ratings" | "library" | "progress" | "xp" | "profile"> {
  sources: Omit<HibikiApi["sources"], "onChanged">;
  downloads: Omit<HibikiApi["downloads"], "onProgress">;
  tracking: TrackingApi;
}

export function createCoreApi(runtime: ExtensionRuntime): CoreApi {
  const { repositories, marketplace } = createRepositoriesApi();
  return {
    sources: {
      ...createSourcesApi(runtime),
      repositories,
      marketplace,
      ...createExtensionsApi(runtime),
      // Offered, not installed: any configured repository may be the one, the person picks.
      missingSources: async () => findMissingSources(runtime, await sourcesInData(), { apkFromDefaultOnly: false }),
    },
    ...createLibraryApi(runtime),
    xp: createXpApi(),
    profile: createProfileApi(),
    downloads: createDownloadsApi(runtime),
    tracking: createTrackingApi(runtime),
  };
}
