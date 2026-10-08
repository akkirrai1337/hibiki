import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";

// A horizontally-scrolling row of fixed-width cards with fade+arrow scroll affordances on
// whichever side actually has more content - originally ContinueWatchingRow's own ScrollableRow
// (the home page's "continue watching" poster row), pulled out here once the anime detail page's
// RelatedStrip needed the exact same chrome and a hand-rolled second copy had drifted into its own
// (buggier) implementation instead of just reusing this one. Deliberately does NOT redirect a plain
// vertical mouse wheel into horizontal scroll (RelatedStrip's old copy did) - that hijacked a normal
// attempt to scroll the page itself the moment the cursor happened to be over the row.
export function HorizontalScrollRow<T>({
  items,
  getKey,
  renderItem,
  cardWidthClassName,
  arrowAspectClassName = "aspect-[2/3]",
}: {
  items: T[];
  getKey: (item: T) => string;
  renderItem: (item: T) => React.ReactNode;
  // A Tailwind width class (can be responsive, e.g. `w-[...] xl:w-[...]`) - kept as a class string
  // rather than a computed inline style so callers can express per-breakpoint card counts the same
  // way the rest of the app's own grids do.
  cardWidthClassName: string;
  // Reference box the arrow buttons vertically center themselves against (the card's own poster
  // area, not any caption text below it) - every current caller is a 2:3 poster, but kept a prop
  // rather than hardcoded for whatever shape a future caller turns out to need.
  arrowAspectClassName?: string;
}) {
  const { t } = useTranslation();
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = () => {
    const el = scrollRef.current;
    // A `display:none` ancestor (a persisted-but-hidden page - see __root.tsx) reports 0 for every
    // size read on this element, so a recompute that happens to land while hidden would otherwise
    // latch both arrows to "nothing to scroll to" permanently, since nothing was left to ever
    // recompute it again once the page became visible again. Bailing out here instead of trusting
    // the zero-width reading leaves whatever state was already showing untouched until a real,
    // visible measurement comes in.
    if (!el || el.clientWidth === 0) return;
    setCanScrollLeft(el.scrollLeft > 4);
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 4);
  };
  // Re-checks whenever the row's actual rendered size changes - not just on window resize (the
  // viewport is only one reason its width could change; the item count changing, a sidebar resize,
  // or this page going from hidden back to visible while kept alive are all others). A
  // ResizeObserver catches all of those in one place, including the hidden→visible transition
  // itself (going from a reported width of 0 back to the real one is itself a resize).
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    updateScrollState();
    const observer = new ResizeObserver(updateScrollState);
    observer.observe(el);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items.length]);

  const scrollByPage = (direction: 1 | -1) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollBy({ left: direction * el.clientWidth * 0.9, behavior: "smooth" });
  };

  // A real alpha mask on the scroller itself, not a painted color overlay on top of it - a flat
  // color (even a translucent one) only ever approximates whatever's actually visible behind a
  // given card (a plain background color here, a gradient or a blurred poster backdrop
  // elsewhere), so it either mismatches outright or, even when close, still reads as a hard-edged
  // card with a faint tint rather than the card itself genuinely fading away. Masking the element
  // makes its own pixels transparent, which blends correctly no matter what ends up behind it.
  const maskImage = `linear-gradient(to right, ${canScrollLeft ? "transparent, black 24px" : "black 0"}, ${canScrollRight ? "black calc(100% - 24px), transparent" : "black 100%"})`;

  return (
    <div className="relative -mx-1">
      {/* The arrow buttons live in their own card-width reference box (so their vertical centering
          can be computed against the poster's own aspect ratio, not the whole card including any
          caption below it) - purely for positioning now that the fade itself is a mask on the
          scroller rather than a second overlay layer here. */}
      {/* Arrows are for a mouse; on the phone the thumb scrolls the row. */}
      <div className={cn(cardWidthClassName, "pointer-events-none absolute inset-y-0 left-0 z-10 mobile:hidden")}>
        <div className={cn("relative w-full", arrowAspectClassName)}>
          <button
            type="button"
            onClick={() => scrollByPage(-1)}
            aria-label={t("common.scrollLeft")}
            tabIndex={canScrollLeft ? 0 : -1}
            className={cn(
              "pointer-events-auto absolute inset-y-0 left-0 flex w-14 items-center pl-1 transition-opacity duration-300",
              canScrollLeft ? "opacity-100" : "pointer-events-none opacity-0",
            )}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-black/70 text-white shadow-lg transition-transform hover:scale-105">
              <ChevronLeft className="h-5 w-5" strokeWidth={2.5} />
            </span>
          </button>
        </div>
      </div>
      <div
        ref={scrollRef}
        onScroll={updateScrollState}
        style={{ maskImage, WebkitMaskImage: maskImage }}
        className={cn(
          "flex gap-4 overflow-x-auto scroll-smooth px-1 pb-1 mobile:gap-3",
          "[scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden",
        )}
      >
        {items.map((item) => (
          <div key={getKey(item)} className={cn(cardWidthClassName, "shrink-0")}>
            {renderItem(item)}
          </div>
        ))}
      </div>
      <div className={cn(cardWidthClassName, "pointer-events-none absolute inset-y-0 right-0 z-10 mobile:hidden")}>
        <div className={cn("relative w-full", arrowAspectClassName)}>
          <button
            type="button"
            onClick={() => scrollByPage(1)}
            aria-label={t("common.scrollRight")}
            tabIndex={canScrollRight ? 0 : -1}
            className={cn(
              "pointer-events-auto absolute inset-y-0 right-0 flex w-14 items-center justify-end pr-1 transition-opacity duration-300",
              canScrollRight ? "opacity-100" : "pointer-events-none opacity-0",
            )}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-black/70 text-white shadow-lg transition-transform hover:scale-105">
              <ChevronRight className="h-5 w-5" strokeWidth={2.5} />
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}
