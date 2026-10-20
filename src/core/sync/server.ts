// Device sync, the computer's side: answering pairing and sync requests from phones. The listening
// itself (TCP, UDP discovery) is the host's - see main/sync.ts; this only turns one request into its
// answer.
import { IPC } from "@shared/ipc";
import type { SyncPairingInfo } from "@shared/types";
import { logger } from "../logger";
import { getPlatform } from "../platform";
import { applyChanges, changeCount, collectChanges, currentSeq, describeChanges, deviceId, KnownVersions, wipeSyncedData } from "./changes";
import { checkProof, deriveSharedKey, keyProof, newPairingCode, newPairingKeys, open, seal, type Bytes } from "./crypto";
import { getPeer, peerKey, removePeer, savePeer, updatePeer } from "./peers";
import { DISCOVERY_PROBE, LOCATE_PROBE_PREFIX, PROTOCOL_VERSION, type DiscoveryAnswer, type SyncAnswer, type SyncPayload, type SyncRequest, type SyncResponse, type UnpairPayload } from "./protocol";

// While a pairing window is open the device shows up in other devices' searches and takes a code. The
// code is replaced every couple of minutes, so one that was read over a shoulder or intercepted does
// not stay good; the window itself ends on its own, after a few tries, or when a device has paired.
const CODE_LIFETIME_MS = 2 * 60 * 1000;
const WINDOW_MAX_MS = 10 * 60 * 1000;
/** Tries (a device saying hello with a code) per window before it closes. */
const MAX_PAIRING_ATTEMPTS = 5;
/** After the last try is used, how long that one may still be completed. */
const LAST_ATTEMPT_GRACE_MS = 15 * 1000;

interface PairingSession {
  code: string;
  codeExpiresAt: number;
  closesAt: number;
  attempts: number;
  /** Phones that said hello with this session, waiting to prove the code. */
  pending: Map<string, { name: string; key: Bytes; address: string }>;
  rotateTimer: ReturnType<typeof setTimeout> | null;
  closeTimer: ReturnType<typeof setTimeout> | null;
}

let session: PairingSession | null = null;

/** A timer that does not keep a process alive just to fire. */
function timer(callback: () => void, ms: number): ReturnType<typeof setTimeout> {
  const handle = setTimeout(callback, Math.max(0, ms));
  (handle as { unref?: () => void }).unref?.();
  return handle;
}

const infoOf = (current: PairingSession): SyncPairingInfo => ({ code: current.code, expiresAt: current.codeExpiresAt, closesAt: current.closesAt });

/** Tells whatever listens for discovery probes (the platform's own socket, where it has one) whether to answer. */
function announceOpen(open: boolean): void {
  const failed = (error: unknown) => logger.warn("sync", `could not tell the network layer the pairing window is ${open ? "open" : "closed"}: ${error}`);
  try {
    void Promise.resolve(getPlatform().syncTransport?.setPairingOpen?.(open)).catch(failed);
  } catch (error) {
    failed(error);
  }
  notifySyncChanged("pairing");
}

function armCloseTimer(current: PairingSession): void {
  if (current.closeTimer) clearTimeout(current.closeTimer);
  current.closeTimer = timer(() => {
    if (session === current) closeSession("ran out");
  }, current.closesAt - Date.now());
}

function armRotation(current: PairingSession): void {
  if (current.rotateTimer) clearTimeout(current.rotateTimer);
  current.rotateTimer = timer(() => {
    if (session !== current) return;
    current.code = newPairingCode();
    current.codeExpiresAt = Date.now() + CODE_LIFETIME_MS;
    logger.info("sync", "pairing code replaced");
    notifySyncChanged("pairing");
    armRotation(current);
  }, CODE_LIFETIME_MS);
}

function closeSession(reason: string): void {
  const current = session;
  if (!current) return;
  if (current.rotateTimer) clearTimeout(current.rotateTimer);
  if (current.closeTimer) clearTimeout(current.closeTimer);
  session = null;
  logger.info("sync", `pairing window closed (${reason})`);
  announceOpen(false);
}

