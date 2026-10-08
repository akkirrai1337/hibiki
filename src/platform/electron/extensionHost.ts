// Electron's ExtensionHostPort: extension scripts run in worker_threads (see
// main/extensions/worker.ts) - their *synchronous* fetch() blocks whichever thread runs it, and on
// Electron's main thread that would freeze the whole app for the request's duration; a worker keeps
// the freeze contained to that one call. A script's host calls come back over the Atomics bridge
// (main/extensions/syncHostBridge.ts) and are answered by the caller's BridgeHandler.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Worker } from "node:worker_threads";
import type { BridgeHandler, ExtensionHostPort, ExtensionWorkerCall, ExtensionWorkerResult } from "../types";
import type { ExtensionCall, ExtensionMethod } from "../../main/extensions/execute";
import type { BridgeRequestMessage } from "../../main/extensions/syncHostBridge";
import type { WorkerCallMessage, WorkerReadyMessage, WorkerResultMessage } from "../../main/extensions/worker";
import { logger } from "../../core/logger";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerPath = () => path.join(__dirname, "extensionWorker.js");

function namedError(name: "TimeoutError" | "AbortError", message: string): Error {
  return Object.assign(new Error(message), { name });
}

export class ElectronExtensionHost implements ExtensionHostPort {
  // A worker that is done with a call goes back on this list instead of being terminated - see
  // worker.ts for why (~195ms of bundle parsing per spawn, paid on every search keystroke,
  // catalog card and resolver attempt). A worker is only ever handed one call at a time, so the
  // list length is exactly "workers currently idle"; a call that arrives with none idle spawns a
  // fresh one rather than queueing, since the calls are network-bound and serializing them behind
  // a fixed pool size would make a multi-source catalog load slower, not faster.
  private readonly idleWorkers: Worker[] = [];
  private readonly warmingWorkers = new Set<Worker>();
  private nextCallId = 1;
  // Only ever grown back to this on release. Beyond it a worker is terminated: a handful of live
  // threads is the point, a thread per source the user has ever touched is not.
  private static readonly MAX_IDLE_WORKERS = 4;

  // Deliberately below MAX_IDLE_WORKERS: warm-up runs while the window is still being created and
  // the renderer's own bundle is being parsed, so spawning the whole pool up front competes for
  // CPU on the one load where first paint matters most. The pool still grows to its full size on
  // demand, from calls that would have spawned a worker anyway.
  private static readonly WARM_WORKERS = 2;

  // An idle worker holds a whole V8 isolate - about 13 MB each - and used to sit in the pool until
  // the app quit. Browsing keeps the pool busy, so this only ever reclaims the memory of a pool
  // nobody has asked anything of for a while; the next call pays one ~200 ms spawn instead.
  private static readonly IDLE_WORKER_TTL_MS = 2 * 60_000;
  private readonly idleTimers = new Map<Worker, NodeJS.Timeout>();

  /** Starts the expensive worker bundle parsing before the renderer's first source queries arrive.
   * Only workers that have loaded the whole module and posted `ready` enter the idle pool; calls
   * arriving earlier still take the normal fresh-worker path rather than waiting behind warm-up. */
  warm(): void {
    const missing = ElectronExtensionHost.WARM_WORKERS - this.idleWorkers.length - this.warmingWorkers.size;
    for (let i = 0; i < missing; i++) {
      const worker = new Worker(workerPath());
      this.warmingWorkers.add(worker);
      worker.once("message", (_message: WorkerReadyMessage) => {
        this.warmingWorkers.delete(worker);
        this.releaseWorker(worker);
      });
      worker.once("error", (error) => {
        this.warmingWorkers.delete(worker);
        // Terminated, not just forgotten: a warm-up that failed still holds a live thread.
        void worker.terminate();
        logger.warn("ext", `worker warm-up failed: ${error instanceof Error ? error.message : String(error)}`);
      });
    }
  }

  /** Tears the pool down - called on app quit, so idle threads don't hold the process open. */
  dispose(): void {
    for (const worker of this.idleWorkers.splice(0)) {
      this.clearIdleTimer(worker);
      void worker.terminate();
    }
    for (const worker of this.warmingWorkers) void worker.terminate();
    this.warmingWorkers.clear();
  }

