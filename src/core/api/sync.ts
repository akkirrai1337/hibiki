// The device-sync part of `window.hibiki` (see core/sync). A computer gets the waiting half
// (pairing window), a phone the reaching-out half (discover, pair, sync now); both list and forget
// paired devices.
import type { HibikiApi } from "@shared/hibikiApi";
import { discover, forgetPeer, pair, syncNow } from "../sync/client";
import { listPeers, toDevice } from "../sync/peers";
import { startPairing, stopPairing } from "../sync/server";

type SyncApi = Omit<HibikiApi["sync"], "onChanged">;

function common(): Pick<SyncApi, "devices" | "remove"> {
  return {
    devices: async () => (await listPeers()).map(toDevice),
    // Both sides: see forgetPeer.
    remove: (deviceId) => forgetPeer(deviceId),
  };
}

/** A computer: it is paired with and answers, and can itself reach another computer. */
export function createSyncServerApi(): SyncApi {
  return {
    ...common(),
    startPairing: async () => startPairing(),
    stopPairing: async () => stopPairing(),
    discover,
    pair,
    syncNow,
  };
}

/** The phone's side: it finds, pairs and syncs. Errors carry the code as their message (see SyncError). */
export function createSyncClientApi(): SyncApi {
  return { ...common(), discover, pair, syncNow };
}
