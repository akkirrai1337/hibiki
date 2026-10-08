import type { HttpPort, HttpRequest, HttpResponse } from "../types";
import { headerRecord } from "./headers";

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
      const response = await fetch(request.url, {
        method: (request.method ?? "GET").toUpperCase(),
        headers: request.headers,
        body: request.body,
        redirect: request.redirect ?? "follow",
        signal: controller.signal,
      });
      const body = await response.text();
      return { status: response.status, url: response.url || request.url, headers: headerRecord(response.headers), body };
    } finally {
      if (timer) clearTimeout(timer);
      request.signal?.removeEventListener("abort", abort);
    }
  },
};
