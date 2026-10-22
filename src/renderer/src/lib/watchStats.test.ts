import { describe, expect, it } from "vitest";
import type { AnimeTitle, DailyActivity, LibraryEntry, StatsEpisode } from "@shared/types";
import { computeWatchStats } from "./watchStats";

const NOW = new Date(2026, 9, 22, 12).getTime();
const DAY = 86_400_000;

function entry(animeId: string, anime: Partial<AnimeTitle>, category: LibraryEntry["category"] = "watching"): LibraryEntry {
  return { sourceId: "s", animeId, category, addedAt: 0, anime: { id: animeId, sourceId: "s", ...anime } };
}

function episodes(titleId: string, count: number, at: Date, translation: string | null = null): StatsEpisode[] {
  return Array.from({ length: count }, (_, i) => ({
    sourceId: "s",
    titleId,
    episodeNumber: i + 1,
    durationMs: 24 * 60_000,
    watched: true,
    updatedAt: at.getTime(),
    translation,
  }));
}

function day(daysAgo: number, completedCount: number, minutes = completedCount * 24): DailyActivity {
  const d = new Date(NOW - daysAgo * DAY);
  const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  return { date: key, completedCount, watchedMs: minutes * 60_000 };
}

const axis = (stats: ReturnType<typeof computeWatchStats>, id: string) => stats.axes.find((a) => a.id === id)!;

describe("computeWatchStats", () => {
  it("is all zeroes with nothing watched", () => {
    const stats = computeWatchStats({ library: [], episodes: [], ratings: [], activity: [], now: NOW });
    expect(stats.axes.every((a) => a.value === 0)).toBe(true);
    expect(stats.peakHour).toBeNull();
    expect(stats.topDub).toBeNull();
    expect(stats.ratingAverage).toBeNull();
  });

  it("reads the days: pace, best day, longest run of days", () => {
    const stats = computeWatchStats({ library: [], episodes: [], ratings: [], activity: [day(5, 2), day(4, 6), day(3, 1), day(1, 3)], now: NOW });
    expect(stats.activeDays).toBe(4);
    expect(stats.episodesPerActiveDay).toBe(3);
    expect(stats.bestDayEpisodes).toBe(6);
    expect(stats.longestStreak).toBe(3);
    expect(axis(stats, "binge").value).toBeCloseTo(0.5);
    expect(axis(stats, "consistency").raw).toBe(4);
  });

  it("counts a title as finished, dropped, or still in progress", () => {
    const old = new Date(NOW - 60 * DAY);
    const recent = new Date(NOW - DAY);
    const library = [
      entry("done", { episodeCount: 3 }),
      entry("caught-up", { status: "ongoing", episodeCount: 12, availableEpisodeCount: 2 }),
      entry("dropped", { episodeCount: 12 }, "dropped"),
      entry("abandoned", { episodeCount: 12 }),
      entry("current", { episodeCount: 12 }),
    ];
    const stats = computeWatchStats({
      library,
      episodes: [...episodes("done", 3, old), ...episodes("caught-up", 2, old), ...episodes("dropped", 1, old), ...episodes("abandoned", 4, old), ...episodes("current", 4, recent)],
      ratings: [],
      activity: [],
      now: NOW,
    });
    expect(stats.titlesStarted).toBe(5);
    expect(stats.titlesFinished).toBe(2);
    expect(stats.titlesDropped).toBe(1);
    // "current" is still being watched and stays out of it: 2 of 4.
    expect(axis(stats, "completion").raw).toBe(0.5);
  });

  it("weighs genres by episodes watched and merges their spelling", () => {
    const at = new Date(NOW - 60 * DAY);
    const stats = computeWatchStats({
      library: [entry("a", { genres: ["Экшен", "комедия"] }), entry("b", { genres: ["экшен"] }), entry("planned", { genres: ["Драма"] }, "planned")],
      episodes: [...episodes("a", 2, at), ...episodes("b", 6, at)],
      ratings: [],
      activity: [],
      now: NOW,
    });
    expect(stats.genres.map((g) => g.name)).toEqual(["Экшен", "Комедия"]);
    expect(stats.genres[0].share).toBeCloseTo(0.8);
  });

  it("finds the hour, the night share, the main dub and the average rating", () => {
    const stats = computeWatchStats({
      library: [],
      episodes: [...episodes("a", 3, new Date(2026, 9, 1, 23, 30), "AniLibria"), ...episodes("b", 1, new Date(2026, 9, 2, 14), "Studio Band")],
      ratings: [{ sourceId: "s", animeId: "a", rating: 8, ratedAt: 0 }, { sourceId: "s", animeId: "b", rating: 7, ratedAt: 0 }],
      activity: [],
      now: NOW,
    });
    expect(stats.peakHour).toBe(23);
    expect(axis(stats, "night").raw).toBe(0.75);
    expect(axis(stats, "night").value).toBe(1);
    expect(stats.topDub).toEqual({ name: "AniLibria", episodes: 3 });
    expect(stats.ratingAverage).toBe(7.5);
  });
});
