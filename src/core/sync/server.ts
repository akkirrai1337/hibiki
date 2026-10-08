// Device sync, the computer's side: answering pairing and sync requests from phones. The listening
// itself (TCP, UDP discovery) is the host's - see main/sync.ts; this only turns one request into its
// answer.
import { IPC } from "@shared/ipc";
import { logger } from "../logger";
import { getPlatform } from "../platform";
import { applyChanges, changeCount, collectChanges, describeChanges, deviceId, wipeSyncedData } from "./changes";
import { checkProof, deriveSharedKey, keyProof, newPairingCode, newPairingKeys, open, seal, type Bytes } from "./crypto";
import { getPeer, peerKey, removePeer, savePeer, updatePeer } from "./peers";
import { PROTOCOL_VERSION, type SyncAnswer, type SyncPayload, type SyncRequest, type SyncResponse, type UnpairPayload } from "./protocol";

const PAIRING_WINDOW_MS = 5 * 60 * 1000;
/** Wrong codes allowed per pairing window before it closes. */
const MAX_PAIRING_ATTEMPTS = 5;

interface PairingSession {
  code: string;
  expiresAt: number;
  attempts: number;
  /** Phones that said hello with this session, waiting to prove the code. */
  pending: Map<string, { name: string; key: Bytes; address: string }>;
}

let session: PairingSession | null = null;

/** Opens a pairing window: the code to show, and until when it works. */
export function startPairing(): { code: string; expiresAt: number } {
  session = { code: newPairingCode(), expiresAt: Date.now() + PAIRING_WINDOW_MS, attempts: 0, pending: new Map() };
  logger.info("sync", "pairing window opened");
  return { code: session.code, expiresAt: session.expiresAt };
}

export function stopPairing(): void {
  if (session) logger.info("sync", "pairing window closed");
  session = null;
}

function activeSession(): PairingSession | null {
  if (session && Date.now() > session.expiresAt) {
    logger.info("sync", "pairing window ran out");
    session = null;
  }
  return session;
}

/** Tells the UI something changed: paired devices, or data a sync brought in. */
export function notifySyncChanged(what: "devices" | "data"): void {
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
      session = null;
      return { ok: false, error: "too-many-attempts" };
    }
    current.attempts++;
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
    session = null;
    logger.info("sync", `paired with "${pending.name}" (${address}, ${request.from.slice(0, 8)}); it starts the syncs`);
    notifySyncChanged("devices");
    return { ok: true, type: "pair-confirm" };
  }

  // The other device forgot this one: the pairing ends here too.
  if (request.type === "unpair") {
    const peer = await getPeer(request.from);
    if (!peer) return { ok: true, type: "unpair" };
    // Opening it proves the pair's key; the time keeps an unpair from an earlier pairing out of this one.
    const payload = await open<UnpairPayload>(await peerKey(peer), request.sealed);
    if (!payload.unpair || payload.pairedAt !== peer.pairedAt) return { ok: false, error: "bad-message" };
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
    // The other device chose, when pairing, that its data replaces this one's.
    if (payload.replace) {
      logger.info("sync", `"${peer.name}" replaces this device's data with its own`);
      await wipeSyncedData();
    }
    const { changed } = await applyChanges(payload.changes);
    const outgoing = await collectChanges(payload.want);
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
