import { useEffect, useMemo, useState } from "react";
import { isMobile } from "@/lib/mobile";
import { LibrarySegments } from "@/components/MobilePageHeader";
import { Link } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimatePresence } from "motion/react";
import { Download, Play, Trash2 } from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { Modal } from "@/components/Modal";
import type { DownloadedEpisode } from "@shared/types";

// Rendered persistently from __root.tsx instead - see index.tsx for why.
function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const mb = bytes / (1024 * 1024);
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  return `${(mb / 1024).toFixed(2)} GB`;
}

export function DownloadsPage() {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const episodesQuery = useQuery({ queryKey: ["downloadedEpisodes"], queryFn: () => hibiki.downloads.list() });

  // A download that finishes (or gets deleted) elsewhere - the anime detail page's context menu,
  // most likely - should show up here without needing a manual refresh; this screen only exists to
  // show the *result* of downloading, not to own that process itself.
  useEffect(() => {
    return hibiki.downloads.onProgress((progress) => {
      if (progress.status === "done" || progress.status === "cancelled") {
        queryClient.invalidateQueries({ queryKey: ["downloadedEpisodes"] });
      }
    });
  }, [queryClient]);

  const episodes = episodesQuery.data ?? [];
  const groups = useMemo(() => {
    const byAnime = new Map<string, { sourceId: string; animeId: string; animeTitle: string; animePosterUrl: string | null; episodes: DownloadedEpisode[] }>();
    for (const ep of episodes) {
      const key = `${ep.sourceId}:${ep.animeId}`;
      const group = byAnime.get(key);
      if (group) group.episodes.push(ep);
      else byAnime.set(key, { sourceId: ep.sourceId, animeId: ep.animeId, animeTitle: ep.animeTitle, animePosterUrl: ep.animePosterUrl, episodes: [ep] });
    }
    for (const group of byAnime.values()) group.episodes.sort((a, b) => a.episodeNumber - b.episodeNumber);
    return [...byAnime.values()].sort((a, b) => Math.max(...b.episodes.map((e) => e.downloadedAt)) - Math.max(...a.episodes.map((e) => e.downloadedAt)));
  }, [episodes]);

  // The trash button sits inside the row you click to play, and what it deletes has to be
  // downloaded all over again - so it asks first.
  const [pendingRemoval, setPendingRemoval] = useState<DownloadedEpisode | null>(null);
  const removeEpisode = async (ep: DownloadedEpisode) => {
    setPendingRemoval(null);
    await hibiki.downloads.remove(ep.sourceId, ep.animeId, ep.episodeId);
    queryClient.invalidateQueries({ queryKey: ["downloadedEpisodes"] });
  };

  return (
    <div className="min-h-full bg-app-bg px-8 py-8 pb-16 mobile:px-4 mobile:pb-6 mobile:pt-4">
      {isMobile ? <LibrarySegments active="/downloads" /> : <h1 className="mb-6 text-xl font-bold tracking-[-.02em] text-text">{t("downloads.title")}</h1>}
      {episodesQuery.isLoading ? null : groups.length === 0 ? (
        <div className="flex min-h-[calc(100vh-220px)] items-center justify-center">
          <div className="max-w-sm text-center">
            <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-text/[.06]">
              <Download className="h-6 w-6 text-muted" strokeWidth={1.75} />
            </div>
            <p className="text-sm leading-6 text-muted">{t("downloads.empty")}</p>
          </div>
        </div>
      ) : (
        <div className="space-y-8">
          {groups.map((group) => (
            <AnimeGroup key={`${group.sourceId}:${group.animeId}`} group={group} locale={i18n.language} onRemove={setPendingRemoval} />
          ))}
        </div>
      )}
      <AnimatePresence>
        {pendingRemoval && (
          <Modal onDismiss={() => setPendingRemoval(null)}>
            <h2 className="text-base font-bold text-text">{t("downloads.removeConfirmTitle")}</h2>
            <p className="mt-2 select-text text-sm leading-relaxed text-muted">
              {t("downloads.removeConfirmMessage", { episode: pendingRemoval.episodeNumber, title: pendingRemoval.animeTitle })}
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button onClick={() => setPendingRemoval(null)} className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]">{t("common.cancel")}</button>
              <button onClick={() => removeEpisode(pendingRemoval)} className="rounded-lg bg-rose-500 px-3.5 py-2 text-sm font-bold text-text transition-opacity hover:opacity-90">{t("downloads.remove")}</button>
            </div>
          </Modal>
        )}
      </AnimatePresence>
    </div>
  );
}

