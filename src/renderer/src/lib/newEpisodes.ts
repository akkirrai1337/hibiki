// New-episode notices from the page's side (the checking itself is core/newEpisodes): when to run
// a check, and the notices' texts in the app's language. A check runs a little after start, on
// coming back to the app, and every few hours while it stays open - each one only asks the sources
// of the titles whose time has come (core decides that), so most runs ask nobody at all.
import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { QueryClient } from "@tanstack/react-query";
import type { NewEpisodeNotice } from "@shared/types";
import { hibiki } from "@/lib/hibiki";
import i18n from "@/lib/i18n";
import { log } from "@/lib/log";
import { isGenericDubTitle } from "@/lib/dubTitle";

const START_DELAY_MS = 15_000;
const INTERVAL_MS = 3 * 60 * 60 * 1000;
/** Coming back to the app checks again only after this long. */
const RESUME_GAP_MS = 30 * 60 * 1000;

interface NewEpisodesPrefs {
  enabled: boolean;
  setEnabled: (enabled: boolean) => void;
}

/** Settings' "New episodes" switch (phone). */
export const useNewEpisodesPrefs = create<NewEpisodesPrefs>()(
  persist((set) => ({ enabled: true, setEnabled: (enabled) => set({ enabled }) }), { name: "hibiki-new-episodes" }),
);

/** The notice's line under the title: which episodes, in which dub. */
export function noticeText(notice: NewEpisodeNotice): string {
  const numbers = notice.numbers;
  const dub = isGenericDubTitle(notice.groupTitle) ? "" : notice.groupTitle;
  const key = numbers.length === 1 ? "newEpisodes.one" : "newEpisodes.range";
  const text = i18n.t(key, { number: numbers[0], from: numbers[0], to: numbers[numbers.length - 1] });
  return dub ? `${text} · ${dub}` : text;
}

const watchUrl = (notice: NewEpisodeNotice) =>
  `hibiki://watch/${[notice.sourceId, notice.animeId, notice.groupId, notice.episodeId].map(encodeURIComponent).join("/")}`;

let lastRun = 0;
let started = false;

async function run(queryClient: QueryClient, reason: string): Promise<void> {
  const api = hibiki.newEpisodes;
  if (!api?.notify || !useNewEpisodesPrefs.getState().enabled) return;
  if (reason === "resume" && Date.now() - lastRun < RESUME_GAP_MS) return;
  lastRun = Date.now();
  try {
    const notices = await api.checkDue();
    if (notices.length === 0) return;
    // The title pages these are about show the new episodes the next time they are opened.
    for (const notice of notices) {
      void queryClient.invalidateQueries({ queryKey: ["playbackGroups", notice.sourceId, notice.animeId] });
      void queryClient.invalidateQueries({ queryKey: ["anime", notice.sourceId, notice.animeId] });
    }
    const shown = await api.notify(
      notices.map((notice) => ({ key: `${notice.sourceId}\n${notice.animeId}`, title: notice.title, text: noticeText(notice), url: watchUrl(notice) })),
      { channelName: i18n.t("newEpisodes.channel"), summary: i18n.t("newEpisodes.summary", { count: notices.length }) },
    );
    log.info("episodes", `${notices.length} notice(s) after the ${reason} check${shown ? "" : " - not shown: notifications are not allowed"}`);
  } catch (error) {
    log.warn("episodes", "new-episode check failed:", error);
  }
}

/** Starts the checks (once); nothing happens where the host has no notices (desktop). */
export function startNewEpisodeChecks(queryClient: QueryClient): void {
  if (started || !hibiki.newEpisodes?.notify) return;
  started = true;
  window.setTimeout(() => void run(queryClient, "start"), START_DELAY_MS);
  window.setInterval(() => void run(queryClient, "timer"), INTERVAL_MS);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") void run(queryClient, "resume");
  });
}
