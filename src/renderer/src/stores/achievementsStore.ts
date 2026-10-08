import { create } from "zustand";
import type { Achievement } from "@/lib/achievements";

// Deliberately not persisted (unlike most other stores here) - this is a live projection of the
// SQLite library/progress data, recomputed by useAchievementUnlocks() on every load, not a user
// preference that should survive as its own stale snapshot between sessions.
interface AchievementsState {
  achievements: Achievement[];
  setAchievements: (achievements: Achievement[]) => void;
}

export const useAchievementsStore = create<AchievementsState>((set) => ({
  achievements: [],
  setAchievements: (achievements) => set({ achievements }),
}));
