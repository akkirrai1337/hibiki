import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence } from "motion/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Laptop, Loader2, RefreshCw, Smartphone, Trash2, Wifi } from "lucide-react";
import type { SyncCandidate, SyncDevice, SyncPairMode } from "@shared/types";
import { MissingSourcesNotice } from "@/components/MissingSourcesPrompt";
import { BottomSheet } from "@/components/BottomSheet";
import { Modal } from "@/components/Modal";
import { cn } from "@/lib/cn";
import { hibiki } from "@/lib/hibiki";
import { isMobile } from "@/lib/mobile";

const DEVICES_KEY = ["syncDevices"];
// What a sync can bring in: everything that shows the library, progress, ratings or xp.
const DATA_KEYS = ["library", "recent-progress", "progress-all", "progress", "dailyActivity", "rating", "xpEvents", "recommendations", "missingSources"];

/**
 * Keeps the screens current when a sync brings data in, on either device - mounted once at the root.
 * Renders nothing.
 */
export function SyncListener() {
  const queryClient = useQueryClient();
  useEffect(() => hibiki.sync.onChanged((what) => {
    if (what === "devices") void queryClient.invalidateQueries({ queryKey: DEVICES_KEY });
    else for (const key of DATA_KEYS) void queryClient.invalidateQueries({ queryKey: [key] });
  }), [queryClient]);
  return null;
}

function useRelativeTime() {
  const { t } = useTranslation();
  return (at: number | null) => {
    if (!at) return t("deviceSync.never");
    const minutes = Math.round((Date.now() - at) / 60_000);
    if (minutes < 1) return t("deviceSync.justNow");
    if (minutes < 60) return t("deviceSync.minutesAgo", { count: minutes });
    const hours = Math.round(minutes / 60);
    if (hours < 24) return t("deviceSync.hoursAgo", { count: hours });
    return new Date(at).toLocaleDateString();
  };
}

/**
 * Settings > Sync: the library, progress, ratings and xp shared between devices on the same network -
 * phone and computer, two computers, or two phones (moving to a new one). One device shows a code,
 * the other finds it and takes the code; after that the one that took it keeps them in step.
 */
export function DeviceSyncSection() {
  const { t } = useTranslation();
  const devices = useQuery({ queryKey: DEVICES_KEY, queryFn: () => hibiki.sync.devices() });
  const list = devices.data ?? [];

  const devicesAndActions = (
    <>
      {list.length > 0 && (
        <div className="mt-3 space-y-1.5">
          {list.map((device) => <DeviceRow key={device.deviceId} device={device} />)}
        </div>
      )}

      {/* Phone: the way in first, full width; showing a code for another phone is the rarer case. */}
      <div className={cn("mt-3", isMobile ? "space-y-2" : "flex flex-wrap items-start gap-2")}>
        {isMobile && <ConnectToDevice paired={list.some((device) => device.connects)} />}
        <ShowCode />
        {!isMobile && <ConnectToDevice paired={list.some((device) => device.connects)} />}
      </div>
    </>
  );

  // The same card as the settings rows beside it (settings.tsx's SettingsRow): the icon in its own
  // column, everything else lined up under the title - on a phone the list and buttons take the
  // card's whole width instead.
  return (
    <section className="rounded-2xl border border-border bg-text/[.03] p-4">
      <div className="flex items-start gap-3">
        <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-text/[.06] text-muted">
          <Wifi className="h-[18px] w-[18px]" strokeWidth={2} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-text">{t("deviceSync.title")}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-muted">{t(isMobile ? "deviceSync.hintPhone" : "deviceSync.hint")}</p>
          {!isMobile && devicesAndActions}
          {!isMobile && <MissingSourcesNotice />}
        </div>
      </div>
      {isMobile && devicesAndActions}
      {isMobile && <MissingSourcesNotice />}
    </section>
  );
}

