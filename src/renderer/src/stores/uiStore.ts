import { create } from "zustand";
import { persist } from "zustand/middleware";
import { normalizeZoom } from "@/lib/zoom";
import { clearSectionTitle } from "@/lib/sectionTitleMemory";

interface UiState {
  theme: "light" | "dark";
  activeSourceId: string | null;
  sidebarWidth: number;
  // Sidebar entries the user hid from its context menu, by route. Settings can never be in here: it is
  // the way back to everything else.
  hiddenNav: string[];
  // The episode list on a title page: newest first instead of oldest first.
  episodesNewestFirst: boolean;
  // How the episodes are drawn: big tiles or a list of wide rows.
  episodesView: "tiles" | "list";
  // Defaults to on, so a fresh install shows what you're watching in Discord without having to
  // find the switch first - Settings is where you turn it *off*. Only affects new installs: an
  // existing one keeps whatever its persisted "hibiki-ui" value already says.
  discordRpcEnabled: boolean;
  // Keep a title from an NSFW-marked source out of Rich Presence by default. This is source-level
  // deliberately: a source may not expose reliable adult metadata for every individual title.
  discordIgnoreNsfwSources: boolean;
  // False only until the first-launch onboarding flow (see Onboarding.tsx) is finished once -
  // persisted so it never shows again after that, same as the rest of this store.
  onboardingCompleted: boolean;
  // The streak count the profile page's badge has already played its pop/burst animation for -
  // independent of the watch player's own toast (that one only ever needs a same-session ref,
  // since it only reacts to a change happening while it's already open). This one is separate and
  // persisted because the profile page is the "home" of this stat: arriving there sometime after
  // an increment that happened elsewhere (in the player) should still get to show the animation
  // once, not silently show the new number as if nothing happened. -1 is a sentinel for "never
  // recorded yet" (distinct from a real streak, which is always >= 0) - without it, a pre-existing
  // streak from before this field existed would read as one giant uncelebrated jump the first time
  // the updated app opens the profile page, and celebrate progress that isn't actually new.
  profileCelebratedStreak: number;
  // A user-picked accent color (hex, e.g. "#ec4899"), applied on top of --color-accent - see
  // applyAccentColor in lib/theme.ts. null means "use the app's default pink" rather than storing
  // that pink literally, so a future default change isn't silently masked by everyone's persisted
  // "custom" value happening to match today's default.
  accentColor: string | null;
  // A picked preset id from lib/theme.ts's BACKGROUND_THEME_PRESETS, `"custom"` for the
  // user-picked pair below, or null for no decorative gradient - see applyBackgroundTheme. Picking
  // one also makes the Sidebar/TitleBar (and each page's own background) translucent + blurred, so
  // the gradient actually shows through the chrome instead of being hidden behind it.
  backgroundTheme: string | null;
  // The two stops of the "custom" background gradient (see backgroundTheme above) - null until the
  // custom swatch is ever actually opened, same reasoning as accentColor's own null default: no
  // literal color pair is baked in as secretly meaning "hasn't been set yet".
  customBackgroundGradient: { from: string; to: string } | null;
  // Whether the catalog page fetches its next page itself once the "load more" sentinel scrolls
  // into view (see catalog.tsx), instead of waiting for an explicit button click. Defaults to on -
  // Settings just gives a way back to the manual button for anyone who'd rather not have pages
  // load automatically as they scroll.
  catalogAutoLoad: boolean;
  // Window zoom as a plain factor (1 = 100%), stepped with Ctrl +/- and reset with Ctrl+0 - see
  // lib/zoom.ts. Stored as the factor rather than Chromium's logarithmic zoom *level* because it
  // is the number a person would recognise, and because the ladder in lib/zoom.ts is defined in
  // those terms.
  zoomFactor: number;
  // Off by default: downloading a hundred-plus megabytes and restarting the app is not something
  // to start doing to someone who never asked for it. Settings is where you turn it *on*.
  autoUpdate: boolean;
  // Which of a source's own catalog sort orders (see SearchFilterCatalog.sortOptions) fills the home
  // page's hero and "popular" row, keyed by source id. Missing = "Auto" (see pickRelevanceSort) -
  // a source's sort ids aren't a fixed vocabulary the host can rely on, so this is the way to pick
  // something more specific than that per source.
  homeSortBySource: Record<string, string>;
  setTheme: (theme: "light" | "dark") => void;
  setActiveSourceId: (id: string | null) => void;
  setSidebarWidth: (width: number) => void;
  setNavHidden: (to: string, hidden: boolean) => void;
  setEpisodesNewestFirst: (newestFirst: boolean) => void;
  setEpisodesView: (view: "tiles" | "list") => void;
  setDiscordRpcEnabled: (enabled: boolean) => void;
  setDiscordIgnoreNsfwSources: (enabled: boolean) => void;
  setOnboardingCompleted: (completed: boolean) => void;
  setProfileCelebratedStreak: (streak: number) => void;
  setAccentColor: (color: string | null) => void;
  setBackgroundTheme: (id: string | null) => void;
  setCustomBackgroundGradient: (gradient: { from: string; to: string }) => void;
  setCatalogAutoLoad: (enabled: boolean) => void;
  setZoomFactor: (factor: number) => void;
  setAutoUpdate: (enabled: boolean) => void;
  setHomeSort: (sourceId: string, sortId: string | null) => void;
}

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      theme: "dark",
      activeSourceId: null,
      sidebarWidth: 236,
      hiddenNav: [],
      episodesNewestFirst: false,
      // A fresh install only - zustand's persist middleware only ever falls back to this default
      // for a key that was never actually written to localStorage, so anyone with an existing
      // profile keeps whichever view they already have (even one that only ever got there by way
      // of the old default) rather than being silently switched over.
      episodesView: "list",
      discordRpcEnabled: true,
      discordIgnoreNsfwSources: true,
      onboardingCompleted: false,
      profileCelebratedStreak: -1,
      accentColor: null,
      backgroundTheme: null,
      customBackgroundGradient: null,
      catalogAutoLoad: true,
      zoomFactor: 1,
      autoUpdate: false,
      homeSortBySource: {},
      setTheme: (theme) => set({ theme }),
      setActiveSourceId: (activeSourceId) =>
        set((state) => {
          if (state.activeSourceId === activeSourceId) return state;
          // A remembered "last title opened from Home/Catalog" (see sectionTitleMemory.ts) belongs
          // to whichever source's Home/Catalog it was actually opened from - switching sources
          // makes it stale, and the sidebar's own toggle-back behavior would otherwise send you
          // straight into a title that isn't even from the source you're now browsing.
          clearSectionTitle("home");
          clearSectionTitle("catalog");
          return { activeSourceId };
        }),
      setSidebarWidth: (sidebarWidth) => set({ sidebarWidth }),
      setEpisodesNewestFirst: (episodesNewestFirst) => set({ episodesNewestFirst }),
      setEpisodesView: (episodesView) => set({ episodesView }),
      setNavHidden: (to, hidden) =>
        set((state) => ({ hiddenNav: hidden ? [...new Set([...state.hiddenNav, to])] : state.hiddenNav.filter((item) => item !== to) })),
      setDiscordRpcEnabled: (discordRpcEnabled) => set({ discordRpcEnabled }),
      setDiscordIgnoreNsfwSources: (discordIgnoreNsfwSources) => set({ discordIgnoreNsfwSources }),
      setOnboardingCompleted: (onboardingCompleted) => set({ onboardingCompleted }),
      setProfileCelebratedStreak: (profileCelebratedStreak) => set({ profileCelebratedStreak }),
      setAccentColor: (accentColor) => set({ accentColor }),
      setBackgroundTheme: (backgroundTheme) => set({ backgroundTheme }),
      setCustomBackgroundGradient: (customBackgroundGradient) => set({ customBackgroundGradient }),
      setCatalogAutoLoad: (catalogAutoLoad) => set({ catalogAutoLoad }),
      setZoomFactor: (zoomFactor) => set({ zoomFactor: normalizeZoom(zoomFactor) }),
      setAutoUpdate: (autoUpdate) => set({ autoUpdate }),
      setHomeSort: (sourceId, sortId) =>
        set((state) => {
          const homeSortBySource = { ...state.homeSortBySource };
          if (sortId) homeSortBySource[sourceId] = sortId;
          else delete homeSortBySource[sourceId];
          return { homeSortBySource };
        }),
    }),
    {
      name: "hibiki-ui",
      version: 2,
      // Version 2 removed the aggregator browsing preference, and the aggregator metadata settings went
      // with the feature itself. Persisted profiles may retain those keys safely; nothing reads them.
    },
  ),
);
