import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { isMobile, useBackHandler } from "@/lib/mobile";
import { MobilePageHeader } from "@/components/MobilePageHeader";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import {
  Search,
  RefreshCw,
  Filter,
  Plus,
  Radio,
  TriangleAlert,
  Check,
  Globe,
  Copy,
  Trash2,
  Sparkles,
  Play,
  Layers,
  Shuffle,
  Activity,
  UserRound,
  MessageSquare,
  Star,
  Settings2,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { useUiStore } from "@/stores/uiStore";
import { Switch } from "@/components/Switch";
import { cn } from "@/lib/cn";
import { SourceSettingsDialog } from "@/components/SourceSettingsDialog";
import { isExtensionUpdateAvailable } from "@/lib/version";
import { TabButton } from "@/components/TabButton";
import { Modal } from "@/components/Modal";
import { BottomSheet, SheetOption } from "@/components/BottomSheet";
import type { MarketplaceExtension, RepositoryFetchResult, SourceCapability } from "@shared/types";
import { isRepositoryAlreadyAdded } from "@shared/repositoryErrors";

type Tab = "extensions" | "repositories";

const CAPABILITY_ICONS: Record<SourceCapability, typeof Sparkles> = {
  LATEST_RELEASES: Sparkles,
  PLAYBACK: Play,
  RELATED_TITLES: Layers,
  SIMILAR_TITLES: Shuffle,
  ACCOUNT: UserRound,
  COMMENTS: MessageSquare,
  REVIEWS: Star,
  LIBRARY_SYNC: RefreshCw,
  ACTIVITY_SYNC: Activity,
};

/*
 * Which capabilities are worth a row's scarce horizontal space, most telling first.
 *
 * Playback is the one that decides whether a source is usable at all; an account and what it
 * unlocks is the next thing anyone looks for. The relationship graph capabilities are last: they
 * are nice, and nobody picks a source for them. Everything past the first few becomes a "+n", so
 * the language and version - which were being truncated to "RU…" by a row of eight icons - keep
 * their room.
 */
const CAPABILITY_PRIORITY: SourceCapability[] = [
  "PLAYBACK",
  "ACCOUNT",
  "LATEST_RELEASES",
  "LIBRARY_SYNC",
  "ACTIVITY_SYNC",
  "COMMENTS",
  "REVIEWS",
  "RELATED_TITLES",
  "SIMILAR_TITLES",
];

const MAX_VISIBLE_CAPABILITIES = 3;
const UPDATE_ALL_CONCURRENCY = 3;

const CAPABILITY_LABEL_KEYS: Record<SourceCapability, string> = {
  LATEST_RELEASES: "sources.capability.latest",
  PLAYBACK: "sources.capability.playback",
  RELATED_TITLES: "sources.capability.related",
  SIMILAR_TITLES: "sources.capability.similar",
  ACCOUNT: "sources.capability.account",
  COMMENTS: "sources.capability.comments",
  REVIEWS: "sources.capability.reviews",
  LIBRARY_SYNC: "sources.capability.librarySync",
  ACTIVITY_SYNC: "sources.capability.activitySync",
};

/**
 * A language tag as people read it: its own name and the English one. The platform knows the names of
 * every real language, so there is no list here to keep in step with the repositories; a tag it does
 * not know is shown as the tag.
 */
function languageLabel(lang: string): { native: string; english: string } {
  const tag = lang.replace("_", "-");
  try {
    const native = new Intl.DisplayNames([tag], { type: "language" }).of(tag);
    const english = new Intl.DisplayNames(["en"], { type: "language" }).of(tag);
    if (native && english && native.toLowerCase() !== tag.toLowerCase()) {
      return { native: native.charAt(0).toLocaleUpperCase(tag) + native.slice(1), english };
    }
  } catch {
    // Not a language tag at all (a repository's own label): fall through to the tag itself.
  }
  return { native: lang.toUpperCase(), english: lang.toUpperCase() };
}

/** The short code shown next to a version: the tag in capitals, with Ukrainian as UA (the country people know it by). */
function languageBadge(lang: string): string {
  return lang.toLowerCase() === "uk" ? "UA" : lang.toUpperCase();
}

function repositoryDisplayName(url: string): string {
  const match = url.match(/^https?:\/\/raw\.githubusercontent\.com\/([^/]+)\/([^/]+)\//);
  return match ? `${match[1]}/${match[2]}` : url;
}

/** `embedded`: shown inside another screen (Settings' category, onboarding) - on the phone that
 * screen already has the header and the side padding. */
export function SourcesPage({ embedded = false }: { embedded?: boolean } = {}) {
  // Which source's settings screen is open, if any. One at a time: the screen is modal, and a
  // source is identified by id so it survives the list refreshing underneath it.
  const [settingsSourceId, setSettingsSourceId] = useState<string | null>(null);
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("extensions");
  const [query, setQuery] = useState("");
  const [selectedLanguages, setSelectedLanguages] = useState<Set<string>>(new Set());
  const [languageFilterOpen, setLanguageFilterOpen] = useState(false);
  const [addRepositoryOpen, setAddRepositoryOpen] = useState(false);
  const [repositoryPendingRemoval, setRepositoryPendingRemoval] = useState<string | null>(null);
  // The repository whose sources are listed on the Repositories tab; null shows the repositories.
  const [openRepository, setOpenRepository] = useState<string | null>(null);
  const [installingIds, setInstallingIds] = useState<Set<string>>(new Set());
  const installingIdsRef = useRef(new Set<string>());
  const [installErrors, setInstallErrors] = useState<Record<string, string>>({});
  const [updateAllInProgress, setUpdateAllInProgress] = useState(false);
  const updateAllInProgressRef = useRef(false);
  const [refreshSignal, setRefreshSignal] = useState(0);

  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const setActiveSourceId = useUiStore((s) => s.setActiveSourceId);

  const installedSources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const repositories = useQuery({ queryKey: ["repositories"], queryFn: () => hibiki.sources.repositories.list() });
  // Which installed sources have anything to configure. The cards below are built from marketplace
  // entries, which carry no settings - only the installed manifest does.
  const sourcesWithSettings = useMemo(
    () =>
      new Set(
        (installedSources.data ?? [])
          .filter((source) => (source.settings?.length ?? 0) > 0)
          .map((source) => source.id),
      ),
    [installedSources.data],
  );
  const settingsSource = useMemo(
    () => (installedSources.data ?? []).find((source) => source.id === settingsSourceId),
    [installedSources.data, settingsSourceId],
  );
  const marketplace = useQuery({
    queryKey: ["marketplace", repositories.data, refreshSignal],
    enabled: !!repositories.data,
    queryFn: () => hibiki.sources.marketplace(repositories.data!),
  });

  const repositoryUrls = repositories.data ?? [];
  const repoResults = marketplace.data ?? [];
  const resultByUrl = useMemo(() => new Map(repoResults.map((r) => [r.url, r])), [repoResults]);

  // First repository (in stored order) to list an id owns it - mirrors the Android client, and
  // matters for ScriptExtensionRepository's origin guard (see runtime.ts install()).
  const { mergedExtensions, originByExtensionId } = useMemo(() => {
    const origins = new Map<string, string>();
    const seen = new Set<string>();
    const merged: MarketplaceExtension[] = [];
    for (const url of repositoryUrls) {
      const result = resultByUrl.get(url);
      if (!result?.ok) continue;
      for (const extension of result.extensions) {
        if (!origins.has(extension.id)) origins.set(extension.id, url);
        if (!seen.has(extension.id)) {
          seen.add(extension.id);
          merged.push(extension);
        }
      }
    }
    return { mergedExtensions: merged, originByExtensionId: origins };
  }, [repositoryUrls, resultByUrl]);

  // The cards come from the repositories' indexes, so a source installed from none of them (the
  // diagnostics source, or one its repository has since dropped) would be installed and invisible -
  // nowhere to see or remove it. Those get a card from their own manifest, once the indexes are in.
  const sourceExtensions = useMemo(() => {
    const listed = mergedExtensions.filter((e) => e.type === "source");
    const known = new Set(mergedExtensions.map((e) => e.id));
    const unlisted: MarketplaceExtension[] = (installedSources.data ?? [])
      .filter((source) => !known.has(source.id))
      .map((source) => ({
        id: source.id,
        name: source.name,
        version: source.version,
        website: source.website ?? null,
        iconUrl: source.iconUrl,
        lang: source.lang ?? "",
        capabilities: source.capabilities,
        resolverDependencies: [],
        isNsfw: source.isNsfw === true,
        type: "source",
        manifestUrl: "",
      }));
    return [...listed, ...unlisted];
  }, [mergedExtensions, installedSources.data]);
  const installed = useQuery({
    queryKey: ["installedVersions"],
    queryFn: () => hibiki.sources.installedVersions(),
  });
  const installedVersions = useMemo(
    () => new Map(Object.entries(installed.data?.sources ?? {})),
    [installed.data],
  );

  // The Extensions tab lists only what is installed; everything a repository offers is under that
  // repository, so many repositories don't pile into one list.
  const openRepositoryResult = openRepository ? resultByUrl.get(openRepository) : undefined;
  const pool = useMemo(
    () => openRepository
      ? (openRepositoryResult?.ok ? openRepositoryResult.extensions.filter((e) => e.type === "source") : [])
      : sourceExtensions.filter((e) => installedVersions.has(e.id)),
    [openRepository, openRepositoryResult, sourceExtensions, installedVersions],
  );
  const languages = useMemo(() => [...new Set(pool.map((e) => e.lang).filter(Boolean))].sort(), [pool]);
  // One snapshot of everything installed, sources and resolvers together. They used to be two
  // queries, and that is what let them drift: the resolver half was read once at app start and
  // nothing refreshed it, so a source that had genuinely just updated stayed under "updates
  // available" - the update applied, the screen kept saying it hadn't. Both halves now come from
  // the same read of the same runtime state, and main tells us when to look again (below).
  // Whatever changed what is installed - this screen's own install button, an uninstall, anything
  // added later - main says so and both queries are re-read. Refreshing by hand at each call site
  // is what went wrong before: the install path updated the sources list and left the resolver
  // versions behind, and nothing about that was visible until a row refused to leave the updates
  // section.
  useEffect(() => hibiki.sources.onChanged(() => {
    void queryClient.invalidateQueries({ queryKey: ["sources"] });
    void queryClient.invalidateQueries({ queryKey: ["installedVersions"] });
  }), [queryClient]);

  const trimmedQuery = query.trim().toLowerCase();
  const visibleExtensions = pool.filter((extension) => {
    const matchesQuery =
      !trimmedQuery ||
      extension.name.toLowerCase().includes(trimmedQuery) ||
      extension.id.toLowerCase().includes(trimmedQuery);
    const matchesLanguage = selectedLanguages.size === 0 || selectedLanguages.has(extension.lang);
    return matchesQuery && matchesLanguage;
  });
  const installedExtensions = visibleExtensions.filter((e) => installedVersions.has(e.id));
  const updateAvailableExtensions = installedExtensions.filter((e) =>
    isExtensionUpdateAvailable(e, installedVersions, installed.data?.resolvers ?? {}, mergedExtensions),
  );
  const upToDateExtensions = installedExtensions.filter((e) => !updateAvailableExtensions.includes(e));
  const availableExtensions = visibleExtensions.filter((e) => !installedVersions.has(e.id));

  // Installed sources need no index to be listed; an open repository waits for its own.
  const listState: "loading" | "error" | "loaded" = !openRepository
    ? (installedSources.isPending || installed.isPending ? "loading" : "loaded")
    : !openRepositoryResult ? "loading" : openRepositoryResult.ok ? "loaded" : "error";

  const showRepository = (url: string | null) => {
    setOpenRepository(url);
    setQuery("");
    setSelectedLanguages(new Set());
  };
  const switchTab = (next: Tab) => {
    setTab(next);
    showRepository(null);
  };
  const searching = tab === "extensions" || openRepository !== null;
  // The phone screen has a header of its own; add and refresh sit at its right end, not in the toolbar.
  const headerActions = isMobile && !embedded;
  const refreshButton = (
    <ToolbarButton onClick={() => setRefreshSignal((v) => v + 1)} label={t("sources.refresh")}>
      <RefreshCw className={headerActions ? "h-5 w-5" : "h-4 w-4"} strokeWidth={2} />
    </ToolbarButton>
  );
  // The phone's Back leaves an open repository the way the header arrow does.
  useBackHandler(openRepository !== null, () => showRepository(null));

  const installExtension = useCallback(async (extension: MarketplaceExtension) => {
    // State updates do not become visible until React renders again, so `installingIds` alone
    // cannot stop two fast clicks (or two "Update all" presses) from starting the same download.
    // The ref is updated synchronously and is the actual per-source mutex.
    if (installingIdsRef.current.has(extension.id)) return;
    installingIdsRef.current.add(extension.id);
    setInstallingIds((prev) => new Set(prev).add(extension.id));
    setInstallErrors((prev) => { const next = { ...prev }; delete next[extension.id]; return next; });
    const hadNoSources = (installedSources.data?.length ?? 0) === 0;
    try {
      // From the repository it was picked in, else the first one to list it.
      const origin = openRepository && openRepositoryResult?.ok && openRepositoryResult.extensions.some((e) => e.id === extension.id) ? openRepository : originByExtensionId.get(extension.id) ?? "";
      const updated = await hibiki.sources.install(extension, origin);
      queryClient.setQueryData(["sources"], updated);
      if (hadNoSources) setActiveSourceId(extension.id);
    } catch (error) {
      setInstallErrors((prev) => ({ ...prev, [extension.id]: error instanceof Error ? error.message : String(error) }));
    } finally {
      installingIdsRef.current.delete(extension.id);
      setInstallingIds((prev) => { const next = new Set(prev); next.delete(extension.id); return next; });
    }
  }, [installedSources.data, originByExtensionId, openRepository, openRepositoryResult, queryClient, setActiveSourceId]);

  const updateAll = useCallback(async () => {
    if (updateAllInProgressRef.current) return;
    updateAllInProgressRef.current = true;
    setUpdateAllInProgress(true);
    // Keep a fixed snapshot: each successful install refreshes the marketplace state, but that
    // must not append new work into an update operation already in progress.
    const pending = [...updateAvailableExtensions];
    let nextIndex = 0;
    const worker = async () => {
      for (;;) {
        const extension = pending[nextIndex++];
        if (!extension) return;
        await installExtension(extension);
      }
    };
    try {
      await Promise.all(Array.from({ length: Math.min(UPDATE_ALL_CONCURRENCY, pending.length) }, worker));
    } finally {
      updateAllInProgressRef.current = false;
      setUpdateAllInProgress(false);
    }
  }, [installExtension, updateAvailableExtensions]);

  async function uninstallExtension(id: string) {
    const updated = await hibiki.sources.uninstall(id);
    queryClient.setQueryData(["sources"], updated);
    if (activeSourceId === id) setActiveSourceId(null);
  }

  async function addRepository(url: string): Promise<string | null> {
    try {
      const updated = await hibiki.sources.repositories.add(url);
      queryClient.setQueryData(["repositories"], updated);
      return null;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      return isRepositoryAlreadyAdded(message) ? t("sources.repositoriesAlreadyAdded") : message;
    }
  }

  async function removeRepository(url: string) {
    const updated = await hibiki.sources.repositories.remove(url);
    queryClient.setQueryData(["repositories"], updated);
    if (openRepository === url) showRepository(null);
  }

  return (
    <div className={cn("flex min-h-full flex-col bg-app-bg", embedded && "mobile:min-h-0 mobile:bg-transparent")}>
      <div className={cn("px-8 pt-8 mobile:px-4 mobile:pt-2", embedded && "mobile:pt-0")}>
        {/* An open repository is one level deeper: the header names it and its arrow goes back to the list. */}
        {headerActions && (
          <MobilePageHeader
            title={openRepository ? repositoryDisplayName(openRepository) : t("nav.sources")}
            parent="/settings"
            onBack={openRepository ? () => showRepository(null) : undefined}
            actions={<>
              {!searching && (
                <ToolbarButton onClick={() => setAddRepositoryOpen(true)} label={t("sources.repositoriesAdd")}>
                  <Plus className="h-5 w-5" strokeWidth={2} />
                </ToolbarButton>
              )}
              {refreshButton}
            </>}
          />
        )}
        {/* Inside a repository the tabs are left behind: it is a level below them. On the phone the
            header names it and its arrow leads back; elsewhere this row does, where the tabs were. */}
        {openRepository ? (
          !headerActions && (
            <button onClick={() => showRepository(null)} className="-ml-1 flex h-[46px] max-w-full items-center gap-1 rounded-lg pr-2 text-base font-bold text-text transition-colors hover:text-accent-text">
              <ChevronLeft className="h-5 w-5 shrink-0" strokeWidth={2} />
              <span className="truncate">{repositoryDisplayName(openRepository)}</span>
            </button>
          )
        ) : (
          <div className="flex gap-1 border-b border-border mobile:gap-0">
            <TabButton layoutId="sourcesTabIndicator" active={tab === "extensions"} onClick={() => switchTab("extensions")}>{t("sources.tabs.extensions")}</TabButton>
            <TabButton layoutId="sourcesTabIndicator" active={tab === "repositories"} onClick={() => switchTab("repositories")}>{t("sources.tabs.repositories")}</TabButton>
          </div>
        )}
        {/* On the phone the repositories list has nothing left here: its buttons are in the header. */}
        {(searching || !headerActions) && (
        <div className={cn("flex items-center gap-2", (!openRepository || !headerActions) && "mt-5")}>
          <AnimatePresence mode="popLayout" initial={false}>
            {searching ? (
              <motion.div
                key="extensions-toolbar"
                initial={{ opacity: 0, x: -6 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -6 }}
                transition={{ duration: 0.15 }}
                className="flex flex-1 items-center gap-2"
              >
                <div className="relative flex-1 max-w-sm mobile:max-w-none">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" strokeWidth={2} />
                  <input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder={t("sources.searchPlaceholder")}
                    className="select-text h-9 w-full rounded-lg bg-text/[.06] pl-9 pr-3 text-sm text-text placeholder:text-muted outline-none ring-1 ring-transparent focus:ring-accent/50"
                  />
                </div>
                <div className="relative">
                  <ToolbarButton onClick={() => setLanguageFilterOpen((v) => !v)} label={t("sources.filterLanguages")}>
                    <Filter className="h-4 w-4" strokeWidth={2} />
                    {selectedLanguages.size > 0 && (
                      <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[10px] font-bold text-accent-fg">
                        {selectedLanguages.size}
                      </span>
                    )}
                  </ToolbarButton>
                  {/* Phone: a sheet over the screen - a list of dozens of languages doesn't fit a dropdown. */}
                  {isMobile && (
                    <BottomSheet
                      open={languageFilterOpen}
                      prewarm
                      onClose={() => setLanguageFilterOpen(false)}
                      title={t("sources.filterLanguages")}
                      footer={selectedLanguages.size > 0 ? (
                        <button onClick={() => setSelectedLanguages(new Set())} className="w-full rounded-xl bg-text/[.07] py-3 text-sm font-semibold text-text active:bg-text/[.12]">
                          {t("search.filters.reset")}
                        </button>
                      ) : undefined}
                    >
                      <LanguageRows
                        languages={languages}
                        selected={selectedLanguages}
                        onToggle={(lang) => setSelectedLanguages((prev) => {
                          const next = new Set(prev);
                          if (next.has(lang)) next.delete(lang); else next.add(lang);
                          return next;
                        })}
                      />
                    </BottomSheet>
                  )}
                  <AnimatePresence>
                    {languageFilterOpen && !isMobile && (
                      <LanguageFilterPopover
                        languages={languages}
                        selected={selectedLanguages}
                        onToggle={(lang) => setSelectedLanguages((prev) => {
                          const next = new Set(prev);
                          if (next.has(lang)) next.delete(lang); else next.add(lang);
                          return next;
                        })}
                        onClose={() => setLanguageFilterOpen(false)}
                      />
                    )}
                  </AnimatePresence>
                </div>
              </motion.div>
            ) : (
              <motion.div
                key="repositories-toolbar"
                initial={{ opacity: 0, x: -6 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -6 }}
                transition={{ duration: 0.15 }}
              >
                <ToolbarButton onClick={() => setAddRepositoryOpen(true)} label={t("sources.repositoriesAdd")}>
                  <Plus className="h-4 w-4" strokeWidth={2} />
                </ToolbarButton>
              </motion.div>
            )}
          </AnimatePresence>
          {!headerActions && refreshButton}
        </div>
        )}
      </div>

      <div className="flex-1 px-8 pb-12 pt-6 mobile:px-4 mobile:pb-6 mobile:pt-4">
        <AnimatePresence mode="wait" initial={false}>
          {searching ? (
            <motion.div
              key={openRepository ?? "extensions-tab"}
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              transition={{ duration: 0.18, ease: "easeOut" }}
            >
              <ExtensionsTab
                state={listState}
                errorMessage={openRepositoryResult && !openRepositoryResult.ok ? openRepositoryResult.error : ""}
                onRetry={() => setRefreshSignal((v) => v + 1)}
                updateAvailable={updateAvailableExtensions}
                upToDate={upToDateExtensions}
                available={availableExtensions}
                isEmpty={visibleExtensions.length === 0}
                emptyText={!openRepository && pool.length === 0 ? t("sources.noInstalled") : undefined}
                installedVersions={installedVersions}
                installingIds={installingIds}
                installErrors={installErrors}
                activeSourceId={activeSourceId ?? installedSources.data?.[0]?.id ?? null}
                onOpenSettings={setSettingsSourceId}
                sourcesWithSettings={sourcesWithSettings}
                onInstall={installExtension}
                onUpdateAll={() => void updateAll()}
                updateAllInProgress={updateAllInProgress}
                onUninstall={uninstallExtension}
                onSelect={setActiveSourceId}
              />
            </motion.div>
          ) : (
            <motion.div
              key="repositories-tab"
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              transition={{ duration: 0.18, ease: "easeOut" }}
            >
              <RepositoriesTab
                urls={repositoryUrls}
                resultByUrl={resultByUrl}
                onOpen={showRepository}
                onRemove={setRepositoryPendingRemoval}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <AnimatePresence>
        {addRepositoryOpen && (
          <AddRepositoryDialog onAdd={addRepository} onClose={() => setAddRepositoryOpen(false)} />
        )}
        {repositoryPendingRemoval && (
          <RemoveRepositoryDialog
            url={repositoryPendingRemoval}
            onConfirm={() => { removeRepository(repositoryPendingRemoval); setRepositoryPendingRemoval(null); }}
            onDismiss={() => setRepositoryPendingRemoval(null)}
          />
        )}
        {settingsSource && (
          <SourceSettingsDialog source={settingsSource} onClose={() => setSettingsSourceId(null)} />
        )}
      </AnimatePresence>
    </div>
  );
}

function ToolbarButton({ onClick, label, children }: { onClick: () => void; label: string; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      title={label}
      className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.06] hover:text-text"
    >
      {children}
    </button>
  );
}

function LanguageFilterPopover({
  languages,
  selected,
  onToggle,
  onClose,
}: {
  languages: string[];
  selected: Set<string>;
  onToggle: (lang: string) => void;
  onClose: () => void;
}) {
  return (
    <>
      <div className="fixed inset-0 z-40" onClick={onClose} />
      {/* No `scale` - see LibraryButton in anime.$sourceId.$animeId.tsx for why. */}
      <motion.div
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -4 }}
        transition={{ type: "spring", stiffness: 500, damping: 45 }}
        // No `py-1` here (unlike an earlier version) - it left a gap between the panel's own
        // rounded top/bottom edges and the first/last row's own hover highlight, which then
        // visibly stopped short of the border instead of reaching it. `overflow-hidden` + this
        // same `rounded-2xl` already clips each row's own corners correctly on their own, the same
        // pattern GroupDropdown's panel uses with no extra padding.
        className="absolute right-0 top-11 z-50 w-64 overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl"
      >
        <LanguageRows languages={languages} selected={selected} onToggle={onToggle} />
      </motion.div>
    </>
  );
}

/** The language switches, in the desktop dropdown and the phone's sheet alike. */
function LanguageRows({ languages, selected, onToggle }: { languages: string[]; selected: Set<string>; onToggle: (lang: string) => void }) {
  return (
    <>
        {languages.length === 0 ? (
          <p className="px-3 py-2 text-sm text-muted">—</p>
        ) : (
          languages.map((lang) => {
            const label = languageLabel(lang);
            const checked = selected.has(lang);
            return (
              // A `<div>`, not a `<button>` - it wraps the shared Switch, which is its own real
              // `<button>`; nesting one inside the other is invalid HTML. The whole row still
              // toggles on click (onClick here), with the switch's own click stopped from also
              // bubbling up to this same handler - without that, clicking directly on the switch
              // would fire onToggle twice (once from the switch's own onChange, once more from
              // this row catching the bubbled event) and net out to no visible change at all.
              <div
                key={lang}
                onClick={() => onToggle(lang)}
                className="flex w-full cursor-default items-center justify-between px-3 py-2 text-left transition-colors hover:bg-text/[.06]"
              >
                <span>
                  <span className="block text-sm font-medium text-text">{label.native}</span>
                  <span className="block text-xs text-muted">{label.english}</span>
                </span>
                <span onClick={(e) => e.stopPropagation()}>
                  <Switch checked={checked} onChange={() => onToggle(lang)} />
                </span>
              </div>
            );
          })
        )}
    </>
  );
}

function SectionHeader({ title, action }: { title: string; action?: React.ReactNode }) {
  return (
    <div className="mb-2 mt-6 flex items-center justify-between first:mt-0">
      <h2 className="text-sm font-bold text-text">{title}</h2>
      {action}
    </div>
  );
}

function ExtensionsTab({
  state,
  errorMessage,
  onRetry,
  updateAvailable,
  upToDate,
  available,
  isEmpty,
  emptyText,
  installedVersions,
  installingIds,
  installErrors,
  activeSourceId,
  onInstall,
  onOpenSettings,
  sourcesWithSettings,
  onUpdateAll,
  updateAllInProgress,
  onUninstall,
  onSelect,
}: {
  state: "loading" | "error" | "loaded";
  errorMessage: string;
  onRetry: () => void;
  updateAvailable: MarketplaceExtension[];
  upToDate: MarketplaceExtension[];
  available: MarketplaceExtension[];
  isEmpty: boolean;
  /** What to say when there is nothing to list at all, rather than nothing matching the search. */
  emptyText?: string;
  installedVersions: Map<string, string>;
  installingIds: Set<string>;
  installErrors: Record<string, string>;
  activeSourceId: string | null;
  onInstall: (extension: MarketplaceExtension) => void;
  onUpdateAll: () => void;
  updateAllInProgress: boolean;
  onUninstall: (id: string) => void;
  onOpenSettings: (id: string) => void;
  /** Ids whose installed manifest declares any settings - the marketplace entries these cards are
   * built from carry no settings of their own. */
  sourcesWithSettings: Set<string>;
  onSelect: (id: string) => void;
}) {
  const { t } = useTranslation();
  const { shown: availableShown, sentinel } = useProgressiveList(available.length);
  if (state === "loading") return <CenteredMessage text={t("sources.repositoryLoading")} />;
  if (state === "error") return <CenteredMessage text={t("sources.repositoryError")} detail={errorMessage} onRetry={onRetry} />;
  if (isEmpty) return <CenteredMessage text={emptyText ?? t("sources.noResults")} />;

  return (
    <div className="max-w-[1400px]">
      {updateAvailable.length > 0 && (
        <>
          <SectionHeader
            title={t("sources.updatesSection")}
            action={<button onClick={onUpdateAll} disabled={updateAllInProgress} className={cn("flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-semibold text-text/80 transition-colors", updateAllInProgress ? "cursor-wait opacity-60" : "hover:bg-text/[.06]")}><RefreshCw className={cn("h-3.5 w-3.5", updateAllInProgress && "animate-spin")} strokeWidth={2} />{t("sources.updateAll")}</button>}
          />
          <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-3">
            <AnimatePresence initial={false}>
              {updateAvailable.map((extension) => (
                <ExtensionCard key={extension.id} extension={extension} installedVersion={installedVersions.get(extension.id) ?? null} updateAvailable installing={installingIds.has(extension.id)} errorMessage={installErrors[extension.id]} selected={extension.id === activeSourceId} onInstall={() => onInstall(extension)} onUninstall={() => onUninstall(extension.id)} onOpenSettings={() => onOpenSettings(extension.id)} hasSettings={sourcesWithSettings.has(extension.id)} onSelect={() => onSelect(extension.id)} />
              ))}
            </AnimatePresence>
          </div>
        </>
      )}
      {upToDate.length > 0 && (
        <>
          <SectionHeader title={t("sources.installedSection", { count: upToDate.length })} />
          <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-3">
            <AnimatePresence initial={false}>
              {upToDate.map((extension) => (
                <ExtensionCard key={extension.id} extension={extension} installedVersion={installedVersions.get(extension.id) ?? null} updateAvailable={false} installing={installingIds.has(extension.id)} errorMessage={installErrors[extension.id]} selected={extension.id === activeSourceId} onInstall={() => onInstall(extension)} onUninstall={() => onUninstall(extension.id)} onOpenSettings={() => onOpenSettings(extension.id)} hasSettings={sourcesWithSettings.has(extension.id)} onSelect={() => onSelect(extension.id)} />
              ))}
            </AnimatePresence>
          </div>
        </>
      )}
      {available.length > 0 && (
        <>
          <SectionHeader title={t("sources.availableSection", { count: available.length })} />
          <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-3">
            <AnimatePresence initial={false}>
              {available.slice(0, availableShown).map((extension) => (
                <ExtensionCard key={extension.id} extension={extension} installedVersion={null} installing={installingIds.has(extension.id)} errorMessage={installErrors[extension.id]} selected={false} onInstall={() => onInstall(extension)} onUninstall={() => onUninstall(extension.id)} onOpenSettings={() => onOpenSettings(extension.id)} hasSettings={sourcesWithSettings.has(extension.id)} onSelect={() => onSelect(extension.id)} />
              ))}
            </AnimatePresence>
          </div>
          {/* Keyed by the count: a fresh node is observed afresh, so a sentinel still in view asks again. */}
          {availableShown < available.length && <div key={availableShown} ref={sentinel} className="h-24" />}
        </>
      )}
    </div>
  );
}

const PROGRESSIVE_CHUNK = 24;

/**
 * On the phone, how many of a long list to render: a first screenful, then another chunk each time the
 * sentinel under them comes near the viewport. Rendering every source of every repository at once
 * (hundreds of cards) held up entering the page by a sixth of a second. Desktop renders all.
 */
function useProgressiveList(total: number): { shown: number; sentinel: (node: HTMLDivElement | null) => void } {
  const [shown, setShown] = useState(isMobile ? PROGRESSIVE_CHUNK : Infinity);
  const observer = useRef<IntersectionObserver | null>(null);
  const sentinel = useCallback((node: HTMLDivElement | null) => {
    observer.current?.disconnect();
    if (!node) return;
    observer.current = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) setShown((count) => count + PROGRESSIVE_CHUNK);
      },
      { rootMargin: "800px 0px" },
    );
    observer.current.observe(node);
  }, []);
  useEffect(() => () => observer.current?.disconnect(), []);
  return { shown: Math.min(shown, total), sentinel };
}

