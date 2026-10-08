// The phone's update flow, kept outside any component: a download outlives the sheet that started it
// (the sheet can be pulled away, the settings row reopens it on the same progress), and the sheet,
// the settings row and the launch prompt all read one answer.
import { useQuery } from "@tanstack/react-query";
import { create } from "zustand";
import type { AppUpdate } from "@shared/types";
import { hibiki } from "@/lib/hibiki";
import { log } from "@/lib/log";

/** The codes core/updates.ts fails with; anything else (a plain Error) reads as "download-failed". */
export type UpdateFailure =
  | "permission-needed"
  | "download-failed"
  | "signature-mismatch"
  | "wrong-package"
  | "wrong-version"
  | "not-newer"
  | "unreadable";

const FAILURES: readonly string[] = ["permission-needed", "download-failed", "signature-mismatch", "wrong-package", "wrong-version", "not-newer", "unreadable"];

export type UpdatePhase =
  | { kind: "idle" }
  | { kind: "downloading"; received: number; total: number }
  /** The system installer is up (or was, and the person backed out of it). */
  | { kind: "installer" }
  | { kind: "failed"; failure: UpdateFailure; message: string };

interface UpdateFlowState {
  phase: UpdatePhase;
  sheetOpen: boolean;
  /** The person was sent to the "install unknown apps" setting at least once this launch. */
  permissionAsked: boolean;
  openSheet(): void;
  closeSheet(): void;
  start(update: AppUpdate): Promise<void>;
}

const DISMISSED_KEY = "hibiki-update-dismissed";

/** The version the person said "later" to: not offered on its own again, still one tap away in settings. */
export function dismissedVersion(): string | null {
  try {
    return localStorage.getItem(DISMISSED_KEY);
  } catch {
    return null;
  }
}

export function dismissVersion(version: string): void {
  try {
    localStorage.setItem(DISMISSED_KEY, version);
  } catch { /* Only a convenience. */ }
}

function failureOf(error: unknown): UpdateFailure {
  const code = (error as { code?: unknown } | null)?.code;
  return typeof code === "string" && FAILURES.includes(code) ? (code as UpdateFailure) : "download-failed";
}

let progressAttached = false;

export const useUpdateFlow = create<UpdateFlowState>((set, get) => ({
  phase: { kind: "idle" },
  sheetOpen: false,
  permissionAsked: false,
  openSheet: () => set({ sheetOpen: true }),
  closeSheet: () => set({ sheetOpen: false }),
  start: async (update) => {
    if (get().phase.kind === "downloading") return;
    if (!progressAttached) {
      progressAttached = true;
      hibiki.updates.onProgress(({ receivedBytes, totalBytes }) => {
        if (get().phase.kind === "downloading") set({ phase: { kind: "downloading", received: receivedBytes, total: totalBytes } });
      });
    }
    set({ phase: { kind: "downloading", received: 0, total: update.sizeBytes } });
    log.info("update", `downloading ${update.version} (${update.sizeBytes} bytes)`);
    try {
      await hibiki.updates.downloadAndInstall(update);
      set({ phase: { kind: "installer" } });
    } catch (error) {
      const failure = failureOf(error);
      const message = error instanceof Error ? error.message : String(error);
      log.warn("update", `update to ${update.version} stopped: ${failure} (${message})`);
      set({ phase: { kind: "failed", failure, message } });
    }
  },
}));

/** Once per launch, never refetched: GitHub's unauthenticated API allows 60 requests an hour. */
export function useAppUpdate(): AppUpdate | null {
  const query = useQuery({
    queryKey: ["appUpdate"],
    queryFn: () => hibiki.updates.check(),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    retry: false,
  });
  return query.data ?? null;
}