function AnimeGroup({
  group,
  locale,
  onRemove,
}: {
  group: { sourceId: string; animeId: string; animeTitle: string; animePosterUrl: string | null; episodes: DownloadedEpisode[] };
  locale: string;
  onRemove: (ep: DownloadedEpisode) => void;
}) {
  const { t } = useTranslation();
  const totalBytes = group.episodes.reduce((sum, ep) => sum + ep.fileSizeBytes, 0);
  return (
    <section>
      <Link to="/anime/$sourceId/$animeId" params={{ sourceId: group.sourceId, animeId: group.animeId }} className="group mb-3 flex items-center gap-3">
        <div className="h-14 w-10 shrink-0 overflow-hidden rounded-lg bg-surface ring-1 ring-border">
          {group.animePosterUrl && <img src={group.animePosterUrl} alt="" className="h-full w-full object-cover" />}
        </div>
        <div className="min-w-0">
          <p className="truncate text-base font-semibold text-text group-hover:text-accent-text">{group.animeTitle}</p>
          <p className="text-xs text-muted">{t("downloads.episodeCount", { count: group.episodes.length })} · {formatBytes(totalBytes)}</p>
        </div>
      </Link>
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {group.episodes.map((ep) => (
          <EpisodeRow key={ep.episodeId} episode={ep} locale={locale} onRemove={() => onRemove(ep)} />
        ))}
      </div>
    </section>
  );
}

function EpisodeRow({ episode, locale, onRemove }: { episode: DownloadedEpisode; locale: string; onRemove: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="group relative flex items-center gap-3 overflow-hidden rounded-xl border border-border bg-text/[.03] px-3 py-2.5 transition-colors hover:border-accent/40 hover:bg-text/[.06]">
      <Link
        to="/watch/$sourceId/$animeId/$groupId/$episodeId"
        params={{ sourceId: episode.sourceId, animeId: episode.animeId, groupId: episode.groupId, episodeId: episode.episodeId }}
        className="flex min-w-0 flex-1 items-center gap-3"
      >
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-text/[.06] text-xs font-bold text-muted group-hover:bg-accent/20 group-hover:text-accent-text">
          {episode.episodeNumber}
        </span>
        <span className="min-w-0 flex-1">
          <span className="line-clamp-1 select-text text-sm text-text/80 group-hover:text-text">{episode.episodeLabel}</span>
          <span className="block text-[11px] text-muted">{formatBytes(episode.fileSizeBytes)} · {new Date(episode.downloadedAt).toLocaleDateString(locale, { day: "numeric", month: "short" })}</span>
        </span>
        <Play className="h-3.5 w-3.5 shrink-0 fill-current text-accent-text opacity-0 transition-opacity group-hover:opacity-100" strokeWidth={0} />
      </Link>
      <button
        type="button"
        onClick={onRemove}
        aria-label={t("downloads.remove")}
        title={t("downloads.remove")}
        className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-muted opacity-0 transition-colors hover:bg-rose-400/15 hover:text-rose-600 dark:hover:text-rose-300 group-hover:opacity-100"
      >
        <Trash2 className="h-3.5 w-3.5" strokeWidth={2} />
      </button>
    </div>
  );
}
