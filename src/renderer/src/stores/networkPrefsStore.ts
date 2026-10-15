import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { DohProvider } from "@shared/types";

/** How often Home asks the source again instead of drawing the rows saved last time. */
export type HomeRefreshMode = "launch" | "day" | "3days" | "manual";
/** How far ahead of the playhead the player downloads. */
export type BufferMode = "low" | "normal" | "high";

export const BUFFER_SECONDS: Record<BufferMode, number> = { low: 20, normal: 40, high: 90 };

interface NetworkPrefsState {
  homeRefresh: HomeRefreshMode;
  bufferMode: BufferMode;
  /** Phone only: on mobile data, ask before an episode download starts. */
  wifiOnlyDownloads: boolean;
  /** Phone only: on mobile data, don't start the next episode by itself. */
  wifiOnlyAutoplay: boolean;
  /** Episode downloads running at once, 1-4. */
  maxDownloads: number;
  /** How long one source request may take, in seconds. */
  requestTimeoutSeconds: number;
  /** Desktop only. */
  proxy: string;
  /** Desktop only. */
  doh: DohProvider;
  setMaxDownloads: (count: number) => void;
  setRequestTimeoutSeconds: (seconds: number) => void;
  setProxy: (proxy: string) => void;
  setDoh: (doh: DohProvider) => void;
  setHomeRefresh: (mode: HomeRefreshMode) => void;
  setBufferMode: (mode: BufferMode) => void;
  setWifiOnlyDownloads: (enabled: boolean) => void;
  setWifiOnlyAutoplay: (enabled: boolean) => void;
}

export const useNetworkPrefsStore = create<NetworkPrefsState>()(
  persist(
    (set) => ({
      homeRefresh: "3days",
      bufferMode: "normal",
      wifiOnlyDownloads: false,
      wifiOnlyAutoplay: false,
      maxDownloads: 2,
      requestTimeoutSeconds: 25,
      proxy: "",
      doh: "off",
      setMaxDownloads: (maxDownloads) => set({ maxDownloads }),
      setRequestTimeoutSeconds: (requestTimeoutSeconds) => set({ requestTimeoutSeconds }),
      setProxy: (proxy) => set({ proxy: proxy.trim() }),
      setDoh: (doh) => set({ doh }),
      setHomeRefresh: (homeRefresh) => set({ homeRefresh }),
      setBufferMode: (bufferMode) => set({ bufferMode }),
      setWifiOnlyDownloads: (wifiOnlyDownloads) => set({ wifiOnlyDownloads }),
      setWifiOnlyAutoplay: (wifiOnlyAutoplay) => set({ wifiOnlyAutoplay }),
    }),
    { name: "hibiki-network-prefs" },
  ),
);
