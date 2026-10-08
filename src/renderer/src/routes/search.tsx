import { createFileRoute } from "@tanstack/react-router";
import { SearchResultsPage } from "@/pages/searchResults";

// The full answer to a search made in the quick-search panel (see components/SearchSpotlight.tsx): no field
// of its own - the query is in the URL and the filters in their store.
export const Route = createFileRoute("/search")({
  validateSearch: (search: Record<string, unknown>): { q: string } => ({
    q: typeof search.q === "string" ? search.q : "",
  }),
  component: SearchResultsPage,
});
