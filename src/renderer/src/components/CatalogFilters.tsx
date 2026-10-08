import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { SlidersHorizontal, X } from "lucide-react";
import { cn } from "@/lib/cn";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { FilterControl, FilterSection, LongList, inWindowOrder, isLongList } from "@/components/SearchFiltersPanel";
import { isFilterSet, withFilterValue, type SearchFilters } from "@/lib/searchFilters";
import { withoutUnknown } from "@/lib/filterVisuals";
import { BottomSheet } from "@/components/BottomSheet";
import { isMobile } from "@/lib/mobile";
import type { FilterValue, SearchFilterDef } from "@shared/types";

// Picking a filter reloads the results under the panel, so changes are sent on once the user pauses
// rather than on every click.
const APPLY_DELAY_MS = 400;

/** The draft of a set of filters that is being edited: changes show at once and reach `onChange` after a pause. */
/**
 * The filters being picked, and when they take effect: on the desktop live, a short pause after each
 * change; on the phone (`deferred`) only once the sheet is done with - apply() on "Done" or on closing
 * it - so the results behind it do not reload at every tap.
 */
export function useLiveFilters(filters: SearchFilters, onChange: (filters: SearchFilters) => void, deferred = false) {
  const [draft, setDraft] = useState(filters);
  // Whatever changed the filters from outside (a clear, a source switch) is the new draft.
  useEffect(() => setDraft(filters), [filters]);
  useEffect(() => {
    if (deferred || draft === filters) return;
    const timer = setTimeout(() => onChange(draft), APPLY_DELAY_MS);
    return () => clearTimeout(timer);
  }, [deferred, draft, filters, onChange]);
  const change = (def: SearchFilterDef, value: FilterValue) => setDraft((current) => withFilterValue(current, def.id, value));
  const apply = () => {
    if (draft !== filters) onChange(draft);
  };
  const reset = () => setDraft({});
  return { draft, change, apply, reset };
}

/** How many of the source's filters are picked. */
export function pickedCount(defs: SearchFilterDef[], filters: SearchFilters): number {
  return inWindowOrder(withoutUnknown(defs)).filter((d) => isFilterSet(filters[d.id])).length;
}

