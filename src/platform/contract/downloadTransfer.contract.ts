import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { DownloadTransferPort, FilesPort } from "../types";
import { startTestServer } from "./testServer";

/** `dir` must be an empty scratch directory the port may write into. */
export function describeDownloadTransferContract(name: string, transfer: DownloadTransferPort, files: FilesPort, dir: () => string): void {
  describe(`DownloadTransferPort contract: ${name}`, () => {
    let server: Awaited<ReturnType<typeof startTestServer>>;
    beforeAll(async () => {
      server = await startTestServer();
    });
    afterAll(() => server.close());

    it("writes the body to the file and reports progress", async () => {
      const target = files.join(dir(), "full.bin");
      const progress: Array<[number, number | null]> = [];
      const result = await transfer.fetchToFile({
        url: `${server.url}/bytes`,
        filePath: target,
        append: false,
        onProgress: (received, total) => progress.push([received, total]),
      });
      expect(result.status).toBe(200);
      expect(result.bytesWritten).toBe(20);
      expect(await files.readText(target)).toBe("0123456789abcdefghij");
      expect(progress.at(-1)).toEqual([20, 20]);
    });

    it("appends only when the response says so", async () => {
      const target = files.join(dir(), "resume.bin");
      const appendIfPartial = (response: { status: number }) => response.status === 206;

      await files.writeText(target, "0123456789");
      await transfer.fetchToFile({ url: `${server.url}/bytes`, headers: { Range: "bytes=10-" }, filePath: target, append: appendIfPartial });
      expect(await files.readText(target)).toBe("0123456789abcdefghij");

      // A server that ignores Range sends the whole file with a 200: it must replace, not append.
      await files.writeText(target, "0123456789");
      await transfer.fetchToFile({ url: `${server.url}/bytes?ranges=ignore`, headers: { Range: "bytes=10-" }, filePath: target, append: appendIfPartial });
      expect(await files.readText(target)).toBe("0123456789abcdefghij");
    });

    it("leaves the file untouched when a buffered transfer is aborted midway", async () => {
      const target = files.join(dir(), "segments.ts");
      await files.writeText(target, "SEG1");
      const controller = new AbortController();
      const pending = transfer.fetchToFile({ url: `${server.url}/slow-body`, filePath: target, append: true, buffered: true, signal: controller.signal });
      setTimeout(() => controller.abort(), 300);
      await expect(pending).rejects.toThrow();
      expect(await files.readText(target)).toBe("SEG1");

      await transfer.fetchToFile({ url: `${server.url}/bytes`, filePath: target, append: true, buffered: true });
      expect(await files.readText(target)).toBe("SEG10123456789abcdefghij");
    });

    it("throws on an error status without creating the file", async () => {
      const target = files.join(dir(), "error.bin");
      await expect(transfer.fetchToFile({ url: `${server.url}/status?code=404`, filePath: target, append: false })).rejects.toThrow("HTTP 404");
      expect(await files.exists(target)).toBe(false);
    });
  });
}
