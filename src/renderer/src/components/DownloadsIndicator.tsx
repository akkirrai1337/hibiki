import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { ArrowDown, ArrowDownToLine, Check, ChevronDown, ChevronRight, Clapperboard, Clock, HardDrive, Pause, Play, RotateCw, TriangleAlert, X, type LucideIcon } from "lucide-react";
import { downloadKeyId } from "@shared/downloadKey";
import type { ActiveDownload } from "@shared/types";
import { hibiki } from "@/lib/hibiki";
import { isMobile } from "@/lib/mobile";
import { cn } from "@/lib/cn";
import { BottomSheet } from "@/components/BottomSheet";
import { PosterImage } from "@/components/PosterImage";
import { useActiveDownloadsStore, type FailedDownload } from "@/stores/activeDownloadsStore";

/** How long the finished state stays on the indicator once the last download is done. */
const LINGER_MS = 4500;
/** Episodes shown per title before "N more". */
const FOLDED = 4;

type Mode = "downloading" | "queued" | "paused" | "failed" | "done";

/** What the indicator shows: the queue's state, its overall percent and how many are left. */
function useIndicator(): { mode: Mode | null; percent: number; count: number } {
  const items = useActiveDownloadsStore((s) => s.items);
  const failed = useActiveDownloadsStore((s) => s.failed);
  const completed = useActiveDownloadsStore((s) => s.completed);
  const finishedAt = useActiveDownloadsStore((s) => s.finishedAt);
  const [, rerender] = useState(0);
  useEffect(() => {
    if (finishedAt === null) return;
    const timer = window.setTimeout(() => rerender((n) => n + 1), Math.max(0, finishedAt + LINGER_MS - Date.now()) + 20);
    return () => window.clearTimeout(timer);
  }, [finishedAt]);

  const percent = items.length === 0 ? 100 : Math.round(items.reduce((sum, item) => sum + (item.status === "queued" ? 0 : item.percent), 0) / items.length);
  let mode: Mode | null = null;
  if (items.some((item) => item.status === "downloading")) mode = "downloading";
  else if (items.some((item) => item.status === "queued")) mode = "queued";
  else if (items.length > 0) mode = "paused";
  else if (failed.length > 0) mode = "failed";
  else if (completed > 0 && finishedAt !== null && Date.now() - finishedAt < LINGER_MS) mode = "done";
  return { mode, percent, count: items.length };
}

/**
 * The downloads indicator: shown only while something is downloading (or just finished, or failed),
 * beside the phone's search button and in the desktop title bar. A ring round it fills with the
 * whole queue's progress; it opens the list of every title and episode under way.
 */
