import type { TFunction } from "i18next";
import type { FilterValue, FilterValues } from "@shared/types";

// The host has no notion of "genre" or "year": a source declares its filters (SearchFilterDef) and
// this is just the values the user picked, keyed by filter id, in the shape each filter type
// defines. An unset filter is absent - never an empty value - so "off" and "never touched" are the
// same to the cache key, the badge and the source.
export type SearchFilters = FilterValues;

export const EMPTY_SEARCH_FILTERS: SearchFilters = {};

export type Tristate = { include: string[]; exclude: string[] };
export type Range = { from?: number; to?: number };

export const asList = (value: FilterValue | undefined): string[] =>
  Array.isArray(value) ? value : typeof value === "string" && value !== "" ? [value] : [];

export const asTristate = (value: FilterValue | undefined): Tristate =>
  value && typeof value === "object" && !Array.isArray(value) && "include" in value ? value : { include: asList(value), exclude: [] };

export const asRange = (value: FilterValue | undefined): Range =>
  value && typeof value === "object" && !Array.isArray(value) && !("include" in value) ? value : {};

export function isFilterSet(value: FilterValue | undefined): boolean {
  if (value === undefined) return false;
  if (typeof value === "string") return value.trim() !== "";
  if (Array.isArray(value)) return value.length > 0;
  if ("include" in value) return value.include.length + value.exclude.length > 0;
  return value.from !== undefined || value.to !== undefined;
}

/** How many filters are set - one per filter, whatever its shape. */
export function activeFilterCount(filters: SearchFilters): number {
  return Object.values(filters).filter(isFilterSet).length;
}

export function withFilterValue(filters: SearchFilters, id: string, value: FilterValue): SearchFilters {
  const next = { ...filters };
  if (isFilterSet(value)) next[id] = value;
  else delete next[id];
  return next;
}

export type ChipState = "none" | "include" | "exclude";

export function tristateOf(value: FilterValue | undefined, optionId: string): ChipState {
  const { include, exclude } = asTristate(value);
  if (include.includes(optionId)) return "include";
  if (exclude.includes(optionId)) return "exclude";
  return "none";
}

/** none -> include -> exclude -> none. */
export function cycleTristate(value: FilterValue | undefined, optionId: string): Tristate {
  const { include, exclude } = asTristate(value);
  if (include.includes(optionId)) return { include: include.filter((x) => x !== optionId), exclude: [...exclude, optionId] };
  if (exclude.includes(optionId)) return { include, exclude: exclude.filter((x) => x !== optionId) };
  return { include: [...include, optionId], exclude };
}

// A lot of sources report id===title for type/status (raw technical aliases like "short_movie"),
// unlike genres which usually come through with real human titles already. These are the same
// short fixed vocabulary across (almost) every source, so they get a translated display label
// (search.filters.typeLabels.* / statusLabels.*) instead of whatever the source titled them -
// "announcement" is normalized to the same key as "announced" since both ids show up in the wild.
export const STATUS_ID_ALIASES: Record<string, string> = { announcement: "announced" };

function humanize(raw: string): string {
  return raw.split(/[_-]/).map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w)).join(" ");
}

// Falls back to humanizing a raw technical id (snake_case, all lowercase) only - anything that
// already looks like a real display string from the source (mixed case, spaces) is left as-is.
function fallbackLabel(title: string): string {
  if (title === title.toLowerCase() && /^[a-z0-9]+([_-][a-z0-9]+)*$/.test(title)) return humanize(title);
  return title;
}

function prettifyLabel(id: string, title: string, t: TFunction, i18nPrefix: string, idAliases: Record<string, string>): string {
  const normalizedId = idAliases[id.toLowerCase()] ?? id.toLowerCase();
  return t(`${i18nPrefix}.${normalizedId}`, { defaultValue: fallbackLabel(title) });
}

export const prettifyTypeLabel = (id: string, title: string, t: TFunction) => prettifyLabel(id, title, t, "search.filters.typeLabels", {});
export const prettifyStatusLabel = (id: string, title: string, t: TFunction) => prettifyLabel(id, title, t, "search.filters.statusLabels", STATUS_ID_ALIASES);

/** The request fields a set of filters contributes: `filters` when any is set, nothing otherwise. */
export function toSearchRequestFilters(filters: SearchFilters): { filters?: SearchFilters } {
  return activeFilterCount(filters) > 0 ? { filters } : {};
}
