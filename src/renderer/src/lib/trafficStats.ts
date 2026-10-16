// Settings > Network's traffic statistics: what the app downloaded each day, by what it was for.
// Source requests (core) and pictures (desktop only) are counted by the host and read from it every
// half minute; video is counted here, segment by segment, as the player receives it. Kept in
// localStorage, one entry per day for the last DAYS_KEPT days - a viewer's own numbers, nothing
// that must survive clearing app data.
import { useSyncExternalStore } from "react";
import { hibiki } from "@/lib/hibiki";
import { log } from "@/lib/log";
import { createHostTally } from "@shared/trafficByHost";

const STORAGE_KEY = "hibiki-traffic-days";
const POLL_MS = 30_000;
const DAYS_KEPT = 90;

export interface TrafficDay {
  data: number;
  images: number;
  video: number;
}

export interface TrafficHistory {
  /** "YYYY-MM-DD" (local time) -> bytes that day. Days with nothing downloaded are absent. */
  days: Record<string, TrafficDay>;
  /** False while the host can't count pictures (Android). */
  imagesCounted: boolean;
}

export function dayKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function load(): TrafficHistory {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null") as TrafficHistory | null;
    if (saved && typeof saved.days === "object") return saved;
  } catch {
    // Unreadable: start over.
  }
  return { days: {}, imagesCounted: true };
}

let history = load();
const listeners = new Set<() => void>();

function save(next: TrafficHistory): void {
  // Old days drop off so the entry stays a few kilobytes.
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - DAYS_KEPT);
  const oldest = dayKey(cutoff);
  const days = Object.fromEntries(Object.entries(next.days).filter(([key]) => key >= oldest));
  history = { ...next, days };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
  } catch {
    // Not saved: the numbers still show for this session.
  }
  for (const listener of listeners) listener();
}

function addToday(change: Partial<TrafficDay>, imagesCounted = history.imagesCounted): void {
  const key = dayKey(new Date());
  const today = history.days[key] ?? { data: 0, images: 0, video: 0 };
  save({
    imagesCounted,
    days: {
      ...history.days,
      [key]: { data: today.data + (change.data ?? 0), images: today.images + (change.images ?? 0), video: today.video + (change.video ?? 0) },
    },
  });
}

const videoByHost = createHostTally("video", (line) => log.info("traffic", line));

/** A video segment the player received, from `url`. */
export function countVideoBytes(bytes: number, url: string): void {
  if (!(bytes > 0)) return;
  addToday({ video: bytes });
  videoByHost.add(url, bytes);
}

// The host counts from its own start; only what grew since the last read is added.
let lastHost = { data: 0, images: 0 };
async function pollHost(): Promise<void> {
  try {
    const snapshot = await hibiki.network.traffic();
    const data = Math.max(0, snapshot.data - lastHost.data);
    const images = snapshot.images === null ? 0 : Math.max(0, snapshot.images - lastHost.images);
    lastHost = { data: snapshot.data, images: snapshot.images ?? 0 };
    const imagesCounted = snapshot.images !== null;
    if (data > 0 || images > 0 || history.imagesCounted !== imagesCounted) addToday({ data, images }, imagesCounted);
  } catch {
    // An older host without the call: the video count still works.
  }
}

let started = false;
export function startTrafficStats(): void {
  if (started) return;
  started = true;
  void pollHost();
  setInterval(() => void pollHost(), POLL_MS);
}

export function resetTrafficStats(): void {
  save({ days: {}, imagesCounted: history.imagesCounted });
}

export function useTrafficHistory(): TrafficHistory {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      void pollHost();
      return () => listeners.delete(listener);
    },
    () => history,
  );
}
