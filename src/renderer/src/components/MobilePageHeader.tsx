import { useEffect, useLayoutEffect, useRef } from "react";
import { Link, useNavigate, useRouter, useRouterState } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { ArrowLeft } from "lucide-react";
import { cn } from "@/lib/cn";

/**
 * Phone only: the top of a screen one level inside a tab (settings and sources under the profile) -
 * its name, with the way back to the tab beside it.
 */
export function MobilePageHeader({ title, parent, onBack }: {
  title: string;
  parent: "/library" | "/profile" | "/settings";
  /** Back within the screen itself (a settings section back to the list), instead of leaving it. */
  onBack?: () => void;
}) {
  const { t } = useTranslation();
  const router = useRouter();
  const navigate = useNavigate();
  return (
    <div className="-ml-2 mb-4 flex items-center gap-1">
      <button
        type="button"
        aria-label={t("detail.back")}
        onClick={() => {
          if (onBack) {
            onBack();
            return;
          }
          // Reached some other way than from its tab (a link, the app restored onto it): to the tab.
          const index = (window.history.state as { __TSR_index?: number } | null)?.__TSR_index ?? 0;
          if (index > 0) router.history.back();
          else void navigate({ to: parent });
        }}
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-text active:bg-text/[.08]"
      >
        <ArrowLeft className="h-[22px] w-[22px]" strokeWidth={2.25} />
      </button>
      <h1 className="truncate text-[20px] font-bold tracking-[-.02em] text-text">{title}</h1>
    </div>
  );
}

const LIBRARY_SEGMENTS = [
  { to: "/library", labelKey: "library.segments.saved" },
  { to: "/history", labelKey: "library.segments.history" },
  { to: "/downloads", labelKey: "library.segments.downloads" },
] as const;

const SEGMENT_PATHS: readonly string[] = LIBRARY_SEGMENTS.map((segment) => segment.to);

// Which view the library showed last while it was on screen - where the highlight slides from
// when another one is picked. Forgotten on leaving the tab, so coming back doesn't slide.
let lastSegmentIndex: number | null = null;

/**
 * Phone only: the top of the library tab - a switch between what it holds: saved
 * titles, watch history and downloads, as three views of one place rather than screens to visit.
 * Switching replaces the entry, so Back leaves the tab instead of stepping through the switches.
 */
export function LibrarySegments({ active }: { active: (typeof LIBRARY_SEGMENTS)[number]["to"] }) {
  const { t } = useTranslation();
  // The other two views' code, fetched while this one is on screen, so switching to them is instant.
  useEffect(() => {
    void import("@/pages/history");
    void import("@/pages/downloads");
  }, []);
  // Each view is its own page with its own copy of this switch, and switching shows the other page
  // in place (no page transition between them - see usePageTransition). So the switch looks like
  // one control that stays put: the newly shown copy slides its highlight over from where the
  // previous one had it.
  const index = LIBRARY_SEGMENTS.findIndex((segment) => segment.to === active);
  const pathname = useRouterState({ select: (s) => (s.resolvedLocation ?? s.location).pathname });
  const pill = useRef<HTMLSpanElement>(null);
  useLayoutEffect(() => {
    if (!SEGMENT_PATHS.includes(pathname)) {
      lastSegmentIndex = null;
      return;
    }
    if (pathname !== active) return;
    const from = lastSegmentIndex;
    lastSegmentIndex = index;
    if (from === null || from === index || !pill.current) return;
    pill.current.animate(
      [{ transform: `translateX(${(from - index) * 100}%)` }, { transform: "none" }],
      { duration: 260, easing: "cubic-bezier(0.2, 0.8, 0.2, 1)" },
    );
  }, [pathname, active, index]);
  return (
    <div className="mb-4">
      <div className="relative grid grid-cols-3 rounded-full bg-text/[.06] p-1">
        <span
          ref={pill}
          aria-hidden
          className="absolute inset-y-1 rounded-full bg-app-popover shadow-[0_1px_4px_rgba(0,0,0,0.35)]"
          style={{ width: "calc((100% - 0.5rem) / 3)", left: `calc(0.25rem + ${index} * (100% - 0.5rem) / 3)` }}
        />
        {LIBRARY_SEGMENTS.map((segment) => (
          <Link
            key={segment.to}
            to={segment.to}
            replace
            className={cn(
              "relative truncate rounded-full px-2 py-2 text-center text-[13px] font-semibold transition-colors duration-200",
              segment.to === active ? "text-text" : "text-muted",
            )}
          >
            {t(segment.labelKey)}
          </Link>
        ))}
      </div>
    </div>
  );
}
