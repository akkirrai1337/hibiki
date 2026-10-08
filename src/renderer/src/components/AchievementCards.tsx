import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { Check, Lock, X } from "lucide-react";
import { cn } from "@/lib/cn";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { FAMILY_TIERS, type Achievement } from "@/lib/achievements";

/** The profile's achievements: one card per family; a click opens every level of it. */
export function AchievementGrid({ achievements }: { achievements: Achievement[] }) {
  // Earned first, then by how close each one is - what is nearly done reads before what is far away.
  const sorted = useMemo(
    () => [...achievements].sort((a, b) => Number(b.unlocked) - Number(a.unlocked) || b.current / b.target - a.current / a.target),
    [achievements],
  );
  const [openId, setOpenId] = useState<string | null>(null);
  const open = achievements.find((a) => a.id === openId);
  return (
    <>
      <div className="space-y-2">
        {sorted.map((a) => (
          <AchievementCard key={a.id} achievement={a} onOpen={() => setOpenId(a.id)} />
        ))}
      </div>
      {createPortal(<AnimatePresence>{open && <AchievementDetail key={open.id} achievement={open} onClose={() => setOpenId(null)} />}</AnimatePresence>, document.body)}
    </>
  );
}

// The Android tile's progress ring: a track, and the share done drawn over it from the top.
function ProgressRing({ fraction, size, stroke, children }: { fraction: number; size: number; stroke: number; children: React.ReactNode }) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" strokeWidth={stroke} className="stroke-text/10" />
        {fraction > 0 && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={circumference * (1 - Math.min(1, fraction))}
            className="stroke-accent"
          />
        )}
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">{children}</div>
    </div>
  );
}

const fractionOf = (a: Achievement) => (a.unlocked ? 1 : Math.min(1, a.current / a.target));

function AchievementCard({ achievement, onOpen }: { achievement: Achievement; onOpen: () => void }) {
  const { t } = useTranslation();
  const Icon = achievement.icon;
  const fraction = fractionOf(achievement);
  return (
    <button
      onClick={onOpen}
      className={cn(
        "flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-colors",
        achievement.unlocked ? "border-accent/30 bg-accent/[.08] hover:bg-accent/[.12]" : "border-border bg-text/[.05] hover:bg-text/[.08]",
      )}
    >
      <ProgressRing fraction={fraction} size={44} stroke={3}>
        <Icon className="h-5 w-5 text-accent-text" strokeWidth={2} />
      </ProgressRing>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <p className="truncate text-sm font-semibold text-text">{t(achievement.titleKey)}</p>
          {achievement.unlocked ? (
            <Check className="h-4 w-4 shrink-0 text-accent-text" strokeWidth={2.5} />
          ) : (
            <span className="shrink-0 text-[11px] font-semibold tabular-nums text-text/70">
              {achievement.current}/{achievement.target}
            </span>
          )}
        </div>
        <div className="mt-0.5 flex items-center justify-between gap-2">
          <p className="truncate text-xs text-text/60">{t(achievement.descriptionKey, { target: achievement.target })}</p>
          <span className="shrink-0 text-[11px] font-semibold text-accent-text">+{achievement.xpReward} {t("profile.xp")}</span>
        </div>
        {achievement.maxLevel > 1 && (
          <div className="mt-1.5 flex items-center gap-1.5">
            <span className="text-[10px] text-text/60">{t("profile.achievementLevel", { level: achievement.level, maxLevel: achievement.maxLevel })}</span>
            <div className="flex gap-1">
              {Array.from({ length: achievement.maxLevel }).map((_, i) => (
                <span key={i} className={cn("h-1.5 w-1.5 rounded-full", i < achievement.level ? "bg-accent" : "bg-text/20")} />
              ))}
            </div>
          </div>
        )}
      </div>
    </button>
  );
}

