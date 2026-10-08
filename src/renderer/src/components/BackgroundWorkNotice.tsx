import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { DownloadProgress } from "@shared/types";
import { hibiki } from "@/lib/hibiki";

/** How often the notification's text and bar may change - often enough to look alive. */
const UPDATE_INTERVAL_MS = 2000;

/**
 * Phone: keeps episode downloads going when the app is in the background. While anything is queued
 * or downloading, the system's foreground service runs with a notification saying so (see
 * BackgroundWorkService.java); when the last one finishes, pauses or fails, it stops. Renders
 * nothing. Desktop has no such thing to ask for.
 */
export function BackgroundWorkNotice() {
  const { t } = useTranslation();
  const setBackgroundWork = hibiki.device?.setBackgroundWork;
  const tRef = useRef(t);
  tRef.current = t;

  useEffect(() => {
    if (!setBackgroundWork) return;
    const jobs = new Map<string, { status: DownloadProgress["status"]; percent: number }>();
    let running = false;
    let lastUpdate = 0;
    let timer: number | undefined;

    const publish = () => {
      timer = undefined;
      lastUpdate = Date.now();
      const active = [...jobs.values()].filter((job) => job.status === "queued" || job.status === "downloading");
      if (active.length === 0) {
        if (running) void setBackgroundWork(null);
        running = false;
        return;
      }
      running = true;
      const percent = Math.round(active.reduce((sum, job) => sum + job.percent, 0) / active.length);
      void setBackgroundWork({
        title: tRef.current("backgroundWork.downloadingTitle", { count: active.length }),
        text: `${percent}%`,
        progress: percent,
      });
    };

    const unsubscribe = hibiki.downloads.onProgress((progress) => {
      const previous = jobs.get(progress.episodeId);
      const status = progress.status;
      if (status === "queued" || status === "downloading" || status === "paused") {
        jobs.set(progress.episodeId, { status, percent: progress.percent ?? previous?.percent ?? 0 });
      } else {
        jobs.delete(progress.episodeId);
      }
      // A start or an end shows at once; progress in between is spaced out.
      const changedShape = !previous || previous.status !== status || !jobs.has(progress.episodeId);
      if (changedShape) {
        if (timer !== undefined) window.clearTimeout(timer);
        publish();
      } else if (timer === undefined) {
        timer = window.setTimeout(publish, Math.max(0, UPDATE_INTERVAL_MS - (Date.now() - lastUpdate)));
      }
    });
    return () => {
      unsubscribe();
      if (timer !== undefined) window.clearTimeout(timer);
      if (running) void setBackgroundWork(null);
    };
  }, [setBackgroundWork]);

  return null;
}
