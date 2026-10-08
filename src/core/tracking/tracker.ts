// Live tracking: the library mirrored to AniList as it changes, the way Aniyomi's trackers work.
//
// The Kotlin app synced in batches - an import, and a "send" behind a preview - so a finished episode
// reached AniList only when someone opened that dialog. Here each change goes when it happens. The
// Kotlin app's safety rules stay, because this writes to someone's account:
// - only forward: progress is raised, never lowered, and nothing is ever deleted on AniList;
// - a status goes only when the category was actually changed here, so refreshing a title's stored
//   data can never overwrite something set on the website;
// - a title is linked on its own only on a near-certain, unambiguous match (matching.ts); anything
//   less waits for the person to pick, and a pick by hand is never replaced.
//
// Every push is fire-and-forget, like the source-account sync beside it (api/library.ts): a library
// change is local and succeeds whether or not AniList is reachable. A push that failed is made good
// by the next one, and opening the title page reconciles its progress (getLink).
import { and, eq } from "drizzle-orm";
import { IPC } from "@shared/ipc";
import type { AnimeTitle, LibraryCategory, TrackerAccount, TrackerId, TrackerImportReport, TrackerLink, TrackerMedia } from "@shared/types";
import { cachedAnime, library, trackerLinks, watchProgress } from "../db/schema";
import { logger } from "../logger";
import { getPlatform } from "../platform";
import {
  ANILIST_REDIRECT,
  AniListAuthError,
  aniListNames,
  aniListYear,
  anilistAuthorizeUrl,
  deleteEntry,
  anilistClientId,
  getMediaWithEntry,
  getUserLibrary,
  getViewer,
  parseAniListRedirect,
  saveEntry,
  searchMedia,
  toTrackerMedia,
  toggleFavourite,
  type AniListMedia,
  type AniListViewer,
} from "./anilist";
import { anilistFormatToType, pickConfident, scoreMatch, searchQueriesFor, trackerNamesOf, type Comparable } from "./matching";
import { categoryChange, favouriteChange, importedCategory, progressChange } from "./rules";

const TRACKER: TrackerId = "anilist";
const getDb = () => getPlatform().db.get();

/** A title searched for and not found is looked for again after this long - sites add titles. */
const RETRY_UNMATCHED_MS = 7 * 24 * 60 * 60 * 1000;
/** How long a sign-in started here waits for its redirect; one arriving without one is refused. */
const SIGN_IN_WINDOW_MS = 15 * 60 * 1000;

// --- the account -----------------------------------------------------------------------------------

interface Session {
  accessToken?: string;
  expiresAt?: number | null;
  user?: AniListViewer | null;
  /** Set when a request was refused: the account stays shown, asking to sign in again. */
  needsSignIn?: boolean;
  /** When a sign-in was started here. AniList echoes no state back, so this is what tells a redirect
   * this app asked for from a hibiki://anilist-auth link someone else crafted. Kept on disk: Android
   * may end the app while the browser is in front. */
  pendingSince?: number | null;
  signInError?: string | null;
}

const ENCRYPTED_PREFIX = "enc:";
let sessionCache: Session | null = null;

function sessionFile(): string {
  const { files, paths } = getPlatform();
  return files.join(paths.userData, "tracking", `${TRACKER}.json`);
}

async function loadSession(): Promise<Session> {
  if (sessionCache) return sessionCache;
  const { files, secureStore } = getPlatform();
  let session: Session = {};
  try {
    if (await files.exists(sessionFile())) {
      const raw = await files.readText(sessionFile());
      const json = raw.startsWith(ENCRYPTED_PREFIX) ? await secureStore.decrypt(raw.slice(ENCRYPTED_PREFIX.length)) : raw;
      session = JSON.parse(json) as Session;
    }
  } catch (error) {
    logger.warn("tracking", `AniList session unreadable, signed out: ${String(error)}`);
  }
  sessionCache = session;
  return session;
}

async function saveSession(session: Session): Promise<void> {
  sessionCache = session;
  const { files, secureStore, paths } = getPlatform();
  await files.mkdir(files.join(paths.userData, "tracking"));
  const json = JSON.stringify(session);
  let payload = json;
  try {
    if (await secureStore.isAvailable()) payload = ENCRYPTED_PREFIX + (await secureStore.encrypt(json));
  } catch (error) {
    logger.warn("tracking", `AniList session saved unencrypted: ${String(error)}`);
  }
  await files.writeText(sessionFile(), payload);
}

