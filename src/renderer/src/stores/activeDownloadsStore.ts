import { create } from "zustand";
import { downloadKeyId } from "@shared/downloadKey";
import type { ActiveDownload, DownloadProgress } from "@shared/types";
import { hibiki } from "@/lib/hibiki";

/** A download that ended in an error this session - kept until retried or dismissed. */
export interface FailedDownload extends ActiveDownload {
  /** null: the source offers nothing this app can save ("unsupported"). */
  message: string | null;
}

interface ActiveDownloadsState {
  /** Queued, running and paused, in the order they were asked for. */
  items: ActiveDownload[];
  failed: FailedDownload[];
  /** Finished since the queue was last empty - "3 episodes downloaded" once it empties again. */
  completed: number;
  /** When the last one finished and nothing was left; the indicator lingers a moment after it. */
  finishedAt: number | null;
  retry: (key: string) => void;
  dismiss: (key: string) => void;
}

export const useActiveDownloadsStore = create<ActiveDownloadsState>((set, get) => ({
  items: [],
  failed: [],
  completed: 0,
  finishedAt: null,
  retry: (key) => {
    const entry = get().failed.find((item) => downloadKeyId(item) === key);
    if (!entry) return;
    set((state) => ({ failed: state.failed.filter((item) => item !== entry) }));
    void hibiki.downloads.start(entry);
  },
  dismiss: (key) => set((state) => ({ failed: state.failed.filter((item) => downloadKeyId(item) !== key) })),
}));

let started = false;

/**
 * Keeps the store in step with the backend for the whole session: the list it holds now, then
 * every progress event. An event only carries a download's key and state, so one for a download
 * the store has not seen yet (just queued, from any screen) fetches the list again - batched, as a
 * whole title queues a dozen at once.
 */
export function startActiveDownloads(): void {
  if (started) return;
  started = true;
  let refreshTimer: number | undefined;
  const refresh = () => {
    window.clearTimeout(refreshTimer);
    refreshTimer = window.setTimeout(() => {
      void hibiki.downloads.active().then((items) => {
        useActiveDownloadsStore.setState((state) => {
          const wasEmpty = state.items.length === 0;
          return { items, ...(wasEmpty && items.length > 0 ? { completed: 0, finishedAt: null } : {}) };
        });
      }).catch(() => undefined);
    }, 120);
  };
  refresh();

  hibiki.downloads.onProgress((progress: DownloadProgress) => {
    const key = downloadKeyId(progress);
    const { items } = useActiveDownloadsStore.getState();
    const current = items.find((item) => downloadKeyId(item) === key);
    const status = progress.status;
    if (status === "queued" || status === "downloading" || status === "paused") {
      if (!current) return refresh();
      useActiveDownloadsStore.setState({
        items: items.map((item) => (item === current ? { ...item, status, percent: progress.percent ?? item.percent } : item)),
      });
      return;
    }
    if (!current) return;
    const rest = items.filter((item) => item !== current);
    useActiveDownloadsStore.setState((state) => ({
      items: rest,
      failed: status === "error" || status === "unsupported"
        ? [...state.failed.filter((item) => downloadKeyId(item) !== key), { ...current, message: status === "error" ? progress.message ?? null : null }]
        : state.failed,
      completed: status === "done" ? state.completed + 1 : state.completed,
      finishedAt: rest.length === 0 ? Date.now() : null,
    }));
  });
}
