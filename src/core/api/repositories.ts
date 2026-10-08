import { eq } from "drizzle-orm";
import type { HibikiApi } from "@shared/hibikiApi";
import type { RepositoryFetchResult } from "@shared/types";
import { sourceRepositories } from "../db/schema";
import { DEFAULT_APK_REPOSITORY_URL, DEFAULT_REPOSITORY_URL, fetchRepositoryResult, resolveRepositoryUrl } from "../marketplace";
import { logger } from "../logger";
import { getPlatform } from "../platform";

const getDb = () => getPlatform().db.get();

// Where each one-time seeding is remembered, so removing a preinstalled repository sticks.
const DEFAULT_REPOSITORY_SEEDED = "default-repository-seeded";
const APK_REPOSITORY_SEEDED = "apk-repository-seeded";

/** Adds a preinstalled repository once per install. Each has its own marker: hibiki-sources used to
 * be added only to an empty list, which on Android never was one - the APK repository got there
 * first - so the app's own sources were missing there. */
async function seedOnce(url: string, markerName: string): Promise<void> {
  const { files, paths } = getPlatform();
  const marker = files.join(paths.userData, markerName);
  if (await files.exists(marker)) return;
  await getDb().insert(sourceRepositories).values({ url, addedAt: Date.now() }).onConflictDoNothing().run();
  await files.writeText(marker, "1");
}

async function listRepositoryUrls(): Promise<string[]> {
  // hibiki-sources first, so on a fresh install it heads the list (the first repository to list an
  // id owns it); the Aniyomi APK repository only where APK sources run.
  await seedOnce(DEFAULT_REPOSITORY_URL, DEFAULT_REPOSITORY_SEEDED);
  if (getPlatform().apkSources) await seedOnce(DEFAULT_APK_REPOSITORY_URL, APK_REPOSITORY_SEEDED);
  const rows = await getDb().select().from(sourceRepositories).all();
  return rows.map((r) => r.url);
}

/** The repository list and marketplace browsing parts of `window.hibiki.sources`. */
export function createRepositoriesApi(): Pick<HibikiApi["sources"], "repositories" | "marketplace"> {
  return {
    repositories: {
      list: (): Promise<string[]> => listRepositoryUrls(),

      // Anything that points at a repository - its raw index, the index's page on GitHub, or the
      // repository itself - is saved as the raw index URL it resolves to, checked to be an index.
      async add(input: string): Promise<string[]> {
        let url: string;
        try {
          url = await resolveRepositoryUrl(input);
        } catch (error) {
          logger.warn("sources", `repository not added, ${input}: ${error instanceof Error ? error.message : String(error)}`);
          throw error;
        }
        await getDb().insert(sourceRepositories).values({ url, addedAt: Date.now() }).onConflictDoNothing().run();
        logger.info("sources", `repository added: ${url}`);
        return listRepositoryUrls();
      },

      async remove(url: string): Promise<string[]> {
        await getDb().delete(sourceRepositories).where(eq(sourceRepositories.url, url)).run();
        logger.info("sources", `repository removed: ${url}`);
        return listRepositoryUrls();
      },
    },

    marketplace: async (urls: string[]): Promise<RepositoryFetchResult[]> => {
      const results = await Promise.all(urls.map(fetchRepositoryResult));
      for (const result of results) {
        if (!result.ok) logger.warn("sources", `repository unavailable, ${result.url}: ${result.error ?? "?"}`);
      }
      return results;
    },
  };
}