function emitChanged(): void {
  getPlatform().events.emit(IPC.trackingChanged);
}

/** The token to send, or null when signed out, ran out, or refused before. */
async function activeToken(): Promise<string | null> {
  const session = await loadSession();
  if (!session.accessToken || session.needsSignIn) return null;
  if (session.expiresAt != null && session.expiresAt <= Date.now()) {
    await saveSession({ ...session, needsSignIn: true });
    logger.warn("tracking", "AniList token expired; sign-in needed");
    emitChanged();
    return null;
  }
  return session.accessToken;
}

/** A request was refused for the token: keep the account on screen, asking to sign in again. */
async function markSignInNeeded(): Promise<void> {
  const session = await loadSession();
  if (session.needsSignIn) return;
  await saveSession({ ...session, needsSignIn: true });
  logger.warn("tracking", "AniList refused the token; sign-in needed");
  emitChanged();
}

export async function getAccount(): Promise<TrackerAccount> {
  const session = await loadSession();
  const expired = session.expiresAt != null && session.expiresAt <= Date.now();
  return {
    tracker: TRACKER,
    configured: anilistClientId() !== "",
    user: session.accessToken ? (session.user ?? null) : null,
    expiresAt: session.expiresAt ?? null,
    needsSignIn: Boolean(session.accessToken) && (Boolean(session.needsSignIn) || expired),
    signInError: session.signInError ?? null,
  };
}

export async function beginSignIn(): Promise<void> {
  const clientId = anilistClientId();
  if (!clientId) throw new Error("This build has no AniList client id");
  const session = await loadSession();
  await saveSession({ ...session, pendingSince: Date.now(), signInError: null });
  logger.info("tracking", "AniList sign-in started in the browser");
  await getPlatform().app.openExternal(anilistAuthorizeUrl(clientId));
}

/** Whether `url` is AniList coming back from a sign-in - for the hosts' deep-link handlers. */
export function isAniListRedirect(url: string): boolean {
  return url.toLowerCase().startsWith(ANILIST_REDIRECT);
}

/** Finishes a sign-in from the redirect the browser handed to the app. Never throws: the outcome is
 * in the account (user, or signInError) and announced through trackingChanged. */
export async function completeSignIn(url: string): Promise<void> {
  const session = await loadSession();
  const redirect = parseAniListRedirect(url);
  logger.info("tracking", `AniList sign-in redirect received (${redirect ? (redirect.ok ? "with a token" : "with an error") : "unreadable"})`);
  const fail = async (error: string) => {
    logger.warn("tracking", `AniList sign-in failed: ${error}`);
    await saveSession({ ...session, pendingSince: null, signInError: error });
    emitChanged();
  };
  if (!redirect) return;
  if (!session.pendingSince || Date.now() - session.pendingSince > SIGN_IN_WINDOW_MS) {
    await fail("no sign-in was started from this app");
    return;
  }
  if (!redirect.ok) {
    await fail(redirect.error);
    return;
  }
  try {
    const user = await getViewer(redirect.accessToken);
    await saveSession({
      accessToken: redirect.accessToken,
      expiresAt: redirect.expiresInSeconds != null ? Date.now() + redirect.expiresInSeconds * 1000 : null,
      user,
      needsSignIn: false,
      pendingSince: null,
      signInError: null,
    });
    logger.info("tracking", `AniList signed in as ${user.name}`);
    emitChanged();
  } catch (error) {
    await fail(error instanceof Error ? error.message : String(error));
  }
}

export async function signOut(): Promise<void> {
  // Links stay: they describe titles, not the account, and are right for whoever signs in next.
  await saveSession({});
  logger.info("tracking", "AniList signed out");
  emitChanged();
}

// --- links -----------------------------------------------------------------------------------------

type LinkRow = typeof trackerLinks.$inferSelect;

