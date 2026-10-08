import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { TrackerId } from "@shared/types";
import { hibiki } from "@/lib/hibiki";

/** Every tracking query lives under this key, so one event refreshes them all. */
export const TRACKING_KEY = "tracking";

/**
 * Keeps tracking queries current: a sign-in finishing in the browser, a link made in the background,
 * a push that moved a title's progress there - the backend announces each, and whatever is on
 * screen reads again.
 */
export function useTrackingRefresh(): void {
  const queryClient = useQueryClient();
  useEffect(() => hibiki.tracking.onChanged(() => void queryClient.invalidateQueries({ queryKey: [TRACKING_KEY] })), [queryClient]);
}

export function useTrackerAccount(tracker: TrackerId = "anilist") {
  useTrackingRefresh();
  return useQuery({ queryKey: [TRACKING_KEY, tracker, "account"], queryFn: () => hibiki.tracking.account(tracker) });
}

/** Signed in and the sign-in still good: tracking is live. */
export function isTracking(account: { user: unknown; needsSignIn: boolean } | undefined): boolean {
  return !!account?.user && !account.needsSignIn;
}
