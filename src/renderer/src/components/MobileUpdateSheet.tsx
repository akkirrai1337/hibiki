import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { ArrowDownToLine, ExternalLink, ShieldCheck, TriangleAlert, WifiOff } from "lucide-react";
import type { AppUpdate } from "@shared/types";
import { BottomSheet } from "@/components/BottomSheet";
import { formatBytes } from "@/components/UpdateButton";
import { dismissedVersion, dismissVersion, useAppUpdate, useUpdateFlow, type UpdatePhase } from "@/lib/appUpdate";
import { cn } from "@/lib/cn";
import { hibiki } from "@/lib/hibiki";

const PRIMARY = "flex h-[3.25rem] w-full items-center justify-center gap-2 rounded-full bg-accent-solid text-[15px] font-bold text-accent-solid-fg active:scale-[0.98] disabled:opacity-60";
const SECONDARY = "flex h-11 w-full items-center justify-center rounded-full text-[14px] font-semibold text-muted active:bg-text/[.06]";

/**
 * The phone's update: a sheet that offers a newer release once per launch (until the person says
 * "later" to that version), and that settings reopens. The download keeps going if the sheet is
 * pulled away; what failed is told by its cause, each with the one thing that can be done about it.
 */
export function MobileUpdateSheet({ suppressed }: { suppressed: boolean }) {
  const update = useAppUpdate();
  const sheetOpen = useUpdateFlow((s) => s.sheetOpen);
  const openSheet = useUpdateFlow((s) => s.openSheet);
  const closeSheet = useUpdateFlow((s) => s.closeSheet);
  const phase = useUpdateFlow((s) => s.phase);

  // Offered on its own once per launch, a moment after the app has settled - not over the player,
  // and not again for a version the person already put off.
  const [offered, setOffered] = useState(false);
  useEffect(() => {
    if (!update || offered || suppressed || dismissedVersion() === update.version) return;
    const timer = window.setTimeout(() => {
      setOffered(true);
      openSheet();
    }, 1200);
    return () => window.clearTimeout(timer);
  }, [update, offered, suppressed, openSheet]);

  if (!update) return null;

  const close = () => {
    // Closing is "later" for this version - also after a failure, which would only fail the same way
    // on the next launch (a test build's signature). A running download is not put off: it goes on,
    // and settings leads back to it.
    if (phase.kind !== "downloading") dismissVersion(update.version);
    closeSheet();
  };

  return (
    <BottomSheet open={sheetOpen && !suppressed} onClose={close} footer={<Footer update={update} phase={phase} onLater={close} />}>
      <SheetBody update={update} phase={phase} />
    </BottomSheet>
  );
}

function SheetBody({ update, phase }: { update: AppUpdate; phase: UpdatePhase }) {
  const { t } = useTranslation();
  const current = useQuery({ queryKey: ["appVersion"], queryFn: () => hibiki.app.getVersion(), staleTime: Infinity }).data;

  return (
    <div className="px-5 pb-4 pt-1">
      <div className="flex items-center gap-3.5">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-emerald-500/15 text-emerald-400">
          <ArrowDownToLine className="h-[22px] w-[22px]" strokeWidth={2.25} />
        </span>
        <div className="min-w-0">
          <p className="text-[18px] font-bold leading-tight tracking-[-.01em] text-text">{t("update.dialogTitle")}</p>
          <p className="mt-0.5 text-[13px] tabular-nums text-muted">
            {current
              ? t("update.versionChange", { from: current, to: update.version, size: formatBytes(update.sizeBytes) })
              : t("update.versionLine", { version: update.version, size: formatBytes(update.sizeBytes) })}
          </p>
        </div>
      </div>

      <PhaseNotice update={update} phase={phase} />

      <p className="mt-5 text-[10.5px] font-bold uppercase tracking-[.14em] text-muted">{t("update.whatsNew")}</p>
      <ReleaseNotes text={update.notes} fallback={t("update.noNotes")} />
      {/* The footer's main button is already the release page then. */}
      {!releasePageIsTheWay(phase) && <button
        type="button"
        onClick={() => void hibiki.updates.openRelease(update.releaseUrl)}
        className="mt-3 flex items-center gap-1.5 text-[13px] font-semibold text-muted active:text-text"
      >
        {t("update.releaseNotes")}
        <ExternalLink className="h-3.5 w-3.5" strokeWidth={2} />
      </button>}
    </div>
  );
}

/** What happened, when something did: above the notes, since it is what the person has to act on. */
function PhaseNotice({ phase }: { update: AppUpdate; phase: UpdatePhase }) {
  const { t } = useTranslation();
  // Second time round, the notice says the switch is still off instead of explaining it again.
  const permissionDenied = useUpdateFlow((s) => s.permissionAsked);

  if (phase.kind !== "failed") return null;
  switch (phase.failure) {
    case "permission-needed":
      return <Notice icon={ShieldCheck} tone="neutral" title={t("update.permissionTitle")} text={permissionDenied ? t("update.permissionDenied") : t("update.permissionHint")} />;
    case "download-failed":
      return <Notice icon={WifiOff} tone="bad" title={t("update.downloadFailedTitle")} text={t("update.downloadFailedHint")} />;
    case "signature-mismatch":
      return <Notice icon={TriangleAlert} tone="bad" title={t("update.signatureTitle")} text={t("update.signatureHint")} />;
    default:
      return <Notice icon={TriangleAlert} tone="bad" title={t("update.badFileTitle")} text={t("update.badFileHint", { code: phase.failure })} />;
  }
}

