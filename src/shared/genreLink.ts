import type { FilterValue, SearchFilterCatalog } from "@shared/types";

const normalize = (text: string) => text.trim().toLowerCase();

/**
 * The catalog filter that means "this genre" for a source, or null when the source has no such
 * option. A title only carries a genre's name, so it is matched against the options of the source's
 * own genre filter by name; the host knows nothing about genres beyond what the source declared.
 */
export function genreFilterFor(catalog: Pick<SearchFilterCatalog, "filters"> | undefined, genre: string): { filterId: string; value: FilterValue } | null {
  const wanted = normalize(genre);
  for (const def of catalog?.filters ?? []) {
    if (!/genre|жанр/i.test(`${def.id} ${def.title}`)) continue;
    const option = def.options?.find((o) => normalize(o.title) === wanted);
    if (!option) continue;
    if (def.type === "tristate") return { filterId: def.id, value: { include: [option.id], exclude: [] } };
    if (def.type === "multi") return { filterId: def.id, value: [option.id] };
    if (def.type === "select") return { filterId: def.id, value: option.id };
  }
  return null;
}
