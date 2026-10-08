import { useEffect } from "react";
import { AnimatePresence, motion } from "motion/react";
import { useTranslation } from "react-i18next";
import { useAchievementToastStore } from "@/stores/achievementToastStore";

// How long a toast stays up before auto-dismissing - long enough to actually read an achievement
// name + XP amount, short enough not to linger as stale-looking chrome over whatever page you're
// actually trying to use.
const DISPLAY_MS = 3200;

// Rendered once, globally (see __root.tsx) rather than per-page - achievements can unlock from
// anywhere (see useAchievementUnlocks), so the toast has to be able to show up regardless of which
// page happens to be open at the time, the same way Discord's own quest-complete toast isn't tied
// to any one channel.
export function AchievementToast() {
  const { t } = useTranslation();
  const current = useAchievementToastStore((s) => s.queue[0]);
  const dequeue = useAchievementToastStore((s) => s.dequeue);

  useEffect(() => {
    if (!current) return;
    const timer = setTimeout(dequeue, DISPLAY_MS);
    return () => clearTimeout(timer);
  }, [current, dequeue]);

  return (
    // `overflow-hidden` on this outer band (sized to exactly the titlebar's own height, h-10 -
    // see TitleBar.tsx) clips the toast to underneath the titlebar rather than through it: without
    // it, the slide-in/out distance below made the toast momentarily overlap the titlebar itself,
    // reading as sliding out *through* the chrome instead of out *from under* it.
    <div className="pointer-events-none fixed inset-x-0 top-10 z-[100] flex justify-center overflow-hidden">
      <div className="px-4 pt-3">
        {/* mode="wait" - without it, dequeuing straight into the next queued item (see the timer
            above) changes `current.id` in the same tick, and AnimatePresence's default behavior
            animates the outgoing and incoming toasts at once instead of one after the other: the
            next toast was sliding in while the previous one hadn't finished sliding back out yet. */}
        <AnimatePresence mode="wait">
        {current && (
          <motion.div
            key={current.id}
            initial={{ y: "-100%", opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: "-100%", opacity: 0, transition: { duration: 0.25, ease: "easeIn" } }}
            // damping 24 (vs. this spring's own critical damping of ~39 at stiffness 380) was
            // underdamped enough to visibly overshoot on the way in - confirmed on a frame-by-frame
            // video storyboard, the toast swung up to within 2px of the clip boundary above (see
            // this file's own overflow-hidden comment) before settling ~18px clear of it, clipping
            // its rounded top corners for a couple of frames. This damping is high enough to kill
            // that overshoot while staying under critical, so it still eases in with a little
            // give - it just no longer bounces past its resting position first.
            transition={{ type: "spring", stiffness: 380, damping: 34 }}
            className="pointer-events-auto flex max-w-sm items-center gap-3 rounded-2xl border border-border bg-surface px-3.5 py-3 shadow-2xl"
          >
            <span className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent/15">
              {/* A one-shot ring "burst" out from the icon on entrance, not a looping effect - it's
                  meant to read as the moment the achievement lands, not as ambient decoration that
                  keeps drawing the eye while the toast is still sitting there being read. */}
              <motion.span
                initial={{ opacity: 0.9, scale: 0.6 }}
                animate={{ opacity: 0, scale: 1.6 }}
                transition={{ duration: 0.6, ease: "easeOut", delay: 0.1 }}
                className="absolute -inset-1 rounded-full border-2 border-accent/60"
              />
              <current.icon className="h-[19px] w-[19px] text-accent-text" strokeWidth={2} />
            </span>
            <div className="min-w-0">
              <p className="text-[11px] font-medium uppercase tracking-wide text-accent-text">{t("achievements.unlockedToast")}</p>
              <p className="truncate text-sm font-medium text-text">{t(current.titleKey)}</p>
            </div>
            <span className="shrink-0 rounded-full bg-accent/10 px-2.5 py-1 text-xs font-medium text-accent-text">
              +{current.xp} {t("profile.xp")}
            </span>
          </motion.div>
        )}
        </AnimatePresence>
      </div>
    </div>
  );
}
