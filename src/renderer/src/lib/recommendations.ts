// Why a title was recommended, for its own page. The home screen's recommendation query already holds
// the answer (see pages/home.tsx), so this only looks it up there: a title reached any other way, or
// before the home screen asked, simply has no reason to show.
import { useQueryClient } from "@tanstack/react-query";
import type { RecommendationReason, SourceRecommendations } from "@shared/types";

export function useRecommendationReason(sourceId: string, animeId: string): RecommendationReason | null {
  const queryClient = useQueryClient();
  for (const [, data] of queryClient.getQueriesData<SourceRecommendations>({ queryKey: ["recommendations", sourceId] })) {
    const found = [...(data?.picks ?? []), ...(data?.continuations ?? [])].find((item) => item.anime.id === animeId);
    if (found) return found.reason;
  }
  return null;
}
