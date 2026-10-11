// Whether the connection is slow enough to start videos in a lighter quality (data saver "auto").
//
// Two signals, the better one first:
//  - what this app itself measured: how fast the last video segments actually downloaded. That is
//    the real speed to the CDN that matters, on whatever network the phone is on right now;
//  - the browser's own estimate (navigator.connection: effectiveType/downlink) and the system's
//    data saver switch (saveData), for before anything has been played.
// A network change (Wi-Fi -> mobile data) throws the measurement away: it described another network.
import { useSyncExternalStore } from "react";
import { usePlayerPrefsStore } from "@/stores/playerPrefsStore";

interface NetworkInformationLike extends EventTarget {
  effectiveType?: string;
  downlink?: number;
  rtt?: number;
  saveData?: boolean;
}

// Below this a 720p stream (Kodik's run about 1.4 Mbit/s) still plays, but its first segment takes
// seconds to arrive, and every seek waits the same again.
const SLOW_MBPS = 3;
// Old measurements describe where the phone was, not where it is.
const MEASUREMENT_TTL_MS = 10 * 60 * 1000;
// A response this small measures the round trip, not the bandwidth.
const MIN_MEASURED_BYTES = 200_000;

const connection = typeof navigator !== "undefined"
  ? (navigator as Navigator & { connection?: NetworkInformationLike }).connection
  : undefined;

let measured: { mbps: number; at: number } | null = null;
const listeners = new Set<() => void>();
const notify = () => { for (const listener of listeners) listener(); };

connection?.addEventListener?.("change", () => {
  measured = null;
  notify();
});

function freshMeasurement() {
  return measured && Date.now() - measured.at < MEASUREMENT_TTL_MS ? measured : null;
}

/** One finished download, from the player: `bytes` received over `ms` of transfer. */
export function recordThroughput(bytes: number, ms: number): void {
  if (bytes < MIN_MEASURED_BYTES || !(ms > 0)) return;
  const wasSlow = isSlowConnection();
  const mbps = (bytes * 8) / ms / 1000;
  const previous = freshMeasurement();
  // Smoothed, so one segment from a briefly busy edge node doesn't flip the verdict.
  measured = { mbps: previous ? previous.mbps * 0.6 + mbps * 0.4 : mbps, at: Date.now() };
  if (isSlowConnection() !== wasSlow) notify();
}

export function isSlowConnection(): boolean {
  if (connection?.saveData) return true;
  const fresh = freshMeasurement();
  if (fresh) return fresh.mbps < SLOW_MBPS;
  const type = connection?.effectiveType;
  if (type === "slow-2g" || type === "2g" || type === "3g") return true;
  return typeof connection?.downlink === "number" && connection.downlink > 0 && connection.downlink < SLOW_MBPS / 2;
}

/** Whether data saver applies right now - read at the moment a decision is made, not subscribed to,
 * so a verdict changing mid-episode never reloads the stream that is playing. */
export function isDataSaverActive(): boolean {
  const mode = usePlayerPrefsStore.getState().dataSaver;
  return mode === "on" || (mode === "auto" && isSlowConnection());
}

/** For log lines: what the verdict was made from. */
export function describeConnection(): string {
  const fresh = freshMeasurement();
  const parts = [
    fresh ? `measured ${fresh.mbps.toFixed(1)} Mbit/s` : null,
    connection?.effectiveType ? `effectiveType=${connection.effectiveType}` : null,
    typeof connection?.downlink === "number" ? `downlink=${connection.downlink}` : null,
    connection?.saveData ? "saveData" : null,
  ].filter(Boolean);
  return parts.join(", ") || "no connection info";
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

/** The live verdict, for showing it (Settings) - never for choosing what plays. */
export function useSlowConnection(): boolean {
  return useSyncExternalStore(subscribe, isSlowConnection);
}
