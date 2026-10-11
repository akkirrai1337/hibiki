import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { Search } from "lucide-react";
import { mobileSearchMemory } from "@/lib/mobile";
import { cn } from "@/lib/cn";

/**
 * Phone only: the way into search, as an icon at the top of the screens that browse (home, catalog)
 * - search is something done from there, not a place of its own, and its tab went to the sources.
 * Comes back with what was last searched.
 */
export function MobileSearchButton({ overArtwork = false, className }: {
  /** Over the home hero's artwork: a dark glass disc, legible on any picture. */
  overArtwork?: boolean;
  className?: string;
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <button
      type="button"
      aria-label={t("nav.search")}
      onClick={() => void navigate({ to: "/search", search: { q: mobileSearchMemory.query } })}
      className={cn(
        "flex h-10 w-10 shrink-0 items-center justify-center rounded-full",
        overArtwork
          ? "bg-black/45 text-white ring-1 ring-white/10 backdrop-blur-md active:bg-black/70"
          : "text-text active:bg-text/[.08]",
        className,
      )}
    >
      <Search className="h-[20px] w-[20px]" strokeWidth={2.1} />
    </button>
  );
}
