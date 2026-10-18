// A title's episode list as the source downloaded it, kept so it is not downloaded again while
// nothing has changed. YummyAnime is why: its one /videos answer holds every dub's every episode
// (about 1 MB for a popular ongoing), is sent no-store without validators, and both the episode
// list and each episode's player links are read out of it - so the title page and every episode
// start downloaded the same megabyte again.
//
// A snapshot is the GET responses a getPlaybackGroups call made, by request. While it holds,
// getPlaybackGroups and getPlayerLinks for that title are answered from it (runtime.ts). It holds
// while the title's light details say nothing new has come out - the released and total episode
// counts, the next episode's date and the status (versionOf) - and at most a day, since a new dub
// can add an old episode without changing any of those. A source whose details say none of that
// gets half an hour. A player that finds its links expired asks for fresh ones, which drops the
// snapshot (forgetSnapshot).
//
// Best effort like httpCache.ts: anything failing here just means the source is asked as before.
import type { AnimeTitle } from "@shared/types";
import { getPlatform } from "../platform";
import { logger } from "../logger";
import type { NetFetchResult } from "./netFetch";

const DIR_NAME = "title-snapshots";
const INDEX_FILE = "index.json";
const MAX_TOTAL_BYTES = 60 * 1024 * 1024;
/** Kept at most this long, whatever the details say (a dub catching up on old episodes). */
export const MAX_AGE_MS = 24 * 60 * 60 * 1000;
/** For a source whose details carry no episode counts or dates to compare. */
export const UNVERSIONED_MAX_AGE_MS = 30 * 60 * 1000;
/** The episode list re-checks the details at most this often (the title page's own 10 minutes). */
export const LIST_VERIFY_MS = 10 * 60 * 1000;
/** Player links use a snapshot verified within this - the title page keeps its details as long. */
export const LINKS_VERIFY_MS = 6 * 60 * 60 * 1000;

export interface TitleSnapshot {
  version: string | null;
  savedAt: number;
  verifiedAt: number;
  /** By requestKey(). */
  responses: Record<string, NetFetchResult>;
}

interface IndexEntry {
  file: string;
  bytes: number;
  usedAt: number;
}

/** What says whether a title has anything new, from its details; null when they say none of it. */
export function versionOf(anime: Pick<AnimeTitle, "availableEpisodeCount" | "episodeCount" | "nextEpisodeAt" | "status"> | null | undefined): string | null {
  if (!anime) return null;
  const parts = [anime.availableEpisodeCount, anime.episodeCount, anime.nextEpisodeAt, anime.status];
  if (parts.every((part) => part === null || part === undefined)) return null;
  return parts.map((part) => (part === null || part === undefined ? "" : String(part))).join("|");
}

/** One request, as the extension made it: the URL and the headers it chose. */
export function requestKey(url: string, headers: Record<string, string> | undefined): string {
  const normalized = Object.entries(headers ?? {})
    .map(([key, value]) => [key.toLowerCase(), value] as const)
    .sort(([a], [b]) => a.localeCompare(b));
  return `${url}\n${JSON.stringify(normalized)}`;
}

/** Whether a snapshot can stand in for the source, by what the episode list or the links need. */
export function usable(snapshot: TitleSnapshot, purpose: "list" | "links", currentVersion: string | null | undefined, now = Date.now()): "yes" | "verify" | "no" {
  const age = now - snapshot.savedAt;
  if (age > MAX_AGE_MS) return "no";
  if (snapshot.version === null) return age < UNVERSIONED_MAX_AGE_MS ? "yes" : "no";
  // The details read since (the title page's) already say something changed.
  if (currentVersion !== undefined && currentVersion !== snapshot.version) return "no";
  const sinceVerified = now - snapshot.verifiedAt;
  if (purpose === "links") return sinceVerified < LINKS_VERIFY_MS ? "yes" : "verify";
  return sinceVerified < LIST_VERIFY_MS ? "yes" : "verify";
}

let dir: string | null = null;
let index: Map<string, IndexEntry> | null = null;
let loading: Promise<void> | null = null;
let indexWrite: ReturnType<typeof setTimeout> | null = null;
/** The ones read this run, so an episode start does not read a megabyte from disk again. */
const memory = new Map<string, TitleSnapshot>();
const MEMORY_LIMIT = 4;

function titleKey(sourceId: string, titleId: string): string {
  return `${sourceId}\n${titleId}`;
}

function fileNameFor(key: string): string {
  let h1 = 0x811c9dc5;
  let h2 = 0xcbf29ce4;
  for (let i = 0; i < key.length; i++) {
    const c = key.charCodeAt(i);
    h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0;
    h2 = Math.imul(h2 ^ c, 0x0100019d) >>> 0;
  }
  return `${h1.toString(16).padStart(8, "0")}${h2.toString(16).padStart(8, "0")}.json`;
}

