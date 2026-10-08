import { type CSSProperties, lazy, Suspense, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createRootRoute, Outlet, useRouter, useRouterState } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useUiStore } from "@/stores/uiStore";
import { useKnownSourcesStore } from "@/stores/knownSourcesStore";
import { usePlayerPrefsStore } from "@/stores/playerPrefsStore";
import { applyAccentColor, applyBackgroundTheme, DEFAULT_MOBILE_ACCENT, DEFAULT_MOBILE_ACCENT_LIGHT, BACKGROUND_THEME_PRESETS, CUSTOM_BACKGROUND_THEME_ID, customBackgroundGradientCss } from "@/lib/theme";
import { TitleBar } from "@/components/TitleBar";
import { MobileUpdateSheet } from "@/components/MobileUpdateSheet";
import { BackgroundWorkNotice } from "@/components/BackgroundWorkNotice";
import { SyncListener } from "@/components/DeviceSync";
import { MissingSourcesPrompt } from "@/components/MissingSourcesPrompt";
import { Sidebar } from "@/components/Sidebar";
import { MobileStatusScrim, MobileTabBar } from "@/components/MobileTabBar";
import { LibrarySegments } from "@/components/MobilePageHeader";
import { installBackButton, isMobile, PageActiveContext, usePageTransition } from "@/lib/mobile";
import { SearchSpotlight } from "@/components/SearchSpotlight";
import { AchievementToast } from "@/components/AchievementToast";
import { useAchievementUnlocks } from "@/lib/achievementUnlocks";
import { hibiki } from "@/lib/hibiki";
import { installGlobalErrorLogging } from "@/lib/log";
import { useAppZoom } from "@/lib/useAppZoom";
import { cn } from "@/lib/cn";
import { Onboarding } from "@/features/onboarding/Onboarding";
import { SignInPromptProvider } from "@/components/SignInPrompt";
import { DeepLinkHandler } from "@/components/DeepLinkHandler";
import { rememberSectionTitle, type BrowseSection } from "@/lib/sectionTitleMemory";

// Every static (paramless) route's own route component is a no-op (see index.tsx) - its real
// content is one of these, kept alive here instead once first visited (see `visited` below). Lazy
// imports keep every unvisited screen out of the startup bundle without changing that persistence.
type PersistedPage = React.ComponentType & { preload: () => Promise<unknown> };

// React.lazy suspends on a page's first render even when its chunk is already loaded, and React
// then holds the fallback (an empty page) on screen for a moment - on the phone, half a second of
// blank in the middle of every first visit. Once `preload` has the module, a newly mounted page
// renders it directly instead; the choice is made once per mount, so a page never swaps between
// the two and remounts.
function lazyPage(load: () => Promise<React.ComponentType>): PersistedPage {
  let loaded: React.ComponentType | null = null;
  const preload = () => load().then((component) => { loaded = component; return component; });
  const Lazy = lazy(() => preload().then((component) => ({ default: component })));
  function Page() {
    const [Direct] = useState(() => loaded);
    return Direct ? <Direct /> : <Lazy />;
  }
  return Object.assign(Page, { preload });
}

const PERSISTED_PAGES: Record<string, PersistedPage> = {
  "/": lazyPage(() => import("@/pages/home").then((module) => module.CatalogPage)),
  "/catalog": lazyPage(() => import("@/pages/catalog").then((module) => module.CatalogBrowsePage)),
  "/library": lazyPage(() => import("@/pages/library").then((module) => module.LibraryPage)),
  "/history": lazyPage(() => import("@/pages/history").then((module) => module.HistoryPage)),
  "/downloads": lazyPage(() => import("@/pages/downloads").then((module) => module.DownloadsPage)),
  "/profile": lazyPage(() => import("@/pages/profile").then((module) => module.ProfilePage)),
  "/settings": lazyPage(() => import("@/pages/settings").then((module) => module.SettingsPage)),
  "/sources": lazyPage(() => import("@/pages/sources").then((module) => module.SourcesPage)),
};

