import { useMemo } from "react";
import { isMobile } from "@/lib/mobile";
import { LibrarySegments } from "@/components/MobilePageHeader";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { History as HistoryIcon } from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { useWatchHistory } from "@/lib/continueWatching";
import { WatchFrameCard } from "@/components/WatchFrameCard";

// Rendered persistently from __root.tsx instead - see index.tsx for why.
// Deliberately generous - every episode with any saved progress at all, not just what fits the
// home page's "continue watching" row (12) - this page exists specifically to see everything.
const HISTORY_FETCH_LIMIT = 200;

function relativeDate(epochMs: number, t: TFunction, locale: string): string {
  const days = Math.floor((Date.now() - epochMs) / 86_400_000);
  if (days <= 0) return t("profile.today");
  if (days === 1) return t("profile.yesterday");
  if (days < 7) return t("profile.daysAgo", { count: days });
  return new Date(epochMs).toLocaleDateString(locale, { day: "numeric", month: "short" });
}

export function HistoryPage() {
  const { t, i18n } = useTranslation();
  const { entries, loading } = useWatchHistory(HISTORY_FETCH_LIMIT);
  // Same ["sources"] query the home page uses for its own source badges - a cache hit here, and
  // the map shape WatchFrameCard expects.
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const sourceById = useMemo(() => new Map((sources.data ?? []).map((s) => [s.id, s])), [sources.data]);

  return (
    <div className="min-h-full bg-app-bg px-8 py-8 pb-16 mobile:px-4 mobile:pb-6 mobile:pt-4">
      {isMobile ? <LibrarySegments active="/history" /> : <h1 className="mb-6 text-xl font-bold tracking-[-.02em] text-text">{t("history.title")}</h1>}
      {loading ? null : entries.length === 0 ? (
        <div className="flex min-h-[calc(100vh-220px)] items-center justify-center">
          <div className="max-w-sm text-center">
            <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-text/[.06]">
              <HistoryIcon className="h-6 w-6 text-muted" strokeWidth={1.75} />
            </div>
            <p className="text-sm leading-6 text-muted">{t("history.empty")}</p>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-4 xl:grid-cols-4 mobile:grid-cols-2 mobile:gap-3">
          {entries.map((entry) => {
            const { sourceId, titleId, episodeId } = entry.progress;
            return (
              <WatchFrameCard
                key={`${sourceId}:${titleId}:${episodeId}`}
                slot={entry}
                sourceById={sourceById}
                dateLabel={relativeDate(entry.progress.updatedAt, t, i18n.language)}
                showWatchedBadge
                showUnresolved
                // One episode's entry, not the title's whole progress: this page is a per-episode
                // feed, so deleting a row here shouldn't wipe every other episode of that title.
                remove={{ label: t("history.deleteEntry"), run: () => hibiki.progress.removeEpisode(sourceId, titleId, episodeId) }}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
