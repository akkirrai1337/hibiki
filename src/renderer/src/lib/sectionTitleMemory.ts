export type BrowseSection = "home" | "catalog";

// v2 discards values collected by the old route-referrer heuristic, which could assign a title to
// the wrong browse section.
const STORAGE_KEY = "hibiki.last-title-by-section-v2";

type TitleBySection = Partial<Record<BrowseSection, { sourceId: string; animeId: string }>>;

function read(): TitleBySection {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? "{}");
    return value && typeof value === "object" ? value as TitleBySection : {};
  } catch {
    return {};
  }
}

export function rememberSectionTitle(section: BrowseSection, sourceId: string, animeId: string): void {
  const titles = read();
  titles[section] = { sourceId, animeId };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(titles));
}

export function getSectionTitle(section: BrowseSection): { sourceId: string; animeId: string } | undefined {
  return read()[section];
}

export function clearSectionTitle(section: BrowseSection): void {
  const titles = read();
  delete titles[section];
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(titles));
}

export function isRememberedTitlePath(pathname: string, title: { sourceId: string; animeId: string }): boolean {
  const match = pathname.match(/^\/anime\/([^/]+)\/([^/]+)$/);
  if (!match) return false;
  try {
    return decodeURIComponent(match[1]) === title.sourceId && decodeURIComponent(match[2]) === title.animeId;
  } catch {
    return false;
  }
}