function DeviceRow({ device }: { device: SyncDevice }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const relative = useRelativeTime();
  const [confirming, setConfirming] = useState(false);
  const sync = useMutation({ mutationFn: () => hibiki.sync.syncNow!() });
  const remove = useMutation({
    mutationFn: () => hibiki.sync.remove(device.deviceId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DEVICES_KEY }),
  });
  const failed = sync.error instanceof Error ? sync.error.message : null;

  return (
    <div className="rounded-xl bg-text/[.04] px-3 py-2.5">
      <div className="flex items-center gap-2.5">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-text">{device.name}</p>
          <p className="text-xs text-muted">{t("deviceSync.lastSync", { when: relative(device.lastSyncAt) })}</p>
        </div>
        {/* Only where this device is the one that syncs; the other side does it otherwise. */}
        {device.connects && (
          <button
            type="button"
            onClick={() => sync.mutate()}
            disabled={sync.isPending}
            aria-label={t("deviceSync.syncNow")}
            title={t("deviceSync.syncNow")}
            className="flex h-8 w-8 items-center justify-center rounded-full text-muted transition-colors hover:bg-text/[.08] hover:text-text disabled:opacity-60"
          >
            <RefreshCw className={cn("h-4 w-4", sync.isPending && "animate-spin")} strokeWidth={2} />
          </button>
        )}
        <button
          type="button"
          onClick={() => setConfirming((value) => !value)}
          aria-label={t("deviceSync.forget")}
          title={t("deviceSync.forget")}
          className="flex h-8 w-8 items-center justify-center rounded-full text-muted transition-colors hover:bg-text/[.08] hover:text-rose-500"
        >
          <Trash2 className="h-4 w-4" strokeWidth={2} />
        </button>
      </div>
      {failed && <p className="mt-1.5 text-xs leading-relaxed text-rose-500">{syncErrorText(failed, t)}</p>}
      {confirming && (
        <div className="mt-2 flex items-center gap-2">
          <p className="min-w-0 flex-1 text-xs leading-relaxed text-muted">{t("deviceSync.forgetConfirm")}</p>
          <button type="button" onClick={() => remove.mutate()} disabled={remove.isPending} className="shrink-0 rounded-lg bg-rose-500 px-3 py-1.5 text-xs font-bold text-white disabled:opacity-60 mobile:rounded-full">
            {t("deviceSync.forgetAction")}
          </button>
        </div>
      )}
    </div>
  );
}

function syncErrorText(message: string, t: (key: string) => string): string {
  // Across Electron's IPC the code arrives at the end of a longer message.
  const code = /([a-z-]+)\s*$/.exec(message)?.[1] ?? message;
  switch (code) {
    case "unreachable": return t("deviceSync.error.unreachable");
    case "unknown-device": return t("deviceSync.error.unknownDevice");
    case "bad-code": return t("deviceSync.error.badCode");
    case "not-pairing": return t("deviceSync.error.notPairing");
    case "too-many-attempts": return t("deviceSync.error.tooMany");
    case "version": return t("deviceSync.error.version");
    default: return t("deviceSync.error.generic");
  }
}

