// Worker-thread entry point: runs extension calls off Electron's main thread. An extension's
// fetch()/challenge()/browserFetch() globals are *synchronous* (they match the Rhino runtime the
// scripts were written for), so executing one blocks whichever thread it runs on for the whole
// duration of its network work - on the main thread that means the window, IPC and rendering all
// freeze. Here it blocks only this worker.
//
// This used to be one worker per call, spawned and thrown away. Booting one costs ~195ms on this
// machine before a single line of extension code runs - almost all of it parsing this bundle
// (cheerio, for the Jsoup shim) - and the app pays that on every search keystroke, every catalog
// card and every resolver attempt. So the worker now stays alive and serves calls in a loop, and
// runtime.ts keeps a small pool of them (see WorkerPool there).
//
// One call at a time per worker, and that is not negotiable: an extension call blocks this thread
// inside Atomics.wait() while the main thread services its bridge request, so a second call
// arriving mid-flight could not run anyway - it would just sit in the port's queue. The pool's job
// is to hand each in-flight call its own worker.
import { parentPort, workerData } from "node:worker_threads";
import { executeExtensionCall, type ExtensionCall } from "./execute";
import { createCallStorage } from "@shared/extensionCallStorage";
import { hostBrowserFetchProvider, hostChallengeProvider, hostNetFetchProvider } from "./syncHostBridge";

// Only still needed for globals.ts's sync-fetch *fallback* - the normal path bridges plain fetch()
// to the main thread instead (see core/extensions/netFetch.ts). Kept anyway, because without it that fallback
// doesn't merely run slowly, it hangs: sync-fetch blocks on execFileSync(process.execPath, ...),
// which inside Electron is the Electron binary, and without this variable that child tries to boot
// as a second GUI app instead of as plain Node, so it never writes the output being waited on.
process.env.ELECTRON_RUN_AS_NODE = "1";

export interface WorkerCallMessage {
  kind: "call";
  id: number;
  call: ExtensionCall;
}

export interface WorkerResultMessage {
  kind: "result";
  id: number;
  ok: boolean;
  result?: unknown;
  error?: string;
  /** Keys the script wrote during this call, for the main thread to persist. A failed call still
   * reports them: a login that stored a token and then threw while reading the profile back has
   * still logged the user in. */
  storageWrites?: Record<string, string | null>;
}

export interface WorkerReadyMessage {
  kind: "ready";
}

const providers = {
  challenge: hostChallengeProvider,
  browserFetch: hostBrowserFetchProvider,
  netFetch: hostNetFetchProvider,
};

function handle(id: number, call: ExtensionCall): void {
  const storage = createCallStorage(call.storage);
  try {
    const result = executeExtensionCall(call, providers, storage.binding);
    parentPort?.postMessage({
      kind: "result",
      id,
      ok: true,
      result,
      storageWrites: storage.writes,
    } satisfies WorkerResultMessage);
  } catch (error) {
    parentPort?.postMessage({
      kind: "result",
      id,
      ok: false,
      error: error instanceof Error ? error.message : String(error),
      storageWrites: storage.writes,
    } satisfies WorkerResultMessage);
  }
}

parentPort?.on("message", (message: WorkerCallMessage) => {
  if (message.kind === "call") handle(message.id, message.call);
});

// A call handed over at spawn time, so the very first request a fresh worker serves doesn't have
// to wait for a second event-loop turn to reach it.
if (workerData) handle(0, workerData as ExtensionCall);
else parentPort?.postMessage({ kind: "ready" } satisfies WorkerReadyMessage);
