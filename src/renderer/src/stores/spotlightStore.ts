import { create } from "zustand";

// Whether the quick-search overlay (components/SearchSpotlight.tsx) is showing, and - when a title was
// opened from it - what to bring back if the user returns to the page they searched from.
interface SpotlightState {
  open: boolean;
  /** Text to start the panel with (set when it is reopened on the way back). */
  initialValue: string;
  /** Set while the panel is being brought back, so its filters are kept instead of reset. */
  restoring: boolean;
  /** Where the panel was opened from and what was typed, once a result has been opened. */
  returnTo: { href: string; value: string } | null;
  setOpen: (open: boolean) => void;
  toggle: () => void;
  setInitialValue: (value: string) => void;
  setRestoring: (restoring: boolean) => void;
  setReturnTo: (returnTo: { href: string; value: string } | null) => void;
}

export const useSpotlightStore = create<SpotlightState>()((set) => ({
  open: false,
  initialValue: "",
  restoring: false,
  returnTo: null,
  setOpen: (open) => set({ open }),
  toggle: () => set((state) => ({ open: !state.open })),
  setInitialValue: (initialValue) => set({ initialValue }),
  setRestoring: (restoring) => set({ restoring }),
  setReturnTo: (returnTo) => set({ returnTo }),
}));
