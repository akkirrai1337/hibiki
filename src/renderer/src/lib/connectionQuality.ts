// How fast the connection is, and so what quality an episode starts at (data saver "auto").
//
// Two signals, the better one first:
//  - what this app itself measured: how fast the last video segments actually downloaded. That is
//    the real speed to the CDN that matters, on whatever network the phone is on right now;
//  - the browser's network type (navigator.connection.effectiveType) and the system's
//    data saver switch (saveData), for before anything has been played.
// A network change (Wi-Fi -> mobile data) throws the measurement away: it described another network.
import { useSyncExternalStore } from "react";
import { usePlayerPrefsStore } from "@/stores/playerPrefsStore";

interface NetworkInformationLike extends EventTarget {
  type?: string;
  effectiveType?: string;
  downlink?: number;
  rtt?: number;
  saveData?: boolean;
}

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
  const wasCap = autoQualityCap();
  const mbps = (bytes * 8) / ms / 1000;
  const previous = freshMeasurement();
  // Smoothed, so one segment from a briefly busy edge node doesn't flip the verdict.
  measured = { mbps: previous ? previous.mbps * 0.6 + mbps * 0.4 : mbps, at: Date.now() };
  if (autoQualityCap() !== wasCap) notify();
}

/** Speed tiers for "auto": the tallest picture a connection this fast plays without waiting.
 * HLS anime streams run about 1.4 Mbit/s at 720p and 2-4 at 1080p; each tier leaves headroom
 * for the CDN being slower than the line. Faster than the last tier: no limit at all. */
const QUALITY_TIERS: Array<{ minMbps: number; maxHeight: number }> = [
  { minMbps: 15, maxHeight: Infinity },
  { minMbps: 6, maxHeight: 1080 },
  { minMbps: 3, maxHeight: 720 },
  { minMbps: 1.5, maxHeight: 480 },
  { minMbps: 0, maxHeight: 360 },
];

/** The best guess at the line's speed: this app's own measurement (video segments as they
 * arrived), or the browser's network type when it is plainly slow. Not navigator.connection's
 * downlink: Chromium reports a fixed 1.65 Mbit/s until it has samples of its own, and Electron
 * seen live never got past it - that would hold every fast line to 480p. */
function estimatedMbps(): number | null {
  const fresh = freshMeasurement();
  if (fresh) return fresh.mbps;
  const type = connection?.effectiveType;
  if (type === "slow-2g" || type === "2g") return 0.5;
  if (type === "3g") return 1.5;
  return null;
}

/**
 * The tallest picture an episode should start at, or null for no limit - read at the moment a
 * decision is made, never subscribed to, so a verdict changing mid-episode never reloads the
 * stream that is playing. Data saver "on" holds to 480p; "auto" picks by speed (QUALITY_TIERS);
 * "off", or no idea of the speed at all, leaves the source's own pick alone.
 */
export function autoQualityCap(): number | null {
  const mode = usePlayerPrefsStore.getState().dataSaver;
  if (mode === "off") return null;
  if (mode === "on" || connection?.saveData) return 480;
  const mbps = estimatedMbps();
  if (mbps === null) return null;
  const tier = QUALITY_TIERS.find((t) => mbps >= t.minMbps) ?? QUALITY_TIERS[QUALITY_TIERS.length - 1];
  return Number.isFinite(tier.maxHeight) ? tier.maxHeight : null;
}

/** Whether the connection calls for the light end (480p or lower) - what sources are told when
 * they can pick a lighter stream themselves, and what keeps Home's saved rows longer. */
export function isDataSaverActive(): boolean {
  const cap = autoQualityCap();
  return cap !== null && cap <= 480;
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

/** The live quality cap, for showing it (Settings) - never for choosing what plays. */
export function useAutoQualityCap(): number | null {
  return useSyncExternalStore(subscribe, autoQualityCap);
}

/** Mobile data (Android WebView reports it; desktop Chromium does not). Not a reason to lower video
 * quality on its own, but a reason not to re-download things the app already has. */
export function isCellular(): boolean {
  return connection?.type === "cellular";
}
