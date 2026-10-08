import { useEffect, useRef } from "react";
import { useNavigate, useRouterState } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { Bookmark, Home, LayoutGrid, Search, User } from "lucide-react";
import { mobileSearchMemory } from "@/lib/mobile";
import { cn } from "@/lib/cn";

// The phone's whole navigation: five places, each owning the screens reached from it. History and
// downloads live under the library, settings and sources under the profile - the desktop sidebar's
// other entries, one level down.
const tabs = [
  { to: "/", labelKey: "nav.home", icon: Home, owns: ["/"] },
  { to: "/catalog", labelKey: "nav.catalog", icon: LayoutGrid, owns: ["/catalog"] },
  { to: "/search", labelKey: "nav.search", icon: Search, owns: ["/search"] },
  { to: "/library", labelKey: "library.title", icon: Bookmark, owns: ["/library", "/history", "/downloads"] },
  { to: "/profile", labelKey: "nav.profile", icon: User, owns: ["/profile", "/settings", "/sources"] },
] as const;

type TabPath = (typeof tabs)[number]["to"];

function tabFor(pathname: string): TabPath | null {
  return tabs.find((tab) => (tab.owns as readonly string[]).includes(pathname))?.to ?? null;
}

/**
 * The page colour behind the status bar, so nothing scrolls visibly under the clock and icons. Solid
 * everywhere except the screens whose artwork is meant to run under the bar (the home hero, a title's
 * poster, the profile's banner): there it fades in over the first stretch of scrolling, as the
 * artwork leaves. Solid, it is the page's own colour, so a header continues under the bar unbroken.
 */
export function MobileStatusScrim() {
  const scrim = useRef<HTMLDivElement>(null);
  const pathname = useRouterState({ select: (s) => (s.resolvedLocation ?? s.location).pathname });
  const artwork = pathname === "/" || pathname === "/profile" || pathname.startsWith("/anime/");
  const artworkRef = useRef(artwork);
  artworkRef.current = artwork;
  const opacityFor = (scrollTop: number) => (artworkRef.current ? Math.min(1, scrollTop / 160) : 1);
  useEffect(() => {
    // scroll does not bubble, but capturing on the document sees every page's own scroll box.
    const onScroll = (event: Event) => {
      const target = event.target;
      // Only a page's own vertical scroll box - not a poster row scrolled sideways.
      if (!(target instanceof HTMLElement) || !scrim.current || !target.matches("[data-page-scroll], [data-scroll-restoration-id]")) return;
      scrim.current.style.opacity = String(opacityFor(target.scrollTop));
    };
    document.addEventListener("scroll", onScroll, { capture: true, passive: true });
    return () => document.removeEventListener("scroll", onScroll, { capture: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reads the current screen through artworkRef
  }, []);
  // A page switch shows another scroll box, possibly scrolled elsewhere: start from its position.
  useEffect(() => {
    const box = document.querySelector<HTMLElement>(`[data-page-scroll="${pathname}"], [data-scroll-restoration-id="app-main"]`);
    if (scrim.current) scrim.current.style.opacity = String(opacityFor(box?.scrollTop ?? 0));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- opacityFor only reads refs
  }, [pathname]);
  return <div ref={scrim} aria-hidden className="mobile-status-scrim pointer-events-none fixed inset-x-0 top-0 z-30 h-[var(--safe-top)] bg-app-bg opacity-0" />;
}

/** Floating bar at the bottom of every screen except the player and a title page (it pins its watch button there). */
export function MobileTabBar() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (s) => (s.resolvedLocation ?? s.location).pathname });
  // A title page belongs to whichever tab it was opened from, so that tab stays lit under it.
  const lastTab = useRef<TabPath>("/");
  const owner = tabFor(pathname);
  if (owner) lastTab.current = owner;
  const active = owner ?? lastTab.current;

  return (
    <nav
      className="mobile-tabbar fixed inset-x-3 z-40 flex h-14 items-stretch rounded-[1.75rem] border border-border bg-app-popover shadow-[0_8px_28px_rgba(0,0,0,0.45)]"
      style={{ bottom: "calc(0.625rem + var(--safe-bottom))" }}
    >
      {tabs.map((tab) => {
        const isActive = tab.to === active;
        return (
          <button
            key={tab.to}
            type="button"
            aria-current={isActive ? "page" : undefined}
            onClick={() => {
              if (pathname === tab.to) {
                // Already there: back to the top, the way a tab bar does it.
                document.querySelector<HTMLElement>(`[data-page-scroll="${tab.to}"]`)?.scrollTo({ top: 0, behavior: "smooth" });
                return;
              }
              // Search comes back with what it last searched.
              if (tab.to === "/search") void navigate({ to: "/search", search: { q: mobileSearchMemory.query } });
              else void navigate({ to: tab.to });
            }}
            className={cn("flex flex-1 flex-col items-center justify-center gap-0.5 text-[10.5px] font-medium transition-colors", isActive ? "text-accent-text" : "text-muted active:text-text")}
          >
            <tab.icon className="h-[21px] w-[21px]" strokeWidth={isActive ? 2.3 : 1.9} />
            <span className="leading-none">{t(tab.labelKey)}</span>
          </button>
        );
      })}
    </nav>
  );
}
