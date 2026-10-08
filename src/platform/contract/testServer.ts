import http from "node:http";
import zlib from "node:zlib";
import type { AddressInfo } from "node:net";

/** A local HTTP server with one route per behaviour the transport contracts check. */
export async function startTestServer(): Promise<{ url: string; close: () => Promise<void> }> {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url ?? "/", "http://localhost");
    const chunks: Buffer[] = [];
    req.on("data", (chunk: Buffer) => chunks.push(chunk));
    req.on("end", () => {
      const body = Buffer.concat(chunks).toString("utf-8");
      switch (url.pathname) {
        case "/echo":
          res.setHeader("Content-Type", "application/json");
          res.end(JSON.stringify({ method: req.method, headers: req.headers, body }));
          return;
        case "/cookies":
          res.setHeader("Set-Cookie", ["a=1; Path=/", "b=2; Path=/; HttpOnly"]);
          res.end("ok");
          return;
        case "/redirect":
          res.statusCode = 302;
          res.setHeader("Location", "/echo");
          res.end();
          return;
        case "/gzip":
          res.setHeader("Content-Encoding", "gzip");
          res.end(zlib.gzipSync("compressed body"));
          return;
        case "/status":
          res.statusCode = Number(url.searchParams.get("code") ?? 500);
          res.end("status body");
          return;
        case "/slow":
          setTimeout(() => res.end("late"), 2_000);
          return;
        case "/slow-body":
          // Half the body now, the rest much later: lets a test abort in the middle of a transfer.
          res.setHeader("Content-Length", "20");
          res.write("0123456789");
          setTimeout(() => res.end("abcdefghij"), 2_000);
          return;
        case "/bytes": {
          const data = Buffer.from("0123456789abcdefghij");
          const range = /bytes=(\d+)-/.exec(req.headers.range ?? "");
          if (range && url.searchParams.get("ranges") !== "ignore") {
            const start = Number(range[1]);
            res.statusCode = 206;
            res.setHeader("Content-Range", `bytes ${start}-${data.length - 1}/${data.length}`);
            res.setHeader("Content-Length", String(data.length - start));
            res.end(data.subarray(start));
          } else {
            res.setHeader("Content-Length", String(data.length));
            res.end(data);
          }
          return;
        }
        default:
          res.statusCode = 404;
          res.end("missing");
      }
    });
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address() as AddressInfo;
  return {
    url: `http://127.0.0.1:${port}`,
    close: () =>
      new Promise<void>((resolve) => {
        // /slow requests left behind by the timeout/abort cases would otherwise hold close() open.
        server.closeAllConnections();
        server.close(() => resolve());
      }),
  };
}
