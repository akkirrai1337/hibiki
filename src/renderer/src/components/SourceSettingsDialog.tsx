import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  ArrowDownToLine,
  ArrowLeftRight,
  ArrowUpFromLine,
  Clock,
  Library,
  LogOut,
  UserRound,
  X,
  type LucideIcon,
} from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { Switch } from "@/components/Switch";
import { cn } from "@/lib/cn";
import { planSize, planSync, type SyncDirection } from "@/lib/librarySync";
import type { SourceInfo, SourceSetting } from "@shared/types";

/**
 * One settings screen for every source.
 *
 * The app renders what a source declares - field types, not source names - so adding settings to a
 * source is a manifest change and nothing here has to learn about it. The one row that is not a
 * value is ACCOUNT: it stands for the sign-in block, which the app draws from the source's own
 * login/logout/getAccount, since a password field and a signed-in card are the same two states for
 * every source that has an account at all.
 */
export function SourceSettingsDialog({ source, onClose }: { source: SourceInfo; onClose: () => void }) {
  const { t } = useTranslation();
  const rows = source.settings ?? [];

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return createPortal(
    <>
      <div className="fixed inset-0 z-[60] bg-black/50" onClick={onClose} />
      <div className="pointer-events-none fixed inset-0 z-[61] flex items-center justify-center p-6">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: "spring", stiffness: 500, damping: 45 }}
          className="pointer-events-auto flex max-h-[80vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl"
        >
          <div className="flex items-center gap-3 border-b border-border px-5 py-4">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full bg-text/[.06]">
              {source.iconUrl ? <img src={source.iconUrl} alt="" className="h-full w-full object-cover" /> : null}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold text-text">{source.name}</p>
              <p className="truncate text-xs text-muted">{t("sources.settingsTitle")}</p>
            </div>
            <button
              onClick={onClose}
              className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.06] hover:text-text"
            >
              <X className="h-4 w-4" strokeWidth={2} />
            </button>
          </div>

          <div className="flex flex-col gap-5 overflow-y-auto p-5">
            {rows.length === 0 && (
              <p className="py-4 text-center text-sm text-muted">{t("sources.settingsEmpty")}</p>
            )}
            {rows.map((row) =>
              row.type === "ACCOUNT" ? (
                <AccountRow key={row.key} sourceId={source.id} row={row} />
              ) : row.type === "LIBRARY_SYNC" ? (
                <LibrarySyncRow key={row.key} sourceId={source.id} row={row} />
              ) : (
                <ValueRow key={row.key} sourceId={source.id} row={row} />
              ),
            )}
          </div>
        </motion.div>
      </div>
    </>,
    document.body,
  );
}

/**
 * A source's own wording, in the app's language when the source offers it.
 *
 * Sources write their settings rows in one language; the app is translated into several, and a
 * Russian screen with two English rows in the middle of it reads as broken. A source can supply
 * per-language wording, and anything it has not translated falls back to what it wrote.
 */
function useSourceText(row: SourceSetting): { title: string; description: string | null } {
  const { i18n } = useTranslation();
  const language = i18n.language.split("-")[0];
  return {
    title: row.titleI18n?.[language] ?? row.title,
    description: row.descriptionI18n?.[language] ?? row.description ?? null,
  };
}

/** Same per-language fallback as useSourceText, for the ACCOUNT row's own field labels - most
 * sources leave both unset and get the app's generic "Login"/"Password" wording. */
function useAccountFieldLabels(row: SourceSetting): { login: string; password: string } {
  const { t, i18n } = useTranslation();
  const language = i18n.language.split("-")[0];
  return {
    login: row.loginLabelI18n?.[language] ?? row.loginLabel ?? t("sources.accountLogin"),
    password: row.passwordLabelI18n?.[language] ?? row.passwordLabel ?? t("sources.accountPassword"),
  };
}

function RowHeader({ row }: { row: SourceSetting }) {
  const { title, description } = useSourceText(row);
  return (
    <div className="min-w-0 flex-1">
      <p className="text-sm font-semibold text-text">{title}</p>
      {description && <p className="mt-0.5 text-xs text-muted">{description}</p>}
    </div>
  );
}

