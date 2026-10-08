import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { Check, ChevronDown, Mic } from "lucide-react";
import { cn } from "@/lib/cn";
import { BottomSheet, SheetOption } from "@/components/BottomSheet";
import { isMobile } from "@/lib/mobile";
import type { PlaybackGroup } from "@shared/types";

// A comfortable default cap - but the button can sit anywhere in the viewport (this dropdown gets
// used both in a normal scrollable page and inside a modal dialog), so the panel must never just
// assume this much room actually exists below/above it.
const DROPDOWN_MAX_HEIGHT = 320;
const DROPDOWN_VIEWPORT_MARGIN = 16;

// A dub/translation picker for a title's own PlaybackGroup list - originally the anime detail
// page's own episode-list header, pulled out here once the download dialog needed the exact same
// picker (previously a wall of pill buttons there, one per dub, which didn't scale past a handful
// of translation teams the way this dropdown already did).
export function GroupDropdown({ groups, activeGroupId, onSelect, align = "left" }: { groups: PlaybackGroup[]; activeGroupId?: string; onSelect: (id: string) => void; align?: "left" | "right" }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [placement, setPlacement] = useState<{ direction: "down" | "up"; maxHeight: number }>({ direction: "down", maxHeight: DROPDOWN_MAX_HEIGHT });
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const active = groups.find((g) => g.id === activeGroupId) ?? groups[0];

  // A `fixed inset-0` click-catcher (this dropdown's old approach) sits on top of the whole page
  // while open and swallows every wheel event over it, not just clicks - blocking the title page
  // from scrolling at all until the dropdown closes. A window-level outside-click listener (same
  // pattern the player's own settings menu and TitleBar's filters panel already use) closes it
  // just the same without ever intercepting scroll.
  useEffect(() => {
    // The phone's sheet closes itself (scrim, Back, drag); this would close it on the tap that picks.
    if (!open || isMobile) return;
    const onPointerDown = (e: PointerEvent) => {
      if (e.target instanceof Node && (buttonRef.current?.contains(e.target) || panelRef.current?.contains(e.target))) return;
      setOpen(false);
    };
    window.addEventListener("pointerdown", onPointerDown, { capture: true });
    return () => window.removeEventListener("pointerdown", onPointerDown, { capture: true });
  }, [open]);

  const toggleOpen = () => {
    if (!open) {
      const rect = buttonRef.current?.getBoundingClientRect();
      if (rect) {
        const spaceBelow = window.innerHeight - rect.bottom - DROPDOWN_VIEWPORT_MARGIN;
        const spaceAbove = rect.top - DROPDOWN_VIEWPORT_MARGIN;
        const direction = spaceBelow >= 160 || spaceBelow >= spaceAbove ? "down" : "up";
        const available = direction === "down" ? spaceBelow : spaceAbove;
        setPlacement({ direction, maxHeight: Math.max(120, Math.min(DROPDOWN_MAX_HEIGHT, available)) });
      }
    }
    setOpen((v) => !v);
  };

  // Phone: the same button; the dubs in a sheet from the bottom edge, where a long list has room.
  if (isMobile) {
    return (
      <>
        <button type="button" onClick={() => setOpen(true)} className="flex min-w-0 max-w-full items-center gap-1.5 rounded-full bg-text/[.07] px-3 py-1.5 text-[13px] font-semibold text-text/85 active:bg-text/[.12]">
          <Mic className="h-3.5 w-3.5 shrink-0 text-muted" strokeWidth={2} />
          <span className="truncate">{active.title}{active.qualityLabel ? ` · ${active.qualityLabel}` : ""}</span>
          <ChevronDown className="h-4 w-4 shrink-0 text-muted" strokeWidth={2} />
        </button>
        <BottomSheet open={open} onClose={() => setOpen(false)} title={<span className="flex items-baseline gap-2">{t("detail.downloadDialog.translation")}<span className="text-sm font-semibold tabular-nums text-muted">{groups.length}</span></span>}>
          {groups.map((g) => (
            <SheetOption key={g.id} label={g.title} detail={g.qualityLabel ?? undefined} selected={g.id === active.id} onClick={() => { onSelect(g.id); setOpen(false); }} />
          ))}
        </BottomSheet>
      </>
    );
  }

  return (
    <div className="relative inline-block">
      <button
        ref={buttonRef}
        type="button"
        onClick={toggleOpen}
        className="flex items-center gap-2 rounded-lg bg-text/[.06] px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.1]"
      >
        <Mic className="h-4 w-4 text-muted" strokeWidth={2} />
        {active.title}{active.qualityLabel ? ` · ${active.qualityLabel}` : ""}
        <ChevronDown className={cn("h-4 w-4 text-muted transition-transform", open && "rotate-180")} strokeWidth={2} />
      </button>
      <AnimatePresence>
        {open && (
          // No `scale` - Chromium re-rasterizes small bold text at a slightly different subpixel
          // size every frame when a transform:scale() animates over it, reading as the text
          // shimmering/shifting while the panel is still settling in. A plain fade + slide has no
          // such effect since nothing's actually changing size, just position.
          <motion.div
            ref={panelRef}
            initial={{ opacity: 0, y: placement.direction === "down" ? -4 : 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: placement.direction === "down" ? -4 : 4 }}
            transition={{ type: "spring", stiffness: 500, damping: 45 }}
            style={{ maxHeight: placement.maxHeight }}
            className={cn(
              "absolute z-50 w-64 overflow-y-auto overflow-x-hidden overscroll-contain rounded-xl border border-border bg-surface shadow-2xl",
              align === "right" ? "right-0" : "left-0",
              placement.direction === "down" ? "top-11" : "bottom-11",
            )}
          >
            {groups.map((g) => (
              <button
                key={g.id}
                type="button"
                onClick={() => { onSelect(g.id); setOpen(false); }}
                className="flex w-full items-center justify-between gap-3 px-3.5 py-2.5 text-left text-sm text-text transition-colors hover:bg-text/[.06]"
              >
                <span className="truncate">{g.title}{g.qualityLabel ? ` · ${g.qualityLabel}` : ""}</span>
                {g.id === active.id && <Check className="h-4 w-4 shrink-0 text-accent-text" strokeWidth={2.5} />}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