/** Open a pairing window and show its code until the other device takes it (or it runs out). */
function ShowCode() {
  const { t } = useTranslation();
  const [pairing, setPairing] = useState<{ code: string; expiresAt: number } | null>(null);
  const devices = useQuery({ queryKey: DEVICES_KEY, queryFn: () => hibiki.sync.devices() });
  const [countAtStart, setCountAtStart] = useState(0);

  // The other device paired: the list grew, and the window closed itself on this side.
  useEffect(() => {
    if (pairing && (devices.data?.length ?? 0) > countAtStart) setPairing(null);
  }, [devices.data, pairing, countAtStart]);
  useEffect(() => {
    if (!pairing) return;
    const timer = window.setTimeout(() => setPairing(null), Math.max(0, pairing.expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [pairing]);
  useEffect(() => () => void hibiki.sync.stopPairing?.(), []);

  const close = () => {
    void hibiki.sync.stopPairing?.();
    setPairing(null);
  };
  const body = pairing && (
    <div>
      <p className="text-[13px] leading-relaxed text-muted">{t(isMobile ? "deviceSync.enterOnOtherPhone" : "deviceSync.enterOnPhone")}</p>
      <p className="mt-4 select-text text-center font-mono text-[36px] font-bold tracking-[.3em] text-text">{pairing.code.slice(0, 3)} {pairing.code.slice(3)}</p>
      <p className="mt-4 flex items-center justify-center gap-2 text-xs text-muted">
        <Loader2 className="h-3.5 w-3.5 animate-spin" strokeWidth={2} />
        {t("deviceSync.waiting")}
      </p>
    </div>
  );

  return (
    <>
      <button
        type="button"
        onClick={async () => {
          setCountAtStart(devices.data?.length ?? 0);
          setPairing(await hibiki.sync.startPairing!());
        }}
        className={isMobile
          ? "flex h-10 w-full items-center justify-center rounded-full text-sm font-semibold text-muted active:bg-text/[.06]"
          : "rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14]"}
      >
        {t(isMobile ? "deviceSync.showCodePhone" : "deviceSync.connectPhone")}
      </button>
      {/* Its own window rather than a block growing inside the settings: the code is the one thing
          to look at while the other device takes it, and closing the window ends the pairing. */}
      {isMobile ? (
        <BottomSheet open={!!pairing} onClose={close} title={t("deviceSync.codeTitle")}>
          <div className="px-5 pb-6">{body}</div>
        </BottomSheet>
      ) : createPortal(
        <AnimatePresence>
          {pairing && (
            <Modal onDismiss={close}>
              <h2 className="text-base font-bold text-text">{t("deviceSync.codeTitle")}</h2>
              <div className="mt-3">{body}</div>
            </Modal>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </>
  );
}

/** Find a device on the network that shows a code, then take the code. */
function ConnectToDevice({ paired }: { paired: boolean }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  if (!isMobile) {
    return (
      <>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="rounded-lg px-3 py-1.5 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06] hover:text-text"
        >
          {t("deviceSync.connectComputer")}
        </button>
        {createPortal(
          <AnimatePresence>
            {open && (
              <Modal onDismiss={() => setOpen(false)}>
                <h2 className="text-base font-bold text-text">{t("deviceSync.connectComputer")}</h2>
                <div className="mt-3"><PairFlow onDone={() => setOpen(false)} /></div>
              </Modal>
            )}
          </AnimatePresence>,
          document.body,
        )}
      </>
    );
  }
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={cn(
          "flex h-11 w-full items-center justify-center gap-2 rounded-full text-sm font-bold",
          paired ? "bg-text/[.08] text-text" : "bg-accent-solid text-accent-solid-fg",
        )}
      >
        <Wifi className="h-4 w-4" strokeWidth={2.25} />
        {t(paired ? "deviceSync.connectAnother" : "deviceSync.connectComputer")}
      </button>
      <BottomSheet open={open} onClose={() => setOpen(false)} title={t("deviceSync.connectComputer")}>
        <div className="px-5 pb-5">{open && <PairFlow onDone={() => setOpen(false)} />}</div>
      </BottomSheet>
    </>
  );
}

function PairFlow({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [target, setTarget] = useState<SyncCandidate | null>(null);
  const [code, setCode] = useState("");
  const [mode, setMode] = useState<SyncPairMode>("merge");
  const found = useQuery({ queryKey: ["syncDiscover"], queryFn: () => hibiki.sync.discover!(), staleTime: 0, gcTime: 0 });
  const pair = useMutation({
    mutationFn: () => hibiki.sync.pair!(target!, code, mode),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: DEVICES_KEY });
      onDone();
    },
  });

  if (!target) {
    const list = found.data ?? [];
    return (
      <div>
        <p className="text-[13px] leading-relaxed text-muted">{t("deviceSync.findHint")}</p>
        <div className="mt-3 space-y-1.5">
          {found.isFetching && list.length === 0 && (
            <p className="flex items-center gap-2 py-2 text-sm text-muted"><Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />{t("deviceSync.searching")}</p>
          )}
          {list.map((candidate) => (
            <button
              key={candidate.deviceId}
              type="button"
              onClick={() => setTarget(candidate)}
              className="flex w-full items-center gap-3 rounded-xl bg-text/[.05] px-3.5 py-3 text-left active:bg-text/[.09]"
            >
              {candidate.kind === "phone"
                ? <Smartphone className="h-[18px] w-[18px] shrink-0 text-text" strokeWidth={2} />
                : <Laptop className="h-[18px] w-[18px] shrink-0 text-text" strokeWidth={2} />}
              <span className="min-w-0 flex-1 truncate text-[15px] font-medium text-text">{candidate.name}</span>
              <span className="text-xs text-muted">{candidate.host}</span>
            </button>
          ))}
          {!found.isFetching && list.length === 0 && (
            <p className="py-2 text-sm leading-relaxed text-muted">{found.isError ? t("deviceSync.error.generic") : t("deviceSync.notFound")}</p>
          )}
        </div>
        <button
          type="button"
          onClick={() => void found.refetch()}
          disabled={found.isFetching}
          className="mt-3 flex h-10 w-full items-center justify-center gap-2 rounded-full text-sm font-semibold text-muted active:bg-text/[.06] disabled:opacity-60"
        >
          <RefreshCw className={cn("h-4 w-4", found.isFetching && "animate-spin")} strokeWidth={2} />
          {t("deviceSync.searchAgain")}
        </button>
      </div>
    );
  }

  const error = pair.error instanceof Error ? pair.error.message : null;
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        if (code.length === 6) pair.mutate();
      }}
    >
      <p className="text-[13px] leading-relaxed text-muted">{t("deviceSync.enterCode", { name: target.name })}</p>
      <input
        autoFocus
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        value={code}
        onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
        className="mt-3 w-full rounded-xl border border-border bg-text/[.04] px-4 py-3 text-center font-mono text-[28px] font-bold tracking-[.4em] text-text outline-none focus:border-text/30"
        placeholder="••••••"
      />
      {error && <p className="mt-2 text-xs leading-relaxed text-rose-500">{syncErrorText(error, t)}</p>}
      <PairModePicker mode={mode} onChange={setMode} otherName={target.name} />
      <button
        type="submit"
        disabled={code.length !== 6 || pair.isPending}
        className="mt-4 flex h-[3.25rem] w-full items-center justify-center gap-2 rounded-full bg-accent-solid text-[15px] font-bold text-accent-solid-fg disabled:opacity-50"
      >
        {pair.isPending && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2.25} />}
        {t(mode === "merge" ? "deviceSync.pair" : "deviceSync.pairReplace")}
      </button>
      <button type="button" onClick={() => { setTarget(null); setCode(""); pair.reset(); }} className="mt-1 h-10 w-full text-sm font-semibold text-muted">
        {t("deviceSync.back")}
      </button>
    </form>
  );
}