async function getLinkRow(sourceId: string, animeId: string): Promise<LinkRow | null> {
  const row = await getDb()
    .select()
    .from(trackerLinks)
    .where(and(eq(trackerLinks.tracker, TRACKER), eq(trackerLinks.sourceId, sourceId), eq(trackerLinks.animeId, animeId)))
    .get();
  return row ?? null;
}

async function writeLinkRow(row: LinkRow): Promise<void> {
  await getDb()
    .insert(trackerLinks)
    .values(row)
    .onConflictDoUpdate({
      target: [trackerLinks.tracker, trackerLinks.sourceId, trackerLinks.animeId],
      set: { remoteId: row.remoteId, remoteTitle: row.remoteTitle, linkedBy: row.linkedBy, checkedAt: row.checkedAt },
    })
    .run();
}

async function libraryRow(sourceId: string, animeId: string) {
  return (await getDb().select().from(library).where(and(eq(library.sourceId, sourceId), eq(library.animeId, animeId))).get()) ?? null;
}

/** What the app knows about a title's names and year without asking the source again. */
async function knownAnime(sourceId: string, animeId: string): Promise<AnimeTitle | null> {
  const fromLibrary = await libraryRow(sourceId, animeId);
  const cached = await getDb().select().from(cachedAnime).where(and(eq(cachedAnime.sourceId, sourceId), eq(cachedAnime.animeId, animeId))).get();
  // The fuller of the two: a library row pulled from a source account may hold only a Russian name.
  for (const json of [cached?.animeJson, fromLibrary?.animeJson]) {
    if (!json) continue;
    try {
      const anime = JSON.parse(json) as AnimeTitle;
      if (trackerNamesOf(anime).length > 0) return anime;
    } catch {
      // A damaged row is as good as none.
    }
  }
  return null;
}

function comparableOf(anime: AnimeTitle): Comparable {
  return { names: trackerNamesOf(anime), year: anime.year ?? null, type: anime.type ?? null };
}

/**
 * The AniList entry this title is, linking it first when that is safe. Null when it is not linked
 * and either cannot be matched with confidence, was unlinked by hand, or there is nothing to match by.
 */
async function ensureLink(token: string, sourceId: string, animeId: string, anime: AnimeTitle | null): Promise<LinkRow | null> {
  const existing = await getLinkRow(sourceId, animeId);
  if (existing?.remoteId != null) return existing;
  const key = `${sourceId}/${animeId}`;
  // Unlinked by hand: that is an answer, not a gap to fill.
  if (existing?.linkedBy === "user") {
    logger.debug("tracking", `${key}: unlinked by hand, not matching`);
    return null;
  }
  if (existing && Date.now() - existing.checkedAt < RETRY_UNMATCHED_MS) {
    logger.debug("tracking", `${key}: no match last time (${new Date(existing.checkedAt).toISOString()}), not searching again yet`);
    return null;
  }

  const known = anime && trackerNamesOf(anime).length > 0 ? anime : await knownAnime(sourceId, animeId);
  if (!known) {
    logger.debug("tracking", `${key}: no names known to match by`);
    return null;
  }
  const wanted = comparableOf(known);
  const candidates: Array<{ key: number; comparable: Comparable; media: AniListMedia }> = [];
  const queries = searchQueriesFor(wanted.names, 2);
  logger.debug("tracking", `${key}: matching on AniList by ${JSON.stringify(queries)} (year ${wanted.year ?? "?"}, ${wanted.type ?? "?"})`);
  for (const query of queries) {
    for (const media of await searchMedia(token, query, 8)) {
      candidates.push({ key: media.id, media, comparable: { names: aniListNames(media), year: aniListYear(media), type: anilistFormatToType(media.format) } });
    }
  }
  const picked = pickConfident(wanted, candidates);
  const media = picked ? candidates.find((candidate) => candidate.key === picked.key)?.media : undefined;
  const row: LinkRow = {
    tracker: TRACKER,
    sourceId,
    animeId,
    remoteId: media?.id ?? null,
    remoteTitle: media ? toTrackerMedia(media).title : null,
    linkedBy: "auto",
    checkedAt: Date.now(),
  };
  await writeLinkRow(row);
  logger.info("tracking", media
    ? `${key} linked to AniList ${media.id} "${row.remoteTitle}"`
    : `${key}: no confident AniList match among ${candidates.length} result(s)`);
  return media ? row : null;
}

