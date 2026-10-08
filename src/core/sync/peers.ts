// The devices this one is paired with (sync_peers), and each one's key - kept encrypted with the
// platform's secure store, like the other secrets this app holds.
import { eq } from "drizzle-orm";
import type { SyncDevice } from "@shared/types";
import { syncPeers } from "../db/schema";
import { getPlatform } from "../platform";
import { fromBase64, toBase64, type Bytes } from "./crypto";

const getDb = () => getPlatform().db.get();
export type PeerRow = typeof syncPeers.$inferSelect;

export function toDevice(row: PeerRow): SyncDevice {
  return { deviceId: row.deviceId, name: row.name, lastSyncAt: row.lastSyncAt ?? null, pairedAt: row.pairedAt, connects: row.connects };
}

export async function listPeers(): Promise<PeerRow[]> {
  return await getDb().select().from(syncPeers).all();
}

export async function getPeer(deviceId: string): Promise<PeerRow | null> {
  return (await getDb().select().from(syncPeers).where(eq(syncPeers.deviceId, deviceId)).get()) ?? null;
}

/** Pairs (or re-pairs) a device: a fresh key, and both cursors back to the start - unless this
 * device's data is about to be replaced, when nothing it has now is worth sending (`sentSeq`). */
export async function savePeer(
  deviceId: string,
  name: string,
  key: Bytes,
  address: string | null,
  connects: boolean,
  pending: { replace: "send" | "take" | null; sentSeq: number } = { replace: null, sentSeq: 0 },
): Promise<PeerRow> {
  const keyCiphertext = await getPlatform().secureStore.encrypt(toBase64(key));
  const row = {
    deviceId, name, keyCiphertext, sentSeq: pending.sentSeq, receivedSeq: 0, lastAddress: address, lastSyncAt: null, pairedAt: Date.now(), connects,
    pendingReplace: pending.replace,
  };
  await getDb().insert(syncPeers).values(row).onConflictDoUpdate({ target: syncPeers.deviceId, set: row }).run();
  return row;
}

export async function peerKey(row: PeerRow): Promise<Bytes> {
  return fromBase64(await getPlatform().secureStore.decrypt(row.keyCiphertext));
}

export async function updatePeer(deviceId: string, patch: Partial<Pick<PeerRow, "sentSeq" | "receivedSeq" | "lastAddress" | "lastSyncAt" | "name" | "pendingReplace">>): Promise<void> {
  await getDb().update(syncPeers).set(patch).where(eq(syncPeers.deviceId, deviceId)).run();
}

export async function removePeer(deviceId: string): Promise<void> {
  await getDb().delete(syncPeers).where(eq(syncPeers.deviceId, deviceId)).run();
}
