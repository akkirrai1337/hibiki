import { isMobile } from "@/lib/mobile";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { ArrowDown, ArrowUp, ChevronDown, ChevronUp, Search } from "lucide-react";
import { cn } from "@/lib/cn";
import {
  asList,
  asRange,
  asTristate,
  cycleTristate,
  prettifyStatusLabel,
  prettifyTypeLabel,
  tristateOf,
  type ChipState,
} from "@/lib/searchFilters";
import { ageRatingRank, chipIconFor, inDisplayOrder, isSortFilter, isAgeRatingFilter, isConnectedToggle, optionIcon, singleYearOptions, yearOptions } from "@/lib/filterVisuals";
import type { LucideIcon } from "lucide-react";
import type { FilterValue, SearchFilterDef, SearchFilterOption } from "@shared/types";

// A list this long is a shelf to search in (genres, studios); shorter ones sit with the compact controls.
const LONG_LIST_MINIMUM = 20;

export const isLongList = (def: SearchFilterDef) =>
  (def.type === "multi" || def.type === "tristate") &&
  (def.options?.length ?? 0) >= LONG_LIST_MINIMUM &&
  !yearOptions(def) &&
  !isAgeRatingFilter(def) &&
  !isConnectedToggle(def);

// From this many options a list is sorted alphabetically and split by first letter, so one can be
// found in it (the Android window's threshold). Shorter lists keep the order the source gave them,
// as do lists that are mostly numbers (years, ratings), where alphabetical order means nothing.
const SORTED_OPTION_MINIMUM = 75;
const COLLAPSED_GROUPS = 3;

function groupByLetter(options: SearchFilterOption[]): { letter: string; options: SearchFilterOption[] }[] {
  const byLetter = new Map<string, SearchFilterOption[]>();
  for (const option of options) {
    const letter = (option.title.trim()[0] ?? "#").toUpperCase();
    (byLetter.get(letter) ?? byLetter.set(letter, []).get(letter)!).push(option);
  }
  return [...byLetter.entries()]
    .sort(([a], [b]) => a.localeCompare(b, "ru"))
    .map(([letter, groupOptions]) => ({ letter, options: [...groupOptions].sort((a, b) => a.title.localeCompare(b.title, "ru")) }));
}

// Type and status labels come from a short fixed vocabulary many sources report as raw ids
// ("short_movie"), so those two get a translated label; everything else shows what the source said.
export function optionLabel(def: SearchFilterDef, option: SearchFilterOption, t: ReturnType<typeof useTranslation>["t"]): string {
  const label = def.id === "type" ? prettifyTypeLabel(option.id, option.title, t) : def.id === "status" ? prettifyStatusLabel(option.id, option.title, t) : option.title;
  return tidyLabel(label);
}

// "TV_SHORT" -> "TV Short": raw ids some sites use as their labels. Short all-caps words (TV, OVA,
// ONA) stay as they are.
function tidyLabel(label: string): string {
  if (!label.includes("_")) return label;
  return label
    .split(/_+/)
    .filter(Boolean)
    .map((w) => (w.length <= 3 && w === w.toUpperCase() ? w : w[0].toUpperCase() + w.slice(1).toLowerCase()))
    .join(" ");
}

