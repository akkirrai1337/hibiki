// Sleep timer: pauses playback after a while, or lets the current episode end without starting the
// next one. Kept for the session only, not persisted: a timer set last night must not stop
// tonight's first episode. It outlives switching episodes - "in 30 minutes" means wall-clock time,
// whatever plays in between.
import { create } from "zustand";

export type SleepTimerChoice = "off" | 15 | 30 | 45 | 60 | "episode";

export const SLEEP_TIMER_CHOICES: SleepTimerChoice[] = ["off", 15, 30, 45, 60, "episode"];

interface SleepTimerState {
  choice: SleepTimerChoice;
  /** When a minutes choice runs out (ms since epoch); null for "off" and "episode". */
  endsAt: number | null;
  set: (choice: SleepTimerChoice) => void;
  clear: () => void;
}

export const useSleepTimerStore = create<SleepTimerState>()((set) => ({
  choice: "off",
  endsAt: null,
  set: (choice) => set({ choice, endsAt: typeof choice === "number" ? Date.now() + choice * 60_000 : null }),
  clear: () => set({ choice: "off", endsAt: null }),
}));

/** Whole minutes left, rounded up so the last minute reads "1", not "0". */
export function minutesLeft(endsAt: number, now: number): number {
  return Math.max(1, Math.ceil((endsAt - now) / 60_000));
}

/** Whether an episode that just ended should stop here instead of starting the next one. Clears an
 * "end of episode" timer as it fires. */
export function sleepTimerTakesEpisodeEnd(): boolean {
  const { choice, clear } = useSleepTimerStore.getState();
  if (choice !== "episode") return false;
  clear();
  return true;
}