// --- pushing ---------------------------------------------------------------------------------------

// One title's pushes run one after another: a category change and a finished episode a moment
// apart must not read the same old entry and overwrite each other's result.
const titleQueues = new Map<string, Promise<void>>();

function serialize(key: string, work: () => Promise<void>): void {
  if (titleQueues.has(key)) logger.debug("tracking", `${key}: queued behind the push in progress`);
  const previous = titleQueues.get(key) ?? Promise.resolve();
  const next = previous.then(work).catch(async (error: unknown) => {
    if (error instanceof AniListAuthError) await markSignInNeeded();
    else logger.warn("tracking", `${key} not synced to AniList: ${error instanceof Error ? error.message : String(error)}`);
  });
  titleQueues.set(key, next);
  void next.finally(() => {
    if (titleQueues.get(key) === next) titleQueues.delete(key);
  });
}

/** The highest episode watched to the end - what AniList counts as progress. */
async function watchedEpisodes(sourceId: string, animeId: string): Promise<number> {
  const rows = await getDb()
    .select({ episodeNumber: watchProgress.episodeNumber, watched: watchProgress.watched })
    .from(watchProgress)
    .where(and(eq(watchProgress.sourceId, sourceId), eq(watchProgress.titleId, animeId)))
    .all();
  return rows.filter((row) => row.watched).reduce((max, row) => Math.max(max, Math.floor(row.episodeNumber)), 0);
}

/** Raises AniList's progress to what has been watched here, moving the status along with it. */
async function pushProgress(token: string, row: LinkRow, sourceId: string, animeId: string): Promise<void> {
  if (row.remoteId == null) return;
  const watched = await watchedEpisodes(sourceId, animeId);
  if (watched <= 0) return;
  const { media, entry } = await getMediaWithEntry(token, row.remoteId);
  const change = progressChange(watched, entry, media.episodes ?? null);
  if (!change) {
    logger.debug("tracking", `${sourceId}/${animeId}: AniList ${row.remoteId} already at ep ${entry?.progress ?? 0} (${entry?.status ?? "not listed"}), watched here ${watched}; nothing to send`);
    return;
  }
  await saveEntry(token, row.remoteId, change);
  logger.info("tracking", `${sourceId}/${animeId} -> AniList ${row.remoteId} ep ${change.progress}${change.status ? ` (${change.status})` : ""}`);
}

/** Sets AniList's status from a category picked here. */
async function pushCategory(token: string, row: LinkRow, category: LibraryCategory, previous: LibraryCategory | null = null): Promise<void> {
  if (row.remoteId == null) return;
  const { media, entry, favourite } = await getMediaWithEntry(token, row.remoteId);
  const favouriteNext = favouriteChange(previous, category, favourite);
  // ToggleFavourite flips whatever is there, so it goes only when the answer differs.
  if (favouriteNext !== null) {
    await toggleFavourite(token, row.remoteId);
    logger.info("tracking", `${row.sourceId}/${row.animeId} -> AniList ${row.remoteId} favourite ${favouriteNext ? "on" : "off"}`);
  }
  if (category === "favorite") return;
  const change = categoryChange(category, entry, media.episodes ?? null);
  if (!change) {
    logger.debug("tracking", `${row.sourceId}/${row.animeId}: "${category}" leaves AniList ${row.remoteId} as it is (${entry?.status ?? "not listed"})`);
    return;
  }
  await saveEntry(token, row.remoteId, change);
  logger.info("tracking", `${row.sourceId}/${row.animeId} -> AniList ${row.remoteId} ${change.status}`);
}

/**
 * The library changed (api/library.ts calls this on every write). Only a *changed* category is
 * pushed; removing a title from the library leaves its AniList entry as it is.
 */
export function onLibraryChanged(change: { sourceId: string; animeId: string; anime: AnimeTitle | null; previous: LibraryCategory | null; category: LibraryCategory | null }): void {
  if (change.category === null || change.category === change.previous) return;
  const category = change.category;
  const key = `${change.sourceId}/${change.animeId}`;
  serialize(key, async () => {
    const token = await activeToken();
    if (!token) return;
    logger.debug("tracking", `${key}: library ${change.previous ?? "none"} -> ${category}, pushing to AniList`);
    const row = await ensureLink(token, change.sourceId, change.animeId, change.anime);
    if (!row) return;
    await pushCategory(token, row, category, change.previous);
    // Progress made before the title was linked (watched first, added after) goes along now.
    await pushProgress(token, row, change.sourceId, change.animeId);
    emitChanged();
  });
}