function ValueRow({ sourceId, row }: { sourceId: string; row: SourceSetting }) {
  const queryClient = useQueryClient();
  const stored = useQuery({
    queryKey: ["sourceSettings", sourceId],
    queryFn: () => hibiki.sources.settings.read(sourceId),
  });
  const value = stored.data?.[row.key];

  const save = useMutation({
    mutationFn: (next: string | null) => hibiki.sources.settings.write(sourceId, row.key, next),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sourceSettings", sourceId] }),
  });

  if (row.type === "TOGGLE" || row.type === "ACTIVITY_SYNC") {
    // A stored value wins; the manifest's default only decides what an untouched toggle shows.
    const checked = value === undefined ? row.default === true : value === "true";
    return (
      <div className="flex items-start gap-4">
        <RowHeader row={row} />
        <Switch checked={checked} onChange={(next) => save.mutate(String(next))} />
      </div>
    );
  }

  if (row.type === "SELECT") {
    const options = row.options ?? [];
    const current = value ?? (typeof row.default === "string" ? row.default : options[0]?.id);
    return (
      <div className="flex flex-col gap-2">
        <RowHeader row={row} />
        <div className="flex flex-wrap gap-1.5">
          {options.map((option) => (
            <button
              key={option.id}
              onClick={() => save.mutate(option.id)}
              className={cn(
                "rounded-lg border px-2.5 py-1.5 text-xs font-semibold transition-colors",
                option.id === current ? "border-accent bg-accent text-accent-fg" : "border-border text-text/80 hover:bg-text/[.06]",
              )}
            >
              {option.title}
            </button>
          ))}
        </div>
      </div>
    );
  }

  return <TextRow row={row} value={value ?? (typeof row.default === "string" ? row.default : "")} onSave={(next) => save.mutate(next)} />;
}

function TextRow({ row, value, onSave }: { row: SourceSetting; value: string; onSave: (next: string) => void }) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  return (
    <div className="flex flex-col gap-2">
      <RowHeader row={row} />
      <input
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        // Committed on blur and on Enter rather than per keystroke: every save crosses to the main
        // process and rewrites the source's store on disk.
        onBlur={() => draft !== value && onSave(draft)}
        onKeyDown={(event) => {
          if (event.key === "Enter") event.currentTarget.blur();
        }}
        className="w-full rounded-lg border border-border bg-text/[.04] px-3 py-2 text-sm text-text outline-none focus:border-accent"
      />
    </div>
  );
}

