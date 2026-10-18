import { useEffect, useMemo, useState } from "react";
import { isMobile } from "@/lib/mobile";
import { LibrarySegments } from "@/components/MobilePageHeader";
import { Link } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { Check, ChevronRight, Clapperboard, Clock, Download, HardDrive, Play, Trash2 } from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { cn } from "@/lib/cn";
import { Modal } from "@/components/Modal";
import { PosterImage } from "@/components/PosterImage";
import type { DownloadedEpisode, WatchProgress } from "@shared/types";

// Rendered persistently from __root.tsx instead - see index.tsx for why.
function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const mb = bytes / (1024 * 1024);
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  return `${(mb / 1024).toFixed(2)} GB`;
}

interface TitleGroup {
  sourceId: string;
  animeId: string;
  animeTitle: string;
  animePosterUrl: string | null;
  episodes: DownloadedEpisode[];
}

/** What a confirmation is about: one episode, or every downloaded episode of a title. */
type PendingRemoval = { kind: "episode"; episode: DownloadedEpisode } | { kind: "title"; group: TitleGroup };

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
    const byAnime = new Map<string, TitleGroup>();
    for (const ep of episodes) {
      const key = `${ep.sourceId}:${ep.animeId}`;
      const group = byAnime.get(key);
      if (group) group.episodes.push(ep);
      else byAnime.set(key, { sourceId: ep.sourceId, animeId: ep.animeId, animeTitle: ep.animeTitle, animePosterUrl: ep.animePosterUrl, episodes: [ep] });
    }
    for (const group of byAnime.values()) group.episodes.sort((a, b) => a.episodeNumber - b.episodeNumber);
    return [...byAnime.values()].sort((a, b) => Math.max(...b.episodes.map((e) => e.downloadedAt)) - Math.max(...a.episodes.map((e) => e.downloadedAt)));
  }, [episodes]);

  // What it deletes has to be downloaded all over again - so it asks first.
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null);
  const confirmRemoval = async (pending: PendingRemoval) => {
    setPendingRemoval(null);
    const targets = pending.kind === "episode" ? [pending.episode] : pending.group.episodes;
    for (const ep of targets) await hibiki.downloads.remove(ep.sourceId, ep.animeId, ep.episodeId);
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
        <div className="grid items-start gap-4 mobile:gap-3 lg:grid-cols-2">
          {groups.map((group) => (
            <TitleCard
              key={`${group.sourceId}:${group.animeId}`}
              group={group}
              locale={i18n.language}
              onRemoveEpisode={(episode) => setPendingRemoval({ kind: "episode", episode })}
              onRemoveTitle={() => setPendingRemoval({ kind: "title", group })}
            />
          ))}
        </div>
      )}
      <AnimatePresence>
        {pendingRemoval && (
          <Modal onDismiss={() => setPendingRemoval(null)}>
            <h2 className="text-base font-bold text-text">{t(pendingRemoval.kind === "episode" ? "downloads.removeConfirmTitle" : "downloads.removeAllConfirmTitle")}</h2>
            <p className="mt-2 select-text text-sm leading-relaxed text-muted">
              {pendingRemoval.kind === "episode"
                ? t("downloads.removeConfirmMessage", { episode: pendingRemoval.episode.episodeNumber, title: pendingRemoval.episode.animeTitle })
                : t("downloads.removeAllConfirmMessage", {
                    title: pendingRemoval.group.animeTitle,
                    episodes: t("downloads.episodes", { count: pendingRemoval.group.episodes.length }),
                    size: formatBytes(pendingRemoval.group.episodes.reduce((sum, ep) => sum + ep.fileSizeBytes, 0)),
                  })}
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button onClick={() => setPendingRemoval(null)} className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]">{t("common.cancel")}</button>
              <button onClick={() => void confirmRemoval(pendingRemoval)} className="rounded-lg bg-rose-500 px-3.5 py-2 text-sm font-bold text-white transition-opacity hover:opacity-90">{t("downloads.remove")}</button>
            </div>
          </Modal>
        )}
      </AnimatePresence>
    </div>
  );
}

