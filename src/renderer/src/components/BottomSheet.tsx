import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, animate, motion, useDragControls, useIsPresent, useMotionValue, type MotionValue } from "motion/react";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { useBackHandler } from "@/lib/mobile";
import { cn } from "@/lib/cn";

/**
 * The phone's menu and panel: rises from the bottom edge, where the thumb already is, over a scrim.
 * Closes by a tap on the scrim, by Back, or by pulling it down by its handle (or anywhere on its
 * header), or by pulling its body down while the body is scrolled to the top. Desktop keeps its own
 * dropdowns and drawers; this is only used where `isMobile`.
 *
 * `prewarm` builds the sheet hidden as soon as the page is idle and keeps it built while closed: a
 * heavy body (the filters' hundreds of chips) otherwise took its first frames to create, and the
 * rise started with a visible hitch.
 */
export function BottomSheet({
  open,
  onClose,
  title,
  footer,
  children,
  className,
  prewarm = false,
}: {
  open: boolean;
  onClose: () => void;
  title?: React.ReactNode;
  /** Pinned under the scrolling body - actions such as Reset / Done. */
  footer?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  prewarm?: boolean;
}) {
  const popoverTheme = usePopoverTheme();
  const drag = useDragControls();
  const y = useMotionValue(0);
  useBackHandler(open, onClose);

  // Prewarmed: built once the page is idle (or when first opened), then only hidden.
  const [built, setBuilt] = useState(open);
  const [hidden, setHidden] = useState(!open);
  useEffect(() => {
    if (open) {
      setBuilt(true);
      setHidden(false);
    }
  }, [open]);
  useEffect(() => {
    if (!prewarm || built) return;
    const idle = window.requestIdleCallback?.(() => setBuilt(true), { timeout: 2000 });
    const timer = idle === undefined ? window.setTimeout(() => setBuilt(true), 1000) : undefined;
    return () => {
      if (idle !== undefined) window.cancelIdleCallback(idle);
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [prewarm, built]);

  const sheet = (props: React.ComponentProps<typeof motion.div>) => (
    <motion.div
      role="dialog"
      aria-modal="true"
      aria-hidden={!open || undefined}
      transition={{ type: "spring", stiffness: 420, damping: 40 }}
      drag="y"
      dragListener={false}
      dragControls={drag}
      dragConstraints={{ top: 0, bottom: 0 }}
      dragElastic={{ top: 0, bottom: 0.6 }}
      onDragEnd={(_, info) => {
        if (info.offset.y > CLOSE_DISTANCE || info.velocity.y > CLOSE_VELOCITY) onClose();
      }}
      style={{ ...popoverTheme, y, willChange: prewarm ? "transform" : undefined, paddingBottom: "var(--safe-bottom)" }}
      className={cn("absolute inset-x-0 bottom-0 flex max-h-[85vh] flex-col rounded-t-[1.5rem] border-t border-border bg-app-popover", className)}
      {...props}
    >
      <div className="shrink-0 touch-none select-none pb-1 pt-2.5" onPointerDown={(event) => drag.start(event)}>
        <div className="mx-auto h-1 w-10 rounded-full bg-text/20" />
        {title && <div className="px-5 pb-1 pt-3 text-[15px] font-bold text-text">{title}</div>}
      </div>
      <SheetBody y={y} onClose={onClose}>{children}</SheetBody>
      {/* A visible rule, so the list reads as passing under the footer rather than ending in the
          air. (A fade mask instead cost a GPU pass every frame of the rise.) */}
      {footer && <div className="shrink-0 border-t border-text/10 px-4 py-3">{footer}</div>}
    </motion.div>
  );

  if (prewarm) {
    if (!built) return null;
    return createPortal(
      <div className={cn("fixed inset-0 z-[80]", !open && "pointer-events-none")} data-parked={hidden || undefined}>
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: open ? 1 : 0 }}
          transition={{ duration: 0.2 }}
          onClick={onClose}
          className="absolute inset-0 bg-black/55"
        />
        {sheet({
          initial: { y: "100%" },
          animate: open ? { y: 0 } : { y: "100%", transition: LEAVE },
          onAnimationComplete: () => {
            if (!open) setHidden(true);
          },
        })}
      </div>,
      document.body,
    );
  }

  return createPortal(
    <AnimatePresence>
      {open && (
        <SheetLayer>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
            className="absolute inset-0 bg-black/55"
          />
          {sheet({ initial: { y: "100%" }, animate: { y: 0 }, exit: { y: "100%", transition: LEAVE } })}
        </SheetLayer>
      )}
    </AnimatePresence>,
    document.body,
  );
}

