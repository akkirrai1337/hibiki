import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { Bell, FastForward, Gauge, HardDrive, BarChart3, Download, Globe, ShieldCheck, ShieldOff, Smartphone, Wifi, LogIn, LogOut, MemoryStick, ArrowDownToLine, ArrowUpDown, Ban, Check, CheckCircle2, ChevronDown, ChevronRight, DatabaseBackup, FileText, FolderOpen, Home, Info, Languages, MessageCircle, MonitorPlay, Moon, Palette, Radio, RefreshCw, RotateCcw, ScrollText, SlidersHorizontal, Sparkles, Sun, Timer, TriangleAlert } from "lucide-react";
import { cn } from "@/lib/cn";
import { isMobile, useBackHandler } from "@/lib/mobile";
import { motion } from "motion/react";
import { MobilePageHeader } from "@/components/MobilePageHeader";
import { BottomSheet, SheetOption } from "@/components/BottomSheet";
import { Switch } from "@/components/Switch";
import { SUPPORTED_LOCALES, setLocale } from "@/lib/i18n";
import { SKIP_TIMER_MAX_SECONDS, SKIP_TIMER_MIN_SECONDS, WATCHED_THRESHOLD_MAX_PERCENT, WATCHED_THRESHOLD_MIN_PERCENT, usePlayerPrefsStore, type DataSaverMode } from "@/stores/playerPrefsStore";
import { useAutoQualityCap } from "@/lib/connectionQuality";
import { isWebGpuAvailable, type UpscaleMode } from "@/features/player/upscaler";
import { useUiStore } from "@/stores/uiStore";
import { ACCENT_PRESETS, BACKGROUND_THEME_PRESETS, CUSTOM_BACKGROUND_THEME_ID, customBackgroundGradientCss, DEFAULT_ACCENT, DEFAULT_MOBILE_ACCENT, DEFAULT_MOBILE_ACCENT_LIGHT } from "@/lib/theme";
import { sortLabel } from "@/lib/catalogSort";
import { SelectDropdown } from "@/components/SelectDropdown";
import { hibiki, type LogEntry } from "@/lib/hibiki";
import { useAppUpdate, useUpdateFlow } from "@/lib/appUpdate";
import { AniListLogo } from "@/components/AniListLogo";
import { DeviceSyncSection } from "@/components/DeviceSync";
import { TRACKING_KEY, useTrackerAccount } from "@/lib/tracking";
import type { TrackerImportProgress, TrackerImportReport } from "@shared/types";
import type { MemorySnapshot } from "@shared/types";
import { forgetAllPersisted, persistedSize, useFilterCatalog } from "@/lib/persistedQuery";
import { BUFFER_SECONDS, useNetworkPrefsStore, type BufferMode, type HomeRefreshMode } from "@/stores/networkPrefsStore";
import { videoCache } from "@/lib/videoCache";
import { useNewEpisodesPrefs } from "@/lib/newEpisodes";
import { resetTrafficStats, useTrafficHistory } from "@/lib/trafficStats";
import { TrafficChart, formatBytes } from "@/components/TrafficChart";
import type { DohProvider } from "@shared/types";


// One category's worth of rows, spaced apart - no heading of its own: the category rail's own
// label already names it, and repeating that text here just duplicated it right above the content
// it labels.
function SettingsSection({ children }: { children: React.ReactNode }) {
  return (
    <section className="mb-6 last:mb-0">
      <div className="space-y-3">{children}</div>
    </section>
  );
}

// Every row is its own rounded card, icon-led - same visual unit as a source/extension row elsewhere
// in the app, instead of a bare list item divided from its neighbors by a hairline.
function SettingsRow({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-2xl border border-border bg-text/[.03] p-4">
      <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-text/[.06] text-muted">{icon}</span>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

// A single bordered card holding several plain on/off settings as compact divided lines instead of
// each one being its own full SettingsRow card - a page-long run of nothing but a switch (Discord,
// autoload, auto-update, ...) read as an Android checkbox list once there were enough of them,
// all that repeated card padding/border adding bulk without adding information density.
function SettingsGroup({ children }: { children: React.ReactNode }) {
  return <div className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-text/[.03]">{children}</div>;
}

// One line inside a SettingsGroup - `indent` drops the icon and pushes the text in instead, for a
// toggle that only makes sense as a sub-setting of the one above it (Discord's NSFW toggle under
// Discord itself), so it reads as nested without needing its own icon slot.
function SettingsToggleRow({ icon, title, hint, checked, onChange, indent }: { icon?: React.ReactNode; title: string; hint: string; checked: boolean; onChange: (value: boolean) => void; indent?: boolean }) {
  return (
    <div className={cn("flex items-center gap-3 px-4 py-3", indent ? "pl-[52px]" : "")}>
      {!indent && <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-text/[.06] text-muted">{icon}</span>}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-text">{title}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-muted">{hint}</p>
      </div>
      <Switch checked={checked} onChange={onChange} />
    </div>
  );
}

// Same stepper look as SearchFiltersPanel's YearNumberInput (no-spinner input + a stacked
// up/down chevron pair, instead of the browser's own spinner arrows) plus a slider for the same
// value - kept in sync via local text state so a mid-edit "5" or an empty field while typing "12"
// doesn't get clobbered back to the last committed number on every keystroke, only committed
// (blur/Enter/stepper click) once it parses to a number in range.
function SecondsControl({ label, hint, value, onChange }: { label: string; hint: string; value: number; onChange: (seconds: number) => void }) {
  const { t } = useTranslation();
  return <SliderControl label={label} hint={hint} value={value} min={SKIP_TIMER_MIN_SECONDS} max={SKIP_TIMER_MAX_SECONDS} display={t("common.secondsShort", { count: value })} onChange={onChange} />;
}

function PercentControl({ label, hint, value, onChange }: { label: string; hint: string; value: number; onChange: (percent: number) => void }) {
  return <SliderControl label={label} hint={hint} value={value} min={WATCHED_THRESHOLD_MIN_PERCENT} max={WATCHED_THRESHOLD_MAX_PERCENT} display={`${value}%`} onChange={onChange} />;
}

function ThemeOption({ active, icon: Icon, label, onClick }: { active: boolean; icon: typeof Sun; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-semibold transition-colors",
        active ? "bg-accent text-accent-fg" : "text-muted hover:text-text",
      )}
    >
      <Icon className="h-3.5 w-3.5" strokeWidth={2} />
      {label}
    </button>
  );
}

// One round swatch per preset, Discord-picker style - the active one gets a checkmark instead of
// just a ring, since a ring around a small circle this size reads ambiguously (is it hover, focus,
// or "selected"?) while a checkmark can only ever mean one thing.
function AccentSwatch({ color, active, onClick }: { color: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={color}
      style={{ backgroundColor: color }}
      className={cn(
        "flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-border transition-transform hover:scale-110",
        active && "ring-2 ring-offset-2 ring-offset-bg",
      )}
    >
      {active && <Check className="h-3.5 w-3.5 text-white drop-shadow" strokeWidth={3} style={{ color: accentForegroundColor(color) }} />}
    </button>
  );
}

// Plain black/white (not the app's own accent-fg CSS var, which only tracks the *currently
// applied* accent) - each swatch needs its own checkmark contrast computed against its own color,
// including the seven presets that aren't the active accent at all.
function accentForegroundColor(hex: string): string {
  const int = Number.parseInt(hex.slice(1), 16);
  const r = (int >> 16) & 255, g = (int >> 8) & 255, b = int & 255;
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  return luminance > 140 ? "#1d0c15" : "#ffffff";
}

// A square swatch (not round, unlike AccentSwatch) - a gradient reads better filling a bigger flat
// area than it does packed into a small circle, and the shape difference also makes "this row
// picks a whole background theme" visually distinct from "that row above picks one accent color".
// The `null` (no theme) option renders as a plain checkerboard-free empty square with a label
// instead of a swatch of its own, since there's no single color/gradient standing in for "off".
function BackgroundThemeSwatch({ gradient, active, onClick, label, icon: Icon = Ban }: { gradient?: string; active: boolean; onClick: () => void; label?: string; icon?: typeof Ban }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      style={gradient ? { backgroundImage: gradient } : undefined}
      className={cn(
        "flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border transition-transform hover:scale-105",
        gradient ? "border-transparent" : "border-dashed border-border bg-text/[.04] text-muted",
        active && "ring-2 ring-accent ring-offset-2 ring-offset-bg",
      )}
    >
      {!gradient && <Icon className="h-3.5 w-3.5" strokeWidth={2} />}
      {active && gradient && <Check className="h-4 w-4 text-white drop-shadow" strokeWidth={3} />}
    </button>
  );
}