function AccountRow({ sourceId, row }: { sourceId: string; row: SourceSetting }) {
  const { t } = useTranslation();
  const fieldLabels = useAccountFieldLabels(row);
  const queryClient = useQueryClient();
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const account = useQuery({
    queryKey: ["sourceAccount", sourceId],
    queryFn: () => hibiki.sources.account.get(sourceId),
    // Asking the source means a network round trip through the extension worker; the answer only
    // changes when someone signs in or out here, both of which invalidate it themselves.
    staleTime: 5 * 60_000,
  });

  const signIn = useMutation({
    mutationFn: () => hibiki.sources.account.login(sourceId, { login, password }),
    onSuccess: (signedIn) => {
      setPassword("");
      setError(null);
      queryClient.setQueryData(["sourceAccount", sourceId], signedIn);
    },
    onError: (failure: unknown) => setError(messageOf(failure)),
  });

  // The site's own login page, in a real window, instead of a login+password pair this app never
  // collects - see webLoginUrl on SourceSetting. Mutually exclusive with the form above: a source
  // declares one path or the other, never both at once.
  const signInWeb = useMutation({
    mutationFn: () => hibiki.sources.account.loginWeb(sourceId),
    onSuccess: (signedIn) => {
      setError(null);
      queryClient.setQueryData(["sourceAccount", sourceId], signedIn);
    },
    onError: (failure: unknown) => setError(messageOf(failure)),
  });

  const signOut = useMutation({
    mutationFn: () => hibiki.sources.account.logout(sourceId),
    onSuccess: () => queryClient.setQueryData(["sourceAccount", sourceId], null),
    onError: (failure: unknown) => setError(messageOf(failure)),
  });

  const busy = signIn.isPending || signInWeb.isPending || signOut.isPending;
  const signedIn = account.data ?? null;

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border p-4">
      <RowHeader row={row} />
      <AnimatePresence mode="wait" initial={false}>
        {account.isLoading ? (
          <p key="loading" className="text-sm text-muted">…</p>
        ) : signedIn ? (
          <motion.div key="in" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-text/[.06]">
              {signedIn.avatarUrl ? (
                <img src={signedIn.avatarUrl} alt="" className="h-full w-full object-cover" />
              ) : (
                <UserRound className="h-4 w-4 text-muted" strokeWidth={1.75} />
              )}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold text-text">{signedIn.name}</p>
              {signedIn.profileUrl && (
                <a href={signedIn.profileUrl} target="_blank" rel="noreferrer" className="truncate text-xs text-muted hover:text-text">
                  {t("sources.accountOpenProfile")}
                </a>
              )}
            </div>
            <button
              onClick={() => signOut.mutate()}
              disabled={busy}
              className="flex shrink-0 items-center gap-1.5 rounded-lg border border-border px-2.5 py-1.5 text-xs font-bold text-text/80 transition-colors hover:bg-text/[.06] disabled:opacity-50"
            >
              <LogOut className="h-3.5 w-3.5" strokeWidth={2} />
              {t("sources.accountSignOut")}
            </button>
          </motion.div>
        ) : row.webLoginUrl ? (
          <motion.div key="out-web" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <button
              onClick={() => signInWeb.mutate()}
              disabled={busy}
              className="w-full rounded-lg bg-text px-3 py-2 text-sm font-bold text-bg transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              {signInWeb.isPending ? t("sources.accountSigningIn") : t("sources.accountSignInOnSite")}
            </button>
          </motion.div>
        ) : (
          <motion.form
            key="out"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onSubmit={(event) => {
              event.preventDefault();
              if (login.trim().length > 0 && password.length > 0) signIn.mutate();
            }}
            className="flex flex-col gap-2"
          >
            <input
              value={login}
              onChange={(event) => setLogin(event.target.value)}
              placeholder={fieldLabels.login}
              autoComplete="username"
              className="w-full rounded-lg border border-border bg-text/[.04] px-3 py-2 text-sm text-text outline-none focus:border-accent"
            />
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={fieldLabels.password}
              type="password"
              autoComplete="current-password"
              className="w-full rounded-lg border border-border bg-text/[.04] px-3 py-2 text-sm text-text outline-none focus:border-accent"
            />
            <button
              type="submit"
              disabled={busy || login.trim().length === 0 || password.length === 0}
              className="self-end rounded-lg bg-text px-3 py-1.5 text-xs font-bold text-bg transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              {t("sources.accountSignIn")}
            </button>
          </motion.form>
        )}
      </AnimatePresence>
      {/* The source's own message, not a generic failure: "no user with that login" is worth
          reading, and only the source knows it. */}
      {error && <p className="select-text text-xs text-rose-400">{error}</p>}
    </div>
  );
}

/**
 * The library-sync switch, and the one question it has to ask.
 *
 * Turning it on is the only moment where both libraries already hold things and disagree. Someone
 * with fifty titles here and three in their account wants the fifty pushed; someone with ten here
 * and a hundred there wants the hundred pulled. Neither is the right default, so the switch stops
 * and asks - with the real counts, because a choice between "push" and "pull" is meaningless
 * without knowing which side is bigger.
 *
 * Turning it off asks nothing: stopping is never destructive.
 */
