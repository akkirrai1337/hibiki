import { create } from "zustand";
import type { LucideIcon } from "lucide-react";

export interface AchievementToastItem {
  id: string;
  icon: LucideIcon;
  titleKey: string;
  xp: number;
}

// A plain FIFO queue, not just "the current toast" - useAchievementUnlocks can clear several
// tiers at once (a single big binge session jumping a family two tiers in one go), and each
// deserves its own full-length showing one after another instead of only the last one winning or
// several stacking on top of each other at once.
interface AchievementToastState {
  queue: AchievementToastItem[];
  enqueue: (item: AchievementToastItem) => void;
  dequeue: () => void;
}

export const useAchievementToastStore = create<AchievementToastState>((set) => ({
  queue: [],
  enqueue: (item) => set((s) => ({ queue: [...s.queue, item] })),
  dequeue: () => set((s) => ({ queue: s.queue.slice(1) })),
}));
