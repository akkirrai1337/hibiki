import { create } from "zustand";
import { persist } from "zustand/middleware";

const MAX_QUERIES = 8;

// The last things searched for, newest first, so the empty search page has somewhere to start from.
interface SearchHistoryState {
  queries: string[];
  add: (query: string) => void;
  remove: (query: string) => void;
  clear: () => void;
}

export const useSearchHistoryStore = create<SearchHistoryState>()(
  persist(
    (set) => ({
      queries: [],
      add: (query) =>
        set((state) => {
          const trimmed = query.trim();
          if (!trimmed || state.queries[0]?.toLowerCase() === trimmed.toLowerCase()) return state;
          return { queries: [trimmed, ...state.queries.filter((q) => q.toLowerCase() !== trimmed.toLowerCase())].slice(0, MAX_QUERIES) };
        }),
      remove: (query) => set((state) => ({ queries: state.queries.filter((q) => q !== query) })),
      clear: () => set({ queries: [] }),
    }),
    { name: "hibiki-search-history" },
  ),
);