async function ready(): Promise<boolean> {
  if (index) return true;
  loading ??= (async () => {
    const platform = getPlatform();
    const base = platform.files.join(platform.paths.userData, DIR_NAME);
    await platform.files.mkdir(base);
    const loaded = new Map<string, IndexEntry>();
    try {
      const raw = JSON.parse(await platform.files.readText(platform.files.join(base, INDEX_FILE))) as Record<string, IndexEntry>;
      for (const [key, entry] of Object.entries(raw)) loaded.set(key, entry);
    } catch {
      // No index yet: start empty.
    }
    dir = base;
    index = loaded;
  })().catch(() => { loading = null; });
  await loading;
  return index !== null;
}

function scheduleIndexWrite(): void {
  if (indexWrite) return;
  indexWrite = setTimeout(() => {
    indexWrite = null;
    if (!dir || !index) return;
    const platform = getPlatform();
    void platform.files.writeText(platform.files.join(dir, INDEX_FILE), JSON.stringify(Object.fromEntries(index))).catch(() => undefined);
  }, 2000);
}

function remember(key: string, snapshot: TitleSnapshot): void {
  memory.delete(key);
  memory.set(key, snapshot);
  while (memory.size > MEMORY_LIMIT) memory.delete(memory.keys().next().value!);
}

export async function readSnapshot(sourceId: string, titleId: string): Promise<TitleSnapshot | null> {
  const key = titleKey(sourceId, titleId);
  const inMemory = memory.get(key);
  if (inMemory) return inMemory;
  try {
    if (!(await ready())) return null;
    const entry = index!.get(key);
    if (!entry) return null;
    const platform = getPlatform();
    const snapshot = JSON.parse(await platform.files.readText(platform.files.join(dir!, entry.file))) as TitleSnapshot;
    entry.usedAt = Date.now();
    scheduleIndexWrite();
    remember(key, snapshot);
    return snapshot;
  } catch {
    return null;
  }
}

export async function writeSnapshot(sourceId: string, titleId: string, snapshot: TitleSnapshot): Promise<void> {
  const key = titleKey(sourceId, titleId);
  remember(key, snapshot);
  try {
    if (!(await ready())) return;
    const text = JSON.stringify(snapshot);
    const file = fileNameFor(key);
    const platform = getPlatform();
    await platform.files.writeText(platform.files.join(dir!, file), text);
    index!.set(key, { file, bytes: text.length * 2, usedAt: Date.now() });
    await evict();
    scheduleIndexWrite();
  } catch {
    // Not kept on disk: this run still has it in memory.
  }
}

/** Only the verification time moved: the file is rewritten, the bytes are the same. */
export async function markVerified(sourceId: string, titleId: string, snapshot: TitleSnapshot, version: string | null): Promise<void> {
  await writeSnapshot(sourceId, titleId, { ...snapshot, version, verifiedAt: Date.now() });
}

export async function forgetSnapshot(sourceId: string, titleId: string): Promise<void> {
  const key = titleKey(sourceId, titleId);
  memory.delete(key);
  try {
    if (!(await ready())) return;
    const entry = index!.get(key);
    if (!entry) return;
    index!.delete(key);
    scheduleIndexWrite();
    const platform = getPlatform();
    await platform.files.remove(platform.files.join(dir!, entry.file)).catch(() => undefined);
  } catch {
    // Nothing to forget.
  }
}

async function evict(): Promise<void> {
  let total = 0;
  for (const entry of index!.values()) total += entry.bytes;
  if (total <= MAX_TOTAL_BYTES) return;
  const platform = getPlatform();
  for (const [key, entry] of [...index!.entries()].sort((a, b) => a[1].usedAt - b[1].usedAt)) {
    if (total <= MAX_TOTAL_BYTES * 0.8) break;
    index!.delete(key);
    memory.delete(key);
    total -= entry.bytes;
    await platform.files.remove(platform.files.join(dir!, entry.file)).catch(() => undefined);
  }
  logger.debug("net", `title snapshots trimmed to ${Math.round(total / 1024)} KB`);
}

export async function titleSnapshotsSize(): Promise<number> {
  if (!(await ready().catch(() => false))) return 0;
  let total = 0;
  for (const entry of index!.values()) total += entry.bytes;
  return total;
}

export async function clearTitleSnapshots(): Promise<void> {
  memory.clear();
  try {
    if (!(await ready())) return;
    const platform = getPlatform();
    await platform.files.remove(dir!);
    await platform.files.mkdir(dir!);
    index = new Map();
  } catch {
    // Nothing to clear.
  }
}
