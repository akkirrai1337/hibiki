import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { Check, Search, Home, LayoutGrid, Bookmark, Download, Radio, User, Settings } from "lucide-react";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { useUiStore } from "@/stores/uiStore";
import { useSpotlightStore } from "@/stores/spotlightStore";
import { useSourceUpdateCount } from "@/lib/sourceUpdates";
import { cn } from "@/lib/cn";
import { clearSectionTitle, getSectionTitle, isRememberedTitlePath, type BrowseSection } from "@/lib/sectionTitleMemory";

// 236px is the ceiling (the original full design). Below MIN_FULL_WIDTH there isn't room to keep
// labels legible, so instead of shrinking/truncating text we snap straight to an icon-only strip
// at COMPACT_WIDTH — that's the floor, not a continuum down to zero.
const MAX_WIDTH = 236;
const MIN_FULL_WIDTH = 160;
const COMPACT_WIDTH = 68;
const SNAP_POINT = (MIN_FULL_WIDTH + COMPACT_WIDTH) / 2;

const navigation = [
  { to: "/", labelKey: "nav.home", icon: Home },
  { to: "/catalog", labelKey: "nav.catalog", icon: LayoutGrid },
  { to: "/search", labelKey: "nav.search", icon: Search },
  { to: "/library", labelKey: "library.title", icon: Bookmark },
  { to: "/downloads", labelKey: "downloads.title", icon: Download },
  { to: "/sources", labelKey: "nav.sources", icon: Radio },
] as const;

const bottomNavigation = [
  { to: "/settings", labelKey: "nav.settings", icon: Settings },
  { to: "/profile", labelKey: "nav.profile", icon: User },
] as const;

// Settings is where the hidden entries are brought back from, so it is never offered for hiding.
const ALWAYS_SHOWN = "/settings";

export function Sidebar() {
  const { t } = useTranslation();
  const width = useUiStore((s) => s.sidebarWidth);
  const setWidth = useUiStore((s) => s.setSidebarWidth);
  const compact = width <= COMPACT_WIDTH;
  const draggingRef = useRef(false);
  const sourceUpdateCount = useSourceUpdateCount();
  const hiddenNav = useUiStore((s) => s.hiddenNav);
  const [menu, setMenu] = useState<{ x: number; y: number } | null>(null);

  // Exposed as a CSS var (not just this element's own inline `width` below) so pages that paint a
  // full-viewport-width fixed background of their own - the anime detail page's blurred-poster
  // backdrop is the one that actually needs this, see anime.$sourceId.$animeId.tsx - can stop
  // short of the sidebar's real, current (possibly user-resized) width instead of running full
  // bleed underneath it. That only started mattering once the sidebar itself could become
  // translucent (the "app background" theme, see lib/theme.ts): a per-page backdrop sitting behind
  // an opaque sidebar was invisible regardless of how far it extended, but behind a translucent one
  // it would otherwise visibly tint the nav differently on whichever page happened to have its own
  // backdrop going, which is exactly the "why does the sidebar change color here" bug this fixes.
  useEffect(() => {
    document.documentElement.style.setProperty("--sidebar-width", `${compact ? COMPACT_WIDTH : width}px`);
  }, [width, compact]);

  const onHandlePointerDown = (e: React.PointerEvent) => {
    e.preventDefault();
    draggingRef.current = true;
    const startX = e.clientX;
    const startWidth = compact ? COMPACT_WIDTH : width;

    const onMove = (ev: PointerEvent) => {
      const raw = startWidth + (ev.clientX - startX);
      setWidth(raw < SNAP_POINT ? COMPACT_WIDTH : Math.min(MAX_WIDTH, Math.max(MIN_FULL_WIDTH, raw)));
    };
    const onUp = () => {
      draggingRef.current = false;
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  };

  return (
    <aside
      className="relative flex shrink-0 flex-col border-r border-border bg-app-surface px-3 pb-5 pt-4"
      style={{ width }}
      onContextMenu={(event) => {
        event.preventDefault();
        setMenu({ x: event.clientX, y: event.clientY });
      }}
    >
      <nav className="flex flex-col gap-0.5">{navigation.filter((item) => !hiddenNav.includes(item.to)).map((item) => <NavLink key={item.to} to={item.to} label={t(item.labelKey)} icon={item.icon} compact={compact} badgeCount={item.to === "/sources" ? sourceUpdateCount : 0} />)}</nav>
      <div className="mt-auto flex flex-col gap-0.5 border-t border-border pt-3">
        {bottomNavigation.filter((item) => !hiddenNav.includes(item.to)).map((item) => <NavLink key={item.to} to={item.to} label={t(item.labelKey)} icon={item.icon} compact={compact} />)}
      </div>
      <div
        onPointerDown={onHandlePointerDown}
        className="app-no-drag group absolute -right-1.5 top-0 z-10 h-full w-3 cursor-col-resize"
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize sidebar"
      >
        <div className="mx-auto h-full w-px bg-transparent transition-colors group-hover:bg-accent/50" />
      </div>
      <AnimatePresence>{menu && <NavMenu at={menu} onClose={() => setMenu(null)} />}</AnimatePresence>
    </aside>
  );
}

function NavLink({
  to,
  label,
  icon: Icon,
  compact,
  badgeCount = 0,
}: {
  to: "/" | "/catalog" | "/search" | "/library" | "/downloads" | "/sources" | "/settings" | "/profile";
  label: string;
  icon: typeof Home;
  compact: boolean;
  badgeCount?: number;
}) {
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (state) => (state.resolvedLocation ?? state.location).pathname });
  const section: BrowseSection | null = to === "/" ? "home" : to === "/catalog" ? "catalog" : null;
  const spotlightOpen = useSpotlightStore((s) => s.open);
  if (to === "/search") {
    // Quick search is a panel (Ctrl+K), not a page to move to.
    return (
      <button onClick={() => useSpotlightStore.getState().toggle()} title={compact ? label : undefined} className={cn("app-no-drag group relative flex w-full items-center rounded-lg py-2 text-[13px] font-medium text-muted transition-colors hover:bg-text/[.05] hover:text-text", compact ? "justify-center px-0" : "gap-3 px-3")}>
        <span className={cn("absolute -left-3 top-1/2 h-4 w-[3px] -translate-y-1/2 rounded-r-full bg-accent transition-opacity", spotlightOpen ? "opacity-100" : "opacity-0")} />
        {spotlightOpen && <span className="absolute inset-0 rounded-lg bg-text/[.08]" />}
        <span className="relative z-10 shrink-0"><Icon className="h-[17px] w-[17px]" strokeWidth={2} /></span>
        {!compact && <span className="relative z-10 truncate">{label}</span>}
      </button>
    );
  }
  return <Link to={to} title={compact ? label : undefined} data-browse-section={section ?? undefined} onClick={(event) => {
    if (!section || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    // Clicking the section while already there, or while viewing its remembered title, returns
    // to the section itself. From any other page it opens that section's remembered title.
    if (pathname === to) return;
    const remembered = getSectionTitle(section);
    if (!remembered) return;
    if (isRememberedTitlePath(pathname, remembered)) {
      clearSectionTitle(section);
      return;
    }
    event.preventDefault();
    void navigate({ to: "/anime/$sourceId/$animeId", params: remembered });
  }} className={cn("app-no-drag group relative flex items-center rounded-lg py-2 text-[13px] font-medium text-muted transition-colors hover:bg-text/[.05] hover:text-text", compact ? "justify-center px-0" : "gap-3 px-3")} activeProps={{ className: "!text-text" }}>
    {({ isActive }: { isActive: boolean }) => <>
      <span className={cn("absolute -left-3 top-1/2 h-4 w-[3px] -translate-y-1/2 rounded-r-full bg-accent transition-opacity", isActive ? "opacity-100" : "opacity-0")} />
      {isActive && <span className="absolute inset-0 rounded-lg bg-text/[.08]" />}
      <span className="relative z-10 shrink-0">
        <Icon className="h-[17px] w-[17px]" strokeWidth={2} />
        {badgeCount > 0 && <NavBadge count={badgeCount} />}
      </span>
      {!compact && <span className="relative z-10 truncate">{label}</span>}
    </>}
  </Link>;
}

