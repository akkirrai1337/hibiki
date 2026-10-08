// HTTP, files, download-to-file and secure storage on Android - thin wrappers over the native
// plugins, shaped to the same port contracts the Electron adapters pass
// (src/platform/contract/*.contract.ts).
import type { DownloadTransferPort, FetchToFileRequest, FilesPort, HttpPort, HttpRequest, HttpResponse, SecureStorePort } from "../types";
import { HibikiDownloads, HibikiFiles, HibikiNet, HibikiSecure, namedError, nativeCode, nativeMessage } from "./native";

let nextId = 0;
const newId = (prefix: string) => `${prefix}${Date.now().toString(36)}${(nextId++).toString(36)}`;

/** Lower-cased names, one joined value per header except set-cookie (one entry per cookie). */
function normalizeHeaders(raw: Record<string, string[]>): Record<string, string[]> {
  const merged: Record<string, string[]> = {};
  for (const [name, values] of Object.entries(raw)) {
    const key = name.toLowerCase();
    merged[key] = [...(merged[key] ?? []), ...values];
  }
  for (const [key, values] of Object.entries(merged)) {
    if (key !== "set-cookie") merged[key] = [values.join(", ")];
  }
  return merged;
}

export const androidHttp: HttpPort = {
  async request(request: HttpRequest): Promise<HttpResponse> {
    if (request.signal?.aborted) throw namedError("AbortError", "This operation was aborted");
    const id = newId("h");
    const onAbort = () => void HibikiNet.cancel({ id });
    request.signal?.addEventListener("abort", onAbort, { once: true });
    try {
      const response = await HibikiNet.request({
        id,
        url: request.url,
        method: (request.method ?? "GET").toUpperCase(),
        headers: request.headers,
        body: request.body,
        followRedirects: request.redirect !== "manual",
        timeoutMs: request.timeoutMs ?? 0,
      });
      return { status: response.status, url: response.url || request.url, headers: normalizeHeaders(response.headers), body: response.body };
    } catch (error) {
      if (request.signal?.aborted || nativeCode(error) === "CANCELED") throw namedError("AbortError", "This operation was aborted");
      if (nativeCode(error) === "TIMEOUT") throw namedError("TimeoutError", nativeMessage(error));
      // Worded so shared retry logic (core/extensions/netFetch.ts) treats it like Node's "fetch failed".
      throw new Error(`network error: ${nativeMessage(error)}`);
    } finally {
      request.signal?.removeEventListener("abort", onAbort);
    }
  },
};

function toBase64(data: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < data.length; i += 0x8000) binary += String.fromCharCode(...data.subarray(i, i + 0x8000));
  return btoa(binary);
}

function fromBase64(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

export const androidFiles: FilesPort = {
  join: (...parts) => parts.filter(Boolean).join("/").replace(/\/{2,}/g, "/").replace(/(.)\/$/, "$1"),
  exists: async (path) => (await HibikiFiles.exists({ path })).value,
  async stat(path) {
    const stat = await HibikiFiles.stat({ path });
    if (stat.missing) return null;
    return { size: stat.size ?? 0, mtimeMs: stat.mtimeMs ?? 0, isDirectory: stat.isDirectory === true };
  },
  list: async (path) => (await HibikiFiles.list({ path })).entries,
  readText: async (path) => (await HibikiFiles.readText({ path })).value,
  writeText: (path, value) => HibikiFiles.writeText({ path, value }),
  readBytes: async (path) => fromBase64((await HibikiFiles.readBytes({ path })).base64),
  writeBytes: (path, data) => HibikiFiles.writeBytes({ path, base64: toBase64(data) }),
  mkdir: (path) => HibikiFiles.mkdir({ path }),
  remove: (path) => HibikiFiles.remove({ path }),
  rename: (from, to) => HibikiFiles.rename({ from, to }),
};

export const androidSecureStore: SecureStorePort = {
  isAvailable: async () => (await HibikiSecure.isAvailable()).value,
  encrypt: async (value) => (await HibikiSecure.encrypt({ value })).value,
  decrypt: async (value) => (await HibikiSecure.decrypt({ value })).value,
};

export const androidDownloadTransfer: DownloadTransferPort = {
  async fetchToFile(request: FetchToFileRequest) {
    if (request.signal?.aborted) throw namedError("AbortError", "This operation was aborted");
    const id = newId("d");
    const onAbort = () => void HibikiDownloads.cancel({ id });
    request.signal?.addEventListener("abort", onAbort, { once: true });
    const listener = await HibikiDownloads.addListener("progress", (event) => {
      if (event.id === id) request.onProgress?.(event.received, totalBytes);
    });
    let totalBytes: number | null = null;
    try {
      const opened = await HibikiDownloads.open({ id, url: request.url, headers: request.headers });
      const headers = normalizeHeaders(opened.headers);
      if (opened.status < 200 || opened.status >= 300) {
        await HibikiDownloads.cancel({ id });
        throw new Error(`HTTP ${opened.status}`);
      }
      totalBytes = opened.contentLength > 0 ? opened.contentLength : null;
      const append = typeof request.append === "function" ? request.append({ status: opened.status, headers }) : request.append;
      const { bytesWritten } = await HibikiDownloads.pipe({ id, filePath: request.filePath, append, buffered: request.buffered === true });
      return { status: opened.status, headers, bytesWritten };
    } catch (error) {
      if (request.signal?.aborted) throw namedError("AbortError", "This operation was aborted");
      throw error instanceof Error ? error : new Error(nativeMessage(error));
    } finally {
      request.signal?.removeEventListener("abort", onAbort);
      await listener.remove();
    }
  },
};