const MOBILE_CARD_STYLE: React.CSSProperties = { contentVisibility: "auto", containIntrinsicSize: "auto 104px" };

function ExtensionCard({
  extension,
  installedVersion,
  updateAvailable,
  installing,
  errorMessage,
  selected,
  onInstall,
  onUninstall,
  onOpenSettings,
  hasSettings,
  onSelect,
}: {
  extension: MarketplaceExtension;
  installedVersion: string | null;
  updateAvailable?: boolean;
  installing: boolean;
  errorMessage?: string;
  selected: boolean;
  onInstall: () => void;
  onUninstall: () => void;
  onOpenSettings: () => void;
  hasSettings: boolean;
  onSelect: () => void;
}) {
  const { t } = useTranslation();
  // Decided by the parent, not recomputed here: an update can come from one of this source's
  // resolvers rather than the source itself (see isExtensionUpdateAvailable), in which case
  // `extension.version` and `installedVersion` are equal and comparing them here would have this
  // card read "up to date" while sitting under the "updates available" header.
  const upToDate = installedVersion !== null && !updateAvailable;
  // ...which is also why the arrow only shows when the two versions actually differ. A resolver
  // update would otherwise render as "1.0.2 → 1.0.2".
  const versionLabel = installedVersion && !upToDate && installedVersion !== extension.version
    ? `${installedVersion} → ${extension.version}`
    : installedVersion ?? extension.version;
  const [menuOpen, setMenuOpen] = useState(false);
  const clickable = installedVersion !== null && !installing;
  // Ordered by what a person actually chooses a source on, then cut - see CAPABILITY_PRIORITY.
  const orderedCapabilities = useMemo(
    () => [...extension.capabilities].sort((a, b) => CAPABILITY_PRIORITY.indexOf(a) - CAPABILITY_PRIORITY.indexOf(b)),
    [extension.capabilities],
  );
  const shownCapabilities = orderedCapabilities.slice(0, MAX_VISIBLE_CAPABILITIES);
  const hiddenCapabilities = orderedCapabilities.slice(MAX_VISIBLE_CAPABILITIES);

  return (
    <motion.div
      // The phone lists every source of every repository (hundreds of cards): no layout tracking, which
      // measured them all on each render, and the cards out of view are neither laid out nor painted
      // until scrolled to. Entering the page stalled on them.
      layout={!isMobile}
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.96 }}
      transition={{ duration: 0.18, ease: "easeOut" }}
      style={isMobile ? MOBILE_CARD_STYLE : undefined}
      className={cn(
        "flex flex-col gap-1.5 rounded-xl border p-2.5 transition-colors",
        selected ? "border-accent/40 bg-accent/[.06]" : "border-border bg-text/[.03]",
      )}
    >
      <div className="flex items-center gap-2.5">
        <div onClick={clickable ? onSelect : undefined} className={cn("flex min-w-0 flex-1 items-center gap-2.5", clickable && "cursor-pointer")}>
          <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-text/[.06]">
            {extension.iconUrl ? <img src={extension.iconUrl} alt="" className="h-full w-full object-cover" /> : <Radio className="h-4 w-4 text-muted" strokeWidth={1.75} />}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1">
              <p className="select-text truncate text-[13px] font-bold text-text">{extension.name}</p>
              <AnimatePresence>
                {selected && (
                  <motion.span
                    layout
                    initial={{ opacity: 0, scale: 0.5 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.5 }}
                    transition={{ duration: 0.15, ease: "easeOut" }}
                    className="flex shrink-0 items-center justify-center"
                  >
                    <Check className="h-3.5 w-3.5 text-accent-text" strokeWidth={2.5} />
                  </motion.span>
                )}
              </AnimatePresence>
            </div>
            <div className="flex items-center gap-1.5">
              <p className={cn("truncate text-[11px] font-medium", installedVersion && !upToDate ? "text-accent-text" : "text-muted")}>
                {languageBadge(extension.lang)} · {versionLabel}
                {extension.isNsfw && <span className="ml-1 font-bold text-rose-400">· {t("sources.nsfwBadge")}</span>}
              </p>
              {/* An Aniyomi extension (Android): runs natively, installed as one package with its sibling sources. */}
              {extension.apkPackage && <span title={extension.apkPackage} className="shrink-0 rounded bg-text/[.08] px-1 py-px text-[9.5px] font-bold tracking-wide text-muted">APK</span>}
              {extension.capabilities.length > 0 && (
                <div className="flex shrink-0 items-center gap-1">
                  {shownCapabilities.map((capability) => {
                    const CapabilityIcon = CAPABILITY_ICONS[capability];
                    return (
                      <span key={capability} title={t(CAPABILITY_LABEL_KEYS[capability])} className="flex items-center">
                        <CapabilityIcon className="h-3 w-3 text-muted" strokeWidth={2} />
                      </span>
                    );
                  })}
                  {hiddenCapabilities.length > 0 && (
                    // The rest are still readable, just on hover rather than as eight identical
                    // grey glyphs nobody can tell apart at 12px anyway.
                    <span
                      title={hiddenCapabilities.map((capability) => t(CAPABILITY_LABEL_KEYS[capability])).join(", ")}
                      className="text-[10px] font-bold text-muted"
                    >
                      +{hiddenCapabilities.length}
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
        {installedVersion === null ? (
          <button
            onClick={onInstall}
            disabled={installing}
            className="shrink-0 rounded-lg bg-text px-2.5 py-1.5 text-xs font-bold text-bg transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {t("sources.install")}
          </button>
        ) : (
          <div className="relative shrink-0">
            <button
              onClick={() => setMenuOpen((v) => !v)}
              disabled={installing}
              className="rounded-lg border border-border px-2.5 py-1.5 text-xs font-bold text-text/80 transition-colors hover:bg-text/[.06] disabled:opacity-50"
            >
              {t("sources.manage")}
            </button>
            {/* Phone: a sheet. The dropdown below sits inside the card, whose content-visibility clips
                whatever leaves its box - the menu opened out of sight and the button seemed dead. */}
            {isMobile && (
              <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title={extension.name}>
                {!upToDate && <SheetOption icon={<RefreshCw className="h-5 w-5" strokeWidth={2} />} label={t("sources.update")} onClick={() => { setMenuOpen(false); onInstall(); }} />}
                {hasSettings && <SheetOption icon={<Settings2 className="h-5 w-5" strokeWidth={2} />} label={t("sources.settings")} onClick={() => { setMenuOpen(false); onOpenSettings(); }} />}
                <SheetOption danger icon={<Trash2 className="h-5 w-5" strokeWidth={2} />} label={t("sources.uninstall")} onClick={() => { setMenuOpen(false); onUninstall(); }} />
              </BottomSheet>
            )}
            <AnimatePresence>
              {menuOpen && !isMobile && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
                  {/* No `scale` - see LibraryButton in anime.$sourceId.$animeId.tsx for why. */}
                  <motion.div
                    initial={{ opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -4 }}
                    transition={{ type: "spring", stiffness: 500, damping: 45 }}
                    className="absolute right-0 top-9 z-50 w-40 overflow-hidden rounded-xl border border-border bg-surface shadow-2xl"
                  >
                    {!upToDate && (
                      <button onClick={() => { setMenuOpen(false); onInstall(); }} className="block w-full px-3 py-2 text-left text-sm text-text hover:bg-text/[.06]">{t("sources.update")}</button>
                    )}
                    {hasSettings && (
                      <button onClick={() => { setMenuOpen(false); onOpenSettings(); }} className="block w-full px-3 py-2 text-left text-sm text-text hover:bg-text/[.06]">{t("sources.settings")}</button>
                    )}
                    <button onClick={() => { setMenuOpen(false); onUninstall(); }} className="block w-full px-3 py-2 text-left text-sm text-rose-400 hover:bg-text/[.06]">{t("sources.uninstall")}</button>
                  </motion.div>
                </>
              )}
            </AnimatePresence>
          </div>
        )}
      </div>
      {errorMessage && <p className="select-text pl-[46px] text-xs text-rose-400">{errorMessage}</p>}
    </motion.div>
  );
}

function RepositoriesTab({
  urls,
  resultByUrl,
  onOpen,
  onRemove,
}: {
  urls: string[];
  resultByUrl: Map<string, RepositoryFetchResult>;
  onOpen: (url: string) => void;
  onRemove: (url: string) => void;
}) {
  const { t } = useTranslation();
  if (urls.length === 0) return <CenteredMessage text={t("sources.repositoriesEmpty")} />;
  return (
    <div className="flex max-w-2xl flex-col gap-2">
      <AnimatePresence initial={false}>
        {urls.map((url) => (
          <RepositoryCard key={url} url={url} result={resultByUrl.get(url)} onOpen={() => onOpen(url)} onRemove={() => onRemove(url)} />
        ))}
      </AnimatePresence>
    </div>
  );
}

function RepositoryCard({ url, result, onOpen, onRemove }: { url: string; result: RepositoryFetchResult | undefined; onOpen: () => void; onRemove: () => void }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const statusText = !result
    ? t("sources.repositoryLoading")
    : result.ok
      ? t("sources.repositoriesExtensionCount", { count: result.extensions.filter((e) => e.type === "source").length })
      : result.error;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, height: 0, scale: 0.98 }}
      animate={{ opacity: 1, height: "auto", scale: 1 }}
      exit={{ opacity: 0, height: 0, scale: 0.98 }}
      transition={{ duration: 0.2, ease: "easeOut" }}
      onClick={onOpen}
      className="cursor-pointer overflow-hidden rounded-2xl border border-border bg-text/[.03] p-4 transition-colors hover:border-accent/40 hover:bg-text/[.05]"
    >
      <div className="flex items-start gap-3">
        <Globe className="mt-0.5 h-5 w-5 shrink-0 text-muted" strokeWidth={1.75} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold text-text">{repositoryDisplayName(url)}</p>
          <p className={cn("mt-0.5 text-xs", result && !result.ok ? "text-rose-400" : "text-muted")}>{statusText}</p>
        </div>
        <ChevronRight className="mt-0.5 h-5 w-5 shrink-0 text-muted" strokeWidth={1.75} />
      </div>
      <div onClick={(e) => e.stopPropagation()} className="mt-3 flex items-center justify-end gap-1">
        <a href={url} target="_blank" rel="noreferrer" title={t("sources.openInBrowser")} className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.06] hover:text-text">
          <Globe className="h-4 w-4" strokeWidth={2} />
        </a>
        <button
          onClick={() => { navigator.clipboard.writeText(url); setCopied(true); setTimeout(() => setCopied(false), 1500); }}
          title={t("sources.copyUrl")}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.06] hover:text-text"
        >
          {copied ? <Check className="h-4 w-4 text-accent-text" strokeWidth={2} /> : <Copy className="h-4 w-4" strokeWidth={2} />}
        </button>
        <button onClick={onRemove} title={t("sources.repositoriesRemove")} className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-rose-400/10 hover:text-rose-400">
          <Trash2 className="h-4 w-4" strokeWidth={2} />
        </button>
      </div>
    </motion.div>
  );
}