export function FilterControl({ def, value, onChange }: { def: SearchFilterDef; value: FilterValue | undefined; onChange: (value: FilterValue) => void }) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(false);

  if (def.type === "text") {
    return (
      <input
        type="text"
        value={typeof value === "string" ? value : ""}
        onChange={(e) => onChange(e.target.value)}
        className="h-8 w-full rounded-lg border border-border bg-text/[.04] px-2.5 text-xs text-text outline-none focus:border-accent/70"
      />
    );
  }
  if (def.type === "range") {
    const range = asRange(value);
    return <RangeSlider min={def.min ?? 0} max={def.max ?? 100} from={range.from ?? null} to={range.to ?? null} onChange={(from, to) => onChange({ from: from ?? undefined, to: to ?? undefined })} />;
  }

  const options = def.options ?? [];
  const years = yearOptions(def);
  if (years) {
    // The source lists years; the user picks a span. The span goes back as the years inside it, and
    // the full span is the same as no filter.
    const included = def.type === "tristate" ? asTristate(value).include : asList(value);
    const chosen = years.filter((y) => included.includes(y.id)).map((y) => y.year);
    const full = { min: years[0].year, max: years[years.length - 1].year };
    return (
      <RangeSlider
        min={full.min}
        max={full.max}
        from={chosen.length ? Math.min(...chosen) : null}
        to={chosen.length ? Math.max(...chosen) : null}
        onChange={(from, to) => {
          const lo = from ?? full.min;
          const hi = to ?? full.max;
          const ids = lo <= full.min && hi >= full.max ? [] : years.filter((y) => y.year >= lo && y.year <= hi).map((y) => y.id);
          onChange(def.type === "tristate" ? { include: ids, exclude: [] } : ids);
        }}
      />
    );
  }
  const singleYears = singleYearOptions(def);
  if (singleYears) {
    return <YearPickSlider years={singleYears.years} value={typeof value === "string" ? value : ""} onChange={(id) => onChange(id ?? singleYears.anyId ?? "")} />;
  }
  if (isAgeRatingFilter(def)) {
    const included = def.type === "tristate" ? asTristate(value).include : asList(value);
    return (
      <AgeRatingSlider
        options={options}
        included={included}
        onChange={(ids) => onChange(def.type === "tristate" ? { include: ids, exclude: [] } : ids)}
      />
    );
  }
  if (isConnectedToggle(def)) {
    const pressed = def.type === "tristate" ? asTristate(value).include[0] : asList(value)[0];
    return (
      <ConnectedToggle
        options={options}
        pressed={pressed}
        label={(opt) => optionLabel(def, opt, t)}
        onPress={(id) => onChange(def.type === "select" ? (id ?? "") : def.type === "multi" ? (id ? [id] : []) : { include: id ? [id] : [], exclude: [] })}
      />
    );
  }
  const iconFor = chipIconFor(def);
  // Sort orders are picked, not filtered by: the chip shows a direction. Ascending is the first press
  // (green, up arrow); a source that can run an order backwards (def.directional) gets a second one
  // (red, down arrow); the last clears it - the same cycle as the Android window.
  const sortLike = def.type === "select" && isSortFilter(def);
  const directional = sortLike && def.directional === true;
  const stateOf = (id: string): ChipState => {
    if (directional) return tristateOf(value, id);
    if (def.type === "tristate") return tristateOf(value, id);
    return asList(value).includes(id) ? "include" : "none";
  };
  const markOf = (id: string): ChipMark => {
    const state = stateOf(id);
    if (state === "none") return null;
    if (sortLike) return state === "include" ? "up" : "down";
    return state === "include" ? "plus" : "minus";
  };
  const toggle = (id: string) => {
    if (directional) {
      const state = tristateOf(value, id);
      return onChange(state === "none" ? { include: [id], exclude: [] } : state === "include" ? { include: [], exclude: [id] } : "");
    }
    if (def.type === "tristate") return onChange(cycleTristate(value, id));
    const selected = asList(value);
    const on = selected.includes(id);
    // "select" behaves like a radio row that can be switched off again - a filter you can never
    // clear is a trap.
    if (def.type === "multi") onChange(on ? selected.filter((x) => x !== id) : [...selected, id]);
    else onChange(on ? "" : id);
  };
  const chips = (list: SearchFilterOption[]) => (
    <ChipRow>
      {list.map((opt) => (
        <FilterChip key={opt.id} state={stateOf(opt.id)} mark={markOf(opt.id)} icon={iconFor?.(opt) ?? undefined} onClick={() => toggle(opt.id)}>
          {optionLabel(def, opt, t)}
        </FilterChip>
      ))}
    </ChipRow>
  );

  const mostlyNumbers = options.filter((o) => /^\d/.test(o.title.trim())).length >= options.length / 2;
  if (options.length < SORTED_OPTION_MINIMUM || mostlyNumbers) return chips(options);
  const groups = groupByLetter(options);
  const visible = expanded ? groups : groups.slice(0, COLLAPSED_GROUPS);
  return (
    <div className="flex flex-col gap-3">
      {visible.map((group) => (
        <div key={group.letter}>
          <p className="mb-1.5 text-xs font-bold text-muted">{group.letter}</p>
          {chips(group.options)}
        </div>
      ))}
      {groups.length > COLLAPSED_GROUPS && (
        <button
          onClick={() => setExpanded((v) => !v)}
          aria-label={expanded ? t("search.filters.genresCollapse") : t("search.filters.genresExpand")}
          className="flex items-center justify-center rounded-lg py-1 text-muted transition-colors hover:bg-text/[.06] hover:text-text"
        >
          <ChevronDown className={cn("h-4 w-4 transition-transform", expanded && "rotate-180")} strokeWidth={2.25} />
        </button>
      )}
    </div>
  );
}

