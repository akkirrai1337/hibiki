import {
  Ban,
  CalendarDays,
  Captions,
  CircleCheck,
  Clock,
  Film,
  Flower2,
  Globe,
  Heart,
  Leaf,
  MonitorPlay,
  Library,
  ListOrdered,
  Mic,
  Music,
  Pause,
  Radio,
  RefreshCw,
  Snowflake,
  Sparkles,
  Star,
  Sun,
  ThumbsUp,
  TrendingUp,
  Tv,
  Video,
  BadgePlus,
  ArrowDownAZ,
  ArrowUpDown,
  type LucideIcon,
} from "lucide-react";
import type { SearchFilterDef, SearchFilterOption } from "@shared/types";

// How a filter's options are drawn is decided here, from what they are called - the same rules the
// Android app applies (SourceFilterControls.kt), so a source describes *what* it filters by and
// looks the same on both. Nothing here is per-source.

const normalize = (value: string) => value.trim().toLowerCase().replace(/[-_]+/g, " ");

/** Seasons, release statuses and sub/dub languages: the options that get connected icon buttons. */
export function optionIcon(option: SearchFilterOption): LucideIcon | null {
  for (const name of [option.title, option.id]) {
    switch (normalize(name)) {
      case "winter": return Snowflake;
      case "spring": return Flower2;
      case "summer": return Sun;
      case "fall":
      case "autumn": return Leaf;
      case "finished airing":
      case "finished":
      case "completed":
      case "ended":
      case "released":
      case "завершён":
      case "завершен":
      case "вышел": return CircleCheck;
      case "currently airing":
      case "airing":
      case "ongoing":
      case "releasing":
      case "онгоинг":
      case "выходит": return Radio;
      case "not yet aired":
      case "not yet released":
      case "upcoming":
      case "announced":
      case "anons":
      case "announcement":
      case "анонс": return Clock;
      case "hiatus":
      case "on hiatus": return Pause;
      case "cancelled":
      case "canceled": return Ban;
      case "sub":
      case "subbed":
      case "subtitles":
      case "softsub":
      case "hardsub": return Captions;
      case "dub":
      case "dubbed": return Mic;
      case "raw": return Video;
    }
  }
  return null;
}

// Matched on the option's id as well as its title: titles are often in the source's language
// ("ТБ-серіал", "Фільм") while the id is the plain "tv" / "movie".
function typeChipIcon(option: SearchFilterOption): LucideIcon | null {
  for (const name of [option.title, option.id]) {
    switch (normalize(name)) {
      case "movie":
      case "film":
      case "фильм":
      case "фільм":
      case "полнометражный фильм":
      case "повнометражне": return Film;
      case "music":
      case "музыка":
      case "музика": return Music;
      case "ona": return Globe;
      case "ova":
      case "oad": return Video;
      case "special":
      case "спешл":
      case "спецвыпуск":
      case "спеціальний випуск": return Sparkles;
      case "tv":
      case "tv show":
      case "tv shows":
      case "tv series":
      case "series":
      case "тв":
      case "тб":
      case "тб серіал":
      case "тв сериал":
      case "сериал":
      case "серіал": return Tv;
      case "tv short": return Library;
      case "tv special": return MonitorPlay;
    }
  }
  return null;
}

function sortChipIcon(option: SearchFilterOption): LucideIcon | null {
  const t = normalize(option.title);
  const has = (...words: string[]) => words.some((w) => t.includes(w));
  if (has("favorit", "favourit")) return Heart;
  if (has("like")) return ThumbsUp;
  if (has("updated", "update")) return RefreshCw;
  if (has("added", "newest", "latest", "new")) return BadgePlus;
  if (has("score", "rating", "rated")) return Star;
  if (has("name", "title", "a z", "alphab")) return ArrowDownAZ;
  if (has("release", "date", "year", "aired")) return CalendarDays;
  if (has("view", "popular", "trend")) return TrendingUp;
  if (has("episode")) return ListOrdered;
  if (has("default", "relevan")) return ArrowUpDown;
  return null;
}

export const isSortFilter = (def: SearchFilterDef) => /sort|order|сортир|порядок/i.test(`${def.id} ${def.title}`);
const isTypeFilter = (def: SearchFilterDef) => /type|format|тип|формат/i.test(`${def.id} ${def.title}`);

/** A small icon drawn on each chip of a filter, when its options are types or sort orders. */
export function chipIconFor(def: SearchFilterDef): ((option: SearchFilterOption) => LucideIcon | null) | null {
  if (isSortFilter(def)) return sortChipIcon;
  if (isTypeFilter(def)) return typeChipIcon;
  return null;
}

/** A short list whose every option has an icon is drawn as connected buttons, one pressed at a time. */
export function isConnectedToggle(def: SearchFilterDef): boolean {
  if (def.type !== "select" && def.type !== "multi" && def.type !== "tristate") return false;
  const options = def.options ?? [];
  return options.length >= 2 && options.length <= 6 && options.every((o) => optionIcon(o) !== null);
}