// Same overlay-input trick as CustomAccentInput below - a real native color <input>, invisible and
// on top of the swatch it visually stands in for, kept as its own component only because it also
// needs a `label` for the swatch's `title` and CustomAccentInput doesn't.
function GradientStopSwatch({ value, onCommit, label }: { value: string; onCommit: (color: string) => void; label: string }) {
  const inputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (inputRef.current) inputRef.current.value = value;
  }, [value]);
  return (
    <label
      className="relative flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full border border-border transition-transform hover:scale-110"
      style={{ backgroundColor: value }}
      title={label}
    >
      <input
        ref={inputRef}
        type="color"
        defaultValue={value}
        onChange={(e) => onCommit(e.target.value)}
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
      />
    </label>
  );
}

// Used to stream every drag-time `input` tick straight into --color-accent for a live preview
// (see GradientStopSwatch's own comment on why that mattered for a color like this one, applied
// broadly across the whole UI, but not for a single gradient stop). Dropped: the OS color-picker
// popup's own drag cursor turned laggy and stuttery under that traffic, because every tick forced
// a style recalculation across every accent-colored element in the app on top of whatever the
// picker popup itself was already doing to track the pointer. Only the final color, from the
// native `change` event that fires once the picker closes, is worth the cost now.
function CustomAccentInput({ value, onCommit }: { value: string; onCommit: (color: string) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);

  // Keeps the swatch in sync when the color changes from elsewhere (a preset click) without ever
  // recreating the element - a stable element (not `key={value}`) matters here because the OS/
  // Chromium picker popup can still be open when a commit happens (a preset click while it's open,
  // or the picker itself firing an intermediate `change`), and remounting would leave it bound to
  // a destroyed DOM node.
  useEffect(() => {
    if (inputRef.current) inputRef.current.value = value;
  }, [value]);

  return (
    <input
      ref={inputRef}
      type="color"
      defaultValue={value}
      onChange={(e) => onCommit(e.target.value)}
      className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
    />
  );
}

// Same active-item language as Sidebar's own NavLink (left accent bar + a soft fill, see
// components/Sidebar.tsx) - reusing that exact visual instead of inventing a second "selected tab"
// style keeps this category rail reading as the same kind of navigation the rest of the app already
// uses, just scoped to this one page instead of the whole app.
function SettingsCategoryButton({ active, icon: Icon, label, onClick }: { active: boolean; icon: typeof Sun; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "relative flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-[13px] font-medium text-muted transition-colors hover:bg-text/[.05] hover:text-text",
        // Phone: a chip in a row, not a line in a rail.
        "mobile:w-auto mobile:shrink-0 mobile:gap-2 mobile:rounded-full mobile:bg-text/[.05] mobile:px-3.5",
        active && "text-text mobile:text-accent-solid-fg",
      )}
    >
      <span className={cn("absolute -left-3 top-1/2 h-4 w-[3px] -translate-y-1/2 rounded-r-full bg-accent transition-opacity mobile:hidden", active ? "opacity-100" : "opacity-0")} />
      {/* Phone: the picked chip filled with the accent - a faint tint of it was lost on a light page. */}
      {active && <span className="absolute inset-0 rounded-lg bg-text/[.08] mobile:rounded-full mobile:bg-accent-solid" />}
      <span className="relative z-10 shrink-0"><Icon className="h-[17px] w-[17px]" strokeWidth={2} /></span>
      <span className="relative z-10 truncate">{label}</span>
    </button>
  );
}

// Everything zustand's persist middleware could have written for this origin - not a hardcoded
// list of store names, so a future new persisted store is automatically included without anyone
// having to remember to update this. Safe to dump wholesale: nothing else in the app uses
// localStorage for anything but these persisted stores.
function readAllLocalStorage(): Record<string, string> {
  const entries: Record<string, string> = {};
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key !== null) entries[key] = localStorage.getItem(key)!;
  }
  return entries;
}

type BackupStatus = { kind: "success" | "error"; message: string };

// Diagnostics. The failures this app actually gets reported for (an episode that "sometimes"
// won't load, a source that resolves in ten seconds instead of one) are network-shaped and happen
// only on the user's machine - by the time anyone describes them, the evidence is gone. Every
// extension HTTP request, resolver attempt and player error now records a timed line (see
// core/logger.ts and lib/log.ts); this is the button that gets that out of the app and into a
// file worth attaching to a bug report.
function DiagnosticsSection() {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<BackupStatus | null>(null);
  const [preview, setPreview] = useState<LogEntry[]>([]);
  const [expanded, setExpanded] = useState(false);

  // Only while the preview is actually open - a settings page left on screen shouldn't keep
  // pulling the whole log across IPC every two seconds for nobody to look at.
  useEffect(() => {
    if (!expanded) return;
    let cancelled = false;
    const load = () => {
      hibiki.logs.recent(200).then((entries) => { if (!cancelled) setPreview(entries); }).catch(() => {});
    };
    load();
    const timer = setInterval(load, 2000);
    return () => { cancelled = true; clearInterval(timer); };
  }, [expanded]);

  const handleExport = async () => {
    setBusy(true);
    setStatus(null);
    try {
      const filePath = await hibiki.logs.export();
      if (filePath) setStatus({ kind: "success", message: t("settings.diagnostics.exported", { path: filePath }) });
    } catch (error) {
      setStatus({ kind: "error", message: error instanceof Error ? error.message : String(error) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
    <SettingsSection>
      <SettingsRow icon={<ScrollText className="h-[18px] w-[18px]" strokeWidth={2} />}>
        <p className="text-sm font-semibold text-text">{t("settings.diagnostics.export")}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.diagnostics.exportHint")}</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {/* On the phone the export goes to the system share sheet; there is no folder to open. */}
          <button
            onClick={handleExport}
            disabled={busy}
            className="rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14] disabled:opacity-50"
          >
            {t("settings.diagnostics.exportAction")}
          </button>
          {!isMobile && <button
            onClick={() => hibiki.logs.openFolder()}
            className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]"
          >
            <FolderOpen className="h-3.5 w-3.5" strokeWidth={2} />
            {t("settings.diagnostics.openFolder")}
          </button>}
          <button
            onClick={() => setExpanded((value) => !value)}
            className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]"
          >
            <FileText className="h-3.5 w-3.5" strokeWidth={2} />
            {expanded ? t("settings.diagnostics.hide") : t("settings.diagnostics.show")}
          </button>
        </div>

        {expanded && (
          <div className="mt-3 max-h-72 select-text overflow-auto rounded-lg border border-border bg-text/[.03] p-2 font-mono text-[11px] leading-relaxed">
            {preview.length === 0
              ? <p className="text-muted">{t("settings.diagnostics.empty")}</p>
              : preview.map((entry, index) => (
                  <p
                    key={`${entry.time}-${index}`}
                    className={cn(
                      "whitespace-pre-wrap break-all",
                      entry.level === "error" ? "text-rose-500" : entry.level === "warn" ? "text-amber-500" : "text-muted",
                    )}
                  >
                    {new Date(entry.time).toLocaleTimeString()} [{entry.scope}] {entry.message}
                  </p>
                ))}
          </div>
        )}

        {status && (
          <p className={cn("mt-2 select-text text-xs leading-relaxed", status.kind === "error" ? "text-rose-500" : "text-muted")}>
            {status.message}
          </p>
        )}
      </SettingsRow>
    </SettingsSection>
    {/* Electron's own processes and its GPU switch - nothing of the kind to show on the phone. */}
    {!isMobile && <MemoryDiagnostics />}
    </>
  );
}

// Where the app's memory actually sits: one row per Electron process, biggest first. Polled only
// while open, like the log preview above.
function MemoryDiagnostics() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [snapshot, setSnapshot] = useState<MemorySnapshot | null>(null);
  const [recorded, setRecorded] = useState(false);
  // undefined while the answer is on its way; `changed` once flipped, since it only applies after a restart.
  const [acceleration, setAcceleration] = useState<boolean | undefined>(undefined);
  const [changed, setChanged] = useState(false);
  useEffect(() => { hibiki.app.getHardwareAcceleration().then(setAcceleration).catch(() => {}); }, []);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    const load = () => { hibiki.logs.memory().then((next) => { if (!cancelled) setSnapshot(next); }).catch(() => {}); };
    load();
    const timer = setInterval(load, 2000);
    return () => { cancelled = true; clearInterval(timer); };
  }, [open]);

  const processLabel = (process: MemorySnapshot["processes"][number]) => {
    if (process.type === "Browser") return t("settings.diagnostics.memoryMain");
    if (process.type === "Tab") return process.url === "app" ? t("settings.diagnostics.memoryWindow") : t("settings.diagnostics.memoryHidden", { url: process.url ?? "?" });
    if (process.type === "GPU") return "GPU";
    return process.name ?? process.type;
  };

  return (
    <SettingsSection>
      <SettingsRow icon={<MemoryStick className="h-[18px] w-[18px]" strokeWidth={2} />}>
        <p className="text-sm font-semibold text-text">{t("settings.diagnostics.memory")}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.diagnostics.memoryHint")}</p>
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            onClick={() => setOpen((value) => !value)}
            className="rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14]"
          >
            {open ? t("settings.diagnostics.memoryHide") : t("settings.diagnostics.memoryShow")}
          </button>
          {open && (
            <button
              onClick={() => { void hibiki.logs.memory(true).then(() => setRecorded(true)); }}
              className="rounded-lg px-3 py-1.5 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]"
            >
              {recorded ? t("settings.diagnostics.memoryRecorded") : t("settings.diagnostics.memoryRecord")}
            </button>
          )}
        </div>
        {open && snapshot && (
          <div className="mt-3 select-text rounded-lg border border-border bg-text/[.03] p-3 text-xs">
            <div className="mb-2 flex justify-between font-semibold text-text">
              <span>{t("settings.diagnostics.memoryTotal")}</span>
              <span>{Math.round(snapshot.totalMb)} MB</span>
            </div>
            {snapshot.processes.map((process) => (
              <div key={process.pid} className="flex justify-between gap-3 py-0.5 text-muted">
                <span className="truncate">{processLabel(process)}</span>
                <span className="shrink-0 tabular-nums">{Math.round(process.workingSetMb)} MB</span>
              </div>
            ))}
            <p className="mt-2 text-[11px] text-muted">{t("settings.diagnostics.memoryFootnote", { heap: snapshot.mainHeapUsedMb })}</p>
          </div>
        )}
        {acceleration !== undefined && (
          <div className="mt-4 flex items-start justify-between gap-4 border-t border-border pt-4">
            <div>
              <p className="text-sm font-semibold text-text">{t("settings.diagnostics.saveMemory")}</p>
              <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.diagnostics.saveMemoryHint")}</p>
              {changed && (
                <button
                  onClick={() => hibiki.app.relaunch()}
                  className="mt-2 rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14]"
                >
                  {t("settings.diagnostics.saveMemoryRestart")}
                </button>
              )}
            </div>
            <Switch
              checked={!acceleration}
              onChange={(saveMemory) => {
                setAcceleration(!saveMemory);
                setChanged(true);
                void hibiki.app.setHardwareAcceleration(!saveMemory);
              }}
            />
          </div>
        )}
      </SettingsRow>
    </SettingsSection>
  );
}


