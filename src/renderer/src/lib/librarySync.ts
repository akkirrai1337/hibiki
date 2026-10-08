import type { LibraryEntry, SourceLibraryEntry } from "@shared/types";

/**
 * What turning library sync on should actually do, worked out before anything is written.
 *
 * The interesting case is not the mechanics, it is that both sides already have things in them and
 * they disagree. Someone with fifty titles here and three in their account wants the fifty pushed;
 * someone with ten here and a hundred there wants the hundred pulled. Neither is "the" right
 * answer, so the choice is the user's - this module only says, for a given choice, exactly what
 * will be written and what will be removed, so the screen can show that before doing it.
 *
 * Kept pure and away from the screen so the counts shown and the work done come from one place.
 */
export type SyncDirection = "push" | "pull" | "merge" | "forward";

export interface SyncPlan {
  /** Rows to send to the account. */
  push: LibraryEntry[];
  /** Rows to add to this app's library, or to move to a different category in it. */
  pull: SourceLibraryEntry[];
  /** Rows to delete from this app's library - only ever a "pull" that replaces. */
  removeLocal: LibraryEntry[];
}

const EMPTY: SyncPlan = { push: [], pull: [], removeLocal: [] };

/**
 * @param local  this app's library, already narrowed to the source being synced
 * @param remote what the account says it holds
 */
export function planSync(
  direction: SyncDirection,
  local: LibraryEntry[],
  remote: SourceLibraryEntry[],
): SyncPlan {
  const remoteById = new Map(remote.map((entry) => [entry.animeId, entry]));
  const localById = new Map(local.map((entry) => [entry.animeId, entry]));

  switch (direction) {
    // Nothing is reconciled; sync starts applying to changes made from now on. The honest choice
    // for someone who does not want either side rewritten today.
    case "forward":
      return EMPTY;

    // Local wins everywhere. Rows already identical on both sides are left alone - re-sending
    // fifty unchanged titles is fifty requests that change nothing.
    case "push":
      return {
        ...EMPTY,
        push: local.filter((entry) => remoteById.get(entry.animeId)?.category !== entry.category),
      };

    // The account wins, including by deletion: a local row the account does not have goes away.
    // This is the destructive one, which is why removeLocal is reported separately and the screen
    // says how many rows it is.
    case "pull":
      return {
        push: [],
        pull: remote.filter((entry) => localById.get(entry.animeId)?.category !== entry.category),
        removeLocal: local.filter((entry) => !remoteById.has(entry.animeId)),
      };

    // Both directions, and nothing is deleted. Where the two disagree about a title they both
    // have, local wins - the button was pressed here.
    case "merge":
      return {
        push: local.filter((entry) => remoteById.get(entry.animeId)?.category !== entry.category),
        pull: remote.filter((entry) => !localById.has(entry.animeId)),
        removeLocal: [],
      };
  }
}

/** Total writes a plan will make, for a progress bar to count against. */
export function planSize(plan: SyncPlan): number {
  return plan.push.length + plan.pull.length + plan.removeLocal.length;
}
