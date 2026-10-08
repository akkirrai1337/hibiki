import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Loader2, ShieldCheck } from "lucide-react";
import { cloudflareCheckOf } from "@shared/cloudflare";
import { hibiki } from "@/lib/hibiki";
import { cn } from "@/lib/cn";

/** What to say instead of a source error that is really Cloudflare's check, or null for any other error. */
export function useCloudflareText(error: unknown): string | null {
  const { t } = useTranslation();
  const check = cloudflareCheckOf(error);
  return check ? t("common.cloudflareBlocked", { host: check.host }) : null;
}

/**
 * The way past a source error that is Cloudflare's check (core/extensions/cloudflare.ts): the site in a
 * window of its own to pass the check in, then everything that failed loads again - this screen and
 * any other that stopped at the same check. Renders nothing for any other error.
 */
export function CloudflareCheckButton({ error, onPassed, className }: { error: unknown; onPassed?: () => void; className?: string }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const check = cloudflareCheckOf(error);
  const solve = useMutation({
    mutationFn: (url: string) => hibiki.sources.solveChallenge(url),
    onSuccess: (passed) => {
      if (!passed) return;
      void queryClient.invalidateQueries({ predicate: (query) => query.state.status === "error" });
      onPassed?.();
    },
  });
  if (!check) return null;
  return (
    <button
      onClick={() => solve.mutate(check.url)}
      disabled={solve.isPending}
      className={cn("inline-flex items-center gap-1.5 disabled:opacity-60", className)}
    >
      {solve.isPending ? <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} /> : <ShieldCheck className="h-4 w-4" strokeWidth={2} />}
      {solve.isPending ? t("common.cloudflareSolving") : t("common.cloudflareSolve")}
    </button>
  );
}
