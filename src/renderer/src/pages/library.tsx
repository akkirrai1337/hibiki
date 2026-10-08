import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import * as ContextMenu from "@radix-ui/react-context-menu";
import { Bookmark, Trash2, TriangleAlert } from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { AnimeCard, type AnimeCardSource } from "@/components/AnimeCard";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { cn } from "@/lib/cn";
import { isMobile } from "@/lib/mobile";
import { LibrarySegments } from "@/components/MobilePageHeader";
import { ALL_LIBRARY_CATEGORIES, LIBRARY_CATEGORY_ICONS, LIBRARY_CATEGORY_LABEL_KEYS } from "@/lib/libraryCategories";
import type { LibraryCategory, LibraryEntry } from "@shared/types";

// Rendered persistently from __root.tsx instead - see index.tsx for why.
export function LibraryPage() {
  const { t } = useTranslation();
  const libraryQuery = useQuery({ queryKey: ["library"], queryFn: () => hibiki.library.list() });
  const sourcesQuery = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const [filter, setFilter] = useState<LibraryCategory | "all">("all");

  const entries = libraryQuery.data ?? [];
  const sourceById = useMemo(() => new Map((sourcesQuery.data ?? []).map((s) => [s.id, s])), [sourcesQuery.data]);
  const counts = useMemo(() => {
    const map = new Map<LibraryCategory, number>();
    for (const entry of entries) map.set(entry.category, (map.get(entry.category) ?? 0) + 1);
    return map;
  }, [entries]);
  const visible = filter === "all" ? entries : entries.filter((e) => e.category === filter);

  return (
    <div className="min-h-full bg-app-bg px-8 py-8 pb-16 mobile:px-4 mobile:pb-6 mobile:pt-4">
      {/* Phone: the tab's name, and the switch to the history and downloads it also holds (the
          sidebar's own entries on desktop). */}
      {isMobile && <LibrarySegments active="/library" />}
      {libraryQuery.isError && <ErrorBanner message={(libraryQuery.error as Error).message} />}
      <div className="mb-6 flex flex-wrap gap-2 mobile:[scrollbar-width:none] mobile:-mx-4 mobile:mb-4 mobile:flex-nowrap mobile:overflow-x-auto mobile:px-4">
        <CategoryChip active={filter === "all"} onClick={() => setFilter("all")} label={t("library.filterAll")} count={entries.length} />
        {ALL_LIBRARY_CATEGORIES.map((category) => (
          <CategoryChip
            key={category}
            active={filter === category}
            onClick={() => setFilter(category)}
            label={t(LIBRARY_CATEGORY_LABEL_KEYS[category])}
            count={counts.get(category) ?? 0}
            icon={LIBRARY_CATEGORY_ICONS[category]}
          />
        ))}
      </div>
      {visible.length === 0 ? (
        // The tabs are there either way; only the words under them differ: nothing at all yet, or
        // nothing in this one.
        <EmptyState text={entries.length === 0 ? t("library.empty") : t("library.categoryEmpty")} />
      ) : (
        <div className="grid grid-cols-5 gap-x-4 gap-y-6 mobile:grid-cols-3 mobile:gap-x-3 mobile:gap-y-5">
          {visible.map((entry) => (
            <LibraryCard key={`${entry.sourceId}:${entry.animeId}`} entry={entry} source={sourceById.get(entry.sourceId)} />
          ))}
        </div>
      )}
    </div>
  );
}

// A right-click menu for removing a title from the library, matching the one the continue-watching
// and history cards already have (see WatchFrameCard) - previously the only way out of the library
// was to open the title's own page and toggle it off there.
function LibraryCard({ entry, source }: { entry: LibraryEntry; source?: AnimeCardSource }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const popoverTheme = usePopoverTheme();
  const [menuOpen, setMenuOpen] = useState(false);

  const onRemove = async () => {
    await hibiki.library.remove(entry.sourceId, entry.animeId);
    queryClient.invalidateQueries({ queryKey: ["library"] });
  };

  return (
    <ContextMenu.Root onOpenChange={setMenuOpen}>
      <ContextMenu.Trigger asChild>
        <div>
          <AnimeCard anime={entry.anime} source={source} />
        </div>
      </ContextMenu.Trigger>
      <ContextMenu.Portal forceMount>
        <AnimatePresence>
          {menuOpen && (
            <ContextMenu.Content asChild forceMount>
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ type: "spring", stiffness: 500, damping: 45 }}
                style={popoverTheme}
                className="z-50 w-56 overflow-hidden rounded-xl border border-border bg-app-popover shadow-2xl"
              >
                <ContextMenu.Item
                  onSelect={onRemove}
                  className="flex cursor-default items-center gap-2.5 px-3.5 py-2.5 text-sm text-rose-300 outline-none transition-colors data-[highlighted]:bg-text/[.06]"
                >
                  <Trash2 className="h-4 w-4 shrink-0" strokeWidth={2} />
                  {t("detail.removeFromLibrary")}
                </ContextMenu.Item>
              </motion.div>
            </ContextMenu.Content>
          )}
        </AnimatePresence>
      </ContextMenu.Portal>
    </ContextMenu.Root>
  );
}

function CategoryChip({
  active,
  onClick,
  label,
  count,
  icon: Icon,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  count: number;
  icon?: typeof Bookmark;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors mobile:shrink-0 mobile:rounded-full mobile:px-3.5 mobile:py-2",
        active ? "bg-accent text-accent-fg" : "bg-text/[.05] text-muted hover:bg-text/[.08] hover:text-text",
      )}
    >
      {Icon && <Icon className="h-3.5 w-3.5" strokeWidth={2} />}
      {label}
      <span className={cn("text-xs", active ? "text-accent-fg/70" : "text-muted")}>{count}</span>
    </button>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="flex min-h-[calc(100vh-220px)] items-center justify-center mobile:min-h-[40vh]">
      <div className="max-w-sm text-center">
        <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-text/[.06]">
          <Bookmark className="h-6 w-6 text-muted" strokeWidth={1.75} />
        </div>
        <p className="text-sm leading-6 text-muted">{text}</p>
      </div>
    </div>
  );
}

function ErrorBanner({ message }: { message: string }) {
  const { t } = useTranslation();
  return <div className="flex items-start gap-3 rounded-2xl border border-rose-400/30 bg-rose-400/10 p-4 text-sm text-rose-700 dark:border-rose-400/20 dark:bg-rose-400/5 dark:text-rose-200"><TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={2} /><span>{t("common.loadFailed", { message })}</span></div>;
}