// Backs up hibiki.db, installed sources, and every persisted setting (see readAllLocalStorage) to
// a single file the user picks the location for - and restores the same. Deliberately excludes
// downloaded episodes (see main/backup.ts) - those can be many GB and are re-downloadable from the
// source at any time, unlike a watch history or an installed-but-since-removed source's script.
function BackupSection() {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<BackupStatus | null>(null);
  const [confirmingRestore, setConfirmingRestore] = useState(false);
  const [version, setVersion] = useState<string | null>(null);
  useEffect(() => { hibiki.app.getVersion().then(setVersion); }, []);

  const handleCreate = async () => {
    setBusy(true);
    setStatus(null);
    try {
      const filePath = await hibiki.backup.create(readAllLocalStorage());
      if (filePath) setStatus({ kind: "success", message: t("settings.data.backupCreated", { path: filePath }) });
    } catch (error) {
      setStatus({ kind: "error", message: error instanceof Error ? error.message : String(error) });
    } finally {
      setBusy(false);
    }
  };

  const handleRestore = async () => {
    setConfirmingRestore(false);
    setBusy(true);
    setStatus(null);
    try {
      const result = await hibiki.backup.restore();
      if (!result) { setBusy(false); return; }
      // Applied here (not by main/backup.ts itself) - the main process has no access to this
      // origin's localStorage, only the renderer does. `hibiki.app.relaunch()` right after is what
      // actually makes the restored data take effect - see the IPC handler for why a fresh start
      // is simpler and safer here than trying to hot-swap the live DB connection and re-read every
      // already-mounted store mid-session.
      localStorage.clear();
      for (const [key, value] of Object.entries(result.localStorage)) localStorage.setItem(key, value);
      // A short delay, not an immediate relaunch - `localStorage.setItem` returns before Chromium's
      // storage backend has necessarily flushed those writes to disk, and app.exit() right after
      // would risk tearing the process down before that finishes, silently reverting some of what
      // was just restored the moment the new process reads localStorage back on its own first load.
      await new Promise((resolve) => setTimeout(resolve, 250));
      hibiki.app.relaunch();
    } catch (error) {
      setStatus({ kind: "error", message: error instanceof Error ? error.message : String(error) });
      setBusy(false);
    }
  };

  return (
    <SettingsSection>
      <SettingsRow icon={<DatabaseBackup className="h-[18px] w-[18px]" strokeWidth={2} />}>
        <p className="text-sm font-semibold text-text">{t("settings.data.backup")}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.data.backupHint")}</p>
        <button
          onClick={handleCreate}
          disabled={busy}
          className="mt-3 rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14] disabled:opacity-50"
        >
          {t("settings.data.backupAction")}
        </button>
      </SettingsRow>

      <SettingsRow icon={<RotateCcw className="h-[18px] w-[18px]" strokeWidth={2} />}>
        <p className="text-sm font-semibold text-text">{t("settings.data.restore")}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.data.restoreHint")}</p>
        {confirmingRestore ? (
          <div className="mt-3 rounded-lg border border-rose-400/30 bg-rose-400/10 p-3 dark:border-rose-400/20 dark:bg-rose-400/5">
            <p className="flex items-start gap-1.5 text-xs leading-relaxed text-rose-700 dark:text-rose-200">
              <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" strokeWidth={2} />
              {t("settings.data.restoreConfirm")}
            </p>
            <div className="mt-2.5 flex gap-2">
              <button onClick={handleRestore} disabled={busy} className="rounded-lg bg-rose-500 px-3 py-1.5 text-xs font-bold text-white transition-opacity hover:opacity-90 disabled:opacity-50">
                {t("settings.data.restoreConfirmAction")}
              </button>
              <button onClick={() => setConfirmingRestore(false)} disabled={busy} className="rounded-lg px-3 py-1.5 text-xs font-semibold text-muted transition-colors hover:bg-text/[.06] disabled:opacity-50">
                {t("common.cancel")}
              </button>
            </div>
          </div>
        ) : (
          <button
            onClick={() => setConfirmingRestore(true)}
            disabled={busy}
            className="mt-3 rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14] disabled:opacity-50"
          >
            {t("settings.data.restoreAction")}
          </button>
        )}
      </SettingsRow>

      {status && (
        <p className={cn("select-text px-1 text-xs leading-relaxed", status.kind === "error" ? "text-rose-500" : "text-muted")}>
          {status.message}
        </p>
      )}

      {version && (
        <p className="flex items-center gap-1.5 px-1 text-xs text-muted">
          <Info className="h-3 w-3 shrink-0" strokeWidth={2} />
          {t("settings.data.version", { version })}
        </p>
      )}
    </SettingsSection>
  );
}

/**
 * AniList: the account, and bringing its lists in. Everything after signing in runs by itself (see
 * core/tracking), so this is the one place tracking asks anything of the user.
 */
function TrackingSection() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const account = useTrackerAccount();
  const [waiting, setWaiting] = useState(false);
  const signIn = useMutation({
    mutationFn: () => hibiki.tracking.signIn("anilist"),
    onSuccess: () => setWaiting(true),
  });
  const signOut = useMutation({
    mutationFn: () => hibiki.tracking.signOut("anilist"),
    onSettled: () => queryClient.invalidateQueries({ queryKey: [TRACKING_KEY] }),
  });
  const data = account.data;
  // The browser came back: whichever way it went, stop saying it is awaited.
  useEffect(() => {
    if (data?.user || data?.signInError) setWaiting(false);
  }, [data?.user, data?.signInError]);
  if (!data) return null;
  const user = data.user;

  return (
    <SettingsSection>
      <SettingsRow icon={<AniListLogo className="h-[18px] w-[18px]" />}>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-text">AniList</p>
            {user ? (
              <div className="mt-2 flex items-center gap-2.5">
                {user.avatarUrl
                  ? <img src={user.avatarUrl} alt="" className="h-8 w-8 shrink-0 rounded-full object-cover" />
                  : <span className="h-8 w-8 shrink-0 rounded-full bg-text/10" />}
                <span className="truncate text-sm font-medium text-text">{user.name}</span>
              </div>
            ) : (
              <p className="mt-0.5 text-xs leading-relaxed text-muted">{data.configured ? t("tracking.anilist.signedOutHint") : t("tracking.anilist.notConfigured")}</p>
            )}
          </div>
          {user && (
            <button
              onClick={() => signOut.mutate()}
              disabled={signOut.isPending}
              className="flex shrink-0 items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-muted transition-colors hover:bg-text/[.06] hover:text-text disabled:opacity-50"
            >
              <LogOut className="h-3.5 w-3.5" strokeWidth={2} />
              {t("tracking.anilist.signOut")}
            </button>
          )}
        </div>
        {data.needsSignIn && (
          <p className="mt-3 flex items-start gap-1.5 text-xs leading-relaxed text-amber-600 dark:text-amber-300">
            <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" strokeWidth={2} />
            {t("tracking.anilist.expired")}
          </p>
        )}
        {data.configured && (!user || data.needsSignIn) && (
          <button
            onClick={() => signIn.mutate()}
            disabled={signIn.isPending}
            className="mt-3 flex items-center gap-2 rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14] disabled:opacity-50 mobile:rounded-full mobile:px-4 mobile:py-2"
          >
            <LogIn className="h-4 w-4" strokeWidth={2} />
            {data.needsSignIn ? t("tracking.anilist.signInAgain") : t("tracking.anilist.signIn")}
          </button>
        )}
        {waiting && !user && <p className="mt-2 text-xs leading-relaxed text-muted">{t("tracking.anilist.waiting")}</p>}
        {data.signInError && <p className="mt-2 select-text text-xs leading-relaxed text-rose-500">{t("tracking.anilist.signInFailed", { error: data.signInError })}</p>}
        {user && !data.needsSignIn && (
          <p className="mt-3 flex items-start gap-1.5 text-xs leading-relaxed text-muted">
            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" strokeWidth={2} />
            {t("tracking.anilist.howItWorks")}
          </p>
        )}
      </SettingsRow>
      {user && !data.needsSignIn && <TrackingImportRow />}
    </SettingsSection>
  );
}

