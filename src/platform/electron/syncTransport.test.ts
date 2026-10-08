// The computer's connecting half of device sync over real sockets: a framed request and its answer,
// and the discovery probe, against small stand-ins for main/sync.ts on loopback.
import dgram from "node:dgram";
import net from "node:net";
import { afterEach, describe, expect, it } from "vitest";
import { DISCOVERY_PROBE, frame } from "../../core/sync/protocol";
import { electronSyncTransport } from "./syncTransport";

const closers: Array<() => void> = [];
afterEach(() => {
  for (const close of closers.splice(0)) close();
});

/** Answers each framed request with "echo:<request>", framed the same way. */
function echoServer(): Promise<number> {
  return new Promise((resolve) => {
    const server = net.createServer((socket) => {
      let buffer = Buffer.alloc(0);
      socket.on("data", (chunk) => {
        buffer = Buffer.concat([buffer, chunk as Buffer]);
        if (buffer.length < 4 || buffer.length < 4 + buffer.readUInt32BE(0)) return;
        socket.end(Buffer.from(frame(`echo:${buffer.subarray(4).toString("utf-8")}`)));
      });
    });
    server.listen(0, "127.0.0.1", () => resolve((server.address() as net.AddressInfo).port));
    closers.push(() => server.close());
  });
}

describe("electronSyncTransport", () => {
  it("sends one framed message and returns the framed answer, large ones included", async () => {
    const port = await echoServer();
    expect(await electronSyncTransport.request("127.0.0.1", port, "hello", 5000)).toBe("echo:hello");
    const big = "x".repeat(3 * 1024 * 1024);
    expect(await electronSyncTransport.request("127.0.0.1", port, big, 5000)).toBe(`echo:${big}`);
  });

  it("fails rather than hanging when nothing listens", async () => {
    const port = await echoServer();
    closers.pop()!();
    await expect(electronSyncTransport.request("127.0.0.1", port, "hello", 2000)).rejects.toBeTruthy();
  });

  it("collects the computers that answer the discovery probe", async () => {
    const responder = dgram.createSocket({ type: "udp4", reuseAddr: true });
    closers.push(() => responder.close());
    responder.on("message", (message, remote) => {
      if (message.toString() !== DISCOVERY_PROBE) return;
      responder.send(JSON.stringify({ app: "hibiki", v: 1, deviceId: "pc-2", name: "Second PC", port: 47652 }), remote.port, remote.address);
    });
    await new Promise<void>((resolve) => responder.bind(47653, "0.0.0.0", () => resolve()));
    const found = await electronSyncTransport.discover(800);
    expect(found).toContainEqual(expect.objectContaining({ deviceId: "pc-2", name: "Second PC", port: 47652 }));
  });
});