// Leaves on a short curve: the spring's long settling tail kept the closed sheet on the page for most
// of a second.
const LEAVE = { duration: 0.22, ease: [0.4, 0, 1, 1] as const };

/** The full-screen layer; lets touches through once the sheet is leaving, so the page under it scrolls at once. */
function SheetLayer({ children }: { children: React.ReactNode }) {
  const present = useIsPresent();
  return <div className={cn("fixed inset-0 z-[80]", !present && "pointer-events-none")}>{children}</div>;
}

const CLOSE_DISTANCE = 90;
const CLOSE_VELOCITY = 500;

/**
 * The sheet's scrolling body. A downward pull that starts with the body at its top moves the sheet
 * instead of the list, as the handle does; any other gesture scrolls. Touch events, not the pointer
 * drag, because the browser takes a vertical pan over and cancels the pointer once it starts.
 */
function SheetBody({ y, onClose, children }: { y: MotionValue<number>; onClose: () => void; children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    const body = ref.current;
    if (!body) return;
    let startY = 0;
    let mode: "undecided" | "pull" | "scroll" = "scroll";
    let last = { y: 0, t: 0 };
    let velocity = 0;
    const onStart = (event: TouchEvent) => {
      startY = event.touches[0].clientY;
      mode = body.scrollTop <= 0 ? "undecided" : "scroll";
      last = { y: startY, t: event.timeStamp };
      velocity = 0;
    };
    const onMove = (event: TouchEvent) => {
      if (mode === "scroll") return;
      const current = event.touches[0].clientY;
      const dy = current - startY;
      if (mode === "undecided") {
        if (Math.abs(dy) < 4) return;
        mode = dy > 0 && body.scrollTop <= 0 ? "pull" : "scroll";
        if (mode === "scroll") return;
      }
      event.preventDefault();
      const dt = event.timeStamp - last.t;
      if (dt > 0) velocity = ((current - last.y) / dt) * 1000;
      last = { y: current, t: event.timeStamp };
      y.set(Math.max(0, dy));
    };
    const onEnd = () => {
      if (mode !== "pull") return;
      mode = "scroll";
      if (y.get() > CLOSE_DISTANCE || velocity > CLOSE_VELOCITY) closeRef.current();
      else animate(y, 0, { type: "spring", stiffness: 420, damping: 40 });
    };
    body.addEventListener("touchstart", onStart, { passive: true });
    body.addEventListener("touchmove", onMove, { passive: false });
    body.addEventListener("touchend", onEnd);
    body.addEventListener("touchcancel", onEnd);
    return () => {
      body.removeEventListener("touchstart", onStart);
      body.removeEventListener("touchmove", onMove);
      body.removeEventListener("touchend", onEnd);
      body.removeEventListener("touchcancel", onEnd);
    };
  }, [y]);

  return (
    <div ref={ref} className="no-scrollbar min-h-0 flex-1 overflow-y-auto overscroll-contain px-2 pb-3">
      {children}
    </div>
  );
}

/** One row of a sheet's list: a full-width tap target with an optional icon and trailing mark. */
export function SheetOption({ icon, label, detail, selected, danger, onClick }: { icon?: React.ReactNode; label: React.ReactNode; detail?: React.ReactNode; selected?: boolean; danger?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex min-h-[3rem] w-full items-center gap-3.5 rounded-xl px-3.5 py-2.5 text-left text-[15px] transition-colors active:bg-text/[.08]",
        danger ? "text-rose-400" : "text-text",
        selected && "bg-text/[.06] font-semibold",
      )}
    >
      {icon && <span className={cn("flex shrink-0", danger ? "text-rose-400" : "text-muted")}>{icon}</span>}
      <span className="min-w-0 flex-1">
        <span className="line-clamp-1">{label}</span>
        {detail && <span className="mt-0.5 line-clamp-1 text-xs font-normal text-muted">{detail}</span>}
      </span>
      {selected && <span className="h-2 w-2 shrink-0 rounded-full bg-accent" />}
    </button>
  );
}