export function DownloadsButton({ variant, className }: { variant: "artwork" | "plain" | "titlebar"; className?: string }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const { mode, percent, count } = useIndicator();
  const shownMode: Mode = mode ?? "done";
  const visible = mode !== null || open;
  const size = variant === "titlebar" ? 28 : 40;

  return (
    // The margin only while there is something to show: hidden, the indicator takes no room at all.
    <div className={cn("relative flex shrink-0 items-center", visible && className)}>
      <AnimatePresence initial={false}>
        {visible && (
          <motion.button
            key="downloads"
            type="button"
            initial={{ opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.6 }}
            transition={{ type: "spring", stiffness: 520, damping: 34 }}
            onClick={() => setOpen((value) => !value)}
            aria-label={t("downloadsPanel.title")}
            title={variant === "titlebar" ? t("downloadsPanel.title") : undefined}
            style={{ width: size, height: size }}
            className={cn(
              "relative flex items-center justify-center rounded-full",
              variant === "artwork" && "bg-black/45 text-white ring-1 ring-white/10 backdrop-blur-md active:bg-black/70",
              variant === "plain" && "text-text active:bg-text/[.08]",
              variant === "titlebar" && "app-no-drag text-muted transition-colors hover:bg-text/10 hover:text-text",
            )}
          >
            <ProgressRing size={size} percent={percent} mode={shownMode} onArtwork={variant === "artwork"} />
            <ModeIcon mode={shownMode} small={variant === "titlebar"} />
            <AnimatePresence>
              {count > 1 && (
                <motion.span
                  key={count}
                  initial={{ scale: 0.5, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  exit={{ scale: 0.5, opacity: 0 }}
                  className={cn(
                    "absolute flex items-center justify-center rounded-full bg-accent-solid font-bold tabular-nums text-accent-solid-fg shadow-[0_0_0_2px_rgb(var(--color-bg))]",
                    variant === "titlebar" ? "-right-1 -top-1 h-3.5 min-w-3.5 px-1 text-[9px]" : "-right-0.5 -top-0.5 h-[18px] min-w-[18px] px-1 text-[10px]",
                  )}
                >
                  {count}
                </motion.span>
              )}
            </AnimatePresence>
          </motion.button>
        )}
      </AnimatePresence>

      {isMobile ? (
        <BottomSheet open={open} onClose={() => setOpen(false)} title={<PanelHeader />} footer={<SavedLink onNavigate={() => setOpen(false)} />}>
          {/* The sheet's body already pads by 8px: 12 more lines it up with the title above. */}
          <div className="px-3 pb-3">
            <DownloadsPanel onNavigate={() => setOpen(false)} />
          </div>
        </BottomSheet>
      ) : (
        <AnimatePresence>
          {open && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ type: "spring", stiffness: 500, damping: 45 }}
                className="app-no-drag absolute right-0 top-9 z-50 flex w-[380px] flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl"
              >
                <div className="px-4 pb-1 pt-3.5 text-sm font-bold text-text"><PanelHeader /></div>
                <div className="max-h-[min(68vh,560px)] overflow-y-auto px-4 pb-2">
                  <DownloadsPanel onNavigate={() => setOpen(false)} />
                </div>
                <div className="border-t border-border px-4 py-2.5">
                  <SavedLink onNavigate={() => setOpen(false)} />
                </div>
              </motion.div>
            </>
          )}
        </AnimatePresence>
      )}
    </div>
  );
}

function ProgressRing({ size, percent, mode, onArtwork }: { size: number; percent: number; mode: Mode; onArtwork: boolean }) {
  const stroke = size > 32 ? 2.5 : 2;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const filled = mode === "done" || mode === "failed" ? 1 : Math.max(0.02, percent / 100);
  const color = mode === "failed" ? "rgb(251 113 133)" : mode === "paused" ? (onArtwork ? "rgb(255 255 255 / .55)" : "rgb(var(--color-muted))") : "rgb(var(--color-accent))";
  return (
    <svg width={size} height={size} className="pointer-events-none absolute inset-0 -rotate-90" aria-hidden>
      <circle cx={size / 2} cy={size / 2} r={radius} fill="none" strokeWidth={stroke} stroke={onArtwork ? "rgb(255 255 255 / .14)" : "rgb(var(--color-text) / .1)"} />
      {mode === "queued" ? (
        // Waiting its turn: a short arc going round, no percent to show yet.
        <motion.circle
          cx={size / 2} cy={size / 2} r={radius} fill="none" strokeWidth={stroke} stroke={color} strokeLinecap="round"
          strokeDasharray={`${circumference * 0.22} ${circumference}`}
          style={{ originX: "50%", originY: "50%" }}
          animate={{ rotate: 360 }}
          transition={{ repeat: Infinity, duration: 1.1, ease: "linear" }}
        />
      ) : (
        <circle
          cx={size / 2} cy={size / 2} r={radius} fill="none" strokeWidth={stroke} stroke={color} strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - filled)}
          style={{ transition: "stroke-dashoffset 400ms ease, stroke 200ms" }}
        />
      )}
    </svg>
  );
}

function ModeIcon({ mode, small }: { mode: Mode; small: boolean }) {
  const cls = small ? "h-3.5 w-3.5" : "h-[18px] w-[18px]";
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.span
        key={mode}
        initial={{ opacity: 0, scale: 0.6 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.6 }}
        transition={{ duration: 0.15 }}
        className="relative flex"
      >
        {mode === "downloading" ? (
          // A gentle fall, over and over: the arrow is the one thing moving while bytes arrive.
          <motion.span className="flex" animate={{ y: [-1.5, 1.5, -1.5] }} transition={{ repeat: Infinity, duration: 1.4, ease: "easeInOut" }}>
            <ArrowDown className={cls} strokeWidth={2.4} />
          </motion.span>
        ) : mode === "queued" ? (
          <ArrowDown className={cn(cls, "opacity-70")} strokeWidth={2.4} />
        ) : mode === "paused" ? (
          <Pause className={cn(cls, "fill-current")} strokeWidth={0} />
        ) : mode === "failed" ? (
          <TriangleAlert className={cn(cls, "text-rose-400")} strokeWidth={2.2} />
        ) : (
          <Check className={cn(cls, "text-accent-text")} strokeWidth={2.8} />
        )}
      </motion.span>
    </AnimatePresence>
  );
}