/** A title the app may write progress for: in the library, or linked by hand. Watching one episode
 * of something to try it is not a reason for it to appear on someone's AniList. */
async function tracksProgress(row: LinkRow): Promise<boolean> {
  if (row.remoteId == null) return false;
  if (row.linkedBy === "user") return true;
  return (await libraryRow(row.sourceId, row.animeId)) !== null;
}

/** An episode was just watched to the end (api/library.ts's progress.upsert). */
export function onEpisodeWatched(sourceId: string, animeId: string): void {
  serialize(`${sourceId}/${animeId}`, async () => {
    const token = await activeToken();
    if (!token) return;
    const row = await getLinkRow(sourceId, animeId);
    if (!row || !(await tracksProgress(row))) {
      logger.debug("tracking", `${sourceId}/${animeId}: episode watched; ${row?.remoteId != null ? "not in the library" : "not linked"}, AniList left alone`);
      return;
    }
    await pushProgress(token, row, sourceId, animeId);
    emitChanged();
  });
}

// --- the title page --------------------------------------------------------------------------------

async function linkView(token: string, row: LinkRow): Promise<TrackerLink | null> {
  if (row.remoteId == null) return null;
  const { media, entry, favourite } = await getMediaWithEntry(token, row.remoteId);
  return {
    tracker: TRACKER,
    sourceId: row.sourceId,
    animeId: row.animeId,
    linkedBy: row.linkedBy === "user" ? "user" : "auto",
    media: toTrackerMedia(media),
    entry: entry || favourite ? { status: entry?.status ?? null, progress: entry?.progress ?? 0, favourite } : null,
  };
}

async function guarded<T>(work: (token: string) => Promise<T>, signedOut: T): Promise<T> {
  const token = await activeToken();
  if (!token) return signedOut;
  try {
    return await work(token);
  } catch (error) {
    if (error instanceof AniListAuthError) {
      await markSignInNeeded();
      return signedOut;
    }
    throw error;
  }
}

/**
 * The title's link and the account's entry for it. A title in the library is linked here if it was
 * not yet, and progress watched while AniList could not be reached is sent now.
 */
export function getLink(sourceId: string, animeId: string): Promise<TrackerLink | null> {
  return guarded(async (token) => {
    const inLibrary = (await libraryRow(sourceId, animeId)) !== null;
    const row = inLibrary ? await ensureLink(token, sourceId, animeId, null) : await getLinkRow(sourceId, animeId);
    if (!row || row.remoteId == null) return null;
    const view = await linkView(token, row);
    const watched = view && (await tracksProgress(row)) ? await watchedEpisodes(sourceId, animeId) : 0;
    if (view && watched > (view.entry?.progress ?? 0)) {
      logger.info("tracking", `${sourceId}/${animeId}: AniList at ep ${view.entry?.progress ?? 0}, watched here ${watched}; catching up`);
      onEpisodeWatched(sourceId, animeId);
    }
    return view;
  }, null);
}

export function search(query: string): Promise<TrackerMedia[]> {
  return guarded(async (token) => (await searchMedia(token, query.trim(), 12)).map(toTrackerMedia), []);
}

/** A link picked by hand (or removed: null). The title's current state goes to AniList right away,
 * as it would have when it was first added, had the link been known then. */
