import { create } from "zustand";
import { EMPTY_SEARCH_FILTERS, type SearchFilters } from "@/lib/searchFilters";

// Not persisted on purpose - filters are tied to one source's id space, and the source can change
// between sessions (or within one, via the sources picker), so starting fresh each time is safer
// than restoring aliases that may no longer mean anything.
interface SearchFiltersState {
  filters: SearchFilters;
  setFilters: (filters: SearchFilters) => void;
  resetFilters: () => void;
}

export const useSearchFiltersStore = create<SearchFiltersState>()((set) => ({
  filters: EMPTY_SEARCH_FILTERS,
  setFilters: (filters) => set({ filters }),
  resetFilters: () => set({ filters: EMPTY_SEARCH_FILTERS }),
}));
