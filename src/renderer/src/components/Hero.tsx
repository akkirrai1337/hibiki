import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AnimatePresence, animate, motion, useMotionValue } from "motion/react";
import { ChevronDown, Radio, Star } from "lucide-react";
import { GenreChip } from "@/components/GenreChip";
import { cn } from "@/lib/cn";

/** The look of a hero's own call to action, so every caller's Link matches without the carousel
 * having to own the route it points at. The accent glow on hover is the one place this otherwise
 * black-and-white block borrows the app's own colour, instead of staying entirely neutral. */
export const HERO_ACTION_CLASS =
  "inline-flex items-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-bold text-zinc-900 shadow-[0_0_0_0_rgb(var(--color-accent)/0)] transition-[transform,box-shadow] hover:scale-[1.02] hover:shadow-[0_10px_30px_-6px_rgb(var(--color-accent)/0.55)] active:scale-[0.98]";

const HERO_INTERVAL_MS = 7000;
// A hero genre chip's own look - see GenreChip for the shared open-the-catalog logic.
const HERO_GENRE_CHIP_CLASS = "rounded-full border border-accent/30 bg-accent/[.08] px-2.5 py-1 text-accent-text hover:border-accent/60 hover:bg-accent/[.16]";

/**
 * One slide, as the carousel needs it: display fields plus where the slide leads.
 *
 * Deliberately not an AnimeTitle: a slide is display fields plus where it leads, whatever it was
 * built from.
 */
export interface HeroSlide {
  key: string;
  /** Which source this slide's own genre chips should open the catalog against. */
  sourceId: string;
  /** Shown next to the trending-on label - falls back to a plain icon when the source has none. */
  sourceIconUrl?: string | null;
  title: string;
  description?: string | null;
  posterUrl?: string | null;
  type?: string | null;
  year?: number | null;
  episodeCount?: number | null;
  /** Up to a handful - opens the catalog pre-filtered by the genre, same as the detail page's own
   * genre chips (see GenreChip), wherever the source actually has a matching filter option. */
  genres?: string[] | null;
  /** Whichever one rating the caller considers this slide's "main" one, if it has any at all. */
  rating?: { value: number; source: string } | null;
  /** The "open this" control, rendered by the caller so each keeps its own typed route and params -
   * so each keeps its own typed route and params. Use HERO_ACTION_CLASS on it. */
  action: React.ReactNode;
}