/** Opens a pairing window: the code to show now, and when it is replaced. */
export function startPairing(): SyncPairingInfo {
  if (session) closeSession("reopened");
  const now = Date.now();
  const current: PairingSession = {
    code: newPairingCode(),
    codeExpiresAt: now + CODE_LIFETIME_MS,
    closesAt: now + WINDOW_MAX_MS,
    attempts: 0,
    pending: new Map(),
    rotateTimer: null,
    closeTimer: null,
  };
  session = current;
  armRotation(current);
  armCloseTimer(current);
  logger.info("sync", "pairing window opened");
  announceOpen(true);
  return infoOf(current);
}

export function stopPairing(): void {
  closeSession("stopped");
}

function activeSession(): PairingSession | null {
  if (session && Date.now() > session.closesAt) closeSession("ran out");
  return session;
}

/** The window that is open and its code, or null. */
export function pairingInfo(): SyncPairingInfo | null {
  const current = activeSession();
  return current ? infoOf(current) : null;
}

export function isPairingOpen(): boolean {
  return activeSession() !== null;
}

/**
 * What to answer a UDP probe with, or null for silence. A search for devices to pair with is
 * answered only while a window is open - otherwise this device does not show up in anyone's list. A
 * search for one particular paired device (after its address changed) is answered whenever it is
 * this one that is asked for: it takes knowing its id, and says nothing a paired device does not know.
 */
export async function answerProbe(text: string, name: string, port: number, kind: DiscoveryAnswer["kind"]): Promise<DiscoveryAnswer | null> {
  const id = await deviceId();
  const mine = (): DiscoveryAnswer => ({ app: "hibiki", v: PROTOCOL_VERSION, deviceId: id, name, port, kind });
  if (text === DISCOVERY_PROBE) return isPairingOpen() ? mine() : null;
  if (text.startsWith(LOCATE_PROBE_PREFIX) && text.slice(LOCATE_PROBE_PREFIX.length) === id) return mine();
  return null;
}

/** Tells the UI something changed: paired devices, data a sync brought in, or the pairing window. */
export function notifySyncChanged(what: "devices" | "data" | "pairing"): void {
  getPlatform().events.emit(IPC.syncChanged, { what });
}

export async function handleSyncRequest(text: string, address: string, ownName: string): Promise<string> {
  let request: SyncRequest;
  try {
    request = JSON.parse(text) as SyncRequest;
  } catch {
    logger.warn("sync", `unreadable request from ${address} (${text.length} bytes)`);
    return JSON.stringify({ ok: false, error: "bad-message" } satisfies SyncResponse);
  }
  try {
    const response = await answer(request, address, ownName);
    if (!response.ok) logger.info("sync", `${request.type} from ${address}: refused, ${response.error}`);
    return JSON.stringify(response);
  } catch (error) {
    logger.warn("sync", `request from ${address} failed: ${error instanceof Error ? error.message : String(error)}`);
    return JSON.stringify({ ok: false, error: "bad-message" } satisfies SyncResponse);
  }
}

