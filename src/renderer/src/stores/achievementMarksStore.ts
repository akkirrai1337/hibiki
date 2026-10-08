import { create } from "zustand";
import { persist } from "zustand/middleware";

// The highest library-based figures the profile has ever reached. "First title", "Collector",
// "Finisher" and the genre families are read off the current library, so removing titles would
// take an earned achievement away again; an achievement is earned for good, so the best figure
// seen is kept and used whenever the library is smaller than it once was (see withRecordedBest in
// achievements.ts). The Android app keeps the same three numbers, so both behave alike.
interface AchievementMarksState {
  titles: number;
  completed: number;
  genres: number;
  record: (marks: { titles: number; completed: number; genres: number }) => void;
}

export const useAchievementMarksStore = create<AchievementMarksState>()(
  persist(
    (set, get) => ({
      titles: 0,
      completed: 0,
      genres: 0,
      record: ({ titles, completed, genres }) => {
        const current = get();
        if (titles <= current.titles && completed <= current.completed && genres <= current.genres) return;
        set({
          titles: Math.max(titles, current.titles),
          completed: Math.max(completed, current.completed),
          genres: Math.max(genres, current.genres),
        });
      },
    }),
    { name: "hibiki-achievement-marks" },
  ),
);