function AddRepositoryDialog({ onAdd, onClose }: { onAdd: (url: string) => Promise<string | null>; onClose: () => void }) {
  const { t } = useTranslation();
  const [url, setUrl] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [validating, setValidating] = useState(false);
  // A repository's sources run inside the app with everything the app can reach - its data, the
  // sign-ins of other sources, on the phone native code (APK sources) - so adding one is a decision
  // about trust, asked as one before anything is fetched from it.
  const [confirming, setConfirming] = useState(false);

  const submit = async () => {
    const trimmed = url.trim();
    if (!trimmed) return;
    if (!confirming) {
      setConfirming(true);
      return;
    }
    setValidating(true);
    const validationError = await onAdd(trimmed);
    setValidating(false);
    if (validationError) {
      setError(validationError);
      setConfirming(false);
    } else onClose();
  };

  if (confirming) {
    return (
      <Modal onDismiss={onClose}>
        <h2 className="text-base font-bold text-text">{t("sources.repositoriesTrustTitle")}</h2>
        <p className="mt-2 select-text break-all text-sm font-semibold text-text/80">{repositoryDisplayName(url.trim())}</p>
        <p className="mt-2 text-sm leading-relaxed text-muted">{t("sources.repositoriesTrustMessage")}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button onClick={() => setConfirming(false)} disabled={validating} className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06] disabled:opacity-50">{t("common.cancel")}</button>
          <button onClick={submit} disabled={validating} className="rounded-lg bg-text px-3.5 py-2 text-sm font-bold text-bg transition-opacity hover:opacity-90 disabled:opacity-50">{t("sources.repositoriesTrustConfirm")}</button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal onDismiss={onClose}>
      <h2 className="text-base font-bold text-text">{t("sources.repositoriesAdd")}</h2>
      <input
        autoFocus
        value={url}
        onChange={(e) => { setUrl(e.target.value); setError(null); }}
        placeholder={t("sources.repositoriesAddHint")}
        disabled={validating}
        onKeyDown={(e) => e.key === "Enter" && submit()}
        className="mt-4 h-10 w-full rounded-lg bg-text/[.06] px-3 text-sm text-text placeholder:text-muted outline-none ring-1 ring-transparent focus:ring-accent/50 disabled:opacity-50"
      />
      {error && <p className="mt-2 select-text text-xs text-rose-400">{error}</p>}
      <div className="mt-5 flex justify-end gap-2">
        <button onClick={onClose} disabled={validating} className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06] disabled:opacity-50">{t("common.cancel")}</button>
        <button onClick={submit} disabled={validating || !url.trim()} className="rounded-lg bg-text px-3.5 py-2 text-sm font-bold text-bg transition-opacity hover:opacity-90 disabled:opacity-50">{t("common.save")}</button>
      </div>
    </Modal>
  );
}

