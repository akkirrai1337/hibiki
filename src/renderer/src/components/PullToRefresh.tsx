import { useEffect, useRef, useState } from "react";
import type { InfiniteData, QueryClient, QueryKey } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { cn } from "@/lib/cn";
import { isMobile } from "@/lib/mobile";

// How far the finger has to pull the page (after the rubber-band damping) to refresh on release,
// where the indicator rests while the refresh runs, and its own size.
const THRESHOLD = 72;
const HOLD = 60;
const SIZE = 40;
// Shown at least this long, so a refresh served from a warm cache still reads as one.
const MIN_SPIN_MS = 450;

/**
 * Phone only: pull the page down from its top to refresh it, as Android apps do. Put it anywhere in
 * a page; it works on the page's own scroll box (the persisted page's, or the shared one of a param
 * route) and draws a round indicator that follows the finger and spins while `onRefresh` runs.
 * Sideways swipes (a carousel, a poster row) and pulls that start below the top are left alone.
 */
export function PullToRefresh({ onRefresh, disabled = false, offset = 0 }: {
  onRefresh: () => unknown;
  disabled?: boolean;
  /** Where the indicator settles below the status bar - clear of a bar stuck to the page's top. */
  offset?: number;
}) {
  const anchor = useRef<HTMLDivElement>(null);
  const indicator = useRef<HTMLDivElement>(null);
  const icon = useRef<SVGSVGElement>(null);
  const refresh = useRef(onRefresh);
  refresh.current = onRefresh;
  const [spinning, setSpinning] = useState(false);

  useEffect(() => {
    if (!isMobile || disabled) return;
    const box = anchor.current?.closest<HTMLElement>("[data-page-scroll], [data-scroll-restoration-id]");
    const el = indicator.current;
    if (!box || !el) return;

    const place = (distance: number, animate: boolean) => {
      el.style.transition = animate ? "transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1), opacity 220ms" : "none";
      el.style.transform = `translateY(${distance - SIZE}px)`;
      el.style.opacity = String(Math.min(1, distance / (THRESHOLD * 0.6)));
      if (icon.current) icon.current.style.transform = `rotate(${(distance / THRESHOLD) * 270}deg)`;
    };

    let startX = 0;
    let startY = 0;
    // null: not decided yet; false: this touch is not a pull (sideways, upwards, or mid-page).
    let pulling: boolean | null = false;
    let distance = 0;
    let busy = false;

    const onStart = (event: TouchEvent) => {
      if (busy || box.scrollTop > 0 || event.touches.length !== 1) {
        pulling = false;
        return;
      }
      startX = event.touches[0].clientX;
      startY = event.touches[0].clientY;
      pulling = null;
      distance = 0;
    };
    const onMove = (event: TouchEvent) => {
      if (pulling === false) return;
      const dx = event.touches[0].clientX - startX;
      const dy = event.touches[0].clientY - startY;
      if (pulling === null) {
        if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) pulling = false;
        else if (dy > 8) pulling = true;
        else if (dy < -8) pulling = false;
        if (!pulling) return;
      }
      if (box.scrollTop > 0) {
        pulling = false;
        place(0, true);
        return;
      }
      // Rubber band: the further down, the harder it pulls.
      distance = Math.max(0, Math.min(THRESHOLD * 1.6, (dy - 8) * 0.5));
      place(distance, false);
      if (event.cancelable) event.preventDefault();
    };
    const onEnd = () => {
      if (!pulling) return;
      pulling = false;
      if (distance < THRESHOLD) {
        place(0, true);
        return;
      }
      busy = true;
      setSpinning(true);
      place(HOLD, true);
      const started = Date.now();
      void Promise.resolve()
        .then(() => refresh.current())
        .catch(() => undefined)
        .then(() => new Promise((resolve) => setTimeout(resolve, Math.max(0, MIN_SPIN_MS - (Date.now() - started)))))
        .then(() => {
          busy = false;
          setSpinning(false);
          place(0, true);
        });
    };

    box.addEventListener("touchstart", onStart, { passive: true });
    box.addEventListener("touchmove", onMove, { passive: false });
    box.addEventListener("touchend", onEnd);
    box.addEventListener("touchcancel", onEnd);
    return () => {
      box.removeEventListener("touchstart", onStart);
      box.removeEventListener("touchmove", onMove);
      box.removeEventListener("touchend", onEnd);
      box.removeEventListener("touchcancel", onEnd);
    };
  }, [disabled]);

  if (!isMobile) return null;
  return (
    <div ref={anchor} aria-hidden>
      <div
        ref={indicator}
        className="pointer-events-none fixed left-1/2 z-[35] -ml-5 flex h-10 w-10 items-center justify-center rounded-full bg-app-popover text-text shadow-[0_4px_16px_rgba(0,0,0,0.35)] ring-1 ring-border"
        style={{ top: `calc(var(--safe-top) + ${offset}px)`, transform: `translateY(${-SIZE}px)`, opacity: 0 }}
      >
        <RefreshCw ref={icon} className={cn("h-5 w-5", spinning && "animate-spin")} strokeWidth={2.25} />
      </div>
    </div>
  );
}

/**
 * Refreshes a paged list from its start: drops every page but the first and fetches that one again,
 * rather than refetching each page loaded so far one after another.
 */
export async function refetchFromFirstPage(queryClient: QueryClient, queryKey: QueryKey): Promise<void> {
  queryClient.setQueryData<InfiniteData<unknown, unknown>>(queryKey, (data) => (
    data && { pages: data.pages.slice(0, 1), pageParams: data.pageParams.slice(0, 1) }
  ));
  await queryClient.refetchQueries({ queryKey, exact: true });
}
