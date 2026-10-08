// Worker-thread side of the challenge()/browserFetch() bridge (see browserFetchHost.ts for the
// main-thread side, and runtime.ts's message handler that connects them).
//
// Extension scripts are written against a synchronous, Rhino-style API: challenge()/browserFetch()
// return their result immediately, not a Promise. But the real BrowserWindow work they need only
// runs on Electron's actual main thread, not in this worker. This bridges the two using Node's
// documented pattern for synchronous cross-thread RPC: post a MessageChannel port + a
// SharedArrayBuffer flag to the main thread, then Atomics.wait() on the flag - blocking only this
// worker, never the app - until the main thread finishes the async work, posts the result to the
// port, and flips the flag. receiveMessageOnPort() then pulls that result off synchronously,
// with no need to wait for the port's own (async) "message" event to fire.
// https://nodejs.org/api/worker_threads.html#synchronous-blocking-of-messageports
import { parentPort, MessageChannel, receiveMessageOnPort } from "node:worker_threads";
import type { ChallengeProvider, BrowserFetchProvider, NetFetchProvider, NetFetchRequest, ChallengeSession, BrowserFetchResult } from "./browserBridge";

export interface BridgeRequestMessage {
  kind: "bridge";
  bridgeKind: "challenge" | "browserFetch" | "netFetch" | "netFetchAll";
  payload: Record<string, unknown>;
  sab: SharedArrayBuffer;
  port: MessagePort;
}

function callHostSync<TResponse>(bridgeKind: BridgeRequestMessage["bridgeKind"], payload: Record<string, unknown>): TResponse {
  if (!parentPort) throw new Error("Not running in a worker thread");

  const { port1, port2 } = new MessageChannel();
  const sab = new SharedArrayBuffer(4);
  const flag = new Int32Array(sab);

  const message: BridgeRequestMessage = { kind: "bridge", bridgeKind, payload, sab, port: port2 };
  parentPort.postMessage(message, [port2]);

  Atomics.wait(flag, 0, 0);
  const received = receiveMessageOnPort(port1);
  port1.close();

  if (!received) throw new Error(`${bridgeKind}() got no response from the main process`);
  const response = received.message as { ok: boolean; result?: TResponse; error?: string };
  if (!response.ok) throw new Error(response.error ?? `${bridgeKind}() failed`);
  return response.result as TResponse;
}

// Every plain fetch() an extension makes goes through here now - see core/extensions/netFetch.ts for why
// that beats the per-request child process the `sync-fetch` package used to spawn.
export const hostNetFetchProvider: NetFetchProvider = {
  fetch(url, options) {
    return callHostSync<BrowserFetchResult>("netFetch", { url, options });
  },
  fetchAll(requests: NetFetchRequest[]) {
    return callHostSync<BrowserFetchResult[]>("netFetchAll", { requests });
  },
};

export const hostChallengeProvider: ChallengeProvider = {
  acquire(url, cookieNames, forceRefresh) {
    return callHostSync<ChallengeSession>("challenge", { url, cookieNames, forceRefresh });
  },
};

export const hostBrowserFetchProvider: BrowserFetchProvider = {
  fetch(pageUrl, targetUrl, options) {
    return callHostSync<BrowserFetchResult>("browserFetch", { pageUrl, targetUrl, options });
  },
};
