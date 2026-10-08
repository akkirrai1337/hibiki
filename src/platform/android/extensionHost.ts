// ExtensionHostPort on Android: a pool of Web Workers (see extensionWorker.ts). Same contract as the
// Electron host - TimeoutError after timeoutMs, AbortError on cancel, a stuck or aborted worker is
// terminated and never reused - with one Android-specific limit: every worker waiting on a host call
// holds one of WebView's network threads (its synchronous XHR is parked in native code), so only a
// few calls run at once and the rest wait their turn rather than starving image and page loads.
import type { BridgeHandler, ExtensionHostPort, ExtensionWorkerCall, ExtensionWorkerResult, FilesPort } from "../types";
import type { AndroidWorkerCall, AndroidWorkerMessage } from "./extensionWorker";
import { HibikiNet, namedError } from "./native";

const MAX_RUNNING = 4;
const MAX_IDLE = 4;
const IDLE_TTL_MS = 2 * 60_000;

function spawn(): Worker {
  return new Worker(new URL("./extensionWorker.ts", import.meta.url), { type: "module" });
}

export class AndroidExtensionHost implements ExtensionHostPort {
  private readonly idle: Array<{ worker: Worker; timer: ReturnType<typeof setTimeout> }> = [];
  private readonly waiting: Array<() => void> = [];
  private running = 0;
  private nextCallId = 1;
  private readonly scripts = new Map<string, { key: string; text: string }>();

  constructor(private readonly files: FilesPort) {}

  warm(): void {
    for (let i = this.idle.length; i < 2; i++) this.release(spawn());
  }

  dispose(): void {
    for (const { worker, timer } of this.idle.splice(0)) {
      clearTimeout(timer);
      worker.terminate();
    }
  }

  /** The script's text and a key that changes with the file, read once per change. */
  private async script(path: string): Promise<{ key: string; text: string }> {
    const stat = await this.files.stat(path);
    if (!stat) throw new Error(`Unknown source script: ${path}`);
    const key = `${stat.mtimeMs}:${stat.size}`;
    const cached = this.scripts.get(path);
    if (cached && cached.key === key) return cached;
    const entry = { key, text: await this.files.readText(path) };
    this.scripts.set(path, entry);
    return entry;
  }

  private async slot(signal?: AbortSignal): Promise<void> {
    if (this.running < MAX_RUNNING) {
      this.running++;
      return;
    }
    await new Promise<void>((resolve, reject) => {
      const onAbort = () => {
        const index = this.waiting.indexOf(go);
        if (index >= 0) this.waiting.splice(index, 1);
        reject(namedError("AbortError", "cancelled"));
      };
      const go = () => {
        signal?.removeEventListener("abort", onAbort);
        this.running++;
        resolve();
      };
      this.waiting.push(go);
      signal?.addEventListener("abort", onAbort, { once: true });
    });
  }

  private freeSlot(): void {
    this.running--;
    this.waiting.shift()?.();
  }

  private acquire(): Worker {
    const entry = this.idle.pop();
    if (!entry) return spawn();
    clearTimeout(entry.timer);
    return entry.worker;
  }

  private release(worker: Worker): void {
    worker.onmessage = null;
    worker.onerror = null;
    if (this.idle.length >= MAX_IDLE) {
      worker.terminate();
      return;
    }
    const timer = setTimeout(() => {
      const index = this.idle.findIndex((entry) => entry.worker === worker);
      if (index >= 0) this.idle.splice(index, 1);
      worker.terminate();
    }, IDLE_TTL_MS);
    this.idle.push({ worker, timer });
  }

  async run(request: ExtensionWorkerCall, bridge: BridgeHandler, options: { timeoutMs: number; signal?: AbortSignal }): Promise<ExtensionWorkerResult> {
    if (options.signal?.aborted) throw namedError("AbortError", "cancelled");
    const scriptPath = `${request.extensionsDir}/${request.sourceId}.js`;
    const manifestPath = `${request.extensionsDir}/${request.sourceId}.manifest.json`;
    if (!(await this.files.exists(manifestPath))) throw new Error(`Unknown source: ${request.sourceId}`);
    const script = await this.script(scriptPath);
    await this.slot(options.signal);

    return new Promise<ExtensionWorkerResult>((resolve, reject) => {
      const worker = this.acquire();
      const id = this.nextCallId++;
      let settled = false;

      const finish = (reuse: boolean) => {
        settled = true;
        clearTimeout(timeout);
        options.signal?.removeEventListener("abort", onAbort);
        if (reuse) this.release(worker);
        else worker.terminate();
        this.freeSlot();
      };
      const onAbort = () => {
        if (settled) return;
        finish(false);
        reject(namedError("AbortError", "cancelled"));
      };
      const timeout = setTimeout(() => {
        if (settled) return;
        finish(false);
        reject(namedError("TimeoutError", `timed out after ${options.timeoutMs}ms`));
      }, options.timeoutMs);

      worker.onmessage = (event: MessageEvent<AndroidWorkerMessage>) => {
        const message = event.data;
        if (message.kind === "bridge") {
          if (message.callId !== id) return;
          void (async () => {
            let reply: { ok: boolean; result?: unknown; error?: string };
            try {
              reply = { ok: true, result: await bridge(message.bridgeKind, message.payload) };
            } catch (error) {
              reply = { ok: false, error: error instanceof Error ? error.message : String(error) };
            }
            await HibikiNet.bridgeResolve({ id: message.bridgeId, body: JSON.stringify(reply) });
          })();
          return;
        }
        if (message.kind !== "result" || settled || message.id !== id) return;
        finish(true);
        resolve({ ok: message.ok, result: message.result, error: message.error, storageWrites: message.storageWrites });
      };
      worker.onerror = (event) => {
        if (settled) return;
        finish(false);
        reject(new Error(event.message || "extension worker crashed"));
      };
      options.signal?.addEventListener("abort", onAbort, { once: true });

      worker.postMessage({
        kind: "call",
        id,
        sourceId: request.sourceId,
        method: request.method as AndroidWorkerCall["method"],
        args: request.args,
        storage: request.storage,
        scriptPath,
        scriptKey: script.key,
        script: script.text,
      } satisfies AndroidWorkerCall);
    });
  }
}
