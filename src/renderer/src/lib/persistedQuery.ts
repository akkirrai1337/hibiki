// A small on-disk copy of a few source answers (home rows, a source's filter catalog), so a fresh
// start draws what it had last time instead of a skeleton - and, while that copy is still within
// the query's staleTime, doesn't ask the source again at all. The same renderer runs in Electron
// and in the Android WebView, so localStorage covers both.
//
// Handed to react-query as initialData together with the time it was saved: react-query then
// decides on its own whether the copy is fresh enough or needs a background refetch, and a
// pull-to-refresh (refetch) always goes to the network as before.

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import type { SearchFilterCatalog } from "@shared/types";
import { hibiki } from "@/lib/hibiki";

const PREFIX = "hibiki-qcache:";
/** Older than this is not shown at all, even with Home set to refresh only by hand. */
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;

interface Entry<T> {
  data: T;
  savedAt: number;
}

function storageKey(kind: string, sourceId: string, rest: string): string {
  return `${PREFIX}${kind}:${sourceId}:${rest}`;
}

export function readPersisted<T>(kind: string, sourceId: string | undefined, rest = ""): Entry<T> | undefined {
  if (!sourceId) return undefined;
  try {
    const raw = localStorage.getItem(storageKey(kind, sourceId, rest));
    if (!raw) return undefined;
    const entry = JSON.parse(raw) as Entry<T>;
    if (typeof entry?.savedAt !== "number" || Date.now() - entry.savedAt > MAX_AGE_MS) return undefined;
    return entry;
  } catch {
    return undefined;
  }
}

export function writePersisted<T>(kind: string, sourceId: string, rest: string, data: T): void {
  try {
    localStorage.setItem(storageKey(kind, sourceId, rest), JSON.stringify({ data, savedAt: Date.now() } satisfies Entry<T>));
  } catch {
    // Quota or storage unavailable: the next start simply loads from the network, as before.
  }
}

/** Drops every saved answer; returns how many bytes they took. */
export function forgetAllPersisted(): number {
  let bytes = 0;
  try {
    const doomed: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(PREFIX)) doomed.push(key);
    }
    for (const key of doomed) {
      bytes += (localStorage.getItem(key)?.length ?? 0) * 2;
      localStorage.removeItem(key);
    }
  } catch {
    // Nothing to forget if storage can't be read.
  }
  return bytes;
}

/** Bytes the saved answers take (localStorage keeps UTF-16, two bytes a character). */
export function persistedSize(): number {
  let bytes = 0;
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(PREFIX)) bytes += (localStorage.getItem(key)?.length ?? 0) * 2;
    }
  } catch {
    // Unreadable storage holds nothing of ours.
  }
  return bytes;
}

/** Drops every saved answer of one source - after its settings change (mirror, language, ...)
 * yesterday's rows may no longer be what it would answer. */
export function forgetPersisted(sourceId: string): void {
  try {
    const marker = `:${sourceId}:`;
    const doomed: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(PREFIX) && key.includes(marker)) doomed.push(key);
    }
    for (const key of doomed) localStorage.removeItem(key);
  } catch {
    // Nothing to forget if storage can't be read.
  }
}

/** react-query options that seed a query from the saved copy and save every fresh answer. The
 * saved copy is read once per key, not on every render. */
export function usePersistedQueryOptions<T>(kind: string, sourceId: string | undefined, rest: string, fetch: () => Promise<T>) {
  const saved = useMemo(() => readPersisted<T>(kind, sourceId, rest), [kind, sourceId, rest]);
  return {
    queryFn: async () => {
      const data = await fetch();
      if (sourceId) writePersisted(kind, sourceId, rest, data);
      return data;
    },
    initialData: saved?.data,
    initialDataUpdatedAt: saved?.savedAt,
  };
}

/** A source's sort options and filters. They change only with the source itself, so a day-old
 * copy is still good, and the copy is keyed by the source's version so an update starts fresh. */
export function useFilterCatalog(sourceId: string | undefined, enabled = true) {
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const version = sources.data?.find((s) => s.id === sourceId)?.version ?? "";
  const persisted = usePersistedQueryOptions<SearchFilterCatalog>("filterCatalog", sourceId, version, () => hibiki.sources.filterCatalog(sourceId!));
  return useQuery({
    queryKey: ["filterCatalog", sourceId, version],
    enabled: !!sourceId && enabled && sources.isSuccess,
    staleTime: 24 * 60 * 60 * 1000,
    ...persisted,
  });
}