function SavedLink({ onNavigate }: { onNavigate: () => void }) {
  const { t } = useTranslation();
  return (
    <Link to="/downloads" onClick={onNavigate} className="flex items-center gap-2.5 py-1 text-[13px] font-semibold text-muted transition-colors hover:text-text">
      <HardDrive className="h-4 w-4 shrink-0" strokeWidth={2} />
      <span className="flex-1">{t("downloads.title")}</span>
      <ChevronRight className="h-4 w-4" strokeWidth={2.2} />
    </Link>
  );
}

interface TitleGroup {
  key: string;
  sourceId: string;
  animeId: string;
  animeTitle: string;
  items: ActiveDownload[];
}

/** Every title under way, each with its episodes, then what failed; one tap reaches a title's page. */
function DownloadsPanel({ onNavigate }: { onNavigate: () => void }) {
  const { t } = useTranslation();
  const items = useActiveDownloadsStore((s) => s.items);
  const failed = useActiveDownloadsStore((s) => s.failed);
  const completed = useActiveDownloadsStore((s) => s.completed);

  const groups = useMemo(() => {
    const byTitle = new Map<string, TitleGroup>();
    for (const item of items) {
      const key = `${item.sourceId}\n${item.animeId}`;
      const group = byTitle.get(key) ?? { key, sourceId: item.sourceId, animeId: item.animeId, animeTitle: item.animeTitle, items: [] };
      group.items.push(item);
      byTitle.set(key, group);
    }
    return [...byTitle.values()];
  }, [items]);

  const posterKeys = [...new Map([...items, ...failed].map((item) => [`${item.sourceId}:${item.animeId}`, { sourceId: item.sourceId, animeId: item.animeId }])).values()];
  const posters = useQuery({
    queryKey: ["downloadPosters", posterKeys.map((key) => `${key.sourceId}:${key.animeId}`).sort().join("|")],
    queryFn: () => hibiki.sources.cachedTitles(posterKeys),
    enabled: posterKeys.length > 0,
    staleTime: Infinity,
  });
  const posterOf = (sourceId: string, animeId: string) => posters.data?.[`${sourceId}:${animeId}`]?.title.posterUrl ?? undefined;

  if (items.length === 0 && failed.length === 0) {
    return (
      <div className="flex flex-col items-center py-8 text-center">
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-accent/15 text-accent-text">
          <Check className="h-6 w-6" strokeWidth={2.6} />
        </span>
        <p className="mt-3 text-[15px] font-bold text-text">{t("downloadsPanel.allDone")}</p>
        {completed > 0 && <p className="mt-1 text-[13px] text-muted">{t("downloadsPanel.completed", { count: completed })}</p>}
      </div>
    );
  }

  return (
    <div>
      <div className="divide-y divide-border/70">
        {groups.map((group) => (
          <TitleSection key={group.key} group={group} posterUrl={posterOf(group.sourceId, group.animeId)} onNavigate={onNavigate} />
        ))}
      </div>
      {failed.length > 0 && <FailedSection failed={failed} />}
    </div>
  );
}