function LibrarySyncRow({ sourceId, row }: { sourceId: string; row: SourceSetting }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [choosing, setChoosing] = useState(false);
  const [confirmingPull, setConfirmingPull] = useState(false);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const cancelled = useRef(false);

  const { title: rowTitle } = useSourceText(row);
  const stored = useQuery({
    queryKey: ["sourceSettings", sourceId],
    queryFn: () => hibiki.sources.settings.read(sourceId),
  });
  const enabled = stored.data?.[row.key] === undefined ? row.default === true : stored.data?.[row.key] === "true";

  const save = useMutation({
    mutationFn: (next: boolean) => hibiki.sources.settings.write(sourceId, row.key, String(next)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sourceSettings", sourceId] }),
  });

  // Both sides, fetched only once the switch is actually being turned on - listLibrary is a real
  // network walk through the account's pages, not something to do on every settings visit.
  const sides = useQuery({
    queryKey: ["librarySyncSides", sourceId],
    enabled: choosing,
    queryFn: async () => {
      const [localAll, remote] = await Promise.all([hibiki.library.list(), hibiki.sources.listLibrary(sourceId)]);
      return { local: localAll.filter((entry) => entry.sourceId === sourceId), remote };
    },
    staleTime: 0,
    gcTime: 0,
  });

  async function run(direction: SyncDirection) {
    if (!sides.data) return;
    const plan = planSync(direction, sides.data.local, sides.data.remote);
    const total = planSize(plan);
    cancelled.current = false;
    setError(null);
    setProgress({ done: 0, total });

    try {
      let done = 0;
      // One write at a time with a pause between them: this API rate limits, and fifty requests
      // fired at once is how a first sync turns into a wall of failures.
      for (const entry of plan.push) {
        if (cancelled.current) break;
        await hibiki.sources.syncLibraryEntry(sourceId, { animeId: entry.animeId, category: entry.category });
        setProgress({ done: ++done, total });
        await sleep(SYNC_WRITE_GAP_MS);
      }
      for (const entry of plan.pull) {
        if (cancelled.current) break;
        // Enough of a title to store and to draw a card from; opening it fills in the rest from
        // the source, the same way a title added from search does.
        await hibiki.library.upsert({
          animeId: entry.animeId,
          sourceId,
          category: entry.category,
          addedAt: Date.now(),
          anime: { id: entry.animeId, sourceId, russianName: entry.title ?? null, posterUrl: entry.posterUrl ?? null },
        });
        setProgress({ done: ++done, total });
      }
      for (const entry of plan.removeLocal) {
        if (cancelled.current) break;
        await hibiki.library.remove(sourceId, entry.animeId);
        setProgress({ done: ++done, total });
      }

      if (!cancelled.current) {
        save.mutate(true);
        setChoosing(false);
      }
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setProgress(null);
      setConfirmingPull(false);
      queryClient.invalidateQueries({ queryKey: ["library"] });
    }
  }

  const plans = sides.data
    ? {
        push: planSync("push", sides.data.local, sides.data.remote),
        pull: planSync("pull", sides.data.local, sides.data.remote),
        merge: planSync("merge", sides.data.local, sides.data.remote),
      }
    : null;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-start gap-4">
        <RowHeader row={row} />
        <Switch
          // `choosing` keeps it up while the question is open: flipping back down under a dialog
          // asking about it would read as the switch refusing.
          checked={enabled || choosing}
          onChange={(next) => {
            setError(null);
            if (next) setChoosing(true);
            else {
              setChoosing(false);
              save.mutate(false);
            }
          }}
        />
      </div>

      {choosing && (
        <SyncChoiceDialog
          title={rowTitle}
          // Only while nothing is being written: stopping half way through a first sync leaves
          // both libraries in a state nobody chose, so the way out during a run is Stop, not Esc.
          onDismiss={progress ? undefined : () => { setChoosing(false); setConfirmingPull(false); }}
        >
          {sides.isLoading && <p className="text-sm text-muted">{t("sources.syncCounting")}</p>}
          {sides.isError && <p className="select-text text-xs text-rose-400">{messageOf(sides.error)}</p>}

          {sides.data && plans && !progress && (
            <>
              <div className="flex items-center gap-2 rounded-lg bg-text/[.04] px-3 py-2">
                <Library className="h-4 w-4 shrink-0 text-muted" strokeWidth={2} />
                <p className="text-sm text-text">
                  {t("sources.syncCounts", { here: sides.data.local.length, there: sides.data.remote.length })}
                </p>
              </div>
              {confirmingPull ? (
                <>
                  {/* The only choice that deletes, so it says how many and asks again. */}
                  <div className="flex items-start gap-2">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" strokeWidth={2} />
                    <p className="text-sm text-rose-400">
                      {t("sources.syncPullWarning", { count: plans.pull.removeLocal.length })}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <SyncButton label={t("sources.syncConfirmPull")} danger onClick={() => run("pull")} />
                    <SyncButton label={t("sources.syncBack")} onClick={() => setConfirmingPull(false)} />
                  </div>
                </>
              ) : (
                <div className="flex flex-col gap-2">
                  {/* An icon each, and the destructive one in red: the four differ by direction
                      more than by wording, and an arrow says a direction faster than a sentence
                      does. */}
                  <SyncChoice
                    icon={ArrowUpFromLine}
                    title={t("sources.syncPushTitle")}
                    description={t("sources.syncPushDescription", { count: plans.push.push.length })}
                    onClick={() => run("push")}
                  />
                  <SyncChoice
                    icon={ArrowDownToLine}
                    danger={plans.pull.removeLocal.length > 0}
                    title={t("sources.syncPullTitle")}
                    description={t("sources.syncPullDescription", { count: plans.pull.pull.length })}
                    onClick={() => (plans.pull.removeLocal.length > 0 ? setConfirmingPull(true) : run("pull"))}
                  />
                  <SyncChoice
                    icon={ArrowLeftRight}
                    title={t("sources.syncMergeTitle")}
                    description={t("sources.syncMergeDescription", {
                      up: plans.merge.push.length,
                      down: plans.merge.pull.length,
                    })}
                    onClick={() => run("merge")}
                  />
                  <SyncChoice
                    icon={Clock}
                    title={t("sources.syncForwardTitle")}
                    description={t("sources.syncForwardDescription")}
                    onClick={() => run("forward")}
                  />
                </div>
              )}
            </>
          )}

          {progress && (
            <div className="flex flex-col gap-2">
              <p className="text-sm text-text">{t("sources.syncProgress", { done: progress.done, total: progress.total })}</p>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-text/[.10]">
                <div
                  className="h-full rounded-full bg-accent transition-[width]"
                  style={{ width: `${progress.total === 0 ? 100 : (progress.done / progress.total) * 100}%` }}
                />
              </div>
              <SyncButton label={t("sources.syncCancel")} onClick={() => { cancelled.current = true; }} />
            </div>
          )}

          {error && <p className="select-text text-xs text-rose-400">{error}</p>}
        </SyncChoiceDialog>
      )}
    </div>
  );
}

