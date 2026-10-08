import type { HibikiApi } from "@shared/hibikiApi";
import { IPC } from "@shared/ipc";
import type { InstalledVersions, MarketplaceExtension, SourceInfo } from "@shared/types";
import { fetchExtensionFiles, fetchRepositoryIndex } from "../marketplace";
import { isApkSource, isRetiredResolver, type ExtensionRuntime } from "../extensions/runtime";
import { logger } from "../logger";
import { getPlatform } from "../platform";

// A source's resolverDependencies (e.g. YummyAnime needs "kodik", "sibnet", ...) are hidden
// dependencies, not something the user installs themselves - mirrors the Android app, which
// installs them silently alongside the source and never lists them in its source picker (see
// ExtensionRuntime's separate resolversDir). Best-effort: a resolver failing to fetch/install
// just means its EMBED links fall back to the existing iframe player instead of a direct stream,
// not a broken install.
async function installResolverDependencies(dependencyIds: string[], originUrl: string, runtime: ExtensionRuntime): Promise<void> {
  if (dependencyIds.length === 0) return;
  let index: MarketplaceExtension[];
  try {
    index = await fetchRepositoryIndex(originUrl);
  } catch {
    return;
  }
  const resolversById = new Map(index.filter((e) => e.type === "player-resolver").map((e) => [e.id, e]));
  for (const id of dependencyIds) {
    if (isRetiredResolver(id)) continue;
    const resolverExtension = resolversById.get(id);
    if (!resolverExtension) continue;
    try {
      const { manifestJson, jsPayload } = await fetchExtensionFiles(resolverExtension);
      await runtime.installResolver(id, manifestJson, jsPayload);
      logger.info("sources", `resolver ${id} ${resolverExtension.version} installed`);
    } catch (error) {
      logger.warn("sources", `resolver ${id} could not be installed: ${errorText(error)}`);
    }
  }
}

/** Retry resolver downloads that previously failed during a best-effort source install.
 *
 * Source and resolver files are intentionally separate, so an interrupted resolver download can
 * leave a usable source installed but make every EMBED link fall through to the iframe. Android
 * refreshes downloaded resolvers as its registry changes; Desktop used to make only the original
 * install attempt and then preserve the broken state forever. Normal upgrades remain user-driven.
 */
export async function repairMissingResolverDependencies(runtime: ExtensionRuntime): Promise<boolean> {
  const installed = runtime.installedResolverVersions();
  const requirementsByOrigin = new Map<string, Set<string>>();

  for (const requirement of runtime.installedResolverRequirements()) {
    const resolverIds = requirementsByOrigin.get(requirement.originUrl) ?? new Set<string>();
    for (const id of requirement.resolverIds) {
      if (isRetiredResolver(id)) continue;
      if (installed[id] === undefined) resolverIds.add(id);
    }
    if (resolverIds.size > 0) requirementsByOrigin.set(requirement.originUrl, resolverIds);
  }

  let changed = false;
  for (const [originUrl, resolverIds] of requirementsByOrigin) {
    let index: MarketplaceExtension[];
    try {
      index = await fetchRepositoryIndex(originUrl);
    } catch (error) {
      logger.warn("resolvers", `startup repair could not fetch repository: ${error instanceof Error ? error.message : String(error)}`);
      continue;
    }

    const resolversById = new Map(index.filter((entry) => entry.type === "player-resolver").map((entry) => [entry.id, entry]));
    for (const id of resolverIds) {
      const extension = resolversById.get(id);
      if (!extension) {
        logger.warn("resolvers", `startup repair could not find dependency ${id}`);
        continue;
      }
      try {
        const { manifestJson, jsPayload } = await fetchExtensionFiles(extension);
        await runtime.installResolver(id, manifestJson, jsPayload);
        installed[id] = extension.version;
        changed = true;
        logger.info("resolvers", `restored missing dependency ${id}`);
      } catch (error) {
        logger.warn("resolvers", `startup repair failed for ${id}: ${error instanceof Error ? error.message : String(error)}`);
      }
    }
  }
  return changed;
}

/** Tells the renderer its picture of what's installed is out of date. Sent after every mutation
 * rather than left to each caller, since forgetting one is invisible until someone notices a
 * screen showing an update that has already been applied. */
function notifyChanged(): void {
  getPlatform().events.emit(IPC.sourcesChanged);
}

const errorText = (error: unknown) => (error instanceof Error ? error.message : String(error));

/** Installing, updating and removing sources: the parts of `window.hibiki.sources` that change
 * what is installed. */
export function createExtensionsApi(runtime: ExtensionRuntime): Pick<HibikiApi["sources"], "install" | "uninstall" | "installedVersions"> {
  return {
    async install(extension: MarketplaceExtension, originUrl: string): Promise<SourceInfo[]> {
      const previous = runtime.installedVersions().get(extension.id);
      const what = `${extension.id} ${previous ? `${previous} -> ` : ""}${extension.version}${extension.apkPackage ? " (APK)" : ""}`;
      try {
        const sources = await installExtension(extension, originUrl);
        logger.info("sources", `${previous ? "updated" : "installed"} ${what}`);
        return sources;
      } catch (error) {
        logger.warn("sources", `could not ${previous ? "update" : "install"} ${what}: ${errorText(error)}`);
        throw error;
      }
    },

    // Both halves read from the same runtime state in the same tick, so they cannot disagree.
    installedVersions: async (): Promise<InstalledVersions> => ({
      sources: Object.fromEntries(runtime.installedVersions()),
      resolvers: runtime.installedResolverVersions(),
    }),

    async uninstall(id: string): Promise<SourceInfo[]> {
      const sources = await uninstallExtension(id);
      logger.info("sources", `removed ${id}`);
      return sources;
    },
  };

  async function installExtension(extension: MarketplaceExtension, originUrl: string): Promise<SourceInfo[]> {
    if (extension.apkPackage) {
      const port = getPlatform().apkSources;
      if (!port) throw new Error("APK sources are only supported on Android");
      await port.install(extension.manifestUrl, extension.apkPackage);
      await runtime.reload();
      notifyChanged();
      return runtime.list();
    }
    const { manifestJson, jsPayload } = await fetchExtensionFiles(extension);
    await runtime.install(extension.id, manifestJson, jsPayload, originUrl);
    await installResolverDependencies(extension.resolverDependencies, originUrl, runtime);
    notifyChanged();
    return runtime.list();
  }

  async function uninstallExtension(id: string): Promise<SourceInfo[]> {
    const apkPackage = isApkSource(id) ? runtime.apkPackageOf(id) : null;
    if (apkPackage) {
      // The whole package goes: its other sources (a mirror, say) were installed with it.
      await getPlatform().apkSources?.uninstall(apkPackage);
      await runtime.reload();
      notifyChanged();
      return runtime.list();
    }
    await runtime.uninstall(id);
    notifyChanged();
    return runtime.list();
  }
}
