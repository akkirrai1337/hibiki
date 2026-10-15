// The parts of Settings > Network that core itself acts on, on every platform: how many episode
// downloads run at once and how long a source request may take. The renderer owns the settings
// and hands them over at start and on every change (hibiki.network.configure); until then the
// defaults below are what the app has always done. Also counts the bytes core's own requests
// bring in, for the traffic statistics.
import type { NetworkConfig } from "@shared/types";

export const DEFAULT_NETWORK_CONFIG: NetworkConfig = {
  maxDownloads: 2,
  requestTimeoutMs: 25_000,
  proxy: "",
  doh: "off",
};

let config: NetworkConfig = DEFAULT_NETWORK_CONFIG;
const listeners = new Set<(config: NetworkConfig) => void>();

export function getNetworkConfig(): NetworkConfig {
  return config;
}

export function setNetworkConfig(next: Partial<NetworkConfig>): void {
  config = {
    ...config,
    ...next,
    // Kept in the range the settings offer: a bad value from an old store must not stall downloads
    // (0) or outlive the extension worker's own 60s limit.
    maxDownloads: Math.min(4, Math.max(1, Math.round(next.maxDownloads ?? config.maxDownloads))),
    requestTimeoutMs: Math.min(50_000, Math.max(5_000, next.requestTimeoutMs ?? config.requestTimeoutMs)),
  };
  for (const listener of listeners) listener(config);
}

export function onNetworkConfigChange(listener: (config: NetworkConfig) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

// --- traffic ------------------------------------------------------------------------------------

let dataBytes = 0;

/** A response body core received from a source (catalog pages, episode lists, player pages). */
export function countDataBytes(bytes: number): void {
  if (bytes > 0) dataBytes += bytes;
}

/** Bytes counted since the app started. */
export function dataBytesThisSession(): number {
  return dataBytes;
}