function TitleCard({ group, locale, onRemoveEpisode, onRemoveTitle }: {
  group: TitleGroup;
  locale: string;
  onRemoveEpisode: (episode: DownloadedEpisode) => void;
  onRemoveTitle: () => void;
}) {
  const { t } = useTranslation();
  const totalBytes = group.episodes.reduce((sum, ep) => sum + ep.fileSizeBytes, 0);
  // Where each downloaded episode was left - the same query the title page keeps.
  const progressQuery = useQuery({
    queryKey: ["progress-all", group.sourceId, group.animeId],
    queryFn: () => hibiki.progress.listForAnime(group.sourceId, group.animeId),
  });
  const progressByEpisode = useMemo(() => new Map((progressQuery.data ?? []).map((p) => [p.episodeId, p])), [progressQuery.data]);

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-text/[.03]">
      <div className="flex items-center gap-3.5 p-3.5 pb-3">
        <Link to="/anime/$sourceId/$animeId" params={{ sourceId: group.sourceId, animeId: group.animeId }} className="group flex min-w-0 flex-1 items-center gap-3.5">
          <span className="h-[72px] w-[50px] shrink-0 overflow-hidden rounded-lg bg-text/[.06] ring-1 ring-border">
            {group.animePosterUrl && <PosterImage lazy src={group.animePosterUrl} alt="" className="h-full w-full object-cover" />}
          </span>
          <span className="min-w-0 flex-1">
            <span className="line-clamp-2 text-[15px] font-bold leading-snug text-text transition-colors group-hover:text-accent-text">{group.animeTitle}</span>
            <span className="mt-1.5 flex items-center gap-3 text-[12px] tabular-nums text-muted">
              <span className="flex items-center gap-1"><Clapperboard className="h-3.5 w-3.5" strokeWidth={2} />{t("downloads.episodes", { count: group.episodes.length })}</span>
              <span className="flex items-center gap-1"><HardDrive className="h-3.5 w-3.5" strokeWidth={2} />{formatBytes(totalBytes)}</span>
            </span>
          </span>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted transition-transform group-hover:translate-x-0.5" strokeWidth={2.2} />
        </Link>
        <IconButton label={t("downloads.removeAll")} onClick={onRemoveTitle} danger>
          <Trash2 className="h-4 w-4" strokeWidth={2} />
        </IconButton>
      </div>
      <div className="divide-y divide-border/60 border-t border-border/60">
        <AnimatePresence initial={false}>
          {group.episodes.map((ep) => (
            <EpisodeRow key={ep.episodeId} episode={ep} progress={progressByEpisode.get(ep.episodeId)} locale={locale} onRemove={() => onRemoveEpisode(ep)} />
          ))}
        </AnimatePresence>
      </div>
    </section>
  );
}

function EpisodeRow({ episode, progress, locale, onRemove }: { episode: DownloadedEpisode; progress?: WatchProgress; locale: string; onRemove: () => void }) {
  const { t } = useTranslation();
  const watched = progress?.watched ?? false;
  const percent = !watched && progress && progress.durationMs > 0 ? Math.min(100, (progress.positionMs / progress.durationMs) * 100) : 0;
  const minutes = episode.durationMs ? Math.max(1, Math.round(episode.durationMs / 60_000)) : null;
  return (
    <motion.div layout exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.2 }} className="group flex items-center gap-3 px-3.5 py-2.5">
      <Link
        to="/watch/$sourceId/$animeId/$groupId/$episodeId"
        params={{ sourceId: episode.sourceId, animeId: episode.animeId, groupId: episode.groupId, episodeId: episode.episodeId }}
        className="flex min-w-0 flex-1 items-center gap-3"
      >
        <span
          className={cn(
            "relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-[14px] font-bold tabular-nums transition-colors",
            watched ? "bg-text/[.04] text-muted" : "bg-text/[.07] text-text group-hover:bg-accent/20 group-hover:text-accent-text",
          )}
        >
          {episode.episodeNumber}
          {watched && (
            <span className="absolute -bottom-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-accent-solid text-accent-solid-fg shadow-[0_0_0_2px_rgb(var(--color-bg))]">
              <Check className="h-2.5 w-2.5" strokeWidth={3.5} />
            </span>
          )}
        </span>
        <span className="relative min-w-0 flex-1">
          <span className={cn("block truncate text-[14px] font-semibold", watched ? "text-text/60" : "text-text/90")}>{episode.episodeLabel}</span>
          <span className="mt-0.5 flex items-center gap-2.5 text-[12px] tabular-nums text-muted">
            {minutes !== null && <span className="flex items-center gap-1"><Clock className="h-3 w-3" strokeWidth={2.2} />{t("downloads.minutes", { count: minutes })}</span>}
            <span>{formatBytes(episode.fileSizeBytes)}</span>
            <span className="truncate">{new Date(episode.downloadedAt).toLocaleDateString(locale, { day: "numeric", month: "short" })}</span>
          </span>
          {/* Laid over the row's bottom padding, so a row with progress is as tall as one without. */}
          {percent > 0 && (
            <span className="absolute inset-x-0 -bottom-[7px] block h-[3px] overflow-hidden rounded-full bg-text/[.08]">
              <span className="block h-full rounded-full bg-accent" style={{ width: `${percent}%` }} />
            </span>
          )}
        </span>
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-text/[.06] text-text transition-colors group-hover:bg-accent-solid group-hover:text-accent-solid-fg">
          <Play className="ml-px h-3.5 w-3.5 fill-current" strokeWidth={0} />
        </span>
      </Link>
      <IconButton label={t("downloads.remove")} onClick={onRemove} danger>
        <Trash2 className="h-[15px] w-[15px]" strokeWidth={2} />
      </IconButton>
    </motion.div>
  );
}

function IconButton({ label, onClick, danger = false, children }: { label: string; onClick: () => void; danger?: boolean; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cn(
        "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-muted transition-colors",
        danger ? "hover:bg-rose-400/15 hover:text-rose-500 active:bg-rose-400/20 dark:hover:text-rose-300" : "hover:bg-text/[.08] hover:text-text",
      )}
    >
      {children}
    </button>
  );
}