async function answer(request: SyncRequest, address: string, ownName: string): Promise<SyncResponse> {
  if (request.v !== PROTOCOL_VERSION) {
    logger.warn("sync", `${address} speaks protocol v${request.v}, this device v${PROTOCOL_VERSION}`);
    return { ok: false, error: "version" };
  }

  if (request.type === "pair-hello") {
    const current = activeSession();
    if (!current) return { ok: false, error: "not-pairing" };
    if (current.attempts >= MAX_PAIRING_ATTEMPTS) {
      logger.warn("sync", `pairing window closed after ${MAX_PAIRING_ATTEMPTS} attempts`);
      closeSession("too many attempts");
      return { ok: false, error: "too-many-attempts" };
    }
    current.attempts++;
    // The last try: the window does not wait for another. It stays just long enough for this one to
    // be finished (the code may be right) and then closes, and has to be opened again.
    if (current.attempts >= MAX_PAIRING_ATTEMPTS) {
      current.closesAt = Math.min(current.closesAt, Date.now() + LAST_ATTEMPT_GRACE_MS);
      armCloseTimer(current);
      notifySyncChanged("pairing");
    }
    logger.info("sync", `pairing request from "${String(request.name).slice(0, 80)}" at ${address} (attempt ${current.attempts}/${MAX_PAIRING_ATTEMPTS})`);
    const keys = await newPairingKeys();
    const key = await deriveSharedKey(keys.privateKey, request.pub, current.code);
    current.pending.set(request.from, { name: String(request.name).slice(0, 80), key, address });
    return { ok: true, type: "pair-hello", from: await deviceId(), name: ownName, pub: keys.publicKey, proof: await keyProof(key, "server") };
  }

  if (request.type === "pair-confirm") {
    const current = activeSession();
    const pending = current?.pending.get(request.from);
    if (!current || !pending) return { ok: false, error: "not-pairing" };
    if (!(await checkProof(pending.key, "client", request.proof))) {
      logger.warn("sync", `pairing from ${address}: wrong code`);
      return { ok: false, error: "bad-code" };
    }
    await savePeer(request.from, pending.name, pending.key, pending.address, false);
    closeSession("paired");
    logger.info("sync", `paired with "${pending.name}" (${address}, ${request.from.slice(0, 8)}); it starts the syncs`);
    notifySyncChanged("devices");
    return { ok: true, type: "pair-confirm" };
  }

  // The other device forgot this one: the pairing ends here too.
  if (request.type === "unpair") {
    const peer = await getPeer(request.from);
    if (!peer) return { ok: true, type: "unpair" };
    // Opening it proves the pair's key - and every pairing has a key of its own, so an unpair from
    // an earlier pairing cannot be opened here at all. (Its `pairedAt` is not compared: each device
    // stamps that itself when the pairing completes, so the two never agree, and requiring them to
    // made every unpair bounce.)
    const payload = await open<UnpairPayload>(await peerKey(peer), request.sealed);
    if (!payload.unpair) return { ok: false, error: "bad-message" };
    await removePeer(peer.deviceId);
    logger.info("sync", `"${peer.name}" forgot this device; forgotten here too`);
    notifySyncChanged("devices");
    return { ok: true, type: "unpair" };
  }

  if (request.type === "sync") {
    const peer = await getPeer(request.from);
    if (!peer) {
      logger.warn("sync", `sync from ${address}: device ${request.from.slice(0, 8)} is not paired here (forgotten?)`);
      return { ok: false, error: "unknown-device" };
    }
    const key = await peerKey(peer);
    let payload: SyncPayload;
    try {
      payload = await open<SyncPayload>(key, request.sealed);
    } catch (error) {
      logger.warn("sync", `sync from "${peer.name}": could not decrypt (${error instanceof Error ? error.message : String(error)}); paired again elsewhere?`);
      throw error;
    }
    const startedAt = Date.now();
    // `want` is how far the other device has read this one's changes. It cannot be ahead of this
    // device's own counter unless this database went back in time (restored from an older copy):
    // then the mark means nothing, and everything is sent - merging it again is harmless.
    const want = Number.isFinite(payload.want) && payload.want >= 0 && payload.want <= (await currentSeq()) ? payload.want : 0;
    // The other device chose, when pairing, that its data replaces this one's.
    if (payload.replace) {
      logger.info("sync", `"${peer.name}" replaces this device's data with its own`);
      await wipeSyncedData();
    }
    const { changed } = await applyChanges(payload.changes);
    // What the other device has just sent is not sent back to it (see KnownVersions).
    const known = new KnownVersions();
    known.remember(payload.changes);
    const outgoing = known.without(await collectChanges(want));
    await updatePeer(peer.deviceId, { lastAddress: address, lastSyncAt: Date.now() });
    const received = changeCount(payload.changes);
    const sent = changeCount(outgoing);
    // A sync with nothing either way comes every few minutes: only worth a line when looking closely.
    logger[received > 0 || sent > 0 ? "info" : "debug"](
      "sync",
      `sync from "${peer.name}" (${address}): got ${describeChanges(payload.changes)}, ${changed} changed here; sent ${describeChanges(outgoing)} in ${Date.now() - startedAt}ms`,
    );
    if (changed > 0 || payload.replace) notifySyncChanged("data");
    notifySyncChanged("devices");
    return { ok: true, type: "sync", sealed: await seal(key, { changes: outgoing } satisfies SyncAnswer) };
  }

  return { ok: false, error: "bad-message" };
}