/** Strictness of an age-rating label, lowest first; null when the label is not a recognisable rating. */
export function ageRatingRank(title: string): number | null {
  const t = title.toLowerCase().replace(/\s+/g, "");
  if (t.includes("rx") || t.includes("hentai") || t.includes("nc-17") || t.includes("18+")) return 5;
  if (t.includes("r+")) return 4;
  if (t.includes("r-17") || t.includes("r17") || t.includes("17+") || t === "r") return 3;
  if (t.includes("pg-13") || t.includes("pg13") || t.includes("13+")) return 2;
  if (t.startsWith("pg")) return 1;
  if (t === "g" || t.startsWith("all") || t.startsWith("g-") || t.includes("0+") || t.includes("everyone")) return 0;
  return null;
}

/** Age ratings are ordered, so they are picked with a slider: everything up to the chosen one. */
export function isAgeRatingFilter(def: SearchFilterDef): boolean {
  if (def.type !== "multi" && def.type !== "tristate") return false;
  const options = def.options ?? [];
  return (
    options.length >= 3 &&
    /rating|age|рейтинг|возраст|вік/i.test(`${def.id} ${def.title}`) &&
    options.every((o) => ageRatingRank(o.title) !== null)
  );
}

const YEAR_FILTER_TITLE = /^(year|years|release year|year of release|год|рік|год выпуска|рік випуску)$/i;

/**
 * A year filter that a source offers as a list of years (a wall of 47 chips) is drawn as a range
 * slider instead, like the Android window does. Null when the filter is not such a list.
 * Ordered oldest first; the slider's ends are the first and last of them.
 */
export function yearOptions(def: SearchFilterDef): Array<{ year: number; id: string }> | null {
  if (def.type !== "multi" && def.type !== "tristate") return null;
  if (!YEAR_FILTER_TITLE.test(def.title.trim()) && !YEAR_FILTER_TITLE.test(def.id.trim())) return null;
  const years = (def.options ?? [])
    .map((o) => ({ year: Number(o.title.trim()), id: o.id }))
    .filter((y) => Number.isInteger(y.year) && y.year >= 1900 && y.year <= 2100)
    .sort((a, b) => a.year - b.year);
  return years.length >= 2 ? years : null;
}

/**
 * A one-choice list of years ("Browse by Year": <select>, 2026, 2025, ...), as many Aniyomi
 * extensions declare their year filter - drawn as a slider over the years instead of fifty chips.
 * A few non-year options (a "<select>" / "Any" placeholder) are allowed; the first becomes "any year".
 */
export function singleYearOptions(def: SearchFilterDef): { years: Array<{ year: number; id: string }>; anyId: string | null } | null {
  if (def.type !== "select" || def.directional) return null;
  if (!/year|год|рік/i.test(`${def.id} ${def.title}`)) return null;
  const options = def.options ?? [];
  const years = options
    .map((o) => ({ year: /^\d{4}$/.test(o.title.trim()) ? Number(o.title.trim()) : NaN, id: o.id }))
    .filter((y) => y.year >= 1900 && y.year <= 2100)
    .sort((a, b) => a.year - b.year);
  const others = options.filter((o) => !years.some((y) => y.id === o.id));
  if (years.length < 5 || others.length > 2) return null;
  return { years, anyId: others[0]?.id ?? null };
}

// Where each kind of filter sits in the window (the Android window's order): sort first, then
// season, genres, status, language, type, year; anything else follows in the order the source gave.
function filterRank(def: SearchFilterDef): number {
  const t = `${def.id} ${def.title}`.toLowerCase();
  const has = (...words: string[]) => words.some((w) => t.includes(w));
  if (isSortFilter(def)) return 0;
  if (has("season", "сезон")) return 1;
  if (has("genre", "tag", "categor", "жанр", "теги")) return 2;
  if (has("status", "статус")) return 4;
  if (has("language", "lang", "audio", "sub", "язык", "мова")) return 5;
  if (isTypeFilter(def)) return 6;
  // The year slider sits under the short chip rows, not above them.
  if (YEAR_FILTER_TITLE.test(def.title.trim()) || YEAR_FILTER_TITLE.test(def.id.trim())) return 7;
  return 8;
}

export function inDisplayOrder(defs: SearchFilterDef[]): SearchFilterDef[] {
  return [...defs].sort((a, b) => filterRank(a) - filterRank(b));
}

/** "Unknown" is not something to filter by - the source's catch-all bucket - so it is never offered. */
export function withoutUnknown(defs: SearchFilterDef[]): SearchFilterDef[] {
  const isUnknown = (o: SearchFilterOption) => normalize(o.title) === "unknown" || normalize(o.id) === "unknown";
  return defs.map((d) => (d.options ? { ...d, options: d.options.filter((o) => !isUnknown(o)) } : d));
}
