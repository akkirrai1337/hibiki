import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ArrowUpDown, Check } from "lucide-react";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { BottomSheet, SheetOption } from "@/components/BottomSheet";
import { isMobile } from "@/lib/mobile";

export interface CatalogModeOption<T extends string> {
  value: T;
  label: string;
}

/** A small dropdown for picking one of several modes (the catalog's sort orders). */
export function CatalogModeMenu<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T;
  options: CatalogModeOption<T>[];
  onChange: (value: T) => void;
}) {
  const popoverTheme = usePopoverTheme();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  // Closes on a press outside or Escape. There is deliberately no full-screen backdrop for this: it
  // would swallow the mouse wheel, and with the menu open the page behind it could not be scrolled.
  useEffect(() => {
    // The phone's sheet closes itself (scrim, Back, drag); this would close it on the tap that picks.
    if (!open || isMobile) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);
  const selected = options.find((option) => option.value === value) ?? options[0];

  if (!selected) return null;

  // Phone: the same button, the choices in a sheet from the bottom edge.
  if (isMobile) {
    return (
      <>
        <button onClick={() => setOpen(true)} className="flex items-center gap-2 rounded-full bg-text/[.07] px-3.5 py-2 text-[13px] font-semibold text-text/85 active:bg-text/[.12]">
          <ArrowUpDown className="h-4 w-4" strokeWidth={2} />
          <span className="max-w-[40vw] truncate">{selected.label}</span>
        </button>
        <BottomSheet open={open} onClose={() => setOpen(false)}>
          {options.map((option) => (
            <SheetOption key={option.value} label={option.label} selected={option.value === value} onClick={() => { onChange(option.value); setOpen(false); }} />
          ))}
        </BottomSheet>
      </>
    );
  }

  return (
    <div ref={root} className="relative">
      <button
        onClick={() => setOpen((current) => !current)}
        className="flex items-center gap-2 rounded-lg bg-text/[.06] px-3.5 py-2 text-sm font-semibold text-text/80 transition-colors hover:bg-text/[.1]"
      >
        <ArrowUpDown className="h-4 w-4" strokeWidth={2} />
        {selected.label}
      </button>
      <AnimatePresence>
        {open && (
          <>
            <motion.div
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ type: "spring", stiffness: 500, damping: 45 }}
              className="absolute right-0 top-11 z-50 w-52 overflow-hidden rounded-xl border border-border bg-app-popover shadow-2xl"
              style={popoverTheme}
            >
              {options.map((option) => (
                <button
                  key={option.value}
                  onClick={() => {
                    onChange(option.value);
                    setOpen(false);
                  }}
                  className="flex w-full items-center justify-between px-3.5 py-2.5 text-left text-sm text-text transition-colors hover:bg-text/[.06]"
                >
                  {option.label}
                  {option.value === value && <Check className="h-4 w-4 text-accent-text" strokeWidth={2.5} />}
                </button>
              ))}
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