function Notice({ icon: Icon, tone, title, text }: { icon: typeof TriangleAlert; tone: "good" | "bad" | "neutral"; title: string; text: string }) {
  return (
    <div
      className={cn(
        "mt-4 flex gap-3 rounded-2xl border p-3.5",
        tone === "good" && "border-emerald-400/20 bg-emerald-400/[.07]",
        tone === "bad" && "border-rose-400/20 bg-rose-400/[.07]",
        tone === "neutral" && "border-border bg-text/[.04]",
      )}
    >
      <Icon
        className={cn("mt-0.5 h-[18px] w-[18px] shrink-0", tone === "good" ? "text-emerald-400" : tone === "bad" ? "text-rose-400" : "text-text")}
        strokeWidth={2}
      />
      <div className="min-w-0">
        <p className="text-[14px] font-semibold text-text">{title}</p>
        <p className="mt-0.5 text-[13px] leading-relaxed text-muted">{text}</p>
      </div>
    </div>
  );
}

function Footer({ update, phase, onLater }: { update: AppUpdate; phase: UpdatePhase; onLater: () => void }) {
  const { t } = useTranslation();
  const start = useUpdateFlow((s) => s.start);
  const [asking, setAsking] = useState(false);

  if (phase.kind === "downloading") {
    const percent = phase.total > 0 ? Math.min(100, (phase.received / phase.total) * 100) : 0;
    return (
      <div className="px-1 pb-1 pt-0.5">
        <div className="h-2 w-full overflow-hidden rounded-full bg-text/[.08]">
          <div className="h-full rounded-full bg-accent-solid transition-[width] duration-200" style={{ width: `${percent}%` }} />
        </div>
        <div className="mt-2.5 flex items-baseline justify-between gap-3 text-[13px] tabular-nums">
          <span className="font-semibold text-text">{t("update.downloadingMobile", { received: formatBytes(phase.received), total: formatBytes(phase.total) })}</span>
          <span className="text-muted">{Math.floor(percent)}%</span>
        </div>
        <p className="mt-1.5 text-[12px] text-muted">{t("update.backgroundHint")}</p>
      </div>
    );
  }

  if (phase.kind === "failed" && phase.failure === "permission-needed") {
    const allow = async () => {
      setAsking(true);
      useUpdateFlow.setState({ permissionAsked: true });
      try {
        const granted = (await hibiki.updates.installPermission?.request()) ?? false;
        // Granted: carry on where it stopped - the package is already on disk, so this only checks
        // it again and opens the installer.
        if (granted) await start(update);
      } finally {
        setAsking(false);
      }
    };
    return (
      <div className="flex flex-col gap-1">
        <button type="button" disabled={asking} onClick={() => void allow()} className={PRIMARY}>{t("update.permissionAction")}</button>
        <button type="button" onClick={onLater} className={SECONDARY}>{t("update.later")}</button>
      </div>
    );
  }

  if (releasePageIsTheWay(phase)) {
    // Nothing a retry changes: the way forward is the release page.
    return (
      <div className="flex flex-col gap-1">
        <button type="button" onClick={() => void hibiki.updates.openRelease(update.releaseUrl)} className={PRIMARY}>
          <ExternalLink className="h-[18px] w-[18px]" strokeWidth={2.25} />
          {t("update.openRelease")}
        </button>
        <button type="button" onClick={onLater} className={SECONDARY}>{t("update.close")}</button>
      </div>
    );
  }

  // After the installer was opened (and maybe backed out of), the same button opens it again: the
  // package is already on disk, so nothing is downloaded twice.
  const label = phase.kind === "installer" ? t("update.installNow") : phase.kind === "failed" ? t("update.retry") : t("update.install");
  return (
    <div className="flex flex-col gap-1">
      <button type="button" onClick={() => void start(update)} className={PRIMARY}>{label}</button>
      <button type="button" onClick={onLater} className={SECONDARY}>{phase.kind === "idle" ? t("update.later") : t("update.close")}</button>
    </div>
  );
}

/** A failure no retry changes - a package that is not this app's, or not signed like it. */
function releasePageIsTheWay(phase: UpdatePhase): boolean {
  return phase.kind === "failed" && phase.failure !== "download-failed" && phase.failure !== "permission-needed";
}

/**
 * A release body is GitHub markdown; the sheet shows it as text - headings, bullets and paragraphs -
 * with the link and emphasis syntax taken out rather than shown raw.
 */
function ReleaseNotes({ text, fallback }: { text: string; fallback: string }) {
  const lines = text
    .replace(/\r\n/g, "\n")
    .split("\n")
    .map((line) => line.replace(/!\[[^\]]*]\([^)]*\)/g, "").replace(/\[([^\]]+)]\([^)]*\)/g, "$1").replace(/(\*\*|__|`)/g, "").trimEnd())
    .filter((line, index, all) => line.trim() !== "" || (index > 0 && all[index - 1].trim() !== ""));

  if (lines.every((line) => line.trim() === "")) return <p className="mt-2 text-[14px] leading-relaxed text-muted">{fallback}</p>;

  return (
    <div className="mt-2 select-text space-y-1.5 text-[14px] leading-relaxed text-text/85">
      {lines.map((line, index) => {
        const heading = /^#{1,6}\s+(.*)$/.exec(line);
        if (heading) return <p key={index} className="pt-1.5 text-[14px] font-bold text-text">{heading[1]}</p>;
        const bullet = /^\s*[-*+]\s+(.*)$/.exec(line);
        if (bullet) {
          return (
            <p key={index} className="flex gap-2.5">
              <span className="mt-[0.6em] h-1 w-1 shrink-0 rounded-full bg-text/50" />
              <span className="min-w-0">{bullet[1]}</span>
            </p>
          );
        }
        if (line.trim() === "") return <div key={index} className="h-1" />;
        return <p key={index}>{line}</p>;
      })}
    </div>
  );
}
