// A source with no real per-dub grouping names its one-and-only group "Episodes" - a structural
// placeholder, not an actual dub name. Always this exact literal in English, regardless of the
// app's own language: it comes from the source's own code (see e.g. hibiki-sources' anichi.js),
// never through this app's i18n, so there is nothing to localize here.
export function isGenericDubTitle(title: string | null | undefined): boolean {
  return !!title && title.trim().toLowerCase() === "episodes";
}
