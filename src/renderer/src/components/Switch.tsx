import { cn } from "@/lib/cn";

// Originally local to settings.tsx, pulled out here once sources.tsx's own language-filter
// popover needed a toggle too and had grown a second, hand-rolled one instead (a flex
// `justify-end`/`justify-start` track with the thumb as its only child) that read as visibly
// uneven - not flush against the track's edges the way this one is. See the thumb's own comment
// below for why flow + `translate-x` avoids that in the first place.
export function Switch({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  // The thumb is a normal-flow child (not `absolute`) positioned purely by `translate-x` from the
  // track's own padding - an `absolute` thumb has to fall back to a browser-computed "static
  // position" for its un-set `left`, which is where an earlier version kept ending up detached
  // from the track instead of flush against it. Flow + transform has no such fallback to get wrong.
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={cn(
        // A border on the track always, not just when unchecked - a black or white accent color
        // (see AccentSwatch) otherwise blends straight into the card's own near-black background
        // or the white thumb sitting on top of it, leaving no visible edge to tell the switch is
        // even there, let alone which state it's in.
        "inline-flex h-6 w-10 shrink-0 items-center rounded-full border border-border p-0.5 transition-colors",
        checked ? "bg-accent" : "bg-text/[.12]",
      )}
    >
      {/* bg-accent-fg (not a fixed bg-white) once checked - --color-accent-fg is exactly this
          app's "contrasts against --color-accent" token (see lib/theme.ts), so a white accent's
          track gets a dark thumb, a black accent's gets a light one, and everything else keeps
          the classic white knob it always had (accent-fg only actually diverges from white for a
          light accent). A fixed white thumb on a white track was the "still fully white" the
          border-only fix above didn't solve. */}
      {/* Sized to the track's actual content box - h-6 minus its 1px border and 2px padding on
          each side leaves exactly 18px, so a 20px thumb overflowed by a pixel top and bottom and,
          once translated, by two on the right: the "sits low and hangs off the edge" look. */}
      <span className={cn("h-[18px] w-[18px] rounded-full shadow ring-1 ring-black/10 transition-[transform,background-color]", checked ? "translate-x-4 bg-accent-fg" : "translate-x-0 bg-white")} />
    </button>
  );
}
