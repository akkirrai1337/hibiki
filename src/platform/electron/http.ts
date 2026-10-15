import { fetch as undiciFetch, type Dispatcher } from "undici";
import type { HttpPort, HttpRequest, HttpResponse } from "../types";
import { headerRecord } from "./headers";

// Set by main/network.ts when Settings > Network asks for a proxy or DNS-over-HTTPS. undici's own
// fetch takes it; without one, Node's built-in fetch is used exactly as before.
let dispatcher: Dispatcher | null = null;

export function setHttpDispatcher(next: Dispatcher | null): void {
  dispatcher = next;
}

/** Node's fetch (undici): no CORS, no cookie jar, any header allowed - the transport
 * the extension fetch host has always used. */
export const electronHttp: HttpPort = {
  async request(request: HttpRequest): Promise<HttpResponse> {
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (request.signal?.aborted) abort();
    request.signal?.addEventListener("abort", abort, { once: true });
    const timer = request.timeoutMs ? setTimeout(abort, request.timeoutMs) : null;
    try {
      const init = {
        method: (request.method ?? "GET").toUpperCase(),
        headers: request.headers,
        body: request.body,
        redirect: request.redirect ?? "follow",
        signal: controller.signal,
      } as const;
      const response = dispatcher
        ? ((await undiciFetch(request.url, { ...init, dispatcher })) as unknown as Response)
        : await fetch(request.url, init);
      const body = await response.text();
      return { status: response.status, url: response.url || request.url, headers: headerRecord(response.headers), body };
    } finally {
      if (timer) clearTimeout(timer);
      request.signal?.removeEventListener("abort", abort);
    }
  },
};