// A lazy page has no DOM of its own until its chunk arrives. Keep the same page background in
// place for that brief interval so the app-wide theme gradient never flashes through before the
// page's `bg-app-bg` root mounts.
function PageShellFallback({ path }: { path: string }) {
  // Phone: the library's three views share their header, so it stays put while the next one loads.
  if (isMobile && (path === "/library" || path === "/history" || path === "/downloads")) {
    return <div className="min-h-full bg-app-bg px-4 pt-4" aria-busy="true"><LibrarySegments active={path} /></div>;
  }
  return <div className="min-h-full bg-app-bg" aria-busy="true" />;
}

// Development StrictMode intentionally remounts effects once. This is a real account write, not
// a disposable subscription, so keep it once-per-renderer-session in both dev and production.
let activityPingStarted = false;

export const Route = createRootRoute({ component: RootLayout });

/**
 * Wrapped here rather than inside the layout below because the layout returns early three separate
 * ways (onboarding, the player, the normal chrome) and the prompt has to outlive all of them: the
 * detail page and the player alike can refuse an action that needs a source account.
 */
function RootLayout() {
  // App-wide, not per screen: an install can happen with no source-listing screen open (the carry-over
  // from the previous Android app installs in the background at start), and a list fetched before it
  // would otherwise stay "fresh" and empty - the first-run sources step kept offering to skip.
  const queryClient = useQueryClient();
  useEffect(() => hibiki.sources.onChanged(() => {
    void queryClient.invalidateQueries({ queryKey: ["sources"] });
    void queryClient.invalidateQueries({ queryKey: ["installedVersions"] });
    void queryClient.invalidateQueries({ queryKey: ["missingSources"] });
  }), [queryClient]);
  return (
    <SignInPromptProvider>
      <DeepLinkHandler />
      <RootLayoutContent />
    </SignInPromptProvider>
  );
}

