import { create } from "zustand";
import { persist } from "zustand/middleware";

// Remembers which player (and dub, where a source varies it inside one group) an explicit pick
// from the in-player settings menu landed on, per title *and* playback group.
//
// The watch route already resumed the player/dub saved on an episode's own watch_progress row,
// but that only ever helps for an episode you've already watched: every *new* episode of a title
// started back at the source's default ordering, so a title watched entirely through one provider
// still meant re-picking it on every single episode. Keyed by group (dub) rather than title alone
// because which providers exist - and which ones actually work - differs per dub.
const SELECTION_LIMIT = 300;

export interface PlayerSelection {
  playerName?: string | null;
  translation?: string | null;
}

function selectionKey(sourceId: string, animeId: string, groupId: string): string {
  return `${sourceId}:${animeId}:${groupId}`;
}

interface PlayerSelectionState {
  selections: Record<string, PlayerSelection>;
  remember: (sourceId: string, animeId: string, groupId: string, selection: PlayerSelection) => void;
  get: (sourceId: string, animeId: string, groupId: string) => PlayerSelection | undefined;
}

export const usePlayerSelectionStore = create<PlayerSelectionState>()(
  persist(
    (set, get) => ({
      selections: {},
      remember: (sourceId, animeId, groupId, selection) => set((state) => {
        const key = selectionKey(sourceId, animeId, groupId);
        // Re-inserted (not updated in place) so a repeated pick moves the key back to the end -
        // object key order is what the eviction below reads as recency.
        const { [key]: _previous, ...rest } = state.selections;
        const next: Record<string, PlayerSelection> = { ...rest, [key]: selection };
        // Unbounded, this grows one entry per title/dub ever watched and is rewritten to
        // localStorage on every pick. Oldest-first eviction is enough: insertion order here is
        // "least recently picked first", since re-remembering a key re-inserts it at the end.
        const keys = Object.keys(next);
        if (keys.length > SELECTION_LIMIT) {
          for (const stale of keys.slice(0, keys.length - SELECTION_LIMIT)) delete next[stale];
        }
        return { selections: next };
      }),
      get: (sourceId, animeId, groupId) => get().selections[selectionKey(sourceId, animeId, groupId)],
    }),
    { name: "hibiki-player-selections" },
  ),
);
