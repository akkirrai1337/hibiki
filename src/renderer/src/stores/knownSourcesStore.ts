import { create } from "zustand";
import { persist } from "zustand/middleware";

// Remembers every source's display name/icon the app has ever seen installed, keyed by source id
// - same idea as Android's AppPreferences.rememberAnimeSourceAppearances(). A continue-watching or
// library card whose source was later uninstalled still has *something* to show in its source
// badge (see AnimeCard's `missing` styling) instead of falling back to a bare, unrecognizable id.
interface KnownSourcesState {
  byId: Record<string, { name: string; iconUrl: string | null }>;
  remember: (sources: { id: string; name: string; iconUrl?: string | null }[]) => void;
}

export const useKnownSourcesStore = create<KnownSourcesState>()(
  persist(
    (set, get) => ({
      byId: {},
      remember: (sources) => {
        const byId = { ...get().byId };
        let changed = false;
        for (const s of sources) {
          const existing = byId[s.id];
          const iconUrl = s.iconUrl ?? null;
          if (!existing || existing.name !== s.name || existing.iconUrl !== iconUrl) {
            byId[s.id] = { name: s.name, iconUrl };
            changed = true;
          }
        }
        if (changed) set({ byId });
      },
    }),
    { name: "hibiki-known-sources" },
  ),
);
