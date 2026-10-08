/// <reference lib="webworker" />
// The Android extension worker: the counterpart of main/extensions/worker.ts + execute.ts, running
// the very same globals (main/extensions/globals.ts, with node:zlib/node:module swapped for browser
// shims by vite.android.config.ts) and the shared dispatcher (core/extensions/dispatch.ts). A
// script's synchronous host calls block in a synchronous XHR that native code holds open until the
// main thread answers - Android WebView offers no SharedArrayBuffer for Atomics.wait.
import { Buffer } from "buffer";
import { createCallStorage, type ExtensionStorageBinding } from "@shared/extensionCallStorage";
import { buildExtensionGlobals } from "../../main/extensions/globals";
import { invokeProvider } from "../../core/extensions/dispatch";
import type { ExtensionMethod } from "../../core/extensions/methods";
import type { BridgeKind } from "../types";

// globals.ts reads Buffer at call time, never at import time, so this is early enough.
(globalThis as unknown as { Buffer: typeof Buffer }).Buffer = Buffer;

export interface AndroidWorkerCall {
  kind: "call";
  id: number;
  sourceId: string;
  method: ExtensionMethod;
  args: unknown[];
  storage?: Record<string, string>;
  scriptPath: string;
  /** Changes whenever the file does (mtime:size); a persisted Provider is reused only while it matches. */
  scriptKey: string;
  script: string;
}

export type AndroidWorkerMessage =
  | { kind: "ready" }
  | { kind: "bridge"; callId: number; bridgeId: string; bridgeKind: BridgeKind; payload: Record<string, unknown> }
  | { kind: "result"; id: number; ok: boolean; result?: unknown; error?: string; storageWrites?: Record<string, string | null> };

const scope = self as unknown as DedicatedWorkerGlobalScope;
const post = (message: AndroidWorkerMessage) => scope.postMessage(message);

let currentCallId = 0;
let nextBridgeId = 0;

function callHostSync<T>(bridgeKind: BridgeKind, payload: Record<string, unknown>): T {
  const bridgeId = `${currentCallId}-${nextBridgeId++}-${Math.random().toString(36).slice(2, 10)}`;
  post({ kind: "bridge", callId: currentCallId, bridgeId, bridgeKind, payload });
  const xhr = new XMLHttpRequest();
  xhr.open("GET", `/_hibiki/bridge/${bridgeId}`, false);
  xhr.send();
  if (xhr.status !== 200) throw new Error(`host bridge failed (HTTP ${xhr.status})`);
  const reply = JSON.parse(xhr.responseText) as { ok: boolean; result?: unknown; error?: string };
  if (!reply.ok) throw new Error(reply.error);
  return reply.result as T;
}

interface Persisted {
  key: string;
  provider: Record<string, unknown>;
  bindings: { storage?: ExtensionStorageBinding };
}

// Like desktop, a Provider lives for the worker's whole life: module-scope state in a script (a
// solved Cloudflare session, memoised filters) must survive between calls.
const persisted = new Map<string, Persisted>();

function loadProvider(call: AndroidWorkerCall): Persisted {
  const existing = persisted.get(call.scriptPath);
  if (existing && existing.key === call.scriptKey) return existing;

  const bindings: Persisted["bindings"] = {};
  const globals = buildExtensionGlobals({
    logger: {
      log: (m) => console.log(`[${call.sourceId}]`, m),
      warn: (m) => console.warn(`[${call.sourceId}]`, m),
      error: (m) => console.error(`[${call.sourceId}]`, m),
    },
    preferredLanguage: "ru",
    challenge: { acquire: (url, cookieNames, forceRefresh) => callHostSync("challenge", { url, cookieNames, forceRefresh }) },
    browserFetch: { fetch: (pageUrl, targetUrl, options) => callHostSync("browserFetch", { pageUrl, targetUrl, options }) },
    netFetch: {
      fetch: (url, options) => callHostSync("netFetch", { url, options }),
      fetchAll: (requests) => callHostSync("netFetchAll", { requests }),
    },
    storage: {
      get: (key) => bindings.storage?.get(key) ?? null,
      set: (key, value) => bindings.storage?.set(key, value),
      remove: (key) => bindings.storage?.remove(key),
    },
  }) as Record<string, unknown>;

  // desktop compiles with node:vm into a context of these globals; here they are the parameters of
  // a Function, which also catches a top-level `const Provider`.
  const names = Object.keys(globals);
  const factory = new Function(...names, `${call.script}\n;return typeof Provider !== "undefined" ? Provider : undefined;`);
  const provider = factory(...names.map((name) => globals[name])) as Record<string, unknown> | undefined;
  if (!provider) throw new Error(`Extension ${call.sourceId} did not define a Provider object`);
  const entry = { key: call.scriptKey, provider, bindings };
  persisted.set(call.scriptPath, entry);
  return entry;
}

scope.onmessage = (event: MessageEvent<AndroidWorkerCall>) => {
  const call = event.data;
  if (call.kind !== "call") return;
  currentCallId = call.id;
  const storage = createCallStorage(call.storage);
  try {
    const entry = loadProvider(call);
    entry.bindings.storage = storage.binding;
    const result = invokeProvider(entry.provider, call.sourceId, call.method, call.args);
    post({ kind: "result", id: call.id, ok: true, result, storageWrites: storage.writes });
  } catch (error) {
    post({ kind: "result", id: call.id, ok: false, error: error instanceof Error ? error.message : String(error), storageWrites: storage.writes });
  }
};

post({ kind: "ready" });
