import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { ArrowLeft, Info } from "lucide-react";
import appIcon from "@/assets/app-icon.png";
import { cn } from "@/lib/cn";
import { hibiki } from "@/lib/hibiki";
import { isMobile, useBackHandler } from "@/lib/mobile";

const SourcesPage = lazy(() => import("@/pages/sources").then((module) => ({ default: module.SourcesPage })));

// Loosely mirrors the Android app's own first-launch flow, with one extra step in between -
// Android's onboarding never actually grew a source-picking step of its own (leftover, unused
// strings for one still sit in its resources), so this reuses the desktop app's real sources page
// outright rather than inventing a separate, parallel "pick a source" UI that would drift from the
// real one over time.
type Step = "welcome" | "sources";
const STEPS: Step[] = ["welcome", "sources"];

const slideVariants = {
  enter: (d: number) => ({ opacity: 0, x: d * 24 }),
  center: { opacity: 1, x: 0 },
  exit: (d: number) => ({ opacity: 0, x: d * -24 }),
};

export function Onboarding({ onComplete }: { onComplete: () => void }) {
  if (isMobile) return <MobileOnboarding onComplete={onComplete} />;
  return <DesktopOnboarding onComplete={onComplete} />;
}

function DesktopOnboarding({ onComplete }: { onComplete: () => void }) {
  const [step, setStep] = useState<Step>("welcome");
  const [dir, setDir] = useState(1);
  const index = STEPS.indexOf(step);

  function goTo(next: Step) {
    setDir(STEPS.indexOf(next) > index ? 1 : -1);
    setStep(next);
  }

  return (
    <div className="flex h-full flex-col bg-app-bg">
      <div className="flex min-h-0 flex-1 items-center justify-center overflow-hidden px-8 py-8">
        <AnimatePresence mode="wait" initial={false} custom={dir}>
          <motion.div
            key={step}
            custom={dir}
            variants={slideVariants}
            initial="enter"
            animate="center"
            exit="exit"
            transition={{ duration: 0.22, ease: "easeOut" }}
            className="flex h-full w-full items-center justify-center"
          >
            {step === "welcome" && <WelcomeStep onNext={() => goTo("sources")} />}
            {step === "sources" && <SourcesStep />}
          </motion.div>
        </AnimatePresence>
      </div>
      {step !== "welcome" && (
        <OnboardingFooter
          index={index}
          total={STEPS.length}
          onBack={() => goTo(STEPS[index - 1])}
          onNext={step === "sources" ? onComplete : () => goTo(STEPS[index + 1])}
          isLast={step === "sources"}
        />
      )}
    </div>
  );
}

function WelcomeStep({ onNext }: { onNext: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex max-w-sm flex-col items-center text-center">
      <div className="flex h-32 w-32 items-center justify-center overflow-hidden rounded-full bg-white shadow-2xl">
        <img src={appIcon} alt="" className="h-20 w-20 object-contain" />
      </div>
      <h1 className="mt-8 text-3xl font-bold text-text">{t("onboarding.welcome.title")}</h1>
      <p className="mt-3 text-sm leading-relaxed text-muted">{t("onboarding.welcome.description")}</p>
      <button onClick={onNext} className="mt-8 rounded-xl bg-accent px-6 py-3 text-sm font-bold text-accent-fg transition-opacity hover:opacity-90">
        {t("onboarding.getStarted")}
      </button>
    </div>
  );
}

function SourcesStep() {
  const { t } = useTranslation();
  return (
    <div className="mx-auto flex h-full w-full max-w-5xl flex-col">
      <div className="shrink-0 px-2 pb-4 text-center">
        <h1 className="text-2xl font-bold text-text">{t("onboarding.sources.title")}</h1>
        <p className="mt-2 text-sm text-muted">{t("onboarding.sources.description")}</p>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto rounded-2xl border border-border bg-text/[.03]">
        <Suspense fallback={null}><SourcesPage embedded /></Suspense>
      </div>
    </div>
  );
}

function OnboardingFooter({
  index,
  total,
  onBack,
  onNext,
  isLast,
}: {
  index: number;
  total: number;
  onBack: () => void;
  onNext: () => void;
  isLast: boolean;
}) {
  const { t } = useTranslation();
  return (
    <div className="flex h-20 shrink-0 items-center justify-between border-t border-border px-8">
      <button onClick={onBack} className="rounded-lg px-3 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06] hover:text-text">
        {t("onboarding.back")}
      </button>
      <div className="flex items-center gap-1.5">
        {Array.from({ length: total }).map((_, i) => (
          <span key={i} className={cn("h-1.5 rounded-full transition-all duration-300", i === index ? "w-6 bg-accent" : "w-1.5 bg-text/[.15]")} />
        ))}
      </div>
      <button onClick={onNext} className="rounded-lg bg-accent px-4 py-2 text-sm font-bold text-accent-fg transition-opacity hover:opacity-90">
        {isLast ? t("onboarding.done") : t("onboarding.next")}
      </button>
    </div>
  );
}

// --- phone ----------------------------------------------------------------------------------------
// The phone's own first launch, in the look of the rest of the phone app: no frames or footers, the
// one action pinned under the thumb as on a title page, and the app's background theme behind it all
// (so nothing here paints a background of its own).

const MOBILE_STEP_EASE = [0.2, 0.8, 0.2, 1] as const;