export function setLink(sourceId: string, animeId: string, mediaId: number | null): Promise<TrackerLink | null> {
  return guarded(async (token) => {
    if (mediaId == null) {
      await writeLinkRow({ tracker: TRACKER, sourceId, animeId, remoteId: null, remoteTitle: null, linkedBy: "user", checkedAt: Date.now() });
      logger.info("tracking", `${sourceId}/${animeId}: unlinked from AniList by hand`);
      emitChanged();
      return null;
    }
    const { media } = await getMediaWithEntry(token, mediaId);
    const row: LinkRow = { tracker: TRACKER, sourceId, animeId, remoteId: media.id, remoteTitle: toTrackerMedia(media).title, linkedBy: "user", checkedAt: Date.now() };
    await writeLinkRow(row);
    logger.info("tracking", `${sourceId}/${animeId}: linked by hand to AniList ${media.id} "${row.remoteTitle}"`);
    const local = await libraryRow(sourceId, animeId);
    if (local) await pushCategory(token, row, local.category as LibraryCategory);
    await pushProgress(token, row, sourceId, animeId);
    emitChanged();
    return linkView(token, row);
  }, null);
}

/**
 * Takes the title off the account's list, asked for on the title page. It also stops syncing it:
 * otherwise the next finished episode would put it straight back, which would look like the delete
 * had failed. The link goes the way an unlink by hand does, so it is never re-made on its own.
 */
export function removeFromList(sourceId: string, animeId: string): Promise<TrackerLink | null> {
  return guarded(async (token) => {
    const row = await getLinkRow(sourceId, animeId);
    if (row?.remoteId == null) return null;
    const { entry } = await getMediaWithEntry(token, row.remoteId);
    if (entry?.id != null) await deleteEntry(token, entry.id);
    logger.info("tracking", `${sourceId}/${animeId}: removed from the AniList list (${row.remoteId}), syncing stopped`);
    await writeLinkRow({ tracker: TRACKER, sourceId, animeId, remoteId: null, remoteTitle: null, linkedBy: "user", checkedAt: Date.now() });
    emitChanged();
    return null;
  }, null);
}

/** Puts the title in the account's favourites, or takes it out. */
export function setFavourite(sourceId: string, animeId: string, favourite: boolean): Promise<TrackerLink | null> {
  return guarded(async (token) => {
    const row = await getLinkRow(sourceId, animeId);
    if (row?.remoteId == null) return null;
    const current = await getMediaWithEntry(token, row.remoteId);
    // ToggleFavourite flips whatever is there, so it is sent only when the answer differs.
    if (current.favourite !== favourite) await toggleFavourite(token, row.remoteId);
    logger.info("tracking", `${sourceId}/${animeId} -> AniList ${row.remoteId} favourite ${favourite ? "on" : "off"}${current.favourite === favourite ? " (already)" : ""}`);
    emitChanged();
    return linkView(token, row);
  }, null);
}

// --- import ----------------------------------------------------------------------------------------

/** What the import needs of the extension runtime. */
export interface ImportRuntime {
  search(sourceId: string, request: { query: string }): Promise<AnimeTitle[]>;
  getById(sourceId: string, id: string): Promise<AnimeTitle>;
}

const IMPORT_CONCURRENCY = 3;

/**
 * The account's lists brought into the library, each title found on `sourceId`.
 *
 * AniList knows nothing of sources, so each entry is searched for there and taken only on a
 * confident match - a source's search results rarely carry a year, so the closest few by name are
 * opened for theirs before deciding. A title linked before is not searched again. The import writes
 * the library directly, not through library.upsert: what came from AniList has nothing to send back.
 */
