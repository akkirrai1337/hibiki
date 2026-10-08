import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { useTranslation } from "react-i18next";
import { LogIn, UserRound } from "lucide-react";
import type { SourceInfo } from "@shared/types";
import { SourceSettingsDialog } from "@/components/SourceSettingsDialog";

/**
 * The app-wide answer to "you have to be signed in for that".
 *
 * Every source action that needs an account - posting a comment, voting on one, pushing a rating -
 * used to handle a signed-out visitor on its own, and each one in a different way: a disabled
 * button that looked broken, a line of text pointing at the Sources page, a note after the fact.
 * The button stays live now and calls this instead, so the answer arrives where the press happened
 * and carries the way out with it.
 *
 * The way out is the source's own settings dialog, which already draws the sign-in block for any
 * source with an ACCOUNT setting - so this owns no login form of its own, and a source that changes
 * how it authenticates changes nothing here. Signing in there writes ["sourceAccount", id] straight
 * into the query cache, which is the same key every caller watches, so the action that was refused
 * becomes available without a refetch.
 */
const SignInPromptContext = createContext<((source: SourceInfo) => void) | null>(null);

/** Ask for the source's account. The caller decides when: it already knows whether one is there. */
export function useSignInPrompt(): (source: SourceInfo) => void {
  const prompt = useContext(SignInPromptContext);
  if (!prompt) throw new Error("useSignInPrompt used outside SignInPromptProvider");
  return prompt;
}

export function SignInPromptProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const [source, setSource] = useState<SourceInfo | null>(null);
  // Two steps rather than one: the prompt says which source is being asked for and why, and only a
  // deliberate "sign in" opens the settings dialog. Dropping someone straight into a password form
  // they never asked for, over the page they were reading, is a lot to do to a misplaced click.
  const [settingsOpen, setSettingsOpen] = useState(false);

  const prompt = useCallback((next: SourceInfo) => {
    setSettingsOpen(false);
    setSource(next);
  }, []);

  const close = useCallback(() => {
    setSource(null);
    setSettingsOpen(false);
  }, []);

  const value = useMemo(() => prompt, [prompt]);

  return (
    <SignInPromptContext.Provider value={value}>
      {children}
      <AnimatePresence>
        {source && !settingsOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-[60] bg-black/50"
              onClick={close}
            />
            <div className="pointer-events-none fixed inset-0 z-[61] flex items-center justify-center p-6">
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: 8 }}
                transition={{ type: "spring", stiffness: 500, damping: 45 }}
                className="pointer-events-auto w-full max-w-sm rounded-2xl border border-border bg-surface p-5 shadow-2xl"
              >
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-full bg-text/[.06]">
                    {source.iconUrl ? (
                      <img src={source.iconUrl} alt="" className="h-full w-full object-cover" />
                    ) : (
                      <UserRound className="h-5 w-5 text-muted" strokeWidth={1.75} />
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-bold text-text">{t("sources.signInPrompt.title")}</p>
                    <p className="truncate text-xs text-muted">{source.name}</p>
                  </div>
                </div>
                <p className="mt-3 text-sm leading-relaxed text-text/80">
                  {t("sources.signInPrompt.body", { source: source.name })}
                </p>
                <div className="mt-5 flex justify-end gap-2">
                  <button
                    onClick={close}
                    className="rounded-xl border border-border px-3.5 py-2 text-sm font-semibold text-text/80 transition-colors hover:bg-text/[.06]"
                  >
                    {t("common.cancel")}
                  </button>
                  <button
                    onClick={() => setSettingsOpen(true)}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-accent px-3.5 py-2 text-sm font-bold text-accent-fg transition-[filter] hover:brightness-110"
                  >
                    <LogIn className="h-4 w-4" strokeWidth={2.25} />
                    {t("sources.accountSignIn")}
                  </button>
                </div>
              </motion.div>
            </div>
          </>
        )}
      </AnimatePresence>
      {/* The dialog portals itself; rendering it here only decides that it is open. It stays open
          after a successful sign-in rather than closing itself - the same source usually has a
          library-sync switch worth a look at that moment, and the refused action is already live
          behind it. */}
      {source && settingsOpen && <SourceSettingsDialog source={source} onClose={close} />}
    </SignInPromptContext.Provider>
  );
}