/** "Downloads", with the whole queue paused, resumed or cancelled at once beside it. */
function PanelHeader() {
  const { t } = useTranslation();
  const items = useActiveDownloadsStore((s) => s.items);
  const [confirmCancel, setConfirmCancel] = useState(false);
  useEffect(() => {
    if (!confirmCancel) return;
    const timer = window.setTimeout(() => setConfirmCancel(false), 3000);
    return () => window.clearTimeout(timer);
  }, [confirmCancel]);
  const running = items.filter((item) => item.status === "downloading").length;
  const paused = items.filter((item) => item.status === "paused").length;

  const pauseAll = () => { for (const item of items) if (item.status === "downloading") void hibiki.downloads.pause(item.sourceId, item.animeId, item.episodeId); };
  const resumeAll = () => { for (const item of items) if (item.status === "paused") void hibiki.downloads.resume(item.sourceId, item.animeId, item.episodeId); };
  const cancelAll = () => {
    setConfirmCancel(false);
    for (const item of items) void hibiki.downloads.cancel(item.sourceId, item.animeId, item.episodeId);
  };

  return (
    <div className="flex min-h-7 items-center gap-2">
      <span className="flex min-w-0 flex-1 items-center gap-2">
        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent/15 text-accent-text">
          <ArrowDownToLine className="h-3.5 w-3.5" strokeWidth={2.5} />
        </span>
        <span className="truncate">{t("downloadsPanel.title")}</span>
      </span>
      {items.length > 0 && (running > 0 ? (
        <PillButton onClick={pauseAll} icon={Pause} filled>{t("downloadsPanel.pauseAll")}</PillButton>
      ) : paused > 0 ? (
        <PillButton onClick={resumeAll} icon={Play} filled>{t("downloadsPanel.resumeAll")}</PillButton>
      ) : null)}
      {items.length > 0 && (
        <PillButton onClick={confirmCancel ? cancelAll : () => setConfirmCancel(true)} danger={confirmCancel} icon={confirmCancel ? TriangleAlert : X}>
          {confirmCancel ? t("downloadsPanel.cancelAllConfirm") : t("downloadsPanel.cancelAll")}
        </PillButton>
      )}
    </div>
  );
}

function PillButton({ onClick, danger = false, icon: Icon, filled = false, children }: { onClick: () => void; danger?: boolean; icon: LucideIcon; filled?: boolean; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex shrink-0 items-center gap-1.5 rounded-full py-1 pl-2.5 pr-3 text-[12px] font-semibold transition-colors",
        danger ? "bg-rose-500/15 text-rose-400 hover:bg-rose-500/25" : "bg-text/[.06] text-text/80 hover:bg-text/[.1] hover:text-text active:bg-text/[.12]",
      )}
    >
      {filled ? <Icon className="h-3 w-3 fill-current" strokeWidth={0} /> : <Icon className="h-3.5 w-3.5" strokeWidth={2.4} />}
      {children}
    </button>
  );
}