function TrackingImportRow() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const [picked, setPicked] = useState<string | null>(null);
  const sourceId = picked ?? activeSourceId ?? sources.data?.[0]?.id ?? "";
  const sourceName = sources.data?.find((source) => source.id === sourceId)?.name ?? sourceId;
  const [progress, setProgress] = useState<TrackerImportProgress | null>(null);
  const [report, setReport] = useState<TrackerImportReport | null>(null);
  const [unmatchedOpen, setUnmatchedOpen] = useState(false);
  useEffect(() => hibiki.tracking.onImportProgress(setProgress), []);
  const run = useMutation({
    mutationFn: () => hibiki.tracking.importLibrary("anilist", sourceId),
    onMutate: () => {
      setReport(null);
      setProgress(null);
      setUnmatchedOpen(false);
    },
    onSuccess: (result) => setReport(result),
    onSettled: () => {
      setProgress(null);
      void queryClient.invalidateQueries({ queryKey: ["library"] });
    },
  });

  return (
    <SettingsRow icon={<ArrowDownToLine className="h-[18px] w-[18px]" strokeWidth={2} />}>
      <p className="text-sm font-semibold text-text">{t("tracking.import.title")}</p>
      <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("tracking.import.hint")}</p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <SelectDropdown
          className="min-w-[12rem] flex-1"
          value={sourceId}
          onChange={setPicked}
          disabled={run.isPending || !sources.data?.length}
          placeholder={t("tracking.import.source")}
          options={(sources.data ?? []).map((source) => ({
            id: source.id,
            label: source.name,
            icon: source.iconUrl ? <img src={source.iconUrl} alt="" className="h-full w-full object-cover" /> : <Radio className="h-3 w-3 text-muted" strokeWidth={1.75} />,
            // An Aniyomi extension (Android), the way the Sources screen marks one.
            badge: source.id.startsWith("apk:") ? "APK" : undefined,
          }))}
        />
        <button
          onClick={() => run.mutate()}
          disabled={run.isPending || !sourceId}
          className="rounded-lg bg-text/[.08] px-3 py-1.5 text-sm font-semibold text-text transition-colors hover:bg-text/[.14] disabled:opacity-50 mobile:rounded-full mobile:px-4 mobile:py-2"
        >
          {t("tracking.import.action")}
        </button>
      </div>
      {run.isPending && progress && progress.total > 0 && (
        <div className="mt-3">
          <p className="text-xs tabular-nums text-muted">{t("tracking.import.running", { done: progress.done, total: progress.total })}</p>
          <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-text/10">
            <div className="h-full rounded-full bg-accent-solid transition-[width] duration-300" style={{ width: `${(progress.done / progress.total) * 100}%` }} />
          </div>
        </div>
      )}
      {run.error && <p className="mt-2 select-text text-xs leading-relaxed text-rose-500">{t("tracking.import.error", { error: run.error instanceof Error ? run.error.message : String(run.error) })}</p>}
      {report && (
        <div className="mt-3 text-xs leading-relaxed">
          <p className="font-semibold text-text">{t("tracking.import.result", { added: report.added, updated: report.updated, missing: report.unmatched.length })}</p>
          {report.failed > 0 && <p className="mt-1 text-muted">{t("tracking.import.failed", { count: report.failed })}</p>}
          {report.unmatched.length > 0 && (
            <>
              <button onClick={() => setUnmatchedOpen((v) => !v)} className="mt-1.5 inline-flex items-center gap-1 font-semibold text-muted transition-colors hover:text-text">
                {t("tracking.import.unmatched", { source: sourceName })}
                <ChevronDown className={cn("h-3.5 w-3.5 transition-transform", unmatchedOpen && "rotate-180")} strokeWidth={2.5} />
              </button>
              {unmatchedOpen && <ul className="mt-1.5 max-h-56 select-text list-disc space-y-0.5 overflow-y-auto pl-4 text-muted">{report.unmatched.map((name) => <li key={name}>{name}</li>)}</ul>}
            </>
          )}
        </div>
      )}
    </SettingsRow>
  );
}

// Which of the active source's own catalog sort orders fills the home page's hero and "popular"
// row - "Auto" (see pickRelevanceSort, in home.tsx) sends the source's own relevance ordering, or
// no sort at all where it has no such concept. A source's sort ids are its own vocabulary, not a
// fixed one the host can rely on, so this is the way to pick something more specific per source.
function HomeSortSection() {
  const { t } = useTranslation();
  const sourcesQuery = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const source = sourcesQuery.data?.find((s) => s.id === activeSourceId) ?? sourcesQuery.data?.[0];
  // The same key the home page and the filter panel read, so this costs no extra request.
  const catalogQuery = useFilterCatalog(source?.id);
  const homeSortBySource = useUiStore((s) => s.homeSortBySource);
  const setHomeSort = useUiStore((s) => s.setHomeSort);
  if (!source) return null;
  const options = catalogQuery.data?.sortOptions ?? [];
  return (
    <SettingsSection>
      <SettingsRow icon={<ArrowUpDown className="h-[18px] w-[18px]" strokeWidth={2} />}>
        <p className="text-sm font-semibold text-text">{t("settings.home.sort")}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.home.sortHint", { source: source.name })}</p>
        <SelectDropdown
          className="mt-3"
          value={homeSortBySource[source.id] ?? ""}
          onChange={(id) => setHomeSort(source.id, id || null)}
          disabled={options.length === 0}
          placeholder={t("settings.home.sortAuto")}
          options={[{ id: "", label: t("settings.home.sortAuto") }, ...options.map((option) => ({ id: option.id, label: sortLabel(option, t) }))]}
        />
      </SettingsRow>
    </SettingsSection>
  );
}

const DATA_SAVER_MODES: DataSaverMode[] = ["auto", "on", "off"];

