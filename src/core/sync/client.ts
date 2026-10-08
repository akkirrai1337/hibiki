// Device sync, the phone's side: finding a computer, pairing with it, and syncing - on start, when
// the app comes back to the screen, every few minutes while it is open, and shortly after anything
// synced changes here. The computer only ever answers (server.ts), so this is where sync happens.
import type { SyncCandidate, SyncDevice, SyncPairMode } from "@shared/types";
import { logger } from "../logger";
import { getPlatform } from "../platform";
import { applyChanges, changeCount, collectChanges, currentSeq, describeChanges, deviceId, wipeSyncedData } from "./changes";
import { checkProof, deriveSharedKey, keyProof, newPairingKeys, open, seal } from "./crypto";
import { getPeer, listPeers, peerKey, removePeer, savePeer, toDevice, updatePeer, type PeerRow } from "./peers";
import { PROTOCOL_VERSION, SYNC_PORT, type SyncAnswer, type SyncErrorCode, type SyncPayload, type SyncRequest, type SyncResponse, type UnpairPayload } from "./protocol";
import { notifySyncChanged } from "./server";

const REQUEST_TIMEOUT_MS = 20_000;
const DISCOVERY_TIMEOUT_MS = 1_500;
/** A change here waits this long for others before a sync goes out with all of them. */
const CHANGE_DEBOUNCE_MS = 8_000;

export class SyncError extends Error {
  constructor(
    readonly code: SyncErrorCode | "unreachable" | "no-transport",
    /** What the socket said, for the log; the message stays the bare code the UI reads. */
    readonly detail?: string,
  ) {
    super(code);
    this.name = "SyncError";
  }
}

function errorText(error: unknown): string {
  if (error instanceof SyncError) return error.detail ? `${error.code} (${error.detail})` : error.code;
  return error instanceof Error ? error.message : String(error);
}

function transport() {
  const port = getPlatform().syncTransport;
  if (!port) throw new SyncError("no-transport");
  return port;
}

async function request(host: string, port: number, message: SyncRequest): Promise<Extract<SyncResponse, { ok: true }>> {
  let text: string;
  const body = JSON.stringify(message);
  const startedAt = Date.now();
  try {
    text = await transport().request(host, port, body, REQUEST_TIMEOUT_MS);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    logger.debug("sync", `${message.type} to ${host}:${port}: no answer after ${Date.now() - startedAt}ms (${detail})`);
    throw new SyncError("unreachable", detail);
  }
  let response: SyncResponse;
  try {
    response = JSON.parse(text) as SyncResponse;
  } catch {
    logger.warn("sync", `${message.type} to ${host}:${port}: unreadable answer (${text.length} bytes)`);
    throw new SyncError("bad-message");
  }
  logger.debug("sync", `${message.type} to ${host}:${port}: ${response.ok ? "ok" : response.error} in ${Date.now() - startedAt}ms (${body.length} bytes out, ${text.length} in)`);
  if (!response.ok) throw new SyncError(response.error);
  return response;
}

/** Devices running hibiki on this network that answer the discovery probe. */
export async function discover(): Promise<SyncCandidate[]> {
  const startedAt = Date.now();
  let found: SyncCandidate[];
  try {
    found = await transport().discover(DISCOVERY_TIMEOUT_MS);
  } catch (error) {
    logger.warn("sync", `discovery failed: ${errorText(error)}`);
    throw error;
  }
  const own = await deviceId();
  const unique = new Map<string, SyncCandidate>();
  for (const candidate of found) if (candidate.deviceId !== own) unique.set(candidate.deviceId, candidate);
  const list = [...unique.values()];
  logger.info("sync", `discovery: ${list.length ? list.map((c) => `"${c.name}" (${c.kind}) ${c.host}:${c.port}`).join(", ") : "nothing"} in ${Date.now() - startedAt}ms`);
  return list;
}

/**
 * Pairs with a device using the code it shows. Fails with SyncError("bad-code") on a wrong code.
 * `mode` says whose data stays; a replacement is carried out by the first exchange (see syncWith).
 */
export async function pair(candidate: SyncCandidate, code: string, mode: SyncPairMode = "merge"): Promise<SyncDevice> {
  logger.info("sync", `pairing with "${candidate.name}" (${candidate.kind}) at ${candidate.host}:${candidate.port}, ${mode}`);
  try {
    return await pairSteps(candidate, code, mode);
  } catch (error) {
    logger.warn("sync", `pairing with "${candidate.name}" failed: ${errorText(error)}`);
    throw error;
  }
}