  run(request: ExtensionWorkerCall, bridge: BridgeHandler, options: { timeoutMs: number; signal?: AbortSignal }): Promise<ExtensionWorkerResult> {
    const call: ExtensionCall = {
      extensionsDir: request.extensionsDir,
      sourceId: request.sourceId,
      method: request.method as ExtensionMethod,
      args: request.args,
      storage: request.storage,
    };

    return new Promise<ExtensionWorkerResult>((resolve, reject) => {
      if (options.signal?.aborted) {
        reject(namedError("AbortError", "cancelled"));
        return;
      }
      const { worker, fresh } = this.acquireWorker(call);
      const callId = fresh ? 0 : this.nextCallId++;
      // Guards against resolve()/reject() firing twice: a worker that times out is terminated,
      // which itself can surface as an "error" event, and a script that throws right after posting
      // a result would deliver both a result and an error.
      let settled = false;

      const onAbort = () => {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        void worker.terminate();
        reject(namedError("AbortError", "cancelled"));
      };
      const stopListening = () => options.signal?.removeEventListener("abort", onAbort);

      const timeout = setTimeout(() => {
        if (settled) return;
        settled = true;
        stopListening();
        // Deliberately terminated, never pooled: this worker is stuck inside a script that hasn't
        // returned, and handing the next call to it would hang that one too.
        void worker.terminate();
        reject(namedError("TimeoutError", `timed out after ${options.timeoutMs}ms`));
      }, options.timeoutMs);

      worker.on("message", (message: BridgeRequestMessage | WorkerResultMessage) => {
        if (message.kind === "bridge") {
          void this.serviceBridgeRequest(message, bridge);
          return;
        }
        // A late result from a call this promise already gave up on (a timeout that fired while
        // the worker was still working) - the worker was terminated, so this can only be a
        // straggler already in the queue.
        if (settled || message.id !== callId) return;
        settled = true;
        clearTimeout(timeout);
        stopListening();
        this.releaseWorker(worker);
        resolve({ ok: message.ok, result: message.result, error: message.error, storageWrites: message.storageWrites });
      });
      worker.once("error", (error: unknown) => {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        stopListening();
        void worker.terminate();
        reject(error instanceof Error ? error : new Error(String(error)));
      });

      if (!fresh) worker.postMessage({ kind: "call", id: callId, call } satisfies WorkerCallMessage);
      options.signal?.addEventListener("abort", onAbort, { once: true });
    });
  }

  // Services one host request from a worker's extension script (see syncHostBridge.ts): the
  // caller's bridge does the async work here on the main thread, then the worker is woken back up
  // via its Atomics.wait() flag.
  private async serviceBridgeRequest(message: BridgeRequestMessage, bridge: BridgeHandler): Promise<void> {
    let response: { ok: boolean; result?: unknown; error?: string };
    try {
      response = { ok: true, result: await bridge(message.bridgeKind, message.payload) };
    } catch (error) {
      response = { ok: false, error: error instanceof Error ? error.message : String(error) };
    }
    message.port.postMessage(response);
    message.port.close();
    const flag = new Int32Array(message.sab);
    Atomics.store(flag, 0, 1);
    Atomics.notify(flag, 0);
  }

  private acquireWorker(call: ExtensionCall): { worker: Worker; fresh: boolean } {
    const idle = this.idleWorkers.pop();
    if (idle) {
      // Dropped now that it is in use again - otherwise every release/acquire cycle would leave
      // another one behind and trip Node's max-listeners warning after ten reuses.
      idle.removeAllListeners("exit");
      this.clearIdleTimer(idle);
      return { worker: idle, fresh: false };
    }
    // A fresh worker takes its first call through workerData, so it starts executing as soon as it
    // has booted rather than waiting for a message to arrive afterwards.
    return { worker: new Worker(workerPath(), { workerData: call }), fresh: true };
  }

  private releaseWorker(worker: Worker): void {
    worker.removeAllListeners("message");
    worker.removeAllListeners("error");
    if (this.idleWorkers.length >= ElectronExtensionHost.MAX_IDLE_WORKERS) {
      void worker.terminate();
      return;
    }
    // An idle worker that dies on its own (OOM, a native crash) must not stay on the list waiting
    // to be handed a call it can never answer.
    worker.once("exit", () => {
      const index = this.idleWorkers.indexOf(worker);
      if (index >= 0) this.idleWorkers.splice(index, 1);
    });
    this.idleWorkers.push(worker);
    const timer = setTimeout(() => {
      this.idleTimers.delete(worker);
      const index = this.idleWorkers.indexOf(worker);
      if (index < 0) return;
      this.idleWorkers.splice(index, 1);
      worker.removeAllListeners("exit");
      void worker.terminate();
    }, ElectronExtensionHost.IDLE_WORKER_TTL_MS);
    // A pending timer must not be what keeps the process alive at quit.
    timer.unref();
    this.idleTimers.set(worker, timer);
  }

  private clearIdleTimer(worker: Worker): void {
    const timer = this.idleTimers.get(worker);
    if (timer) clearTimeout(timer);
    this.idleTimers.delete(worker);
  }
}
