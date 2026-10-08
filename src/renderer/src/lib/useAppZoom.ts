import { useEffect } from "react";
import { hibiki } from "./hibiki";
import { useUiStore } from "@/stores/uiStore";
import { DEFAULT_ZOOM, zoomIn, zoomOut } from "./zoom";

/**
 * Ctrl/Cmd +, - and 0, applied to the whole window and remembered across restarts.
 *
 * Matched on `e.code`, not `e.key`: the physical key is what the shortcut means, so this keeps
 * working on a non-Latin layout (where `key` is a Cyrillic letter) and doesn't need a separate
 * case for shifted "+" versus unshifted "=".
 */
export function useAppZoom(): void {
  const zoomFactor = useUiStore((s) => s.zoomFactor);
  const setZoomFactor = useUiStore((s) => s.setZoomFactor);

  // Applied here rather than only in the key handler, so the saved factor is restored on launch
  // and stays applied across a reload.
  useEffect(() => {
    hibiki.zoom.set(zoomFactor);
  }, [zoomFactor]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!event.ctrlKey && !event.metaKey) return;
      // Reading the store imperatively - subscribing would re-bind this listener on every change,
      // and the handler only ever needs whatever the value is at the moment a key is pressed.
      const current = useUiStore.getState().zoomFactor;
      switch (event.code) {
        case "Equal":
        case "NumpadAdd":
          event.preventDefault();
          setZoomFactor(zoomIn(current));
          break;
        case "Minus":
        case "NumpadSubtract":
          event.preventDefault();
          setZoomFactor(zoomOut(current));
          break;
        case "Digit0":
        case "Numpad0":
          event.preventDefault();
          setZoomFactor(DEFAULT_ZOOM);
          break;
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [setZoomFactor]);
}