/** One joined control: the button toggles the filters, and once something is picked a ✕ on its right end clears them all. */
export function FiltersControl({ count, open, onToggle, onClear }: { count: number; open: boolean; onToggle: () => void; onClear: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex gap-0.5">
      <button
        onClick={onToggle}
        aria-expanded={open}
        className={cn(
          "flex items-center gap-2 rounded-l-lg px-3.5 py-2 text-sm font-semibold transition-[background-color,border-radius] duration-200 mobile:rounded-l-full mobile:text-[13px]",
          count > 0 ? "rounded-r" : "rounded-r-lg mobile:rounded-r-full",
          open ? "bg-text/[.12] text-text" : "bg-text/[.06] text-text/80 hover:bg-text/[.1]",
        )}
      >
        <SlidersHorizontal className="h-4 w-4" strokeWidth={2} />
        {t("search.filters.button")}
        {count > 0 && <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1 text-[11px] font-bold text-accent-fg">{count}</span>}
      </button>
      <AnimatePresence initial={false}>
        {count > 0 && (
          <motion.button
            key="clear"
            initial={{ opacity: 0, width: 0 }}
            animate={{ opacity: 1, width: 36 }}
            exit={{ opacity: 0, width: 0 }}
            transition={{ duration: 0.18 }}
            onClick={onClear}
            aria-label={t("search.filters.reset")}
            title={t("search.filters.reset")}
            className="flex items-center justify-center overflow-hidden rounded-l rounded-r-lg bg-text/[.06] mobile:rounded-r-full text-text/80 transition-colors hover:bg-text/[.1] hover:text-text"
          >
            <X className="h-4 w-4 shrink-0" strokeWidth={2.25} />
          </motion.button>
        )}
      </AnimatePresence>
    </div>
  );
}

/** Every filter the source offers, short controls first and long lists (genres) after - the body of any filters window. */
export function FilterSections({ defs, draft, onChange }: { defs: SearchFilterDef[]; draft: SearchFilters; onChange: (def: SearchFilterDef, value: FilterValue) => void }) {
  const { t } = useTranslation();
  const ordered = useMemo(() => inWindowOrder(withoutUnknown(defs)), [defs]);
  return (
    <div className="flex flex-col gap-6">
      {ordered.map((def) =>
        isLongList(def) ? (
          <LongList key={def.id} def={def} value={draft[def.id]} onChange={(value) => onChange(def, value)} />
        ) : (
          <FilterSection key={def.id} title={t(`search.filters.${def.id}`, { defaultValue: def.title })}>
            <FilterControl def={def} value={draft[def.id]} onChange={(value) => onChange(def, value)} />
          </FilterSection>
        ),
      )}
    </div>
  );
}

/**
 * The catalog's filters: a toolbar row (the Filters button with a ✕ that clears them, and whatever else
 * goes at the right - the sort menu) and a drawer that slides in from the right over the catalog. Changes
 * apply on their own after a short pause.
 */
export function CatalogFilters({
  defs,
  filters,
  onChange,
  loading,
  lead,
  children,
}: {
  defs: SearchFilterDef[];
  filters: SearchFilters;
  onChange: (filters: SearchFilters) => void;
  loading: boolean;
  /** What fills the left of the row. */
  lead?: React.ReactNode;
  children?: React.ReactNode;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const popoverTheme = usePopoverTheme();
  const { draft, change, apply, reset } = useLiveFilters(filters, onChange, isMobile);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open]);

  const count = pickedCount(defs, filters);

  if (isMobile) {
    // Phone: the toolbar starts at the left edge, and the filters rise as a sheet instead of a side drawer.
    return (
      <div className="mb-4">
        <div className="flex items-center gap-2">
          {lead}
          {defs.length > 0 && <FiltersControl count={count} open={open} onToggle={() => setOpen((v) => !v)} onClear={() => onChange({})} />}
          {children}
        </div>
        {/* What is picked applies when the sheet is done with - "Done", or closing it any other way. */}
        <BottomSheet
          open={open}
          prewarm
          onClose={() => { apply(); setOpen(false); }}
          title={t("search.filters.button")}
          className="h-[85vh]"
          footer={
            <div className="flex items-center justify-between gap-2">
              <button onClick={reset} disabled={pickedCount(defs, draft) === 0} className="rounded-full px-4 py-2.5 text-sm font-semibold text-muted active:bg-text/[.06] disabled:opacity-40">{t("search.filters.reset")}</button>
              <button onClick={() => { apply(); setOpen(false); }} className="rounded-full bg-text px-6 py-2.5 text-sm font-bold text-bg active:opacity-90">{t("search.filters.done")}</button>
            </div>
          }
        >
          <div className="px-3 pt-2">{loading ? <p className="py-6 text-center text-sm text-muted">…</p> : <FilterSections defs={defs} draft={draft} onChange={change} />}</div>
        </BottomSheet>
      </div>
    );
  }

  return (
    <div className="mb-6">
      <div className="flex items-center justify-between gap-4">
        <div className="min-w-0 flex-1">{lead}</div>
        <div className="flex shrink-0 items-center gap-2">
          {defs.length > 0 && <FiltersControl count={count} open={open} onToggle={() => setOpen((v) => !v)} onClear={() => onChange({})} />}
          {children}
        </div>
      </div>

      {/* A drawer over the catalog, not part of it: portaled to <body> below the title bar. Its scrim
          dims the catalog (which reloads live behind it as filters are picked) and closes it. */}
      {createPortal(
        <AnimatePresence>
          {open && (
            <div className="fixed inset-x-0 bottom-0 top-10 z-50">
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
                onClick={() => setOpen(false)}
                className="absolute inset-0 bg-black/45"
              />
              <motion.aside
                initial={{ x: "100%" }}
                animate={{ x: 0 }}
                exit={{ x: "100%" }}
                transition={{ type: "spring", stiffness: 420, damping: 42 }}
                style={popoverTheme}
                className="absolute inset-y-0 right-0 flex w-[420px] max-w-[92vw] flex-col border-l border-border bg-app-popover shadow-2xl"
              >
                <div className="flex items-center justify-between px-5 pb-2 pt-4">
                  <h2 className="text-base font-bold text-text">{t("search.filters.button")}</h2>
                  <button onClick={() => setOpen(false)} aria-label="Close" className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.08] hover:text-text">
                    <X className="h-4 w-4" strokeWidth={2.25} />
                  </button>
                </div>
                <div className="no-scrollbar flex-1 overflow-y-auto px-5 py-3">
                  {loading ? <p className="py-6 text-center text-sm text-muted">…</p> : <FilterSections defs={defs} draft={draft} onChange={change} />}
                </div>
                <div className="flex items-center justify-between gap-2 border-t border-border p-3">
                  <button
                    onClick={() => onChange({})}
                    disabled={count === 0}
                    className="rounded-lg px-3 py-1.5 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06] hover:text-text disabled:opacity-40"
                  >
                    {t("search.filters.reset")}
                  </button>
                  <button onClick={() => setOpen(false)} className="rounded-lg bg-text px-5 py-1.5 text-sm font-bold text-bg transition-opacity hover:opacity-90">
                    {t("search.filters.done")}
                  </button>
                </div>
              </motion.aside>
            </div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </div>
  );
}