export async function importLibrary(runtime: ImportRuntime, sourceId: string, onProgress: (done: number, total: number) => void): Promise<TrackerImportReport> {
  const token = await activeToken();
  if (!token) throw new AniListAuthError();
  const session = await loadSession();
  const userId = session.user?.id ?? (await getViewer(token)).id;
  let items;
  try {
    items = await getUserLibrary(token, userId);
  } catch (error) {
    if (error instanceof AniListAuthError) await markSignInNeeded();
    throw error;
  }

  const wanted = items
    .map((item) => ({ item, category: importedCategory(item.entry?.status ?? null, item.favourite) }))
    .filter((entry): entry is { item: typeof items[number]; category: LibraryCategory } => entry.category !== null);

  const linked = await getDb()
    .select()
    .from(trackerLinks)
    .where(and(eq(trackerLinks.tracker, TRACKER), eq(trackerLinks.sourceId, sourceId)))
    .all();
  const animeIdByMedia = new Map(linked.filter((row) => row.remoteId != null).map((row) => [row.remoteId as number, row.animeId]));
  logger.info("tracking", `AniList import into ${sourceId} started: ${items.length} list entries, ${wanted.length} to bring in, ${animeIdByMedia.size} already linked here`);
  const startedAt = Date.now();

  const report: TrackerImportReport = { added: 0, updated: 0, unmatched: [], failed: 0 };
  let done = 0;
  onProgress(0, wanted.length);

  let next = 0;
  const worker = async () => {
    for (;;) {
      const current = wanted[next++];
      if (!current) return;
      const { item, category } = current;
      try {
        const known = animeIdByMedia.get(item.media.id);
        const found = known ? { animeId: known, anime: await knownAnime(sourceId, known) } : await findOnSource(runtime, sourceId, item.media);
        if (!found) {
          report.unmatched.push(toTrackerMedia(item.media).title);
          logger.debug("tracking", `import: AniList ${item.media.id} "${toTrackerMedia(item.media).title}" not found on ${sourceId}`);
        } else {
          const anime = found.anime ?? (await runtime.getById(sourceId, found.animeId));
          const existing = await libraryRow(sourceId, found.animeId);
          if (!existing) {
            await getDb()
              .insert(library)
              .values({ sourceId, animeId: found.animeId, category, addedAt: Date.now(), animeJson: JSON.stringify(anime) })
              .run();
            report.added++;
          } else if (existing.category !== category) {
            await getDb().update(library).set({ category }).where(and(eq(library.sourceId, sourceId), eq(library.animeId, found.animeId))).run();
            report.updated++;
          }
          if (!known) {
            const link = await getLinkRow(sourceId, found.animeId);
            // A pick by hand stays the person's, even when the import found something else.
            if (link?.linkedBy !== "user") {
              await writeLinkRow({ tracker: TRACKER, sourceId, animeId: found.animeId, remoteId: item.media.id, remoteTitle: toTrackerMedia(item.media).title, linkedBy: "auto", checkedAt: Date.now() });
            }
          }
        }
      } catch (error) {
        report.failed++;
        logger.warn("tracking", `import of AniList ${item.media.id} failed: ${error instanceof Error ? error.message : String(error)}`);
      }
      onProgress(++done, wanted.length);
    }
  };
  await Promise.all(Array.from({ length: Math.min(IMPORT_CONCURRENCY, wanted.length) }, worker));
  report.unmatched.sort((a, b) => a.localeCompare(b));
  logger.info("tracking", `AniList import into ${sourceId} done in ${Math.round((Date.now() - startedAt) / 1000)}s: +${report.added} added, ~${report.updated} recategorised, ?${report.unmatched.length} not found, !${report.failed} failed`);
  emitChanged();
  return report;
}

/** How many of the closest-named results are opened for their year when the search left it out. */
const DETAIL_LOOKUPS = 2;

async function findOnSource(runtime: ImportRuntime, sourceId: string, media: AniListMedia): Promise<{ animeId: string; anime: AnimeTitle | null } | null> {
  // Romaji first: it is what most sources index; then the English name.
  const names = aniListNames(media);
  const ordered = [media.title?.romaji, media.title?.english, ...names].filter((name): name is string => !!name);
  const wanted: Comparable = { names, year: aniListYear(media), type: anilistFormatToType(media.format) };
  const results = new Map<string, AnimeTitle>();
  for (const query of searchQueriesFor([...new Set(ordered)], 2)) {
    for (const title of await runtime.search(sourceId, { query })) {
      if (!results.has(title.id)) results.set(title.id, title);
    }
  }
  const candidates = [...results.values()];
  // A name alone cannot tell a season from its sequel, and search results rarely say the year.
  const plausible = candidates
    .filter((title) => title.year == null && scoreMatch({ names: wanted.names }, { names: trackerNamesOf(title) }) >= 0.7)
    .slice(0, DETAIL_LOOKUPS);
  for (const title of plausible) {
    try {
      results.set(title.id, { ...title, ...(await runtime.getById(sourceId, title.id)) });
    } catch {
      // Judged on what the search said, then.
    }
  }
  const picked = pickConfident(
    wanted,
    [...results.values()].map((title) => ({ key: title.id, comparable: comparableOf(title) })),
  );
  if (!picked) return null;
  return { animeId: picked.key, anime: results.get(picked.key) ?? null };
}