// A section can be folded away, like the Android window's - a source with a dozen filters would
// otherwise be one long scroll.
export function FilterSection({ title, children }: { title: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(true);
  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="mb-2 flex items-center gap-1 text-xs font-bold uppercase tracking-wide text-muted transition-colors hover:text-text"
      >
        {title}
        <ChevronDown className={cn("h-3.5 w-3.5 transition-transform duration-200", !open && "-rotate-90")} strokeWidth={2.5} />
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            key="content"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: "easeOut" }}
            className="overflow-hidden"
          >
            {children}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
// The phone's sheet rises with its rows already in place: a fade on every row during the rise cost
// the WebView a repaint of the whole sheet each frame and dropped it visibly below 60 fps.
function ChipRow({ children }: { children: React.ReactNode }) {
  if (isMobile) return <div className="flex flex-wrap gap-2">{children}</div>;
  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.2 }} className="flex flex-wrap gap-2">{children}</motion.div>;
}

type ChipMark = "plus" | "minus" | "up" | "down" | null;

// Same look as the Android chip: no border, a tint of the state's colour behind text in that colour,
// the colour easing between states and the leading mark cross-fading in and out.
const CHIP_TONE: Record<"none" | "include" | "exclude", string> = {
  none: "bg-text/[.06] text-muted hover:bg-text/10",
  include: "bg-[#80DF87]/20 text-[#80DF87]",
  exclude: "bg-[#FF9999]/20 text-[#FF9999]",
};

function ChipMarkGlyph({ mark }: { mark: Exclude<ChipMark, null> }) {
  if (mark === "up") return <ArrowUp className="h-3 w-3" strokeWidth={3} />;
  if (mark === "down") return <ArrowDown className="h-3 w-3" strokeWidth={3} />;
  return <span className="font-bold leading-none">{mark === "plus" ? "+" : "−"}</span>;
}

function FilterChip({ state, mark = null, icon: Icon, onClick, children }: { state: "none" | "include" | "exclude"; mark?: ChipMark; icon?: LucideIcon; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={cn("inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold transition-colors duration-200 mobile:px-3 mobile:py-1.5 mobile:text-[13px]", CHIP_TONE[state])}
    >
      {Icon && <Icon className="h-3.5 w-3.5" strokeWidth={2} />}
      <AnimatePresence initial={false} mode="popLayout">
        {mark && (
          <motion.span
            key={mark}
            initial={{ opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.6 }}
            transition={{ duration: 0.15 }}
            className="inline-flex"
          >
            <ChipMarkGlyph mark={mark} />
          </motion.span>
        )}
      </AnimatePresence>
      {children}
    </button>
  );
}

