import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { hibiki } from "@/lib/hibiki";
import { genreFilterFor } from "@/lib/genreLink";
import { cn } from "@/lib/cn";
import { useUiStore } from "@/stores/uiStore";
import { useCatalogIntentStore } from "@/stores/catalogIntentStore";

/** A genre chip that opens the catalog pre-filtered by it, wherever the source's own catalog
 * filters actually have a matching genre option - otherwise it's just a label, since there'd be
 * nothing honest to open. Shared by the title detail page and the home hero carousel so both
 * behave identically; each caller still supplies its own pill styling via `className`. */
export function GenreChip({ genre, sourceId, className }: { genre: string; sourceId: string; className: string }) {
  const navigate = useNavigate();
  const setActiveSourceId = useUiStore((s) => s.setActiveSourceId);
  const requestCatalog = useCatalogIntentStore((s) => s.request);
  // The same key the catalog and the filter panel read, so this costs no extra request.
  const catalog = useQuery({ queryKey: ["filterCatalog", sourceId], queryFn: () => hibiki.sources.filterCatalog(sourceId) });
  const target = genreFilterFor(catalog.data, genre);
  if (!target) return <span className={className}>{genre}</span>;
  return (
    <button
      onClick={() => {
        setActiveSourceId(sourceId);
        requestCatalog({ sourceId, filters: { [target.filterId]: target.value } });
        void navigate({ to: "/catalog" });
      }}
      className={cn(className, "cursor-pointer transition-colors")}
    >
      {genre}
    </button>
  );
}