async function pairSteps(candidate: SyncCandidate, code: string, mode: SyncPairMode): Promise<SyncDevice> {
  const keys = await newPairingKeys();
  const hello = await request(candidate.host, candidate.port, {
    v: PROTOCOL_VERSION,
    type: "pair-hello",
    from: await deviceId(),
    name: await transport().deviceName(),
    pub: keys.publicKey,
  });
  if (hello.type !== "pair-hello") throw new SyncError("bad-message");
  const key = await deriveSharedKey(keys.privateKey, hello.pub, code.trim());
  // A different code (or someone in between) gives a different key: the computer's proof fails here.
  if (!(await checkProof(key, "server", hello.proof))) throw new SyncError("bad-code", "the other device's proof does not match this code");
  await request(candidate.host, candidate.port, { v: PROTOCOL_VERSION, type: "pair-confirm", from: await deviceId(), proof: await keyProof(key, "client") });
  const peer = await savePeer(hello.from, hello.name, key, `${candidate.host}:${candidate.port}`, true, {
    replace: mode === "keep-here" ? "send" : mode === "take-there" ? "take" : null,
    // Taking the other device's data: what is here now goes, so none of it is sent.
    sentSeq: mode === "take-there" ? await currentSeq() : 0,
  });
  logger.info("sync", `paired with "${hello.name}" (${hello.from.slice(0, 8)}); this device starts the syncs`);
  notifySyncChanged("devices");
  void syncNow().catch(() => {});
  return toDevice(peer);
}

function splitAddress(address: string | null): { host: string; port: number } | null {
  if (!address) return null;
  const index = address.lastIndexOf(":");
  if (index <= 0) return { host: address, port: SYNC_PORT };
  return { host: address.slice(0, index), port: Number(address.slice(index + 1)) || SYNC_PORT };
}

/** One full sync with one computer: batches both ways until neither side has more. */
async function syncWith(peer: PeerRow): Promise<void> {
  const key = await peerKey(peer);
  const from = await deviceId();
  let address = splitAddress(peer.lastAddress);
  let sentSeq = peer.sentSeq;
  let receivedSeq = peer.receivedSeq;
  let changedHere = 0;
  let sentRows = 0;
  let receivedRows = 0;
  let pendingReplace = peer.pendingReplace as "send" | "take" | null;
  const startedAt = Date.now();

  const exchange = async (target: { host: string; port: number }) => {
    for (let round = 0; round < 200; round++) {
      const outgoing = await collectChanges(sentSeq);
      logger.debug("sync", `"${peer.name}" round ${round + 1}: sending ${describeChanges(outgoing)}, asking for changes after ${receivedSeq}`);
      const response = await request(target.host, target.port, {
        v: PROTOCOL_VERSION,
        type: "sync",
        from,
        sealed: await seal(key, { want: receivedSeq, changes: outgoing, ...(pendingReplace === "send" ? { replace: true } : {}) } satisfies SyncPayload),
      });
      if (response.type !== "sync") throw new SyncError("bad-message");
      const { changes } = await open<SyncAnswer>(key, response.sealed);
      // Taking the other device's data: cleared here only once it has answered, so an unreachable
      // device never leaves this one empty.
      if (pendingReplace === "take") {
        logger.info("sync", `replacing this device's data with "${peer.name}"'s`);
        await wipeSyncedData();
      }
      const { changed } = await applyChanges(changes);
      changedHere += changed;
      sentRows += changeCount(outgoing);
      receivedRows += changeCount(changes);
      logger.debug("sync", `"${peer.name}" round ${round + 1}: received ${describeChanges(changes)}, ${changed} changed here`);
      // Both sides have what this round carried: the cursors move on, and stay put if it failed.
      sentSeq = outgoing.upTo;
      receivedSeq = changes.upTo;
      // A replacement is done once its first exchange has gone through; the rest is an ordinary sync.
      if (pendingReplace === "send") logger.info("sync", `"${peer.name}" replaced its data with this device's`);
      await updatePeer(peer.deviceId, { sentSeq, receivedSeq, lastAddress: `${target.host}:${target.port}`, lastSyncAt: Date.now(), pendingReplace: null });
      if (pendingReplace) changedHere++;
      pendingReplace = null;
      if (!outgoing.more && !changes.more) return;
    }
  };

  try {
    if (!address) throw new SyncError("unreachable");
    await exchange(address);
  } catch (error) {
    if (!(error instanceof SyncError) || error.code !== "unreachable") throw error;
    // The other device's address changed (a new one from the router), or it was never known: look again.
    logger.debug("sync", `"${peer.name}" not at ${peer.lastAddress ?? "an unknown address"} (${errorText(error)}), looking for it`);
    const found = (await discover()).find((candidate) => candidate.deviceId === peer.deviceId);
    if (!found) throw error;
    logger.info("sync", `"${peer.name}" found at a new address ${found.host}:${found.port}`);
    address = { host: found.host, port: found.port };
    await exchange(address);
  }
  // A run with nothing either way happens every few minutes: kept out of the way.
  const quiet = sentRows === 0 && receivedRows === 0;
  logger[quiet ? "debug" : "info"]("sync", `synced with "${peer.name}" in ${Date.now() - startedAt}ms: ${sentRows} row(s) sent, ${receivedRows} received, ${changedHere} changed here`);
  if (changedHere > 0) notifySyncChanged("data");
  notifySyncChanged("devices");
}

