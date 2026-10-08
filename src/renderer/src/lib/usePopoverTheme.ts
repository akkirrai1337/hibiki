import { useUiStore } from "@/stores/uiStore";
import { BACKGROUND_THEME_PRESETS } from "@/lib/theme";

// Subscribe at the popup itself: portals and already-mounted layouts must not
// depend on a separate effect installing a CSS variable on the document.
export function usePopoverTheme() {
  const theme = useUiStore((state) => state.backgroundTheme);
  const gradient = BACKGROUND_THEME_PRESETS.find((preset) => preset.id === theme)?.gradient;
  return {
    backgroundImage: gradient
      ? `linear-gradient(rgb(var(--color-surface) / .8), rgb(var(--color-surface) / .8)), ${gradient}`
      : "none",
  };
}
