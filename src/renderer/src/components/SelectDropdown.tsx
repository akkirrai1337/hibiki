import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Check, ChevronDown } from "lucide-react";
import { cn } from "@/lib/cn";

const DROPDOWN_MAX_HEIGHT = 320;
const DROPDOWN_VIEWPORT_MARGIN = 16;

export interface SelectDropdownOption {
  id: string;
  label: string;
  /** Drawn before the label, in the button and in the list (a source's icon). */
  icon?: React.ReactNode;
  /** A short tag after the label (APK). */
  badge?: string;
}

function OptionContent({ option }: { option: SelectDropdownOption }) {
  return (
    <span className="flex min-w-0 items-center gap-2.5">
      {option.icon && <span className="flex h-5 w-5 shrink-0 items-center justify-center overflow-hidden rounded-full bg-text/[.06]">{option.icon}</span>}
      <span className="truncate">{option.label}</span>
      {option.badge && <span className="shrink-0 rounded bg-text/[.08] px-1 py-px text-[9.5px] font-bold tracking-wide text-muted">{option.badge}</span>}
    </span>
  );
}

/**
 * A themed stand-in for a native `<select>`, same panel look as GroupDropdown - a native select's
 * own dropdown popup is drawn by the OS/Chromium, not by this app's CSS, so it can't be made to
 * follow the app's own theme at all: on a dark theme with a light OS popup style, its default text
 * color reads as near-invisible against the (also default, uncontrollable) light popup background.
 */
export function SelectDropdown({ options, value, onChange, placeholder, disabled, className }: {
  options: SelectDropdownOption[];
  value: string;
  onChange: (id: string) => void;
  placeholder: string;
  disabled?: boolean;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [placement, setPlacement] = useState<{ direction: "down" | "up"; maxHeight: number }>({ direction: "down", maxHeight: DROPDOWN_MAX_HEIGHT });
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const active = options.find((o) => o.id === value);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (e.target instanceof Node && (buttonRef.current?.contains(e.target) || panelRef.current?.contains(e.target))) return;
      setOpen(false);
    };
    window.addEventListener("pointerdown", onPointerDown, { capture: true });
    return () => window.removeEventListener("pointerdown", onPointerDown, { capture: true });
  }, [open]);

  const toggleOpen = () => {
    if (disabled) return;
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

  return (
    <div className={cn("relative", className)}>
      <button
        ref={buttonRef}
        type="button"
        onClick={toggleOpen}
        disabled={disabled}
        className="flex w-full items-center justify-between gap-2 rounded-lg border border-border bg-text/[.04] px-3 py-2 text-left text-sm text-text outline-none transition-colors focus:border-accent/70 disabled:opacity-50"
      >
        {active ? <OptionContent option={active} /> : <span className="truncate">{placeholder}</span>}
        <ChevronDown className={cn("h-4 w-4 shrink-0 text-muted transition-transform", open && "rotate-180")} strokeWidth={2} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            ref={panelRef}
            initial={{ opacity: 0, y: placement.direction === "down" ? -4 : 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: placement.direction === "down" ? -4 : 4 }}
            transition={{ type: "spring", stiffness: 500, damping: 45 }}
            style={{ maxHeight: placement.maxHeight }}
            className={cn(
              "absolute left-0 right-0 z-50 overflow-y-auto overflow-x-hidden overscroll-contain rounded-xl border border-border bg-surface shadow-2xl",
              placement.direction === "down" ? "top-[calc(100%+4px)]" : "bottom-[calc(100%+4px)]",
            )}
          >
            {options.map((option) => (
              <button
                key={option.id}
                type="button"
                onClick={() => { onChange(option.id); setOpen(false); }}
                className="flex w-full items-center justify-between gap-3 px-3.5 py-2.5 text-left text-sm text-text transition-colors hover:bg-text/[.06]"
              >
                <OptionContent option={option} />
                {option.id === value && <Check className="h-4 w-4 shrink-0 text-accent-text" strokeWidth={2.5} />}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