// Seasons, statuses and sub/dub: a short row of icon buttons joined at the edges, one pressed at a
// time; pressing the pressed one clears it. Same geometry as the Android window (rounded ends, 2px
// gaps, a pressed button rounding fully), drawn in this app's own colours.
function ConnectedToggle({ options, pressed, label, onPress }: { options: SearchFilterOption[]; pressed: string | undefined; label: (option: SearchFilterOption) => string; onPress: (id: string | null) => void }) {
  return (
    // A grid, not a flex row: every button gets exactly the same width whatever its label, so the
    // row stays symmetric.
    <div className="grid gap-0.5" style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
      {options.map((opt, index) => {
        const on = opt.id === pressed;
        const Icon = optionIcon(opt)!;
        const round = (edge: boolean) => (on || edge ? "24px" : "4px");
        const first = index === 0;
        const last = index === options.length - 1;
        return (
          <button
            key={opt.id}
            type="button"
            onClick={() => onPress(on ? null : opt.id)}
            style={{ borderRadius: `${round(first)} ${round(last)} ${round(last)} ${round(first)}` }}
            className={cn(
              "flex min-w-0 flex-col items-center justify-center gap-1 px-4 py-3 transition-[border-radius,background-color,color] duration-200",
              on ? "bg-accent text-accent-fg" : "bg-text/[.06] text-muted hover:bg-text/10",
            )}
          >
            <Icon className={cn("h-3.5 w-3.5", on ? "opacity-90" : "opacity-60")} strokeWidth={2} />
            <span className={cn("text-balance text-center font-semibold leading-tight", options.length > 4 ? "text-xs" : "text-sm")}>{label(opt)}</span>
          </button>
        );
      })}
    </div>
  );
}

// Age ratings are ordered, so they are one slider: everything up to the chosen rating is included,
// and the far left means "any rating" (the filter unset).
function AgeRatingSlider({ options, included, onChange }: { options: SearchFilterOption[]; included: string[]; onChange: (ids: string[]) => void }) {
  const { t } = useTranslation();
  const ordered = [...options].sort((a, b) => (ageRatingRank(a.title) ?? 0) - (ageRatingRank(b.title) ?? 0));
  let level = 0;
  ordered.forEach((opt, i) => { if (included.includes(opt.id)) level = i + 1; });
  const [drag, setDrag] = useState<number | null>(null);
  const shown = drag ?? level;
  const commit = (n: number) => {
    setDrag(null);
    onChange(ordered.slice(0, n).map((o) => o.id));
  };
  return (
    <div className="flex flex-col items-center gap-2">
      <span className="text-sm font-semibold text-text">
        {shown === 0 ? t("search.filters.ratingAny") : t("search.filters.ratingUpTo", { rating: ordered[shown - 1].title })}
      </span>
      <input
        type="range"
        min={0}
        max={ordered.length}
        step={1}
        value={shown}
        onChange={(e) => setDrag(Number(e.target.value))}
        onPointerUp={() => drag !== null && commit(drag)}
        onKeyUp={() => drag !== null && commit(drag)}
        // The app's own slider look (see .range-slider in globals.css), filled up to the level.
        className="range-slider w-full"
        style={{ "--fill": `${ordered.length ? (shown / ordered.length) * 100 : 0}%` } as React.CSSProperties}
      />
    </div>
  );
}

/** One year out of a source's list, or any: the slider's left end is "any year". */
function YearPickSlider({ years, value, onChange }: { years: Array<{ year: number; id: string }>; value: string; onChange: (id: string | null) => void }) {
  const { t } = useTranslation();
  const level = Math.max(0, years.findIndex((y) => y.id === value) + 1);
  const [drag, setDrag] = useState<number | null>(null);
  const shown = drag ?? level;
  const commit = (n: number) => {
    setDrag(null);
    onChange(n === 0 ? null : years[n - 1].id);
  };
  return (
    <div className="flex flex-col items-center gap-2">
      <span className="text-sm font-semibold text-text">{shown === 0 ? t("search.filters.yearAny") : years[shown - 1].year}</span>
      <input
        type="range"
        min={0}
        max={years.length}
        step={1}
        value={shown}
        onChange={(e) => setDrag(Number(e.target.value))}
        onPointerUp={() => drag !== null && commit(drag)}
        onKeyUp={() => drag !== null && commit(drag)}
        // The app's own slider look (see .range-slider in globals.css), filled up to the year.
        className="range-slider w-full"
        style={{ "--fill": `${years.length ? (shown / years.length) * 100 : 0}%` } as React.CSSProperties}
      />
    </div>
  );
}

