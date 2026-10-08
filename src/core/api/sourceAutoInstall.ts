// Sources this device lacks for titles that came here from elsewhere - the previous Android app's
// library (legacyImport.ts) or another device (device sync). Without them those titles cannot open.
//
// The carry-over installs them on its own (installMissingSources). Sync only offers them
// (findMissingSources, shown on the Sync screen): what another device uses is not necessarily wanted
// here, and installing it is the person's call.
import { library, titleRatings, watchProgress } from "../db/schema";
import type { ExtensionRuntime } from "../extensions/runtime";
import { logger } from "../logger";
import { DEFAULT_APK_REPOSITORY_URL } from "../marketplace";
import { getPlatform } from "../platform";
import type { MissingSources } from "@shared/types";
import { createExtensionsApi } from "./extensions";
import { createRepositoriesApi } from "./repositories";

/**
 * Where each of `sourceIds` not installed here can be installed from, by the configured repositories.
 * An APK source is looked for only where APK sources run, and with `apkFromDefaultOnly` only in the
 * preinstalled Aniyomi repository. One entry per APK package, which installs all of its sources.
 */
export async function findMissingSources(runtime: ExtensionRuntime, sourceIds: Iterable<string>, options: { apkFromDefaultOnly: boolean }): Promise<MissingSources> {
  const installed = new Set(runtime.list().map((source) => source.id));
  const runsApk = !!getPlatform().apkSources;
  const missing = [...new Set(sourceIds)].filter((id) => !installed.has(id) && (runsApk || !id.startsWith("apk:")));
  if (missing.length === 0) return { available: [], unavailable: [] };
  const { repositories, marketplace } = createRepositoriesApi();
  const results = await marketplace(await repositories.list());
  const available: MissingSources["available"] = [];
  const unavailable: string[] = [];
  const packages = new Set<string>();
  for (const id of missing) {
    const found = results
      .flatMap((result) => (result.ok ? result.extensions.filter((extension) => extension.id === id).map((extension) => ({ extension, repositoryUrl: result.url })) : []))
      .find(({ extension, repositoryUrl }) => !extension.apkPackage || !options.apkFromDefaultOnly || repositoryUrl === DEFAULT_APK_REPOSITORY_URL);
    if (!found) {
      unavailable.push(id);
      continue;
    }
    if (found.extension.apkPackage) {
      if (packages.has(found.extension.apkPackage)) continue;
      packages.add(found.extension.apkPackage);
    }
    available.push(found);
  }
  return { available, unavailable };
}

/** Every source the library, history and ratings here refer to. */
export async function sourcesInData(): Promise<string[]> {
  const db = getPlatform().db.get();
  const ids = new Set<string>();
  for (const row of await db.selectDistinct({ id: library.sourceId }).from(library).all()) ids.add(row.id);
  for (const row of await db.selectDistinct({ id: watchProgress.sourceId }).from(watchProgress).all()) ids.add(row.id);
  for (const row of await db.selectDistinct({ id: titleRatings.sourceId }).from(titleRatings).all()) ids.add(row.id);
  return [...ids];
}

/** Installs what findMissingSources finds for these ids, Aniyomi ones only from the preinstalled repository. */
export async function installMissingSources(runtime: ExtensionRuntime, sourceIds: Iterable<string>, reason: string): Promise<void> {
  const { available, unavailable } = await findMissingSources(runtime, sourceIds, { apkFromDefaultOnly: true });
  for (const id of unavailable) logger.warn("sources", `${id} (needed for ${reason}) is in no repository it may come from`);
  const { install } = createExtensionsApi(runtime);
  for (const { extension, repositoryUrl } of available) {
    try {
      await install(extension, repositoryUrl);
      logger.info("sources", `installed ${extension.id} for ${reason}`);
    } catch (error) {
      logger.warn("sources", `could not install ${extension.id} for ${reason}: ${error instanceof Error ? error.message : String(error)}`);
    }
  }
}