function TitleSection({ group, posterUrl, onNavigate }: { group: TitleGroup; posterUrl?: string; onNavigate: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(false);
  const percent = Math.round(group.items.reduce((sum, item) => sum + (item.status === "queued" ? 0 : item.percent), 0) / group.items.length);
  const shown = expanded ? group.items : group.items.slice(0, FOLDED);
  const hidden = group.items.length - shown.length;

  return (
    <section className="py-3">
      <button
        type="button"
        onClick={() => {
          onNavigate();
          void navigate({ to: "/anime/$sourceId/$animeId", params: { sourceId: group.sourceId, animeId: group.animeId } });
        }}
        className="group flex w-full items-center gap-3 text-left"
      >
        <span className="relative h-14 w-10 shrink-0 overflow-hidden rounded-md bg-text/[.06]">
          {posterUrl && <PosterImage src={posterUrl} alt="" className="h-full w-full object-cover" />}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[14px] font-bold text-text">{group.animeTitle}</span>
          <span className="mt-1 flex items-center gap-3 text-[12px] tabular-nums text-muted">
            <span className="flex items-center gap-1"><Clapperboard className="h-3.5 w-3.5" strokeWidth={2} />{t("downloadsPanel.episodes", { count: group.items.length })}</span>
            <span className="flex items-center gap-1"><ArrowDownToLine className="h-3.5 w-3.5" strokeWidth={2} />{percent}%</span>
          </span>
        </span>
        <ChevronRight className="h-4 w-4 shrink-0 text-muted transition-transform group-hover:translate-x-0.5" strokeWidth={2.2} />
      </button>
      <div className="mt-1.5 pl-[52px]">
        <AnimatePresence initial={false}>
          {shown.map((item) => <EpisodeRow key={downloadKeyId(item)} item={item} />)}
        </AnimatePresence>
        {hidden > 0 && (
          <button type="button" onClick={() => setExpanded(true)} className="mt-1 flex items-center gap-1 text-[12px] font-semibold text-muted transition-colors hover:text-text">
            <ChevronDown className="h-3.5 w-3.5" strokeWidth={2.4} />
            {t("downloadsPanel.more", { count: hidden })}
          </button>
        )}
      </div>
    </section>
  );
}

function EpisodeRow({ item }: { item: ActiveDownload }) {
  const { t } = useTranslation();
  const downloading = item.status === "downloading";
  const paused = item.status === "paused";
  const statusText = downloading ? `${item.percent}%` : paused ? t("downloadsPanel.paused") : t("detail.episodeMenu.queued");
  const StatusIcon = downloading ? ArrowDown : paused ? Pause : Clock;
  return (
    <motion.div
      layout
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: "auto" }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.2 }}
      className="overflow-hidden"
    >
      <div className="flex items-center gap-2 py-1.5">
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline justify-between gap-2">
            <span className="truncate text-[13px] font-semibold text-text/90">{item.episodeLabel}</span>
            <span className={cn("flex shrink-0 items-center gap-1 text-[12px] tabular-nums", downloading ? "text-accent-text" : "text-muted")}>
              <StatusIcon className={cn("h-3 w-3", paused && "fill-current")} strokeWidth={paused ? 0 : 2.4} />
              {statusText}
            </span>
          </div>
          <div className="mt-1.5 h-[3px] overflow-hidden rounded-full bg-text/[.08]">
            <div
              className={cn("h-full rounded-full transition-[width] duration-300", downloading ? "bg-accent" : "bg-text/30")}
              style={{ width: `${item.status === "queued" ? 0 : Math.max(2, item.percent)}%` }}
            />
          </div>
        </div>
        {item.status === "queued" ? (
          // Nothing to pause yet; the space stays so every row's bar ends at the same place.
          <span className="h-7 w-7 shrink-0" aria-hidden />
        ) : (
          <RowButton
            label={downloading ? t("detail.episodeMenu.pauseDownload") : t("detail.episodeMenu.resumeDownload")}
            onClick={() => void (downloading ? hibiki.downloads.pause(item.sourceId, item.animeId, item.episodeId) : hibiki.downloads.resume(item.sourceId, item.animeId, item.episodeId))}
          >
            {downloading ? <Pause className="h-3 w-3 fill-current" strokeWidth={0} /> : <Play className="ml-px h-3 w-3 fill-current" strokeWidth={0} />}
          </RowButton>
        )}
        <RowButton label={t("detail.episodeMenu.cancelDownload")} onClick={() => void hibiki.downloads.cancel(item.sourceId, item.animeId, item.episodeId)}>
          <X className="h-3.5 w-3.5" strokeWidth={2.4} />
        </RowButton>
      </div>
    </motion.div>
  );
}

function RowButton({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-text/[.06] text-text/80 transition-colors hover:bg-text/[.12] hover:text-text active:bg-text/[.14]"
    >
      {children}
    </button>
  );
}

function FailedSection({ failed }: { failed: FailedDownload[] }) {
  const { t } = useTranslation();
  const retry = useActiveDownloadsStore((s) => s.retry);
  const dismiss = useActiveDownloadsStore((s) => s.dismiss);
  return (
    <section className="mt-1 border-t border-border/70 pt-3">
      <p className="mb-1 flex items-center gap-1.5 text-[12px] font-semibold uppercase tracking-wide text-rose-400">
        <TriangleAlert className="h-3.5 w-3.5" strokeWidth={2.4} />
        {t("downloadsPanel.failed")}
      </p>
      {failed.map((item) => {
        const key = downloadKeyId(item);
        return (
          <div key={key} className="flex items-center gap-2 py-1.5">
            <div className="min-w-0 flex-1">
              <p className="truncate text-[13px] font-semibold text-text/90">{item.animeTitle} · {item.episodeLabel}</p>
              <p className="truncate text-[12px] text-muted">{item.message ?? t("detail.episodeMenu.downloadUnsupported")}</p>
            </div>
            {item.message !== null && (
              <RowButton label={t("downloadsPanel.retry")} onClick={() => retry(key)}>
                <RotateCw className="h-3.5 w-3.5" strokeWidth={2.3} />
              </RowButton>
            )}
            <RowButton label={t("downloadsPanel.dismiss")} onClick={() => dismiss(key)}>
              <X className="h-3.5 w-3.5" strokeWidth={2.4} />
            </RowButton>
          </div>
        );
      })}
    </section>
  );
}