export function HeroCarousel({ slides, label, sourceName }: { slides: HeroSlide[]; label: string; sourceName: string }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const slideKey = slides.map((slide) => slide.key).join(",");
  // The one source of truth for "when does the current slide end" - a wall-clock timestamp,
  // instead of a countdown that has to be manually decremented on every pause/resume/skip. The
  // previous version tracked a "remaining time" number and adjusted it from effect cleanups; that
  // had a real bug: clicking a dot (goTo) reset it to a fresh interval, but the timer effect's own
  // cleanup - closing over a `startedAt` from *before* the click - then subtracted the time since
  // that older start from the just-reset value, silently shrinking the new slide's dwell time down
  // to whatever was left of the old one. A deadline only ever gets set to "now + one full
  // interval" (on advance, on a click, on a fresh slide list) or shifted forward by exactly however
  // long a pause lasted - nothing reads or writes it from a stale closure, so it can't drift.
  const deadlineRef = useRef(Date.now() + HERO_INTERVAL_MS);
  const pausedAtRef = useRef<number | null>(null);
  useEffect(() => { setIndex(0); deadlineRef.current = Date.now() + HERO_INTERVAL_MS; }, [slideKey]);
  // A pause set by the pointer resting on the hero has no matching "it left" to clear it if the
  // window gets minimized (or the app loses focus some other way) while that's still true - no
  // mouse events reach a hidden window at all, mouse-leave included, so without this the carousel
  // stayed paused forever after coming back, not just while actually minimized.
  useEffect(() => {
    const onVisibilityChange = () => { if (document.hidden) setPaused(false); };
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => document.removeEventListener("visibilitychange", onVisibilityChange);
  }, []);
  useEffect(() => {
    if (paused) {
      pausedAtRef.current = Date.now();
      return;
    }
    // Resuming shifts the deadline forward by exactly how long the pause lasted, rather than
    // reconstructing "how much was left" by hand - the wall clock already knows the answer.
    if (pausedAtRef.current !== null) {
      deadlineRef.current += Date.now() - pausedAtRef.current;
      pausedAtRef.current = null;
    }
    if (slides.length <= 1) return;
    const id = setTimeout(() => {
      deadlineRef.current = Date.now() + HERO_INTERVAL_MS;
      setIndex((i) => (i + 1) % slides.length);
    }, Math.max(0, deadlineRef.current - Date.now()));
    return () => clearTimeout(id);
  }, [paused, index, slides.length, slideKey]);
  const current = slides[Math.min(index, slides.length - 1)];
  // A title with a one-line title/description needs less room than one with two - real, wanted
  // variation, not a bug. Left alone, that also meant the whole page below the hero visibly jumped
  // on every single slide change, whether or not anyone was even looking at the hero right then.
  // The section reports its own natural height up through `onHeightChange`, applied here as an
  // explicit, CSS-transitioned `height` on this wrapper (which - unlike the slide's own content -
  // never remounts, so it actually has something to transition *from*): a real slide-to-slide
  // difference now animates smoothly instead of jumping. `isVisibleRef`/`pendingHeightRef` below
  // additionally hold that update back while this carousel is scrolled out of view - a change still
  // happens on schedule underneath, but nothing below it moves until it can actually be seen
  // happening, which is the one part a smooth animation alone wouldn't have fixed.
  const [sectionHeight, setSectionHeight] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const isVisibleRef = useRef(true);
  const pendingHeightRef = useRef<number | null>(null);
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(([entry]) => {
      isVisibleRef.current = entry.isIntersecting;
      if (entry.isIntersecting && pendingHeightRef.current !== null) {
        setSectionHeight(pendingHeightRef.current);
        pendingHeightRef.current = null;
      }
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  const handleHeightChange = useCallback((height: number) => {
    if (isVisibleRef.current) setSectionHeight(height);
    else pendingHeightRef.current = height;
  }, []);
  if (!current) return null;
  const goTo = (i: number) => { deadlineRef.current = Date.now() + HERO_INTERVAL_MS; setIndex(i); };
  return <div
    ref={containerRef}
    className="relative overflow-hidden transition-[height] duration-300 ease-in-out"
    style={sectionHeight ? { height: sectionHeight } : undefined}
    onMouseEnter={() => setPaused(true)}
    onMouseLeave={() => setPaused(false)}
  >
    <AnimatePresence mode="wait">
      <motion.div key={current.key} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.4, ease: "easeOut" }}>
        <Hero slide={current} label={label} sourceName={sourceName} paused={paused} onHeightChange={handleHeightChange} />
      </motion.div>
    </AnimatePresence>
    {slides.length > 1 && <div className="absolute bottom-8 right-8 z-10 flex items-center gap-1.5">
      {slides.map((slide, i) => (
        <button
          key={slide.key}
          onClick={() => goTo(i)}
          aria-label={`${i + 1}`}
          className="h-1.5 w-6 overflow-hidden rounded-full bg-white/25 transition-colors hover:bg-white/40"
        >
          {i === index && (
            // Keyed on the slide index alone: a pause/resume just toggles animation-play-state on
            // this same element (freezes and continues from exactly where it was, in step with
            // the timer above doing the same), rather than remounting and restarting it. Only an
            // actual slide change is a fresh bar.
            <span
              key={index}
              style={{ animationDuration: `${HERO_INTERVAL_MS}ms`, animationPlayState: paused ? "paused" : "running" }}
              className="block h-full w-full origin-left animate-[hero-progress_linear_forwards] rounded-full bg-accent"
            />
          )}
        </button>
      ))}
    </div>}
  </div>;
}
function Hero({ slide, label, sourceName, paused, onHeightChange }: { slide: HeroSlide; label: string; sourceName: string; paused: boolean; onHeightChange: (height: number) => void }) {
  const { t } = useTranslation();
  // Reports this slide's own natural height up to HeroCarousel (see its own comment on
  // `sectionHeight`) - one observer here catches every reason this section's height can change
  // (a one vs. two-line title, a longer description, the read-more toggle, ...) instead of each
  // needing its own separate measurement plumbed up separately.
  const sectionRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const el = sectionRef.current;
    if (!el) return;
    const observer = new ResizeObserver(() => onHeightChange(el.scrollHeight));
    observer.observe(el);
    return () => observer.disconnect();
  }, [onHeightChange]);
  // The background poster's slow zoom used to be a plain Motion `animate` prop, which - unlike
  // the timer above and the progress pill below - had no way to actually pause: it just kept
  // running the whole time the carousel itself sat paused on hover, so the image visibly kept
  // creeping in while everything else on the slide looked frozen. Driving it through an imperative
  // `animate()` call gives back a real AnimationPlaybackControls with .pause()/.play(), which
  // freezes and resumes the zoom in place exactly like the CSS progress pill already does.
  const zoom = useMotionValue(1);
  const zoomControlsRef = useRef<ReturnType<typeof animate> | null>(null);
  useEffect(() => {
    zoomControlsRef.current = animate(zoom, 1.1, { duration: HERO_INTERVAL_MS / 1000 + 1, ease: "easeOut" });
    return () => zoomControlsRef.current?.stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- one animation per Hero instance; this remounts per slide (see the `key` on its parent motion.div)
  }, []);
  useEffect(() => {
    if (paused) zoomControlsRef.current?.pause();
    else zoomControlsRef.current?.play();
  }, [paused]);
  const title = slide.title;
  const [descriptionOpen, setDescriptionOpen] = useState(false);
  const description = slide.description || t("catalog.heroFallbackDescription");
  const genres = (slide.genres ?? []).slice(0, 3);
  return <section ref={sectionRef} className="relative isolate min-h-[520px] overflow-hidden border-b border-white/[.04] px-8 py-20">
    <div className="absolute inset-0 -z-10 overflow-hidden opacity-75">
      {slide.posterUrl && (
        <motion.img
          src={slide.posterUrl}
          alt=""
          style={{ scale: zoom }}
          className="h-full w-full object-cover object-[center_25%] blur-[2px]"
        />
      )}
      <div className="absolute inset-0" style={{ backgroundImage: [
        "linear-gradient(180deg, #17161b 0px, transparent 64px)",
        "linear-gradient(90deg, rgba(23,22,27,.82) 0%, rgba(23,22,27,.54) 42%, rgba(23,22,27,.08) 100%)",
        "linear-gradient(0deg, #17161b 0px, rgba(23,22,27,.82) 48px, transparent 58%)",
      ].join(", ") }} />
    </div>
    <div className="max-w-2xl">
      <span className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-white/10 py-1 pl-3 pr-3 text-[11px] font-bold uppercase tracking-[.14em] text-accent-text">
        {label}
        <span className="flex h-4 w-4 shrink-0 items-center justify-center overflow-hidden rounded-full bg-white/15">
          {slide.sourceIconUrl ? <img src={slide.sourceIconUrl} alt="" className="h-full w-full object-cover" /> : <Radio className="h-2.5 w-2.5 text-zinc-300" strokeWidth={2} />}
        </span>
        {sourceName}
      </span>
      {/* Plain line-clamp, no measured/reserved height - a one-line title costs less room than a
          two-line one, same as a short description costs less than a long one, and that's fine:
          HeroCarousel's own wrapper (see its `sectionHeight`) is what smooths the resulting height
          difference into a transition instead of a jump, not this. */}
      <h1 className="line-clamp-2 max-w-xl select-text text-4xl font-bold leading-[1.05] tracking-[-.04em] text-white md:text-6xl">{title}</h1>
      <p className={cn("mt-5 max-w-lg select-text text-sm leading-6 text-zinc-200", !descriptionOpen && "line-clamp-3")}>{description}</p>
      {slide.description && <button onClick={() => setDescriptionOpen((value) => !value)} className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-zinc-300 transition hover:text-white">{descriptionOpen ? t("common.hideDescription") : t("common.readDescription")}<ChevronDown className={`h-3.5 w-3.5 transition-transform duration-300 ${descriptionOpen ? "rotate-180" : ""}`} strokeWidth={2.5} /></button>}
      <div className="mt-5 flex flex-wrap items-center gap-2 text-xs font-medium text-zinc-200">
        {slide.rating && (
          <span className="flex items-center gap-1 rounded-md bg-amber-400/15 px-2 py-1 font-bold text-amber-400">
            <Star className="h-3 w-3 fill-current" strokeWidth={0} />
            {formatHeroRating(slide.rating.value)}
          </span>
        )}
        <span className="rounded-md bg-white/15 px-2 py-1">{slide.type?.toUpperCase() || t("common.typeFallback")}</span>
        {slide.year ? <span className="rounded-md bg-white/15 px-2 py-1">{slide.year}</span> : null}
        {slide.episodeCount ? <span className="rounded-md bg-white/15 px-2 py-1">{t("common.episodesShort", { count: slide.episodeCount })}</span> : null}
        {genres.map((genre) => (
          <GenreChip key={genre} genre={genre} sourceId={slide.sourceId} className={HERO_GENRE_CHIP_CLASS} />
        ))}
      </div>
      <div className="mt-8">{slide.action}</div>
    </div>
  </section>;
}

function formatHeroRating(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/0$/, "");
}
