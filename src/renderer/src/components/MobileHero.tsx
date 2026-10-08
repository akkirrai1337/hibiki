import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Radio, Star } from "lucide-react";
import type { HeroSlide } from "@/components/Hero";
import { cn } from "@/lib/cn";

const INTERVAL_MS = 7000;
// How far a finger has to travel, as a share of the hero's width, before letting go turns the slide.
const SWIPE_THRESHOLD = 0.18;
/** Sideways travel (px) under which a gesture still counts as a tap. */
const TAP_SLOP = 16;

const HERO_SLIDE_MASK: React.CSSProperties = {
  maskImage: "linear-gradient(to bottom, #000 35%, rgba(0,0,0,0.35) 72%, transparent)",
  WebkitMaskImage: "linear-gradient(to bottom, #000 35%, rgba(0,0,0,0.35) 72%, transparent)",
};

/**
 * The phone's hero: the poster fills the top of the screen, running under the status bar, with the
 * title and the one action at the bottom where a thumb reaches. Slides follow the finger and turn
 * on a swipe; between touches they advance on their own, as on desktop.
 */
export function MobileHero({ slides, label, sourceName }: { slides: HeroSlide[]; label: string; sourceName: string }) {
  const { t } = useTranslation();
  const [index, setIndex] = useState(0);
  const [touching, setTouching] = useState(false);
  const slideKey = slides.map((slide) => slide.key).join(",");
  const track = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; y: number; dx: number; horizontal: boolean | null } | null>(null);
  // Set by a swipe, so the click the browser sends after it does not also open the slide.
  const swiped = useRef(false);
  const pendingTap = useRef<number | null>(null);
  useEffect(() => setIndex(0), [slideKey]);

  useEffect(() => {
    if (touching || slides.length <= 1) return;
    const id = setTimeout(() => setIndex((i) => (i + 1) % slides.length), INTERVAL_MS);
    return () => clearTimeout(id);
  }, [touching, index, slides.length, slideKey]);

  // The track follows the finger directly (no React render per move); letting go snaps it to a slide
  // through the CSS transition on `transform`.
  const place = (dx: number, animated: boolean) => {
    const el = track.current;
    if (!el) return;
    el.style.transition = animated ? "transform 380ms cubic-bezier(.2,.8,.2,1)" : "none";
    el.style.transform = `translate3d(calc(${-index * 100}% + ${dx}px), 0, 0)`;
  };
  useEffect(() => place(0, true));

  const current = slides[Math.min(index, slides.length - 1)];
  if (!current) return null;

  return (
    <div
      className="relative -mt-[var(--safe-top)] overflow-hidden"
      // pan-y: the page still scrolls vertically under a finger; sideways movement comes here.
      style={{ height: "calc(min(46vh, 420px) + var(--safe-top))", minHeight: 340, touchAction: "pan-y" }}
      onPointerDown={(event) => {
        drag.current = { x: event.clientX, y: event.clientY, dx: 0, horizontal: null };
        swiped.current = false;
        setTouching(true);
      }}
      onPointerMove={(event) => {
        const state = drag.current;
        if (!state) return;
        const dx = event.clientX - state.x;
        const dy = event.clientY - state.y;
        // Decide once per gesture whether it is a swipe or the start of a vertical scroll.
        if (state.horizontal === null && Math.abs(dx) + Math.abs(dy) > 8) state.horizontal = Math.abs(dx) > Math.abs(dy);
        if (!state.horizontal) return;
        // Resistance past the first and last slide.
        const atEdge = (index === 0 && dx > 0) || (index === slides.length - 1 && dx < 0);
        state.dx = atEdge ? dx / 3 : dx;
        place(state.dx, false);
      }}
      onPointerUp={(event) => {
        const state = drag.current;
        drag.current = null;
        setTouching(false);
        // A tap whose finger slid a little is still a tap: the button under it must open. The browser
        // sends no click once the finger has moved past its own small slop, so the tap is clicked
        // here - unless the browser's click turns up after all (see onClickCapture).
        if (state && state.horizontal !== null && Math.hypot(event.clientX - state.x, event.clientY - state.y) < TAP_SLOP) {
          place(0, true);
          const target = (event.target as Element).closest<HTMLElement>("a, button");
          if (target) {
            pendingTap.current = window.setTimeout(() => {
              pendingTap.current = null;
              target.click();
            }, 60);
          }
          return;
        }
        if (!state?.horizontal) return;
        swiped.current = true;
        const width = event.currentTarget.clientWidth || 1;
        if (state.dx < -width * SWIPE_THRESHOLD && index < slides.length - 1) setIndex(index + 1);
        else if (state.dx > width * SWIPE_THRESHOLD && index > 0) setIndex(index - 1);
        else place(0, true);
      }}
      onPointerCancel={() => {
        drag.current = null;
        setTouching(false);
        place(0, true);
      }}
      onClickCapture={(event) => {
        if (pendingTap.current !== null) {
          window.clearTimeout(pendingTap.current);
          pendingTap.current = null;
        }
        if (!swiped.current) return;
        swiped.current = false;
        event.preventDefault();
        event.stopPropagation();
      }}
    >
      <div ref={track} className="flex h-full will-change-transform">
        {slides.map((slide, i) => (
          // Each slide's artwork dissolves (a mask) into whatever is behind the page - a fade to the
          // plain page colour cut off hard against a background theme. Per slide, not on the track:
          // a mask clips to its box, and the track's other slides lie outside it.
          <div key={slide.key} className="relative h-full w-full shrink-0" aria-hidden={i !== index} style={HERO_SLIDE_MASK}>
            {slide.posterUrl && <img src={slide.posterUrl} alt="" draggable={false} className="absolute inset-0 h-full w-full object-cover object-[center_18%]" />}
          </div>
        ))}
      </div>
      {/* Status-bar legibility at the top; under the text, a wash of the page colour that is gone
          again by the bottom edge, so that edge meets the page with no seam. */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-black/60 to-transparent" />
      <div className="pointer-events-none absolute inset-0" style={{ background: "linear-gradient(to bottom, transparent 30%, rgb(var(--color-bg) / 0.6) 68%, rgb(var(--color-bg) / 0.35) 88%, transparent)" }} />

      <div className="absolute inset-x-0 bottom-0 px-4 pb-4">
        <div className="mb-2 flex items-center gap-1.5 text-[10.5px] font-bold uppercase tracking-[.14em] text-accent-text">
          {label}
          <span className="flex h-3.5 w-3.5 shrink-0 items-center justify-center overflow-hidden rounded-full bg-white/15">
            {current.sourceIconUrl ? <img src={current.sourceIconUrl} alt="" className="h-full w-full object-cover" /> : <Radio className="h-2 w-2 text-zinc-300" strokeWidth={2} />}
          </span>
          <span className="truncate">{sourceName}</span>
        </div>
        <h1 className="line-clamp-2 text-[28px] font-bold leading-[1.08] tracking-[-.03em] text-text">{current.title}</h1>
        <div className="mt-2 flex items-center gap-1.5 overflow-hidden whitespace-nowrap text-[13px] text-text/75">
          {current.rating && <span className="flex shrink-0 items-center gap-1 font-semibold text-amber-400"><Star className="h-3 w-3 fill-current" strokeWidth={0} />{formatRating(current.rating.value)}</span>}
          {[current.type?.toUpperCase() || t("common.typeFallback"), current.year, ...(current.genres ?? []).slice(0, 2)].filter(Boolean).map((part, i) => (
            <span key={i} className={cn("truncate", i === 0 && !current.rating ? "" : "before:mr-1.5 before:text-text/40 before:content-['·']")}>{part}</span>
          ))}
        </div>
        <div className="mt-4 flex items-center justify-between gap-4">
          <div className="[&>a]:rounded-full [&>a]:px-6">{current.action}</div>
          {slides.length > 1 && (
            <div className="flex items-center gap-1.5">
              {slides.map((slide, i) => (
                <span key={slide.key} className={cn("h-1 overflow-hidden rounded-full bg-text/25 transition-[width] duration-300", i === index ? "w-6" : "w-1.5")}>
                  {/* Restarts with the timer: a touch holds the slide, letting go gives it a full interval again. */}
                  {i === index && !touching && <span key={index} style={{ animationDuration: `${INTERVAL_MS}ms` }} className="block h-full w-full origin-left animate-[hero-progress_linear_forwards] rounded-full bg-accent" />}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function formatRating(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/0$/, "");
}
