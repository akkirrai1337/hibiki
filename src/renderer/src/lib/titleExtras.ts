// The part of a title that arrives after the rest. The title page asks the source for the title
// alone (getById with `extras: false`) and draws it; when the answer says its extras are still to come
// (`extrasPending`), they are fetched here, from a request of their own, and put into the same query
// the page reads - so the page, and the watch page sharing that query, see them as soon as they are
// in, and a slow request for them never holds the title back. They only decorate: a failure is quiet.
import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { AnimeTitle } from "@shared/types";
import { hibiki } from "@/lib/hibiki";

export function useTitleExtras(sourceId: string, animeId: string, title: AnimeTitle | undefined): void {
  const queryClient = useQueryClient();
  const pending = title?.extrasPending === true;
  useEffect(() => {
    if (!pending) return;
    let cancelled = false;
    void hibiki.sources.titleExtras(sourceId, animeId).then(
      (extras) => {
        if (cancelled) return;
        queryClient.setQueryData<AnimeTitle>(["anime", sourceId, animeId], (current) => {
          if (!current || current.extrasPending !== true) return current;
          // Pending no more even when the source had nothing to add - or it would be asked again.
          return { ...current, ...(extras ?? {}), extrasPending: false };
        });
      },
      () => undefined,
    );
    return () => {
      cancelled = true;
    };
  }, [pending, sourceId, animeId, queryClient]);
}