// Every level of the family as a timeline: cleared ones ticked, the one in progress with its bar, the
// rest locked with their goal and reward - so what comes next never has to be guessed.
function AchievementDetail({ achievement, onClose }: { achievement: Achievement; onClose: () => void }) {
  const { t } = useTranslation();
  const popoverTheme = usePopoverTheme();
  const tiers = FAMILY_TIERS[achievement.id] ?? [];
  const activeIndex = Math.min(achievement.level, tiers.length - 1);
  const ActiveIcon = achievement.icon;

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-6 mobile:z-[90]">
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }} onClick={onClose} className="absolute inset-0 bg-black/55" />
      <motion.div
        // Opacity only: a scale/offset spring leaves the text on a fractional pixel while it settles and
        // it visibly jumps into place at the end.
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.15 }}
        style={popoverTheme}
        className="relative flex max-h-[85vh] w-full max-w-md flex-col overflow-hidden rounded-2xl border border-border bg-app-popover shadow-2xl"
      >
        <button onClick={onClose} aria-label="Close" className="absolute right-3 top-3 z-10 flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.08] hover:text-text">
          <X className="h-4 w-4" strokeWidth={2.25} />
        </button>
        <div className="no-scrollbar overflow-y-auto p-6">
          <div className="flex items-center gap-4 pr-8">
            <ProgressRing fraction={fractionOf(achievement)} size={72} stroke={5}>
              <ActiveIcon className="h-7 w-7 text-accent-text" strokeWidth={2} />
            </ProgressRing>
            <div className="min-w-0">
              <h3 className="text-lg font-bold text-text">{t(achievement.titleKey)}</h3>
              <p className="mt-1 text-sm text-text/65">{t(achievement.descriptionKey, { target: achievement.target })}</p>
            </div>
          </div>
          {achievement.unlocked && (
            <span className="mt-4 inline-block rounded-full bg-accent px-3 py-1 text-xs font-bold text-accent-fg">{t("profile.achievementMaxed")}</span>
          )}
          {tiers.length > 1 && (
            <>
              <p className="mb-3 mt-6 text-xs font-bold uppercase tracking-wide text-muted">{t("profile.achievementLevels")}</p>
              <div>
                {tiers.map((tier, index) => {
                  const cleared = index < achievement.level;
                  const current = index === activeIndex && !achievement.unlocked;
                  const TierIcon = tier.icon;
                  const last = index === tiers.length - 1;
                  return (
                    <div key={tier.id} className="flex gap-3.5">
                      <div className="flex flex-col items-center">
                        <div
                          className={cn(
                            "flex h-8 w-8 shrink-0 items-center justify-center rounded-full",
                            cleared ? "bg-accent text-accent-fg" : current ? "bg-accent/20 text-accent-text" : "bg-text/10 text-muted",
                          )}
                        >
                          {cleared ? <Check className="h-4 w-4" strokeWidth={3} /> : current ? <TierIcon className="h-4 w-4" strokeWidth={2} /> : <Lock className="h-3.5 w-3.5" strokeWidth={2.25} />}
                        </div>
                        {!last && <div className={cn("w-0.5 flex-1", cleared ? "bg-accent" : "bg-text/10")} />}
                      </div>
                      <div className={cn("min-w-0 flex-1", !last && "pb-5")}>
                        <div className="flex items-center justify-between gap-2">
                          <p className={cn("truncate text-sm font-semibold", cleared || current ? "text-text" : "text-text/60")}>{t(`profile.achievements.${tier.id}.title`)}</p>
                          <span className="shrink-0 text-[11px] font-semibold text-accent-text">+{tier.xp} {t("profile.xp")}</span>
                        </div>
                        <p className="mt-0.5 text-xs text-text/55">{t(`profile.achievements.${tier.id}.description`, { target: tier.target })}</p>
                        {current && (
                          <>
                            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-text/10">
                              <div className="h-full rounded-full bg-accent" style={{ width: `${Math.min(100, (achievement.current / tier.target) * 100)}%` }} />
                            </div>
                            <p className="mt-1 text-[11px] tabular-nums text-text/60">
                              {achievement.current} / {tier.target}
                            </p>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>
      </motion.div>
    </div>
  );
}
