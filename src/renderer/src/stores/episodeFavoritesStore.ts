import { create } from "zustand";
import { persist } from "zustand/middleware";

// Purely a visual bookmark on the episode chip (a filled star) - no separate favorites list or
// screen anywhere reads this, it's just "make it obvious at a glance which episodes I flagged".
// A plain `Record<string, true>` rather than a `Set` since zustand's persist middleware just
// JSON-stringifies the store as-is, and `JSON.stringify(new Set())` serializes to `"{}"` - a Set
// wouldn't survive a reload at all.
interface EpisodeFavoritesState {
  favorites: Record<string, true>;
  toggleFavorite: (key: string) => void;
}

export function episodeFavoriteKey(sourceId: string, animeId: string, episodeId: string): string {
  return `${sourceId}:${animeId}:${episodeId}`;
}

export const useEpisodeFavoritesStore = create<EpisodeFavoritesState>()(
  persist(
    (set) => ({
      favorites: {},
      toggleFavorite: (key) =>
        set((s) => {
          const next = { ...s.favorites };
          if (next[key]) delete next[key];
          else next[key] = true;
          return { favorites: next };
        }),
    }),
    { name: "hibiki-episode-favorites" },
  ),
);