/** Whose data stays: both, joined - or one device's, replacing the other's entirely. */
function PairModePicker({ mode, onChange, otherName }: { mode: SyncPairMode; onChange: (mode: SyncPairMode) => void; otherName: string }) {
  const { t } = useTranslation();
  const options: Array<{ value: SyncPairMode; title: string; hint: string }> = [
    { value: "merge", title: t("deviceSync.mode.merge"), hint: t("deviceSync.mode.mergeHint") },
    { value: "keep-here", title: t("deviceSync.mode.keepHere"), hint: t("deviceSync.mode.keepHereHint", { name: otherName }) },
    { value: "take-there", title: t("deviceSync.mode.takeThere", { name: otherName }), hint: t("deviceSync.mode.takeThereHint", { name: otherName }) },
  ];
  return (
    <fieldset className="mt-4">
      <legend className="text-[13px] font-semibold text-text">{t("deviceSync.mode.title")}</legend>
      <div className="mt-2 space-y-1.5" role="radiogroup">
        {options.map((option) => {
          const selected = option.value === mode;
          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => onChange(option.value)}
              className={cn(
                "flex w-full items-start gap-3 rounded-xl border px-3.5 py-2.5 text-left transition-colors",
                selected ? "border-accent/60 bg-accent/[.06]" : "border-border bg-text/[.03] hover:bg-text/[.06]",
              )}
            >
              <span className={cn("mt-[3px] flex h-4 w-4 shrink-0 items-center justify-center rounded-full border-2", selected ? "border-accent" : "border-text/30")}>
                {selected && <span className="h-1.5 w-1.5 rounded-full bg-accent" />}
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-text">{option.title}</span>
                <span className="mt-0.5 block text-xs leading-relaxed text-muted">{option.hint}</span>
              </span>
            </button>
          );
        })}
      </div>
      {mode !== "merge" && <p className="mt-2 text-xs leading-relaxed text-rose-500">{t("deviceSync.mode.warning")}</p>}
    </fieldset>
  );
}
