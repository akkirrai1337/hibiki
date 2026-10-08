import { createFileRoute } from "@tanstack/react-router";

// `sort` is one of the source's own sort-order ids (or "recent"), so it can't be validated here -
// the page checks it against what the source offers and falls back to the source's default.
export const Route = createFileRoute("/catalog")({
  validateSearch: (search: Record<string, unknown>): { sort?: string } => ({
    sort: typeof search.sort === "string" ? search.sort : undefined,
  }),
  component: () => null,
});
