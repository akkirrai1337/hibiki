import { createWriteStream } from "node:fs";
import type { DownloadTransferPort, FetchToFileRequest } from "../types";
import { headerRecord } from "./headers";

/** Streams with Node's fetch into a write stream, as main/ipc/downloads.ts does today. A response
 * outside 2xx is an error and nothing is written. */
export const electronDownloadTransfer: DownloadTransferPort = {
  async fetchToFile(request: FetchToFileRequest) {
    const response = await fetch(request.url, { headers: request.headers, signal: request.signal });
    if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);
    const headers = headerRecord(response.headers);
    const append = typeof request.append === "function" ? request.append({ status: response.status, headers }) : request.append;
    const contentLength = Number(response.headers.get("content-length") ?? 0);
    const totalBytes = contentLength > 0 ? contentLength : null;

    if (request.buffered) {
      const data = new Uint8Array(await response.arrayBuffer());
      const out = createWriteStream(request.filePath, { flags: append ? "a" : "w" });
      try {
        await new Promise<void>((resolve, reject) => out.write(data, (error) => (error ? reject(error) : resolve())));
      } finally {
        await new Promise<void>((resolve) => out.end(resolve));
      }
      request.onProgress?.(data.byteLength, totalBytes);
      return { status: response.status, headers, bytesWritten: data.byteLength };
    }

    const out = createWriteStream(request.filePath, { flags: append ? "a" : "w" });
    const reader = response.body.getReader();
    let bytesWritten = 0;
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        await new Promise<void>((resolve, reject) => out.write(value, (error) => (error ? reject(error) : resolve())));
        bytesWritten += value.byteLength;
        request.onProgress?.(bytesWritten, totalBytes);
      }
    } finally {
      await new Promise<void>((resolve) => out.end(resolve));
      reader.releaseLock();
    }
    return { status: response.status, headers, bytesWritten };
  },
};
