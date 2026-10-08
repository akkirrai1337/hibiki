import { create } from "zustand";
import type { FilterValues } from "@shared/types";

// A request to open the catalog already filtered - "show me more of this genre" from a title page. The
// catalog owns its filters, so the page that asks leaves the request here and the catalog takes it
// (and clears it) once it is showing that source.
interface CatalogIntent {
  sourceId: string;
  filters: FilterValues;
}

interface CatalogIntentState {
  intent: CatalogIntent | null;
  request: (intent: CatalogIntent) => void;
  clear: () => void;
}

export const useCatalogIntentStore = create<CatalogIntentState>((set) => ({
  intent: null,
  request: (intent) => set({ intent }),
  clear: () => set({ intent: null }),
}));
