import { useEffect, useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { useTranslation } from "react-i18next";
import { Download } from "lucide-react";
import { hibiki } from "@/lib/hibiki";

/**
 * Handles a "hibiki://watch/..." deep link - what this app's own Discord Rich Presence "Watch"
 * button opens (see main/discordRpc.ts and main/deepLink.ts). Goes straight to the exact
 * episode/dub when the source it came from is installed; otherwise asks to install that source
 * first, the same two-step shape as SignInPrompt (say what's missing, then a deliberate action to
 * go get it) rather than silently failing or dropping the visitor onto an unrelated page.
 */
export function DeepLinkHandler() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [missingSourceId, setMissingSourceId] = useState<string | null>(null);

  useEffect(() => {
    return hibiki.app.onDeepLinkWatch((target) => {
      void hibiki.sources.list().then((sources) => {
        if (sources.some((source) => source.id === target.sourceId)) {
          navigate({ to: "/watch/$sourceId/$animeId/$groupId/$episodeId", params: target });
        } else {
          setMissingSourceId(target.sourceId);
        }
      });
    });
  }, [navigate]);

  return (
    <AnimatePresence>
      {missingSourceId && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[60] bg-black/50"
            onClick={() => setMissingSourceId(null)}
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
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-text/[.06]">
                  <Download className="h-5 w-5 text-muted" strokeWidth={1.75} />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-bold text-text">{t("sources.installPrompt.title")}</p>
                  {/* The bare source id, not a display name - a source that isn't installed has never
                      told this app its own name (see knownSourcesStore), so there is nothing else
                      honest to show here. */}
                  <p className="truncate text-xs text-muted">{missingSourceId}</p>
                </div>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-text/80">
                {t("sources.installPrompt.body", { source: missingSourceId })}
              </p>
              <div className="mt-5 flex justify-end gap-2">
                <button
                  onClick={() => setMissingSourceId(null)}
                  className="rounded-xl border border-border px-3.5 py-2 text-sm font-semibold text-text/80 transition-colors hover:bg-text/[.06]"
                >
                  {t("common.cancel")}
                </button>
                <button
                  onClick={() => { setMissingSourceId(null); void navigate({ to: "/sources" }); }}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-accent px-3.5 py-2 text-sm font-bold text-accent-fg transition-[filter] hover:brightness-110"
                >
                  <Download className="h-4 w-4" strokeWidth={2.25} />
                  {t("sources.installPrompt.action")}
                </button>
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