function RootLayoutContent() {
  const theme = useUiStore((s) => s.theme);
  // useLayoutEffect (not useEffect) - a passive effect only runs after the browser has already
  // painted the frame, which left one extra frame where the page was still showing the old theme;
  // a layout effect runs synchronously right after the DOM update but before that paint.
  useLayoutEffect(() => { document.documentElement.classList.toggle("dark", theme === "dark"); }, [theme]);
  // On the phone the status/navigation bar icons follow the theme: light icons over the dark one.
  useEffect(() => { void hibiki.device?.setSystemBars({ style: theme }); }, [theme]);
  const router = useRouter();
  useEffect(() => installBackButton(
    () => router.history.back(),
    () => {
      // Nothing behind this screen: the player goes up to its title, anything else to home.
      const path = router.state.location.pathname;
      if (path === "/") return false;
      const watch = path.match(/^\/watch\/([^/]+)\/([^/]+)\//);
      if (watch) void router.navigate({ to: "/anime/$sourceId/$animeId", params: { sourceId: decodeURIComponent(watch[1]), animeId: decodeURIComponent(watch[2]) }, replace: true });
      else void router.navigate({ to: "/", replace: true });
      return true;
    },
  ), [router]);
  const accentColor = useUiStore((s) => s.accentColor);
  // Depends on `theme` too, not just `accentColor` - see applyAccentColor's own comment for why
  // (--color-accent-text's white/black-vs-theme contrast fallback needs to know which theme is
  // active, not just which accent is picked).
  // Once per app start, not per render - see lib/log.ts.
  useEffect(() => { installGlobalErrorLogging(); }, []);
  // Once a launch, tell every source that reports activity that its account is online today -
  // that is what a site's "day streak" counts, and it should not depend on whether an episode
  // happened to be watched. Sources that report nothing, or are signed out, answer false and cost
  // one cheap check in the main process.
  useEffect(() => {
    if (activityPingStarted) return;
    // Account streak maintenance is background work. Starting it alongside the home catalog made
    // both compete for workers/network on the only load where first paint matters most.
    const timer = window.setTimeout(() => {
      if (activityPingStarted) return;
      activityPingStarted = true;
      void hibiki.sources
        .list()
        .then((sources) => Promise.all(sources.map((source) => hibiki.sources.pingOnline(source.id).catch(() => false))))
        .catch(() => undefined);
    }, 1_500);
    return () => window.clearTimeout(timer);
  }, []);

  // Ctrl/Cmd +/-/0, and re-applying the saved factor on launch.
  useAppZoom();

  // No pick yet: the CSS pink on desktop, white on the phone (black in its light theme).
  useEffect(() => {
    const mobileDefault = theme === "light" ? DEFAULT_MOBILE_ACCENT_LIGHT : DEFAULT_MOBILE_ACCENT;
    applyAccentColor(accentColor ?? (isMobile ? mobileDefault : null), theme);
  }, [accentColor, theme]);
  const backgroundTheme = useUiStore((s) => s.backgroundTheme);
  useEffect(() => { applyBackgroundTheme(backgroundTheme); }, [backgroundTheme, applyBackgroundTheme]);
  // Painted as this element's own `background-image`, sitting on top of its `bg-app-bg` background
  // -color (see globals.css) - not a separate fixed layer, since the Sidebar/TitleBar/page
  // backgrounds are just later DOM siblings within this same box: as long as their own backgrounds
  // are translucent (which applyBackgroundTheme's `bg-theme-active` class makes them, only while a
  // gradient is actually picked), the gradient painted here already shows through them exactly
  // where it should, `backdrop-filter: blur` and all, with no z-index/stacking of its own to manage.
  const customBackgroundGradient = useUiStore((s) => s.customBackgroundGradient);
  const backgroundGradient = backgroundTheme === CUSTOM_BACKGROUND_THEME_ID
    ? (customBackgroundGradient ? customBackgroundGradientCss(customBackgroundGradient) : undefined)
    : BACKGROUND_THEME_PRESETS.find((p) => p.id === backgroundTheme)?.gradient;
  const onboardingCompleted = useUiStore((s) => s.onboardingCompleted);
  const setOnboardingCompleted = useUiStore((s) => s.setOnboardingCompleted);
  // The main process owns the actual RPC connection - this just keeps it in sync with the
  // persisted setting, both on boot and whenever the Settings toggle changes.
  const discordRpcEnabled = useUiStore((s) => s.discordRpcEnabled);
  useEffect(() => { hibiki.discord.setEnabled(discordRpcEnabled); }, [discordRpcEnabled]);
  // The player replaces the sidebar/nav chrome with the video itself, but keeps the same TitleBar
  // — it already matches the app's look and gives back/forward navigation + a home for the OS
  // window buttons, so there's no need for the player to grow its own copy of that backdrop.
  // The *resolved* location, not the pending one. `location` flips the moment a navigation starts,
  // while the player is a code-split chunk that still has to load - so the chrome tore down first
  // and the sidebar visibly vanished into an empty black frame before the player appeared. Reading
  // the resolved location keeps the page you are leaving on screen, sidebar and all, until the
  // player is actually ready to be shown, and the swap then happens in one frame.
  const isWatching = useRouterState({ select: (s) => (s.resolvedLocation ?? s.location).pathname.startsWith("/watch/") });
  // Resolved as well, and for the same reason: this decides which persisted page is the visible
  // one, so reading the pending location hid the page being left the instant a navigation started,
  // leaving an empty frame under the chrome until the next route's chunk arrived.
  const pathname = useRouterState({ select: (s) => (s.resolvedLocation ?? s.location).pathname });
  const browseSection = useRef<BrowseSection | null>(null);
  const clickedBrowseSection = useRef<BrowseSection | null>(null);
  useLayoutEffect(() => {
    const onClickCapture = (event: MouseEvent) => {
      if (!(event.target instanceof Element)) return;
      const section = event.target.closest<HTMLElement>("[data-browse-section]")?.dataset.browseSection;
      if (section === "home" || section === "catalog") clickedBrowseSection.current = section;
    };
    document.addEventListener("click", onClickCapture, true);
    return () => document.removeEventListener("click", onClickCapture, true);
  }, []);
  useLayoutEffect(() => {
    if (pathname === "/" || pathname === "/catalog") {
      const section: BrowseSection = pathname === "/" ? "home" : "catalog";
      browseSection.current = section;
      clickedBrowseSection.current = null;
      return;
    }

    const titleMatch = pathname.match(/^\/anime\/([^/]+)\/([^/]+)$/);
    if (titleMatch) {
      // Use the section the user clicked in as the title's origin. Keep it across title-to-title
      // and player navigation, while unrelated routes below clear it.
      const clickedSection = clickedBrowseSection.current;
      clickedBrowseSection.current = null;
      if (clickedSection) browseSection.current = clickedSection;
      if (browseSection.current) {
        rememberSectionTitle(browseSection.current, decodeURIComponent(titleMatch[1]), decodeURIComponent(titleMatch[2]));
      }
      return;
    }

    clickedBrowseSection.current = null;
    if (!pathname.startsWith("/watch/")) browseSection.current = null;
  }, [pathname]);
  // Each of PERSISTED_PAGES only ever joins this set, never leaves it - the first visit mounts it
  // (paying its own load/query cost, same as before) and every visit after that just toggles
  // `hidden` on an already-live component instead of tearing it down and rebuilding its state from
  // scratch. Pages never visited this session (e.g. Settings, for someone who never opens it) never
  // mount at all, so this doesn't front-load every route's queries on app boot.
  const [visited, setVisited] = useState<string[]>(() => (pathname in PERSISTED_PAGES ? [pathname] : []));
  useEffect(() => {
    if (pathname in PERSISTED_PAGES) setVisited((prev) => (prev.includes(pathname) ? prev : [...prev, pathname]));
  }, [pathname]);
  // The page being opened is mounted in the very render that switches to it, not one effect later: the
  // effect above only records it for the renders after. Waiting for it left one frame with the
  // previous page already hidden and this one not there yet - just the app-wide theme gradient, which
  // read as a flash the first time each tab was opened (most visible with a custom theme).
  usePageTransition(pathname);
  // Phone: load every tab's page code shortly after start, so a first visit renders at once (see
  // lazyPage) instead of an empty screen in the middle of the page transition.
  useEffect(() => {
    if (!isMobile) return;
    const timer = window.setTimeout(() => {
      void Promise.all(Object.values(PERSISTED_PAGES).map((page) => page.preload())).catch(() => undefined);
      // The title page's and the player's own code too (split per route): the first title opened
      // otherwise slid in as an empty box while its chunk loaded - the transition looked skipped.
      for (const id of ["/anime/$sourceId/$animeId", "/watch/$sourceId/$animeId/$groupId/$episodeId"] as const) {
        const route = router.routesById[id];
        if (route) void Promise.resolve(router.loadRouteChunk(route)).catch(() => undefined);
      }
    }, 1500);
    return () => window.clearTimeout(timer);
  }, [router]);
  const mountedPages = pathname in PERSISTED_PAGES && !visited.includes(pathname) ? [...visited, pathname] : visited;
  // Discord presence outside the player: a single steady "using hibiki" line, not per-page text
  // (catalog/profile/settings/...) - that was tried and just read as noise. The watch page sets
  // its own detailed presence on mount and this effect only fires again once it's left (isWatching
  // flips back to false), so the two never fight over which one is showing.
  useEffect(() => {
    if (discordRpcEnabled && !isWatching) hibiki.discord.setIdlePresence();
  }, [discordRpcEnabled, isWatching]);
  // Phone: watching is landscape (or any way up, as chosen in Settings > Player), edge to edge without
  // the system bars, and keeps the screen on; leaving the player hands all three back.
  const playerOrientation = usePlayerPrefsStore((s) => s.playerOrientation);
  useEffect(() => {
    const device = hibiki.device;
    if (!device || !isWatching) return;
    void device.setOrientation(playerOrientation === "any" ? "sensor" : "landscape");
    void device.setSystemBars({ hidden: true });
    void device.keepAwake(true);
    return () => {
      void device.setOrientation("auto");
      void device.setSystemBars({ hidden: false });
      void device.keepAwake(false);
    };
  }, [isWatching, playerOrientation]);
  // Kept mounted for the whole session (not just while the profile page is open) - an episode
  // finishing (the most common trigger, via the "finisher" family) happens on the watch page, not
  // profile, so the tier-crossing check this owns has to keep running regardless of where the user
  // actually is when it happens. See its own comment for why profile.tsx no longer computes this
  // itself.
  useAchievementUnlocks();
  // Remembers every currently-installed source's name/icon (see knownSourcesStore) - shared with
  // whichever page happens to fetch ["sources"] first (React Query dedupes the actual request), so
  // a continue-watching/library card whose source later gets uninstalled can still show its real
  // name instead of a bare id in AnimeCard's "source removed" badge.
  const sourcesQuery = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const rememberSources = useKnownSourcesStore((s) => s.remember);
  useEffect(() => {
    if (sourcesQuery.data) rememberSources(sourcesQuery.data.map((s) => ({ id: s.id, name: s.name, iconUrl: s.iconUrl })));
  }, [sourcesQuery.data, rememberSources]);

  // The renderer's browser storage can be recreated when a Windows installer replaces the app
  // bundle, while extensions live in Electron's stable per-user data directory. An existing
  // source is therefore conclusive evidence that this is not a first launch. Mirror that back
  // into the UI store so later launches do not need this fallback either.
  // Judged once, by what was installed at launch: a source installed during onboarding itself ended
  // it on the spot, before a second could be added or the onboarding's own button was ever pressed.
  const launchHadSources = useRef<boolean | null>(null);
  if (launchHadSources.current === null && sourcesQuery.isSuccess) launchHadSources.current = sourcesQuery.data.length > 0;
  const onboardingInferredFromSources = launchHadSources.current === true;
  useEffect(() => {
    if (onboardingInferredFromSources && !onboardingCompleted) setOnboardingCompleted(true);
  }, [onboardingInferredFromSources, onboardingCompleted, setOnboardingCompleted]);
  // Do not briefly show the welcome screen on an update while the persisted source list is being
  // read. A genuinely fresh install resolves to an empty list and then enters onboarding.
  const onboardingResolved = onboardingCompleted || sourcesQuery.isSuccess;
  const shouldShowOnboarding = !onboardingCompleted && !onboardingInferredFromSources;

  // Gated ahead of everything else below (including isWatching, though there's nothing to watch
  // yet at this point anyway) - nothing in the real app is usable without at least one source
  // installed, so this fully replaces the normal chrome instead of layering on top of it.
  if (!onboardingResolved) return <div className="h-screen w-screen bg-app-bg" style={{ backgroundImage: backgroundGradient }} />;
  if (shouldShowOnboarding) return <div className="flex h-screen w-screen flex-col overflow-hidden bg-app-bg text-text mobile:pb-[var(--safe-bottom)] mobile:pt-[var(--safe-top)]" style={{ backgroundImage: backgroundGradient }}>
    {!isMobile && <TitleBar />}
    <div className="min-h-0 flex-1"><Onboarding onComplete={() => setOnboardingCompleted(true)} /></div>
  </div>;

  // The gradient also as a variable: the phone's status-bar scrim repaints this exact backdrop (see globals.css).
  return <div className="flex h-screen w-screen flex-col overflow-hidden bg-app-bg text-text" style={{ backgroundImage: backgroundGradient, ...(backgroundGradient ? { "--app-backdrop": backgroundGradient } as CSSProperties : {}) }}>
    {!isMobile && <TitleBar />}
    <AchievementToast />
    <SearchSpotlight />
    <div className="flex min-h-0 flex-1">
      {!isMobile && <Sidebar />}
      <main className="app-main-area relative flex min-h-0 min-w-0 flex-1 flex-col">
        {/* The router destroys a route's whole component tree on navigating away, which was silently
            resetting anything living in its component state one piece at a time (a carousel's slide,
            a scroll position, a random pool re-rolling on every visit) - patching each of those
            individually was a losing game, so instead the entire category of bug is sidestepped by
            just never letting a visited page unmount in the first place. Routes with a param in the
            URL (anime details, the player) don't get this treatment - there can be unboundedly many
            of them over a session, so they keep the normal mount/unmount-per-visit behavior via
            Outlet below and rely on React Query's cache instead. */}
        {mountedPages.map((path) => {
          const Page = PERSISTED_PAGES[path];
          const isActive = pathname === path;
          // This wrapper only needs to be a flex ITEM of `main` (`flex-1 min-h-0`, so it claims its
          // share of main's height instead of growing to fit content) - it does NOT need to be a
          // flex CONTAINER itself for its one single child (the page). Making it one anyway (via
          // `flex flex-col`) was the actual bug behind the "gray background gets cut off, content
          // keeps going" reports: the page's own root div (`min-h-full bg-[#17161b]`) became a flex
          // item of THIS wrapper too, and setting an explicit min-height (rather than leaving the
          // default `auto`) switches off a flex item's usual protection against shrinking below its
          // own content size - so with content taller than the viewport, the flexbox algorithm
          // happily shrank the page's own box down to exactly `min-h-full`'s value (the viewport
          // height) and let the overflow render past it with no background of its own underneath.
          // A plain block wrapper has no such mechanic: `min-h-full` on a normal block child is just
          // a floor, and it grows with taller content the ordinary way, same as it always looks like
          // it should.
          // It's also its own independent scroll container (`overflow-y-auto`), not a shared one -
          // every persisted page used to scroll inside `main` itself, which is one single DOM
          // element with one single scrollTop: scrolling one page and switching to another showed
          // that same scrollTop applied to different content, since as far as the browser's
          // concerned it's the same scrollable region the whole time. Because a persisted page's own
          // div never unmounts, giving it its own scroll box instead means its scroll position is
          // remembered for free just by staying in the DOM, with no explicit save/restore needed and
          // no way for it to leak into any other page's.
          // Keep the React tree mounted so component state and the element's scrollTop survive page
          // changes, but remove inactive pages from layout and painting. Keeping every visited page
          // as an invisible, composited viewport permanently spent GPU memory on all of their cards
          // and artwork, which then made the active catalog less smooth to scroll.
          return <div
            key={path}
            data-browse-section={path === "/" ? "home" : path === "/catalog" ? "catalog" : undefined}
            data-page-scroll={path}
            aria-hidden={!isActive}
            className={cn(
              "no-scrollbar min-h-0 overflow-y-auto",
              // Phone: clear of the status bar above and the floating tab bar below.
              // Never sideways: one over-wide line (a long link in a comment) must not let the page pan.
              "mobile:overflow-x-hidden mobile:pb-[var(--tabbar-space)] mobile:pt-[var(--safe-top)]",
              isActive ? "flex-1" : "hidden",
            )}
          >
            <PageActiveContext.Provider value={isActive}><Suspense fallback={<PageShellFallback path={path} />}><Page /></Suspense></PageActiveContext.Provider>
          </div>;
        })}
        {/* Param routes (anime details, the player) aren't persisted above - a fresh mount every
            visit, so there's no long-lived DOM box to remember a scroll position in on its own the
            way the persisted pages now do. `data-scroll-restoration-id` hands that one case back to
            the router's own scroll restoration (keyed per full path), same as it always was for
            every route before the persisted pages started sharing `main`'s scroll with it. Only
            rendered while actually on such a route - otherwise this would sit alongside a persisted
            page's own div as a second, empty `flex-1` box silently claiming half of `main`'s height
            for content (Outlet renders nothing for a persisted route) that was never going to use it. */}
        {!(pathname in PERSISTED_PAGES) && (
          // The player fills its box itself (own black background, own fullscreen) and must not scroll
          // inside it; every other param route (anime details) scrolls here as before.
          <div className={cn("min-h-0 flex-1", isWatching ? "bg-black" : "no-scrollbar overflow-y-auto mobile:overflow-x-hidden mobile:pb-[var(--tabbar-space)] mobile:pt-[var(--safe-top)]")} data-scroll-restoration-id="app-main" data-player-fullscreen-root={isWatching ? "" : undefined}>
            <Outlet />
          </div>
        )}
      </main>
    </div>
    {isMobile && <MobileUpdateSheet suppressed={isWatching} />}
    {isMobile && <BackgroundWorkNotice />}
    <SyncListener />
    <MissingSourcesPrompt />
    {isMobile && !isWatching && <><MobileStatusScrim />{!pathname.startsWith("/anime/") && <MobileTabBar />}</>}
  </div>;
}