function RangeSlider({ min, max, from, to, onChange }: { min: number; max: number; from: number | null; to: number | null; onChange: (from: number | null, to: number | null) => void }) {
  const fromValue = from ?? min;
  const toValue = to ?? max;

  return (
    <div className="flex items-center gap-2">
      <RangeNumberInput min={min} max={max} value={from} base={min} onChange={(v) => onChange(v === null ? null : Math.min(v, toValue), to)} />
      <div className="relative h-4 flex-1">
        <div className="absolute left-0 right-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-text/10" />
        <div
          className="absolute top-1/2 h-1 -translate-y-1/2 rounded-full bg-accent"
          style={{ left: `${((fromValue - min) / (max - min)) * 100}%`, right: `${100 - ((toValue - min) / (max - min)) * 100}%` }}
        />
        <input
          type="range"
          className="range-thumb-only absolute inset-0 w-full"
          min={min}
          max={max}
          value={fromValue}
          onChange={(e) => onChange(Math.min(Number(e.target.value), toValue), to)}
        />
        <input
          type="range"
          className="range-thumb-only absolute inset-0 w-full"
          min={min}
          max={max}
          value={toValue}
          onChange={(e) => onChange(from, Math.max(Number(e.target.value), fromValue))}
        />
      </div>
      <RangeNumberInput min={min} max={max} value={to} base={max} onChange={(v) => onChange(from, v === null ? null : Math.max(v, fromValue))} />
    </div>
  );
}

function RangeNumberInput({ min, max, value, base, onChange }: { min: number; max: number; value: number | null; base: number; onChange: (value: number | null) => void }) {
  function step(delta: number) {
    onChange(Math.min(max, Math.max(min, (value ?? base) + delta)));
  }
  return (
    <div className="flex h-8 w-16 shrink-0 items-stretch overflow-hidden rounded-lg border border-border bg-text/[.04] focus-within:border-accent/70">
      <input
        type="number"
        value={value ?? ""}
        placeholder={String(base)}
        min={min}
        max={max}
        onChange={(e) => {
          const raw = e.target.value.trim();
          if (raw === "") return onChange(null);
          const parsed = Number(raw);
          onChange(Number.isFinite(parsed) ? Math.min(max, Math.max(min, parsed)) : null);
        }}
        className="no-spinner min-w-0 flex-1 bg-transparent pl-1.5 text-center text-xs text-text outline-none"
      />
      <div className="flex w-4 shrink-0 flex-col border-l border-border">
        <button type="button" onClick={() => step(1)} aria-label="+1" className="flex flex-1 items-center justify-center text-muted transition-colors hover:bg-text/[.06] hover:text-text">
          <ChevronUp className="h-2.5 w-2.5" strokeWidth={3} />
        </button>
        <button type="button" onClick={() => step(-1)} aria-label="-1" className="flex flex-1 items-center justify-center border-t border-border text-muted transition-colors hover:bg-text/[.06] hover:text-text">
          <ChevronDown className="h-2.5 w-2.5" strokeWidth={3} />
        </button>
      </div>
    </div>
  );
}

// A long list (genres...) with a box to find an option in it; it scrolls on its own so the panel stays
// about the height of the compact controls next to it.
export function LongList({ def, value, onChange }: { def: SearchFilterDef; value: FilterValue | undefined; onChange: (value: FilterValue) => void }) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const needle = query.trim().toLowerCase();
  const shown = needle ? { ...def, options: (def.options ?? []).filter((o) => o.title.toLowerCase().includes(needle)) } : def;
  return (
    <FilterSection title={t(`search.filters.${def.id}`, { defaultValue: def.title })}>
      <div className="relative mb-3">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted" strokeWidth={2} />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t("search.filters.findOption")}
          className="h-8 w-full rounded-lg border border-border bg-text/[.04] pl-8 pr-2.5 text-xs text-text outline-none focus:border-accent/70"
        />
      </div>
      <div className="no-scrollbar max-h-[40vh] overflow-y-auto">
        <FilterControl def={shown} value={value} onChange={onChange} />
      </div>
    </FilterSection>
  );
}

/** The order both filter windows show: the short controls first, the long lists (genres...) after them. */
export const inWindowOrder = (defs: SearchFilterDef[]): SearchFilterDef[] => {
  const ordered = inDisplayOrder(defs);
  return [...ordered.filter((d) => !isLongList(d)), ...ordered.filter(isLongList)];
};