let running: Promise<void> | null = null;
let again = false;
/** Peers last found unreachable: the warning is written when that starts, not on every attempt. */
const unreachable = new Set<string>();

/**
 * Ends a pairing on both devices. Gone here at once; the other device is told in the background, if
 * it can be reached - a phone listens only while the app is on screen. One that is not reached learns
 * it on its next sync, which this device refuses as from an unknown device (see syncNow).
 */
export async function forgetPeer(peerId: string): Promise<void> {
  const peer = await getPeer(peerId);
  if (!peer) return;
  const key = await peerKey(peer);
  await removePeer(peerId);
  logger.info("sync", `forgot "${peer.name}" (${peerId.slice(0, 8)})`);
  notifySyncChanged("devices");
  // A device that connects here is reached where it last answered; one that connects to this one
  // listens on the usual port of the address it last came from.
  const address = peer.connects ? splitAddress(peer.lastAddress) : peer.lastAddress ? { host: peer.lastAddress.slice(0, peer.lastAddress.lastIndexOf(":")) || peer.lastAddress, port: SYNC_PORT } : null;
  if (!address || !getPlatform().syncTransport) return;
  void (async () => {
    const sealed = await seal(key, { unpair: true, pairedAt: peer.pairedAt } satisfies UnpairPayload);
    await request(address.host, address.port, { v: PROTOCOL_VERSION, type: "unpair", from: await deviceId(), sealed });
    logger.info("sync", `"${peer.name}" was told and forgot this device too`);
  })().catch((error) => logger.info("sync", `"${peer.name}" not told of the unpairing (${errorText(error)}); it learns on its next sync`));
}

/** Syncs with every paired computer. Concurrent calls share the run in progress (and one more after it). */
export function syncNow(): Promise<void> {
  if (running) {
    again = true;
    return running;
  }
  running = (async () => {
    try {
      do {
        again = false;
        // Only the ones this device reaches out to: the others sync with it themselves.
        const peers = (await listPeers()).filter((peer) => peer.connects);
        for (const peer of peers) {
          try {
            await syncWith(peer);
            if (unreachable.delete(peer.deviceId)) logger.info("sync", `"${peer.name}" reachable again`);
          } catch (error) {
            // The other device has forgotten this one: so does this one, instead of failing every sync.
            if (error instanceof SyncError && error.code === "unknown-device") {
              await removePeer(peer.deviceId);
              logger.info("sync", `"${peer.name}" forgot this device; forgotten here too`);
              notifySyncChanged("devices");
              continue;
            }
            const isUnreachable = error instanceof SyncError && error.code === "unreachable";
            if (!isUnreachable) {
              logger.warn("sync", `"${peer.name}": sync failed: ${errorText(error)}`);
            } else if (!unreachable.has(peer.deviceId)) {
              unreachable.add(peer.deviceId);
              logger.warn("sync", `"${peer.name}": not reachable on this network (${errorText(error)}); retrying quietly`);
            } else {
              logger.debug("sync", `"${peer.name}": still not reachable`);
            }
            if (peers.length === 1) throw error;
          }
        }
      } while (again);
    } finally {
      running = null;
    }
  })();
  return running;
}

let debounce: ReturnType<typeof setTimeout> | null = null;

/** Something synced changed here: sync soon, once for a burst of changes. No-op without a transport. */
export function requestSync(): void {
  if (!getPlatform().syncTransport) return;
  if (debounce) clearTimeout(debounce);
  else logger.debug("sync", `local change; syncing in ${CHANGE_DEBOUNCE_MS / 1000}s`);
  debounce = setTimeout(() => {
    debounce = null;
    void syncNow().catch(() => {});
  }, CHANGE_DEBOUNCE_MS);
}
