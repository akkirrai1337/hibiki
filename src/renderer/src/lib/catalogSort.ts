import type { TFunction } from "i18next";
import type { SearchFilterOption } from "@shared/types";

const keyOf = (value: string) => value.trim().toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "_").replace(/^_|_$/g, "");

/**
 * A sort order's label. The source picks the id and title; the client only dresses them up: a known
 * word ("rating_counters", "relevance") gets a proper localized name, anything else keeps the
 * source's own title, capitalised.
 */
export function sortLabel(option: SearchFilterOption, t: TFunction): string {
  for (const key of [keyOf(option.id), keyOf(option.title)]) {
    if (key && !key.includes(".")) {
      const name = t(`catalogPage.sort.names.${key}`, { defaultValue: "" });
      if (name) return name;
    }
  }
  const title = option.title.replace(/[_-]+/g, " ").trim();
  return title.charAt(0).toUpperCase() + title.slice(1);
}

// The home page's own "auto" default (see Settings > Home): the source's own relevance ordering
// where it has one, or no sort at all otherwise - never a keyword-matched guess at "popular". A
// guess like that used to pick whichever of the source's own sort ids happened to contain a word
// like "views" or "top", which is exactly the kind of vocabulary the host has no business assuming
// every source shares - and in practice it picked worse results than just asking for nothing at
// all (relevance, or the source's own default listing when it doesn't have that concept either).
export function pickRelevanceSort(options: SearchFilterOption[]): string | undefined {
  return options.find((o) => o.id === "relevance")?.id;
}
