import { useQuery } from "@tanstack/react-query";
import { hibiki } from "@/lib/hibiki";

/**
 * Paints an episode list from SQLite immediately when one is available, while the live source
 * request runs behind it and replaces the list as soon as it answers. The public query key stays
 * the same as before, so detail and watch screens still share one result.
 *
 * The cache is a placeholder, never a substitute for the request: a newly released episode has to
 * show up on the screen the user is looking at, not fifteen minutes later.
 */
export function usePlaybackGroups(sourceId: string, animeId: string, staleTime?: number, enabled = true) {
  const cached = useQuery({
    queryKey: ["cached-playbackGroups", sourceId, animeId],
    queryFn: () => hibiki.sources.cachedPlaybackGroups(sourceId, animeId),
    staleTime: Infinity,
    // A player can be opened straight from "Continue watching", without first mounting the
    // detail page. Reading SQLite is local and gives it the current episode plus its neighbors
    // immediately; `enabled` below still gates the expensive live source request.
    enabled: true,
  });

  return useQuery({
    queryKey: ["playbackGroups", sourceId, animeId],
    placeholderData: cached.data?.groups,
    staleTime,
    enabled,
    queryFn: () => hibiki.sources.playbackGroups(sourceId, animeId),
  });
}