/**
 * The count of installed sources with an update waiting, as a small red circle riding the corner
 * of the sidebar icon - the same shape and rule as the Android app's bottom-nav badge: a bare
 * number, or "9+" once double digits would no longer fit a circle this small.
 */
function NavBadge({ count }: { count: number }) {
  return (
    <span className="absolute -right-1.5 -top-1.5 flex h-[15px] min-w-[15px] items-center justify-center rounded-full bg-rose-500 px-[3px] text-[9px] font-bold leading-none text-white">
      {count > 9 ? "9+" : count}
    </span>
  );
}

/** Right-click on the sidebar: every entry with a check, to show or hide it. */
function NavMenu({ at, onClose }: { at: { x: number; y: number }; onClose: () => void }) {
  const { t } = useTranslation();
  const popoverTheme = usePopoverTheme();
  const hiddenNav = useUiStore((s) => s.hiddenNav);
  const setNavHidden = useUiStore((s) => s.setNavHidden);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onPointerDown = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) onClose();
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener("blur", onClose);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("blur", onClose);
    };
  }, [onClose]);

  const items = [...navigation, ...bottomNavigation];
  // Kept inside the window: opened low on the screen it would otherwise hang off the bottom.
  const height = items.length * 36 + 44;
  const top = Math.min(at.y, window.innerHeight - height - 8);
  const left = Math.min(at.x, window.innerWidth - 232);

  return createPortal(
    <motion.div
      ref={root}
      initial={{ opacity: 0, scale: 0.97 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.97 }}
      transition={{ duration: 0.12 }}
      style={{ ...popoverTheme, top, left, transformOrigin: "top left" }}
      className="fixed z-50 w-56 overflow-hidden rounded-xl border border-border bg-app-popover py-1 shadow-2xl"
    >
      <p className="px-3.5 pb-1 pt-2 text-[11px] font-bold uppercase tracking-wide text-muted">{t("nav.customize")}</p>
      {items.map((item) => {
        const shown = !hiddenNav.includes(item.to);
        const locked = item.to === ALWAYS_SHOWN;
        return (
          <button
            key={item.to}
            disabled={locked}
            onClick={() => setNavHidden(item.to, shown)}
            className="flex w-full items-center gap-2.5 px-3.5 py-2 text-left text-sm text-text transition-colors hover:bg-text/[.06] disabled:opacity-50 disabled:hover:bg-transparent"
          >
            <item.icon className="h-4 w-4 text-muted" strokeWidth={2} />
            <span className="flex-1 truncate">{t(item.labelKey)}</span>
            {shown && <Check className="h-4 w-4 text-accent-text" strokeWidth={2.5} />}
          </button>
        );
      })}
    </motion.div>,
    document.body,
  );
}
