import { motion, useIsPresent } from "motion/react";
import { cn } from "@/lib/cn";
import { X } from "lucide-react";

/** The app's one centered dialog shell: a click-to-dismiss scrim, a close button, and whatever the
 * caller puts inside. Wrap it in AnimatePresence so the exit transition actually plays. */
export function Modal({ onDismiss, children }: { onDismiss: () => void; children: React.ReactNode }) {
  // Leaving, it lets touches through at once: the card's spring kept the closed dialog's scrim on
  // the page, swallowing taps, for most of a second.
  const present = useIsPresent();
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.15 }}
      className={cn("fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 mobile:z-[90]", !present && "pointer-events-none")}
      onClick={onDismiss}
    >
      {/* No `scale` - see LibraryButton in anime.$sourceId.$animeId.tsx for why. */}
      <motion.div
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: 6 }}
        transition={{ type: "spring", stiffness: 420, damping: 32 }}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-sm rounded-2xl border border-border bg-surface p-5 shadow-2xl"
      >
        <button onClick={onDismiss} className="float-right -mr-1 -mt-1 flex h-7 w-7 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.06] hover:text-text">
          <X className="h-4 w-4" strokeWidth={2} />
        </button>
        {children}
      </motion.div>
    </motion.div>
  );
}
