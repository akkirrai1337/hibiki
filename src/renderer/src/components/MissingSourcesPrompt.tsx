import { useEffect } from "react";
import { AnimatePresence } from "motion/react";
import { create } from "zustand";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Loader2, Radio, TriangleAlert } from "lucide-react";
import type { MissingSources } from "@shared/types";
import { Modal } from "@/components/Modal";
import { hibiki } from "@/lib/hibiki";

// Sources the synced data uses that this device lacks (core/api/sourceAutoInstall.ts). Another
// device's titles only open here once their source is installed, so after a sync brings such titles
// in, a window offers those sources once; after that the Sync screen keeps a note until they are in.

const MISSING_KEY = ["missingSources"];
// Sources already offered in the window: it opens again only for one that was not.
const OFFERED_STORAGE_KEY = "hibiki-missing-sources-offered";

const useMissingSourcesDialog = create<{ open: boolean; setOpen: (open: boolean) => void }>((set) => ({
  open: false,
  setOpen: (open) => set({ open }),
}));

function readOffered(): Set<string> {
  try {
    return new Set(JSON.parse(localStorage.getItem(OFFERED_STORAGE_KEY) ?? "[]") as string[]);
  } catch {
    return new Set();
  }
}

function rememberOffered(ids: string[]): void {
  try {
    localStorage.setItem(OFFERED_STORAGE_KEY, JSON.stringify([...new Set([...readOffered(), ...ids])]));
  } catch {
    // Not remembered: the window may simply come up once more after the next sync.
  }
}

function useMissingSources() {
  return useQuery({ queryKey: MISSING_KEY, queryFn: () => hibiki.sources.missingSources() });
}

/** Mounted once at the root: opens the window after a sync that brought in titles of uninstalled sources. */
export function MissingSourcesPrompt() {
  const queryClient = useQueryClient();
  const missing = useMissingSources();
  const open = useMissingSourcesDialog((s) => s.open);
  const setOpen = useMissingSourcesDialog((s) => s.setOpen);
  // Decided on each sync that brought data in, from a fresh answer - not from a change of the
  // cached one, which a sync leaving the same gap does not make. Never at start: only a sync opens it.
  useEffect(() => hibiki.sync.onChanged((what) => {
    if (what !== "data") return;
    void queryClient.fetchQuery({ queryKey: MISSING_KEY, queryFn: () => hibiki.sources.missingSources(), staleTime: 0 }).then((answer) => {
      const offered = readOffered();
      if (answer.available.some(({ extension }) => !offered.has(extension.id))) setOpen(true);
    }).catch(() => undefined);
  }), [queryClient, setOpen]);

  const close = () => {
    rememberOffered((missing.data?.available ?? []).map(({ extension }) => extension.id));
    setOpen(false);
  };

  return <AnimatePresence>{open && missing.data && <MissingSourcesDialog missing={missing.data} onClose={close} />}</AnimatePresence>;
}

function MissingSourcesDialog({ missing, onClose }: { missing: MissingSources; onClose: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const install = useMutation({
    mutationFn: async (items: MissingSources["available"]) => {
      for (const { extension, repositoryUrl } of items) await hibiki.sources.install(extension, repositoryUrl);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: MISSING_KEY }),
  });
  const { available } = missing;
  const unavailable = missing.unavailable.length;
  // Everything that could be installed is: nothing left to offer.
  useEffect(() => {
    if (available.length === 0 && !install.isPending) onClose();
  }, [available.length, install.isPending, onClose]);

  return (
    <Modal onDismiss={onClose}>
      <h2 className="text-base font-bold text-text">{t("deviceSync.missing.title")}</h2>
      <p className="mt-1.5 text-sm leading-relaxed text-muted">{t("deviceSync.missing.hint")}</p>
      <div className="mt-4 max-h-[50vh] space-y-1.5 overflow-y-auto">
        {available.map((item) => {
          const pending = install.isPending && install.variables?.some(({ extension }) => extension.id === item.extension.id);
          return (
            <div key={item.extension.id} className="flex items-center gap-2.5 rounded-xl bg-text/[.04] px-3 py-2">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-text/[.06]">
                {item.extension.iconUrl ? <img src={item.extension.iconUrl} alt="" className="h-full w-full object-cover" /> : <Radio className="h-4 w-4 text-muted" strokeWidth={2} />}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-text">{item.extension.name}</p>
                <p className="text-xs uppercase text-muted">{item.extension.lang}</p>
              </div>
              <button type="button" onClick={() => install.mutate([item])} disabled={install.isPending} className="flex h-8 min-w-[5.5rem] shrink-0 items-center justify-center rounded-lg bg-text/[.08] px-3 text-xs font-semibold text-text transition-colors hover:bg-text/[.14] disabled:opacity-60 mobile:rounded-full">
                {pending ? <Loader2 className="h-3.5 w-3.5 animate-spin" strokeWidth={2.25} /> : t("deviceSync.missing.install")}
              </button>
            </div>
          );
        })}
      </div>
      {unavailable > 0 && <p className="mt-2 text-xs text-muted">{t("deviceSync.missing.notFound", { count: unavailable })}</p>}
      {install.isError && <p className="mt-2 select-text text-xs text-rose-500">{(install.error as Error).message}</p>}
      <div className="mt-5 flex justify-end gap-2">
        <button type="button" onClick={onClose} className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]">{t("deviceSync.missing.later")}</button>
        <button type="button" onClick={() => install.mutate(available)} disabled={install.isPending} className="inline-flex items-center gap-1.5 rounded-lg bg-text px-3.5 py-2 text-sm font-bold text-bg transition-opacity hover:opacity-90 disabled:opacity-60">
          {install.isPending && install.variables?.length === available.length && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2.25} />}
          {t(available.length > 1 ? "deviceSync.missing.installAll" : "deviceSync.missing.install")}
        </button>
      </div>
    </Modal>
  );
}

/** The Sync screen's note while the synced data still has sources missing here. */
export function MissingSourcesNotice() {
  const { t } = useTranslation();
  const missing = useMissingSources();
  const setOpen = useMissingSourcesDialog((s) => s.setOpen);
  const available = missing.data?.available.length ?? 0;
  if (available === 0 && (missing.data?.unavailable.length ?? 0) === 0) return null;
  return (
    <div className="mt-3 flex items-start gap-2.5 rounded-xl border border-amber-400/25 bg-amber-400/[.06] px-3 py-2.5">
      <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" strokeWidth={2} />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-text">{t("deviceSync.missing.noticeTitle")}</p>
        <p className="mt-0.5 text-xs leading-relaxed text-muted">{t("deviceSync.missing.noticeText")}</p>
        {available > 0 && (
          <button type="button" onClick={() => setOpen(true)} className="mt-2 rounded-lg bg-text/[.08] px-3 py-1.5 text-xs font-semibold text-text transition-colors hover:bg-text/[.14] mobile:rounded-full">
            {t("deviceSync.missing.show")}
          </button>
        )}
      </div>
    </div>
  );
}