/**
 * The reconciliation question, in front of the settings screen rather than inside it.
 *
 * Four choices and a warning do not belong in a list of switches - inline, the settings screen had
 * to be scrolled to read a decision that has to be made now, and the row it belonged to scrolled
 * out of sight while reading it. Its own dialog also gives the destructive choice somewhere to ask
 * again without the list shifting under the pointer.
 */
function SyncChoiceDialog({
  title,
  onDismiss,
  children,
}: {
  title: string;
  /** Absent while a run is in progress - see the call site. */
  onDismiss?: () => void;
  children: React.ReactNode;
}) {
  const { t } = useTranslation();
  useEffect(() => {
    if (!onDismiss) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      // Stops the settings screen behind this one from closing too - the topmost thing on screen
      // is what Escape is for.
      event.stopPropagation();
      onDismiss();
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onDismiss]);

  return createPortal(
    <>
      <div className="fixed inset-0 z-[70] bg-black/50" onClick={onDismiss} />
      <div className="pointer-events-none fixed inset-0 z-[71] flex items-center justify-center p-6">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: "spring", stiffness: 500, damping: 45 }}
          className="pointer-events-auto flex max-h-[80vh] w-full max-w-md flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl"
        >
          <div className="flex items-center gap-3 border-b border-border px-5 py-4">
            <p className="min-w-0 flex-1 truncate text-sm font-bold text-text">{title}</p>
            {onDismiss && (
              <button
                onClick={onDismiss}
                title={t("sources.syncBack")}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.06] hover:text-text"
              >
                <X className="h-4 w-4" strokeWidth={2} />
              </button>
            )}
          </div>
          <div className="flex flex-col gap-3 overflow-y-auto p-5">{children}</div>
        </motion.div>
      </div>
    </>,
    document.body,
  );
}

function SyncChoice({
  icon: Icon,
  title,
  description,
  onClick,
  danger = false,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className="flex items-start gap-3 rounded-lg border border-border px-3 py-2.5 text-left transition-colors hover:bg-text/[.06]"
    >
      <span
        className={cn(
          "mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg",
          danger ? "bg-rose-400/[.12] text-rose-400" : "bg-text/[.06] text-muted",
        )}
      >
        <Icon className="h-4 w-4" strokeWidth={2} />
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-semibold text-text">{title}</span>
        <span className="mt-0.5 block text-xs text-muted">{description}</span>
      </span>
    </button>
  );
}

function SyncButton({ label, onClick, danger = false }: { label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "self-start rounded-lg border px-3 py-1.5 text-xs font-bold transition-colors",
        danger ? "border-rose-400/40 text-rose-400 hover:bg-rose-400/10" : "border-border text-text/80 hover:bg-text/[.06]",
      )}
    >
      {label}
    </button>
  );
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Enough of a gap that a first sync of a large library reads as steady work rather than a burst
// the API will start refusing.
const SYNC_WRITE_GAP_MS = 350;

function messageOf(failure: unknown): string {
  const raw = failure instanceof Error ? failure.message : String(failure);
  // Electron wraps a main-process throw as "Error invoking remote method '...': Error: <message>".
  return raw.replace(/^Error invoking remote method '[^']*':\s*/, "").replace(/^Error:\s*/, "");
}