function MobileOnboarding({ onComplete }: { onComplete: () => void }) {
  const [step, setStep] = useState<Step>("welcome");
  const [dir, setDir] = useState(1);
  // The sources step is the real sources screen; its code is fetched while the welcome is read.
  useEffect(() => {
    void import("@/pages/sources");
  }, []);
  // The first step is simply there; its own contents ease in (MobileWelcomeStep).
  const entered = useRef(false);
  function goTo(next: Step) {
    entered.current = true;
    setDir(STEPS.indexOf(next) > STEPS.indexOf(step) ? 1 : -1);
    setStep(next);
  }
  useBackHandler(step === "sources", () => goTo("welcome"));

  return (
    <div className="relative h-full overflow-hidden">
      {/* As the app's own page transitions (usePageTransition): only the step arriving moves, the one
          left is gone at once - two screens of text sliding over each other read as a smear. */}
      <motion.div
        key={step}
        initial={entered.current ? { opacity: 0.5, x: dir * 32 } : false}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.24, ease: MOBILE_STEP_EASE }}
        className="absolute inset-0"
      >
        {step === "welcome"
          ? <MobileWelcomeStep intro={!entered.current} onNext={() => goTo("sources")} />
          : <MobileSourcesStep onBack={() => goTo("welcome")} onComplete={onComplete} />}
      </motion.div>
    </div>
  );
}

/** `intro`: the app's very first screen, whose parts ease in; coming Back to it, it only slides. */
function MobileWelcomeStep({ intro, onNext }: { intro: boolean; onNext: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex h-full flex-col px-4 pb-3">
      <div className="flex min-h-0 flex-1 items-center justify-center">
        <motion.div
          initial={intro ? { opacity: 0, scale: 0.92 } : false}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.4, ease: MOBILE_STEP_EASE }}
          className="flex h-28 w-28 items-center justify-center overflow-hidden rounded-full bg-white shadow-[0_10px_30px_rgba(0,0,0,0.5)]"
        >
          <img src={appIcon} alt="" className="h-[4.5rem] w-[4.5rem] object-contain" />
        </motion.div>
      </div>
      {/* Everything to read and the one thing to press sit low, where the thumb already is. */}
      <motion.div
        initial={intro ? { opacity: 0, y: 18 } : false}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.08, ease: MOBILE_STEP_EASE }}
      >
        <div className="mb-2 text-[10.5px] font-bold uppercase tracking-[.14em] text-accent-text">{t("onboarding.welcome.kicker")}</div>
        <h1 className="text-[44px] font-bold leading-none tracking-[-.04em] text-text">hibiki</h1>
        <p className="mt-3 text-[15px] leading-relaxed text-text/75">{t("onboarding.welcome.lead")}</p>
        <p className="mt-4 flex gap-2.5 text-[12.5px] leading-snug text-muted">
          <Info className="mt-px h-4 w-4 shrink-0" strokeWidth={2} />
          {t("onboarding.welcome.note")}
        </p>
        <button
          type="button"
          onClick={onNext}
          className="mt-6 flex h-[3.25rem] w-full items-center justify-center rounded-full bg-accent-solid text-[15px] font-bold text-accent-solid-fg shadow-[0_10px_30px_rgba(0,0,0,0.5)] active:scale-[0.98]"
        >
          {t("onboarding.getStarted")}
        </button>
      </motion.div>
    </div>
  );
}

function MobileSourcesStep({ onBack, onComplete }: { onBack: () => void; onComplete: () => void }) {
  const { t } = useTranslation();
  // The same query the sources screen fills in on every install, so the button follows it at once.
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const ready = (sources.data?.length ?? 0) > 0;
  return (
    <div className="relative h-full">
      {/* Room at the bottom so the last source can scroll clear of the pinned button. */}
      <div className="h-full overflow-y-auto pb-[calc(3.25rem+1.5rem)]">
        <div className="px-4 pt-2">
          <button
            type="button"
            aria-label={t("onboarding.back")}
            onClick={onBack}
            className="-ml-2 flex h-10 w-10 items-center justify-center rounded-full text-text active:bg-text/[.08]"
          >
            <ArrowLeft className="h-[22px] w-[22px]" strokeWidth={2.25} />
          </button>
          <h1 className="mt-2 text-[28px] font-bold leading-[1.08] tracking-[-.03em] text-text">{t("onboarding.sources.title")}</h1>
          <p className="mt-2 text-sm leading-relaxed text-muted">{t("onboarding.sources.mobileDescription")}</p>
        </div>
        <div className="mt-5">
          <Suspense fallback={null}><SourcesPage embedded /></Suspense>
        </div>
      </div>
      {/* Not a way out before there is anything to watch, but not a locked door either: with no source
          yet it only steps aside, quietly; the first install turns it into the way in. */}
      <button
        type="button"
        onClick={onComplete}
        className={cn(
          "absolute inset-x-3 bottom-3 flex h-[3.25rem] items-center justify-center rounded-full text-[15px] font-bold shadow-[0_10px_30px_rgba(0,0,0,0.5)] transition-colors duration-300 active:scale-[0.98]",
          ready ? "bg-accent-solid text-accent-solid-fg" : "border border-border bg-app-popover text-muted",
        )}
      >
        {ready ? t("onboarding.continue") : t("onboarding.skip")}
      </button>
    </div>
  );
}