/** A row of mutually exclusive choices, the active one filled - the same look as data saver's. */
function ChoiceButtons<T extends string>({ value, options, onChange }: { value: T; options: Array<{ id: T; label: string }>; onChange: (id: T) => void }) {
  return (
    <div className="mt-3 flex flex-wrap items-center gap-2">
      {options.map((option) => (
        <button
          key={option.id}
          onClick={() => onChange(option.id)}
          className={cn(
            "rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
            value === option.id ? "bg-accent text-accent-fg" : "bg-text/[.05] text-muted hover:bg-text/[.08] hover:text-text",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/** A card of ChoiceRows. No overflow clipping: a desktop dropdown opens past the card's edge. */
function ChoiceGroup({ children }: { children: React.ReactNode }) {
  return <div className="divide-y divide-border rounded-2xl border border-border bg-text/[.03]">{children}</div>;
}

/**
 * A setting with a few exclusive choices, as one line: its name and what it is set to now. On the
 * phone a tap opens the choices in a sheet, with the explanation above them; on desktop the choices
 * are a dropdown beside the name, the explanation under it. Instead of a run of buttons under every
 * setting, which stacked up into a wall on the phone.
 */
function ChoiceRow<T extends string>({ icon, title, hint, status, value, options, onChange }: {
  icon: React.ReactNode;
  title: string;
  hint: string;
  /** A short live remark (what "auto" is deciding right now), shown under the name. */
  status?: string;
  value: T;
  options: Array<{ id: T; label: string }>;
  onChange: (id: T) => void;
}) {
  const [open, setOpen] = useState(false);
  const current = options.find((option) => option.id === value)?.label ?? "";
  const tile = <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-text/[.06] text-muted">{icon}</span>;
  if (!isMobile) {
    return (
      <div className="flex items-center gap-3 px-4 py-3">
        {tile}
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-text">{title}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-muted">{hint}</p>
          {status && <p className="mt-1 text-xs text-muted">{status}</p>}
        </div>
        <SelectDropdown className="w-44 shrink-0" value={value} placeholder="" options={options} onChange={(id) => onChange(id as T)} />
      </div>
    );
  }
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors first:rounded-t-2xl last:rounded-b-2xl active:bg-text/[.05]">
        {tile}
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold text-text">{title}</span>
          {status && <span className="mt-0.5 block text-xs text-muted">{status}</span>}
        </span>
        <span className="flex shrink-0 items-center gap-1 text-sm text-muted">
          {current}
          <ChevronRight className="h-4 w-4 text-muted/70" strokeWidth={2.25} />
        </span>
      </button>
      <BottomSheet open={open} onClose={() => setOpen(false)} title={title}>
        <p className="px-5 pb-3 text-xs leading-relaxed text-muted">{hint}</p>
        <div className="px-2 pb-2">
          {options.map((option) => (
            <SheetOption
              key={option.id}
              label={option.label}
              selected={option.id === value}
              onClick={() => {
                onChange(option.id);
                setOpen(false);
              }}
            />
          ))}
        </div>
      </BottomSheet>
    </>
  );
}

const HOME_REFRESH_MODES: HomeRefreshMode[] = ["launch", "day", "3days", "manual"];
const BUFFER_MODES: BufferMode[] = ["low", "normal", "high"];


/** What is kept to avoid downloading it again: home rows and filter lists saved by the app, plus
 * (desktop) the web view's own HTTP cache, where posters live. */
function CacheRow() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const size = useQuery({
    queryKey: ["cacheSize"],
    queryFn: async () =>
      persistedSize() +
      ((await hibiki.app.httpCacheSize?.()) ?? 0) +
      ((await hibiki.network.sourceCacheSize().catch(() => 0)) ?? 0) +
      (await videoCache.size()),
    staleTime: 0,
    gcTime: 0,
  });
  const clear = useMutation({
    mutationFn: async () => {
      forgetAllPersisted();
      await hibiki.app.clearHttpCache?.();
      await hibiki.network.clearSourceCache().catch(() => undefined);
      await videoCache.clear();
    },
    onSuccess: () => {
      // The rows on screen came from what was just thrown away: drop them too, so Home loads afresh.
      for (const key of ["hero", "popular-pool", "latest", "filterCatalog", "catalog", "searchResults"]) queryClient.removeQueries({ queryKey: [key] });
      void size.refetch();
    },
  });
  return (
    <SettingsRow icon={<HardDrive className="h-[18px] w-[18px]" strokeWidth={2} />}>
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-text">{t("settings.network.cache")}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-muted">
            {t(hibiki.app.clearHttpCache ? "settings.network.cacheHint" : "settings.network.cacheHintMobile")}
          </p>
          <p className="mt-1 text-xs text-muted">{size.data === undefined ? "…" : t("settings.network.cacheSize", { size: formatBytes(size.data) })}</p>
        </div>
        <button
          onClick={() => clear.mutate()}
          disabled={clear.isPending}
          className="shrink-0 rounded-lg bg-text/[.05] px-3 py-1.5 text-sm font-medium text-text transition-colors hover:bg-text/[.08] disabled:opacity-50"
        >
          {t("settings.network.cacheClear")}
        </button>
      </div>
    </SettingsRow>
  );
}

/** How much the downloaded episodes take on this device, which titles take it, and the way to them. */
function DownloadsStorageRow() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const episodes = useQuery({ queryKey: ["downloadedEpisodes"], queryFn: () => hibiki.downloads.list() });
  const list = episodes.data ?? [];
  const byTitle = new Map<string, number>();
  for (const ep of list) byTitle.set(`${ep.sourceId}:${ep.animeId}`, (byTitle.get(`${ep.sourceId}:${ep.animeId}`) ?? 0) + ep.fileSizeBytes);
  const shares = [...byTitle].sort((a, b) => b[1] - a[1]);
  const total = list.reduce((sum, ep) => sum + ep.fileSizeBytes, 0);
  return (
    <SettingsRow icon={<Download className="h-[18px] w-[18px]" strokeWidth={2} />}>
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-text">{t("downloads.title")}</p>
          <p className="mt-0.5 text-xs text-muted">
            {episodes.data === undefined
              ? "…"
              : list.length === 0
                ? t("downloads.empty")
                : `${formatBytes(total)} · ${t("downloads.episodes", { count: list.length })} · ${t("downloads.titles", { count: byTitle.size })}`}
          </p>
        </div>
        <button
          onClick={() => void navigate({ to: "/downloads" })}
          className="shrink-0 rounded-lg bg-text/[.05] px-3 py-1.5 text-sm font-medium text-text transition-colors hover:bg-text/[.08]"
        >
          {t("settings.network.downloadsOpen")}
        </button>
      </div>
      {/* Each title's share, the biggest first. */}
      {shares.length > 0 && (
        <div className="mt-3 flex h-1.5 gap-0.5 overflow-hidden rounded-full">
          {shares.map(([key, bytes], index) => (
            <span key={key} className="h-full rounded-full bg-accent" style={{ flexGrow: Math.max(bytes, 1), flexBasis: 0, opacity: Math.max(0.25, 1 - index * 0.2) }} />
          ))}
        </div>
      )}
    </SettingsRow>
  );
}

const MAX_DOWNLOAD_OPTIONS = [1, 2, 3, 4];
const TIMEOUT_OPTIONS = [15, 25, 45];
const DOH_OPTIONS: DohProvider[] = ["off", "cloudflare", "google", "adguard"];

function TrafficRow() {
  const { t } = useTranslation();
  const history = useTrafficHistory();
  const [range, setRange] = useState<7 | 30>(30);
  return (
    <SettingsRow icon={<BarChart3 className="h-[18px] w-[18px]" strokeWidth={2} />}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-text">{t("settings.network.trafficTitle")}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.network.trafficHint")}</p>
        </div>
        <button
          onClick={resetTrafficStats}
          className="shrink-0 rounded-lg bg-text/[.05] px-3 py-1.5 text-sm font-medium text-text transition-colors hover:bg-text/[.08]"
        >
          {t("settings.network.trafficReset")}
        </button>
      </div>
      <div className="mt-4">
        <TrafficChart history={history} range={range} onRangeChange={setRange} />
      </div>
    </SettingsRow>
  );
}

function CloudflareResetRow() {
  const { t } = useTranslation();
  const reset = useMutation({ mutationFn: () => hibiki.network.resetCloudflare() });
  return (
    <SettingsRow icon={<ShieldOff className="h-[18px] w-[18px]" strokeWidth={2} />}>
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-text">{t("settings.network.cloudflareReset")}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-muted">{t(reset.isSuccess ? "settings.network.cloudflareResetDone" : "settings.network.cloudflareResetHint")}</p>
        </div>
        <button
          onClick={() => reset.mutate()}
          disabled={reset.isPending}
          className="shrink-0 rounded-lg bg-text/[.05] px-3 py-1.5 text-sm font-medium text-text transition-colors hover:bg-text/[.08] disabled:opacity-50"
        >
          {t("settings.network.cloudflareResetButton")}
        </button>
      </div>
    </SettingsRow>
  );
}

/** Desktop only: a proxy for both Chromium and the sources' requests, applied when the field is left. */
function ProxyRow() {
  const { t } = useTranslation();
  const proxy = useNetworkPrefsStore((s) => s.proxy);
  const setProxy = useNetworkPrefsStore((s) => s.setProxy);
  const [draft, setDraft] = useState(proxy);
  useEffect(() => setDraft(proxy), [proxy]);
  const valid = draft.trim() === "" || /^(https?|socks5?):\/\/[^\s/]+:\d+\/?$/i.test(draft.trim());
  const commit = () => { if (valid) setProxy(draft); };
  return (
    <SettingsRow icon={<Globe className="h-[18px] w-[18px]" strokeWidth={2} />}>
      <p className="text-sm font-semibold text-text">{t("settings.network.proxy")}</p>
      <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.network.proxyHint")}</p>
      <input
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => { if (event.key === "Enter") commit(); }}
        placeholder="http://127.0.0.1:8080"
        spellCheck={false}
        className={cn(
          "mt-3 w-full rounded-lg border bg-text/[.03] px-3 py-2 text-sm text-text outline-none placeholder:text-muted/60 focus:border-accent",
          valid ? "border-text/10" : "border-red-500/60",
        )}
      />
      {!valid && <p className="mt-1.5 text-xs text-red-400">{t("settings.network.proxyInvalid")}</p>}
    </SettingsRow>
  );
}

const UPSCALE_MODES: UpscaleMode[] = ["off", "light", "strong"];

/** Anime4K (features/player/upscaler.ts), and on an RTX card a pointer to NVIDIA's own upscaler. */
function UpscaleRow() {
  const { t } = useTranslation();
  const upscale = usePlayerPrefsStore((s) => s.upscale);
  const setUpscale = usePlayerPrefsStore((s) => s.setUpscale);
  const gpu = useQuery({ queryKey: ["gpu"], queryFn: () => hibiki.app.gpu?.() ?? Promise.resolve(null), staleTime: Infinity });
  const available = isWebGpuAvailable();
  return (
    <SettingsRow icon={<Sparkles className="h-[18px] w-[18px]" strokeWidth={2} />}>
      <p className="text-sm font-semibold text-text">{t("settings.player.upscale")}</p>
      <p className="mt-0.5 text-xs leading-relaxed text-muted">{t(available ? "settings.player.upscaleHint" : "settings.player.upscaleUnavailable")}</p>
      {available && (
        <ChoiceButtons
          value={upscale}
          onChange={setUpscale}
          options={UPSCALE_MODES.map((id) => ({ id, label: t(`watch.settings.upscale${id[0].toUpperCase()}${id.slice(1)}`) }))}
        />
      )}
      {gpu.data?.nvidiaRtx && <p className="mt-2 text-xs leading-relaxed text-muted">{t("settings.player.rtxHint")}</p>}
    </SettingsRow>
  );
}

function NetworkSection() {
  const { t } = useTranslation();
  const homeRefresh = useNetworkPrefsStore((s) => s.homeRefresh);
  const setHomeRefresh = useNetworkPrefsStore((s) => s.setHomeRefresh);
  const bufferMode = useNetworkPrefsStore((s) => s.bufferMode);
  const setBufferMode = useNetworkPrefsStore((s) => s.setBufferMode);
  const wifiOnlyDownloads = useNetworkPrefsStore((s) => s.wifiOnlyDownloads);
  const setWifiOnlyDownloads = useNetworkPrefsStore((s) => s.setWifiOnlyDownloads);
  const wifiOnlyAutoplay = useNetworkPrefsStore((s) => s.wifiOnlyAutoplay);
  const setWifiOnlyAutoplay = useNetworkPrefsStore((s) => s.setWifiOnlyAutoplay);
  const maxDownloads = useNetworkPrefsStore((s) => s.maxDownloads);
  const setMaxDownloads = useNetworkPrefsStore((s) => s.setMaxDownloads);
  const requestTimeoutSeconds = useNetworkPrefsStore((s) => s.requestTimeoutSeconds);
  const setRequestTimeoutSeconds = useNetworkPrefsStore((s) => s.setRequestTimeoutSeconds);
  const doh = useNetworkPrefsStore((s) => s.doh);
  const setDoh = useNetworkPrefsStore((s) => s.setDoh);
  return (
    <SettingsSection>
      <ChoiceGroup>
        <DataSaverRow />
        <ChoiceRow
          icon={<FastForward className="h-[18px] w-[18px]" strokeWidth={2} />}
          title={t("settings.network.buffer")}
          hint={t("settings.network.bufferHint")}
          value={bufferMode}
          onChange={setBufferMode}
          options={BUFFER_MODES.map((id) => ({ id, label: t(`settings.network.bufferModes.${id}`, { seconds: BUFFER_SECONDS[id] }) }))}
        />
        <ChoiceRow
          icon={<Home className="h-[18px] w-[18px]" strokeWidth={2} />}
          title={t("settings.network.homeRefresh")}
          hint={t("settings.network.homeRefreshHint")}
          value={homeRefresh}
          onChange={setHomeRefresh}
          options={HOME_REFRESH_MODES.map((id) => ({ id, label: t(`settings.network.homeRefreshModes.${id}`) }))}
        />
        <ChoiceRow
          icon={<Download className="h-[18px] w-[18px]" strokeWidth={2} />}
          title={t("settings.network.maxDownloads")}
          hint={t("settings.network.maxDownloadsHint")}
          value={String(maxDownloads)}
          onChange={(id) => setMaxDownloads(Number(id))}
          options={MAX_DOWNLOAD_OPTIONS.map((count) => ({ id: String(count), label: String(count) }))}
        />
        <ChoiceRow
          icon={<Timer className="h-[18px] w-[18px]" strokeWidth={2} />}
          title={t("settings.network.timeout")}
          hint={t("settings.network.timeoutHint")}
          value={String(requestTimeoutSeconds)}
          onChange={(id) => setRequestTimeoutSeconds(Number(id))}
          options={TIMEOUT_OPTIONS.map((seconds) => ({ id: String(seconds), label: t("settings.network.seconds", { seconds }) }))}
        />
        {/* The WebView offers no way to set it; the desktop applies it to Chromium and Node. */}
        {!isMobile && (
          <ChoiceRow
            icon={<ShieldCheck className="h-[18px] w-[18px]" strokeWidth={2} />}
            title={t("settings.network.doh")}
            hint={t("settings.network.dohHint")}
            value={doh}
            onChange={setDoh}
            options={DOH_OPTIONS.map((id) => ({ id, label: t(`settings.network.dohProviders.${id}`) }))}
          />
        )}
      </ChoiceGroup>
      {/* Only the phone can tell mobile data from Wi-Fi. */}
      {isMobile && (
        <SettingsGroup>
          <SettingsToggleRow
            icon={<Smartphone className="h-[18px] w-[18px]" strokeWidth={2} />}
            title={t("settings.network.wifiOnlyDownloads")}
            hint={t("settings.network.wifiOnlyDownloadsHint")}
            checked={wifiOnlyDownloads}
            onChange={setWifiOnlyDownloads}
          />
          <SettingsToggleRow
            icon={<Smartphone className="h-[18px] w-[18px]" strokeWidth={2} />}
            title={t("settings.network.wifiOnlyAutoplay")}
            hint={t("settings.network.wifiOnlyAutoplayHint")}
            checked={wifiOnlyAutoplay}
            onChange={setWifiOnlyAutoplay}
          />
        </SettingsGroup>
      )}
      {!isMobile && <ProxyRow />}
      <CloudflareResetRow />
      <TrafficRow />
      <CacheRow />
      <DownloadsStorageRow />
    </SettingsSection>
  );
}

/** Data saver's three modes, and in "auto" what it is deciding right now. */
function DataSaverRow() {
  const { t } = useTranslation();
  const mode = usePlayerPrefsStore((s) => s.dataSaver);
  const setMode = usePlayerPrefsStore((s) => s.setDataSaver);
  const cap = useAutoQualityCap();
  const label = { auto: t("settings.player.dataSaverAuto"), on: t("settings.player.dataSaverOn"), off: t("settings.player.dataSaverOff") };
  return (
    <ChoiceRow
      icon={<Gauge className="h-[18px] w-[18px]" strokeWidth={2} />}
      title={t("settings.player.dataSaver")}
      hint={t("settings.player.dataSaverHint")}
      status={mode === "auto" ? (cap === null ? t("settings.player.dataSaverNowFull") : t("settings.player.dataSaverNowCap", { height: cap })) : undefined}
      value={mode}
      onChange={setMode}
      options={DATA_SAVER_MODES.map((id) => ({ id, label: label[id] }))}
    />
  );
}

export function SettingsPage() {
  const { t, i18n } = useTranslation();
  const autoSkipDelaySeconds = usePlayerPrefsStore((s) => s.autoSkipDelaySeconds);
  const setAutoSkipDelaySeconds = usePlayerPrefsStore((s) => s.setAutoSkipDelaySeconds);
  const skipButtonTimeoutSeconds = usePlayerPrefsStore((s) => s.skipButtonTimeoutSeconds);
  const setSkipButtonTimeoutSeconds = usePlayerPrefsStore((s) => s.setSkipButtonTimeoutSeconds);
  const watchedThresholdPercent = usePlayerPrefsStore((s) => s.watchedThresholdPercent);
  const setWatchedThresholdPercent = usePlayerPrefsStore((s) => s.setWatchedThresholdPercent);
  const autoUpdate = useUiStore((s) => s.autoUpdate);
  const newEpisodesEnabled = useNewEpisodesPrefs((s) => s.enabled);
  const setNewEpisodesEnabled = useNewEpisodesPrefs((s) => s.setEnabled);
  const setAutoUpdate = useUiStore((s) => s.setAutoUpdate);
  const discordRpcEnabled = useUiStore((s) => s.discordRpcEnabled);
  const setDiscordRpcEnabled = useUiStore((s) => s.setDiscordRpcEnabled);
  const discordIgnoreNsfwSources = useUiStore((s) => s.discordIgnoreNsfwSources);
  const setDiscordIgnoreNsfwSources = useUiStore((s) => s.setDiscordIgnoreNsfwSources);
  const theme = useUiStore((s) => s.theme);
  const setTheme = useUiStore((s) => s.setTheme);
  const accentColor = useUiStore((s) => s.accentColor);
  const defaultAccent = isMobile ? (theme === "light" ? DEFAULT_MOBILE_ACCENT_LIGHT : DEFAULT_MOBILE_ACCENT) : DEFAULT_ACCENT;
  const setAccentColor = useUiStore((s) => s.setAccentColor);
  const backgroundTheme = useUiStore((s) => s.backgroundTheme);
  const setBackgroundTheme = useUiStore((s) => s.setBackgroundTheme);
  const customBackgroundGradient = useUiStore((s) => s.customBackgroundGradient);
  const setCustomBackgroundGradient = useUiStore((s) => s.setCustomBackgroundGradient);
  const catalogAutoLoad = useUiStore((s) => s.catalogAutoLoad);
  const setCatalogAutoLoad = useUiStore((s) => s.setCatalogAutoLoad);
  // Same "sources" query HomeSortSection itself reads (shared cache, no extra request) - just to
  // decide whether that category belongs in the rail at all, the same condition HomeSortSection
  // already used to silently render nothing for.
  const sourcesQuery = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const hasSource = (sourcesQuery.data?.length ?? 0) > 0;
  const categories = [
    { id: "appearance" as const, label: t("settings.appearance.title"), icon: Palette },
    { id: "general" as const, label: t("settings.general"), icon: SlidersHorizontal },
    { id: "player" as const, label: t("settings.player.title"), icon: MonitorPlay },
    { id: "network" as const, label: t("settings.network.title"), icon: Wifi },
    { id: "tracking" as const, label: t("tracking.settings.title"), icon: RefreshCw },
    ...(hasSource ? [{ id: "home" as const, label: t("settings.home.title"), icon: Home }] : []),
    // Backups write and read a file through the desktop's save/open dialogs; the phone has none yet.
    ...(isMobile ? [] : [{ id: "data" as const, label: t("settings.data.title"), icon: DatabaseBackup }]),
    { id: "diagnostics" as const, label: t("settings.diagnostics.title"), icon: ScrollText },
  ];
  const [category, setCategory] = useState<(typeof categories)[number]["id"]>("appearance");
  // The "Home" tab can disappear (last source just got uninstalled) out from under whichever tab
  // was open - falls back to the first one rather than rendering an empty pane for a category that
  // no longer exists in the rail.
  const desktopCategory = categories.some((c) => c.id === category) ? category : categories[0].id;
  // Phone: settings as Android has them - a list of sections, each opening as a screen of its own
  // (Back returns to the list) - instead of a row of chips over one long page.
  const [mobileCategory, setMobileCategory] = useState<(typeof categories)[number]["id"] | null>(null);
  const mobileOpen = categories.find((c) => c.id === mobileCategory) ?? null;
  const activeCategory = isMobile ? mobileOpen?.id ?? null : desktopCategory;
  useBackHandler(mobileOpen !== null, () => setMobileCategory(null));
  const openMobileCategory = (id: (typeof categories)[number]["id"] | null) => {
    setMobileCategory(id);
    // A section opens at its top, and the list comes back at its own.
    document.querySelector<HTMLElement>('[data-page-scroll="/settings"]')?.scrollTo({ top: 0 });
  };

  return <div className="flex h-full bg-app-bg mobile:h-auto mobile:min-h-full mobile:flex-col">
    {isMobile && <div className="px-4 pt-2">
      <MobilePageHeader title={mobileOpen?.label ?? t("nav.settings")} parent="/profile" onBack={mobileOpen ? () => openMobileCategory(null) : undefined} />
    </div>}
    {isMobile && !mobileOpen && <MobileSettingsList categories={categories} onOpen={openMobileCategory} />}
    <nav className="flex w-56 shrink-0 flex-col gap-0.5 overflow-y-auto border-r border-border px-3 py-8 mobile:hidden">
      {categories.map((c) => (
        <SettingsCategoryButton key={c.id} active={activeCategory === c.id} icon={c.icon} label={c.label} onClick={() => setCategory(c.id)} />
      ))}
    </nav>
    <motion.div
      // Phone: a section slides in over where the list was, as a screen of its own.
      key={isMobile ? activeCategory ?? "list" : "content"}
      initial={isMobile && activeCategory ? { opacity: 0.5, x: 28 } : false}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.22, ease: [0.2, 0.8, 0.2, 1] }}
      className={cn("min-w-0 flex-1 overflow-y-auto p-8 mobile:overflow-visible mobile:px-4 mobile:pb-6 mobile:pt-1", isMobile && !activeCategory && "hidden")}
    >
    {/* Not centered (`mx-auto`) - a wide window would then float this column in the middle of
        whatever's left of the rail, forcing a long mouse trip from the category just clicked over
        to the settings it opened. Left-aligned right next to the rail instead, same as the rail
        itself, so the two stay close together regardless of window width. */}
    <div className="max-w-xl">

      {activeCategory === "appearance" && (
      <SettingsSection>
        <SettingsRow icon={theme === "dark" ? <Moon className="h-[18px] w-[18px]" strokeWidth={2} /> : <Sun className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-text">{t("settings.appearance.theme")}</p>
              <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.appearance.themeHint")}</p>
            </div>
            <div className="flex shrink-0 rounded-lg bg-text/[.05] p-0.5">
              <ThemeOption active={theme === "light"} icon={Sun} label={t("settings.appearance.light")} onClick={() => setTheme("light")} />
              <ThemeOption active={theme === "dark"} icon={Moon} label={t("settings.appearance.dark")} onClick={() => setTheme("dark")} />
            </div>
          </div>
        </SettingsRow>

        <SettingsRow icon={<Palette className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <p className="text-sm font-semibold text-text">{t("settings.appearance.accentColor")}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.appearance.accentColorHint")}</p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {ACCENT_PRESETS.map((color) => (
              <AccentSwatch key={color} color={color} active={(accentColor ?? defaultAccent) === color} onClick={() => setAccentColor(color === defaultAccent ? null : color)} />
            ))}
            {/* A relative wrapper around the native color input - the input itself is made invisible
                but stays interactive and on top (opacity-0, not display:none), so clicking anywhere
                on our own custom-styled swatch still opens the OS color picker exactly as if the
                native input were the thing visibly clicked. */}
            <label className="relative flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full border border-dashed border-border text-muted transition-colors hover:border-accent/60 hover:text-text" title={t("settings.appearance.customColor")}>
              <Palette className="h-3.5 w-3.5" strokeWidth={2} />
              <CustomAccentInput value={accentColor ?? defaultAccent} onCommit={setAccentColor} />
            </label>
          </div>
        </SettingsRow>

        <SettingsRow icon={<Sparkles className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <p className="text-sm font-semibold text-text">{t("settings.appearance.backgroundTheme")}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("settings.appearance.backgroundThemeHint")}</p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <BackgroundThemeSwatch active={backgroundTheme === null} onClick={() => setBackgroundTheme(null)} label={t("settings.appearance.backgroundThemeNone")} />
            {BACKGROUND_THEME_PRESETS.map((preset) => (
              <BackgroundThemeSwatch key={preset.id} gradient={preset.gradient} active={backgroundTheme === preset.id} onClick={() => setBackgroundTheme(preset.id)} />
            ))}
            <BackgroundThemeSwatch
              icon={Palette}
              gradient={customBackgroundGradient ? customBackgroundGradientCss(customBackgroundGradient) : undefined}
              active={backgroundTheme === CUSTOM_BACKGROUND_THEME_ID}
              onClick={() => setBackgroundTheme(CUSTOM_BACKGROUND_THEME_ID)}
              label={t("settings.appearance.backgroundThemeCustom")}
            />
            {backgroundTheme === CUSTOM_BACKGROUND_THEME_ID && (
              <div className="flex items-center gap-1.5 pl-1">
                <GradientStopSwatch
                  value={customBackgroundGradient?.from ?? DEFAULT_ACCENT}
                  onCommit={(from) => setCustomBackgroundGradient({ from, to: customBackgroundGradient?.to ?? DEFAULT_ACCENT })}
                  label={t("settings.appearance.backgroundThemeCustomFrom")}
                />
                <GradientStopSwatch
                  value={customBackgroundGradient?.to ?? DEFAULT_ACCENT}
                  onCommit={(to) => setCustomBackgroundGradient({ from: customBackgroundGradient?.from ?? DEFAULT_ACCENT, to })}
                  label={t("settings.appearance.backgroundThemeCustomTo")}
                />
              </div>
            )}
          </div>
        </SettingsRow>

      </SettingsSection>
      )}

      {activeCategory === "general" && (
      <SettingsSection>
        {isMobile ? <MobileLanguagePicker /> : <SettingsRow icon={<Languages className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <p className="mb-2.5 text-sm font-semibold text-text">{t("settings.language")}</p>
          <div className="flex flex-wrap gap-2">
            {SUPPORTED_LOCALES.map((locale) => (
              <button
                key={locale}
                onClick={() => setLocale(locale)}
                className={cn(
                  "rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
                  i18n.language === locale ? "bg-accent text-accent-fg" : "bg-text/[.05] text-muted hover:bg-text/[.08] hover:text-text",
                )}
              >
                {t(`settings.languageNames.${locale}`)}
              </button>
            ))}
          </div>
        </SettingsRow>}

        <SettingsGroup>
          {/* Discord and the app's own updater are desktop programs; the phone has neither. */}
          {!isMobile && <SettingsToggleRow
            icon={<MessageCircle className="h-4 w-4" strokeWidth={2} />}
            title={t("settings.discord.enabled")}
            hint={t("settings.discord.hint")}
            checked={discordRpcEnabled}
            onChange={setDiscordRpcEnabled}
          />}
          {!isMobile && discordRpcEnabled && (
            <SettingsToggleRow
              indent
              title={t("settings.discord.ignoreNsfw")}
              hint={t("settings.discord.ignoreNsfwHint")}
              checked={discordIgnoreNsfwSources}
              onChange={setDiscordIgnoreNsfwSources}
            />
          )}
          {/* Notices exist where the host can show them (the phone). */}
          {hibiki.newEpisodes?.notify && <SettingsToggleRow
            icon={<Bell className="h-4 w-4" strokeWidth={2} />}
            title={t("newEpisodes.setting")}
            hint={t("newEpisodes.settingHint")}
            checked={newEpisodesEnabled}
            onChange={(value) => {
              setNewEpisodesEnabled(value);
              if (value) void hibiki.newEpisodes?.ensurePermission?.();
            }}
          />}
          <SettingsToggleRow
            icon={<ArrowDownToLine className="h-4 w-4" strokeWidth={2} />}
            title={t("settings.catalogAutoLoad.enabled")}
            hint={t("settings.catalogAutoLoad.hint")}
            checked={catalogAutoLoad}
            onChange={setCatalogAutoLoad}
          />
          {!isMobile && <SettingsToggleRow
            icon={<RefreshCw className="h-4 w-4" strokeWidth={2} />}
            title={t("settings.autoUpdate")}
            hint={t("settings.autoUpdateHint")}
            checked={autoUpdate}
            onChange={setAutoUpdate}
          />}
        </SettingsGroup>
      </SettingsSection>
      )}

      {activeCategory === "player" && (
      <SettingsSection>
        <SettingsRow icon={<Timer className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <SecondsControl
            label={t("settings.player.autoSkipDelay")}
            hint={t("settings.player.autoSkipDelayHint")}
            value={autoSkipDelaySeconds}
            onChange={setAutoSkipDelaySeconds}
          />
        </SettingsRow>
        <SettingsRow icon={<Timer className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <SecondsControl
            label={t("settings.player.skipButtonTimeout")}
            hint={t("settings.player.skipButtonTimeoutHint")}
            value={skipButtonTimeoutSeconds}
            onChange={setSkipButtonTimeoutSeconds}
          />
        </SettingsRow>
        <SettingsRow icon={<CheckCircle2 className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <PercentControl
            label={t("settings.player.watchedThreshold")}
            hint={t("settings.player.watchedThresholdHint")}
            value={watchedThresholdPercent}
            onChange={setWatchedThresholdPercent}
          />
        </SettingsRow>
        <UpscaleRow />
      </SettingsSection>
      )}

      {activeCategory === "network" && <NetworkSection />}
      {activeCategory === "tracking" && <div className="space-y-4"><DeviceSyncSection /><TrackingSection /></div>}
      {activeCategory === "home" && <HomeSortSection />}
      {activeCategory === "data" && <BackupSection />}
      {activeCategory === "diagnostics" && <DiagnosticsSection />}
    </div>
    </motion.div>
  </div>;
}

/**
 * Phone: the language as a row showing the current one, opening the list in a sheet - a row of
 * one chip per language read as a fixed set, and stops fitting as languages are added.
 */
function MobileLanguagePicker() {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className="block w-full rounded-2xl text-left active:opacity-80">
        <SettingsRow icon={<Languages className="h-[18px] w-[18px]" strokeWidth={2} />}>
          <div className="flex min-h-9 items-center justify-between gap-3">
            <span className="text-sm font-semibold text-text">{t("settings.language")}</span>
            <span className="flex items-center gap-1 text-sm text-muted">
              {t(`settings.languageNames.${i18n.language}`)}
              <ChevronRight className="h-4 w-4" strokeWidth={2} />
            </span>
          </div>
        </SettingsRow>
      </button>
      <BottomSheet open={open} onClose={() => setOpen(false)} title={t("settings.language")}>
        {SUPPORTED_LOCALES.map((locale) => (
          <SheetOption
            key={locale}
            label={t(`settings.languageNames.${locale}`)}
            selected={i18n.language === locale}
            onClick={() => { setLocale(locale); setOpen(false); }}
          />
        ))}
      </BottomSheet>
    </>
  );
}

/** Phone: the settings sections as one card of rows, like a system settings screen. */
function MobileSettingsList({ categories, onOpen }: {
  categories: Array<{ id: "appearance" | "general" | "player" | "network" | "tracking" | "home" | "data" | "diagnostics"; label: string; icon: typeof Sun }>;
  onOpen: (id: "appearance" | "general" | "player" | "network" | "tracking" | "home" | "data" | "diagnostics") => void;
}) {
  return (
    <motion.div initial={{ opacity: 0.6 }} animate={{ opacity: 1 }} transition={{ duration: 0.16 }} className="px-4 pb-6 pt-1">
      <div className="overflow-hidden rounded-2xl border border-border bg-text/[.03]">
        {categories.map(({ id, label, icon: Icon }, index) => (
          <button
            key={id}
            type="button"
            onClick={() => onOpen(id)}
            className={cn("flex w-full items-center gap-3.5 px-4 py-3.5 text-left active:bg-text/[.06]", index > 0 && "border-t border-border")}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-text/[.07] text-text"><Icon className="h-[18px] w-[18px]" strokeWidth={2} /></span>
            <span className="min-w-0 flex-1 truncate text-[15px] font-medium text-text">{label}</span>
            <ChevronRight className="h-5 w-5 shrink-0 text-muted" strokeWidth={2} />
          </button>
        ))}
      </div>
      <MobileVersionRow />
    </motion.div>
  );
}

/** The app's own version under the list; when a newer one is out, the way back to its sheet. */
function MobileVersionRow() {
  const { t } = useTranslation();
  const update = useAppUpdate();
  const openSheet = useUpdateFlow((s) => s.openSheet);
  const downloading = useUpdateFlow((s) => s.phase.kind === "downloading");
  const version = useQuery({ queryKey: ["appVersion"], queryFn: () => hibiki.app.getVersion(), staleTime: Infinity }).data;
  if (!version) return null;
  const content = <>
    <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-xl", update ? "bg-emerald-500/15 text-emerald-400" : "bg-text/[.07] text-text")}>
      {update ? <ArrowDownToLine className="h-[18px] w-[18px]" strokeWidth={2} /> : <Info className="h-[18px] w-[18px]" strokeWidth={2} />}
    </span>
    <span className="min-w-0 flex-1">
      <span className="block truncate text-[15px] font-medium text-text">{t("update.settingsVersion", { version })}</span>
      <span className={cn("block truncate text-[12.5px]", update ? "text-emerald-400" : "text-muted")}>
        {update ? t("update.settingsAvailable", { version: update.version }) : t("update.settingsLatest")}
      </span>
    </span>
    {update && (downloading
      ? <span className="h-2 w-2 shrink-0 animate-pulse rounded-full bg-emerald-400" />
      : <ChevronRight className="h-5 w-5 shrink-0 text-muted" strokeWidth={2} />)}
  </>;
  const shell = "mt-3 flex w-full items-center gap-3.5 rounded-2xl border border-border bg-text/[.03] px-4 py-3.5 text-left";
  return update
    ? <button type="button" onClick={openSheet} className={cn(shell, "active:bg-text/[.06]")}>{content}</button>
    : <div className={shell}>{content}</div>;
}

/**
 * A setting with a range: its value beside the label, and a slider styled to match the app (a thick
 * track filled up to the value and a round thumb). The arrow keys step it one by one.
 */
function SliderControl({ label, hint, value, min, max, display, onChange }: {
  label: string;
  hint: string;
  value: number;
  min: number;
  max: number;
  display: string;
  onChange: (value: number) => void;
}) {
  const fill = max > min ? ((value - min) / (max - min)) * 100 : 0;
  return <div>
    <div className="flex items-baseline justify-between gap-3">
      <p className="text-sm font-semibold text-text">{label}</p>
      <span className="shrink-0 text-sm font-semibold tabular-nums text-text">{display}</span>
    </div>
    <input
      type="range"
      min={min}
      max={max}
      value={value}
      onChange={(e) => onChange(Number(e.target.value))}
      aria-label={label}
      className="range-slider mt-3 w-full"
      style={{ "--fill": `${fill}%` } as React.CSSProperties}
    />
    <p className="mt-2 text-xs leading-relaxed text-muted">{hint}</p>
  </div>;
}