function RemoveRepositoryDialog({ url, onConfirm, onDismiss }: { url: string; onConfirm: () => void; onDismiss: () => void }) {
  const { t } = useTranslation();
  return (
    <Modal onDismiss={onDismiss}>
      <h2 className="text-base font-bold text-text">{t("sources.repositoriesRemoveConfirmTitle")}</h2>
      <p className="mt-2 select-text text-sm leading-relaxed text-muted">{t("sources.repositoriesRemoveConfirmMessage", { name: repositoryDisplayName(url) })}</p>
      <div className="mt-5 flex justify-end gap-2">
        <button onClick={onDismiss} className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]">{t("common.cancel")}</button>
        <button onClick={onConfirm} className="rounded-lg bg-rose-500 px-3.5 py-2 text-sm font-bold text-text transition-opacity hover:opacity-90">{t("sources.repositoriesRemove")}</button>
      </div>
    </Modal>
  );
}

function CenteredMessage({ text, detail, onRetry }: { text: string; detail?: string; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex min-h-[300px] flex-col items-center justify-center gap-3 text-center">
      {onRetry && <TriangleAlert className="h-6 w-6 text-muted/70" strokeWidth={1.5} />}
      <p className="max-w-sm select-text text-sm text-muted">{text}</p>
      {detail && <p className="max-w-sm select-text text-xs text-muted/70">{detail}</p>}
      {onRetry && (
        <button onClick={onRetry} className="mt-1 rounded-lg border border-border px-3.5 py-1.5 text-xs font-semibold text-text/80 transition-colors hover:bg-text/[.06]">
          {t("sources.repositoryRetry")}
        </button>
      )}
    </div>
  );
}
