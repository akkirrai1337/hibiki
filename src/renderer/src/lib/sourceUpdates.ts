import { useEffect, useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { MarketplaceExtension } from "@shared/types";
import { hibiki } from "@/lib/hibiki";
import { isExtensionUpdateAvailable } from "@/lib/version";

/**
 * How many installed sources have an update waiting - across every repository, with no search or
 * language filter applied. The Sources screen counts the same thing for its own "updates
 * available" section, but only over whatever the search box currently shows; the sidebar badge
 * (mirroring the Android bottom-nav one) needs the true total regardless of whether that screen has
 * even been opened this session, so it keeps its own unfiltered query rather than reading the
 * page's.
 */
export function useSourceUpdateCount(): number {
  const queryClient = useQueryClient();

  const repositories = useQuery({ queryKey: ["repositories"], queryFn: () => hibiki.sources.repositories.list() });
  const marketplace = useQuery({
    queryKey: ["marketplace", repositories.data],
    enabled: !!repositories.data,
    queryFn: () => hibiki.sources.marketplace(repositories.data!),
  });
  const installed = useQuery({ queryKey: ["installedVersions"], queryFn: () => hibiki.sources.installedVersions() });

  // Same invalidation the Sources screen does on its own copy of these queries - an install,
  // uninstall or update happening there (or anywhere else) has to reach this badge too, not just
  // whichever screen triggered it.
  useEffect(() => hibiki.sources.onChanged(() => {
    void queryClient.invalidateQueries({ queryKey: ["installedVersions"] });
  }), [queryClient]);

  const mergedExtensions = useMemo(() => {
    const seen = new Set<string>();
    const merged: MarketplaceExtension[] = [];
    for (const url of repositories.data ?? []) {
      const result = (marketplace.data ?? []).find((r) => r.url === url);
      if (!result?.ok) continue;
      for (const extension of result.extensions) {
        if (seen.has(extension.id)) continue;
        seen.add(extension.id);
        merged.push(extension);
      }
    }
    return merged;
  }, [repositories.data, marketplace.data]);

  const installedVersions = useMemo(
    () => new Map(Object.entries(installed.data?.sources ?? {})),
    [installed.data],
  );

  return useMemo(
    () =>
      mergedExtensions
        .filter((extension) => extension.type === "source" && installedVersions.has(extension.id))
        .filter((extension) =>
          isExtensionUpdateAvailable(extension, installedVersions, installed.data?.resolvers ?? {}, mergedExtensions),
        ).length,
    [mergedExtensions, installedVersions, installed.data],
  );
}
