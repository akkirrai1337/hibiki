import { useCallback, useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { ArrowDownToLine, ExternalLink, TriangleAlert } from "lucide-react";
import type { AppUpdate } from "@shared/types";
import { hibiki } from "@/lib/hibiki";
import { log } from "@/lib/log";
import { useUiStore } from "@/stores/uiStore";

/** Bytes as something a person can weigh a download against. */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "—";
  const megabytes = bytes / 1024 / 1024;
  if (megabytes >= 1024) return `${(megabytes / 1024).toFixed(2)} GB`;
  return `${megabytes.toFixed(1)} MB`;
}

/**
 * The green pill in the title bar, shown only once a newer release actually exists, and the dialog
 * behind it.
 *
 * The check runs once per launch and is never refetched: GitHub's unauthenticated API allows 60
 * requests an hour per address, and an app left open for a day polling it would spend that budget
 * on an answer that changes at most a few times a month.
 */
export function UpdateButton() {
  const { t } = useTranslation();
  const autoUpdate = useUiStore((s) => s.autoUpdate);
  const [open, setOpen] = useState(false);
  const [progress, setProgress] = useState<{ received: number; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const updateQuery = useQuery({
    queryKey: ["appUpdate"],
    queryFn: () => hibiki.updates.check(),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    retry: false,
  });
  const update = updateQuery.data ?? null;

  useEffect(() => hibiki.updates.onProgress(({ receivedBytes, totalBytes }) => {
    setProgress({ received: receivedBytes, total: totalBytes });
  }), []);

  const install = useCallback(async (target: AppUpdate) => {
    setError(null);
    setProgress({ received: 0, total: target.sizeBytes });
    log.info("update", `downloading ${target.version} (${target.sizeBytes} bytes)`);
    try {
      // Resolves only if something went wrong - on success the app quits to let the installer run.
      await hibiki.updates.downloadAndInstall(target);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : String(caught);
      log.error("update", `update to ${target.version} failed:`, message);
      setError(message);
      setProgress(null);
    }
  }, []);

  // Auto-update: check on launch, and if there's something there, take it without being asked.
  // The dialog is opened rather than left hidden - the app is about to close itself, and doing
  // that silently would read as a crash.
  const [autoStarted, setAutoStarted] = useState(false);
  useEffect(() => {
    if (!autoUpdate || !update || autoStarted) return;
    setAutoStarted(true);
    setOpen(true);
    void install(update);
  }, [autoUpdate, update, autoStarted, install]);

  if (!update) return null;
  const downloading = progress !== null && error === null;

  return (
    // `relative` so the panel below can anchor to this button rather than to the window - it hangs
    // out of the title bar's own height, which is fine since the bar sets no overflow.
    <div className="relative mr-2 flex shrink-0 items-center">
      <button
        onClick={() => setOpen((value) => !value)}
        aria-label={t("update.available")}
        title={t("update.availableVersion", { version: update.version })}
        className="app-no-drag flex h-7 shrink-0 items-center gap-1.5 rounded-lg bg-emerald-500 px-2.5 text-[12px] font-semibold text-white transition-colors hover:bg-emerald-400"
      >
        <ArrowDownToLine className="h-3.5 w-3.5" strokeWidth={2.5} />
        {t("update.button")}
      </button>

      <AnimatePresence>
        {open && (
          <UpdatePanel
            update={update}
            downloading={downloading}
            progress={progress}
            error={error}
            onInstall={() => void install(update)}
            onClose={() => setOpen(false)}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

function UpdatePanel({
  update,
  downloading,
  progress,
  error,
  onInstall,
  onClose,
}: {
  update: AppUpdate;
  downloading: boolean;
  progress: { received: number; total: number } | null;
  error: string | null;
  onInstall: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const percent = progress && progress.total > 0 ? Math.min(100, (progress.received / progress.total) * 100) : 0;

  return (
    <>
      {/* Invisible, not dimmed: this is a panel hanging off a title-bar button, and darkening the
          whole app behind it would announce it far more loudly than it deserves. It still needs to
          catch the click that closes it, the same way the app's other dropdowns do. While a
          download is running it catches nothing - dismissing the only thing explaining why the app
          is about to close itself would read as a crash. */}
      {!downloading && <div className="fixed inset-0 z-40" onClick={onClose} />}
      <motion.div
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -4 }}
        transition={{ type: "spring", stiffness: 500, damping: 45 }}
        onClick={(e) => e.stopPropagation()}
        className="app-no-drag absolute right-0 top-9 z-50 w-[300px] rounded-2xl border border-border bg-surface p-4 shadow-2xl"
      >
        <div className="min-w-0">
          <p className="text-sm font-bold text-text">{t("update.dialogTitle")}</p>
          <p className="mt-0.5 select-text text-xs text-muted">
            {t("update.versionLine", { version: update.version, size: formatBytes(update.sizeBytes) })}
          </p>
        </div>

        {downloading ? (
          <div className="mt-3">
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-text/[.08]">
              <div className="h-full rounded-full bg-emerald-500 transition-[width] duration-200" style={{ width: `${percent}%` }} />
            </div>
            <p className="mt-2 text-xs text-muted">
              {t("update.downloading", {
                received: formatBytes(progress?.received ?? 0),
                total: formatBytes(progress?.total ?? update.sizeBytes),
              })}
            </p>
            <p className="mt-1 text-xs text-muted/70">{t("update.willRestart")}</p>
          </div>
        ) : (
          <>
            {error && (
              <p className="mt-3 flex items-start gap-1.5 select-text text-xs leading-relaxed text-rose-500">
                <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" strokeWidth={2} />
                {t("update.failed", { message: error })}
              </p>
            )}
            <div className="mt-3 flex items-center gap-1.5">
              <button
                onClick={onInstall}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-500 px-2.5 py-1.5 text-[13px] font-semibold text-white transition-colors hover:bg-emerald-400"
              >
                <ArrowDownToLine className="h-3.5 w-3.5" strokeWidth={2.5} />
                {error ? t("update.retry") : t("update.download")}
              </button>
              <button
                onClick={onClose}
                className="rounded-lg px-2.5 py-1.5 text-[13px] font-semibold text-muted transition-colors hover:bg-text/[.06]"
              >
                {t("update.close")}
              </button>
            </div>
            <button
              onClick={() => void hibiki.updates.openRelease(update.releaseUrl)}
              className="mt-2.5 flex items-center gap-1 text-xs text-muted transition-colors hover:text-text"
            >
              {t("update.releaseNotes")}
              <ExternalLink className="h-3 w-3" strokeWidth={2} />
            </button>
          </>
        )}
      </motion.div>
    </>
  );
}
