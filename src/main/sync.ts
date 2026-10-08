// Device sync on the computer: listening for phones on the local network (see core/sync/protocol.ts).
// TCP for requests - one framed request and its answer per connection - and UDP for the discovery
// probe phones broadcast to find a computer. All logic is core's; this is only the sockets.
import dgram from "node:dgram";
import net from "node:net";
import os from "node:os";
import { logger } from "../core/logger";
import { deviceId } from "../core/sync/changes";
import { DISCOVERY_PORT, DISCOVERY_PROBE, MAX_MESSAGE_BYTES, PROTOCOL_VERSION, SYNC_PORT, frame, type DiscoveryAnswer } from "../core/sync/protocol";
import { handleSyncRequest } from "../core/sync/server";

const CONNECTION_TIMEOUT_MS = 60_000;

function computerName(): string {
  return os.hostname() || "hibiki";
}

function serveConnection(socket: net.Socket): void {
  const address = (socket.remoteAddress ?? "?").replace(/^::ffff:/, "");
  let buffer = Buffer.alloc(0);
  let expected: number | null = null;
  let handled = false;
  socket.setTimeout(CONNECTION_TIMEOUT_MS, () => {
    logger.warn("sync", `connection from ${address} timed out before a whole request (${buffer.length} bytes)`);
    socket.destroy();
  });
  socket.on("error", (error) => {
    logger.debug("sync", `connection from ${address}: ${error.message}`);
    socket.destroy();
  });
  socket.on("data", (chunk) => {
    if (handled) return;
    buffer = Buffer.concat([buffer, chunk as Buffer]);
    if (expected === null && buffer.length >= 4) {
      expected = buffer.readUInt32BE(0);
      if (expected > MAX_MESSAGE_BYTES) {
        logger.warn("sync", `request from ${address} too large (${expected} bytes), dropped`);
        socket.destroy();
        return;
      }
    }
    if (expected === null || buffer.length < 4 + expected) return;
    handled = true;
    const text = buffer.subarray(4, 4 + expected).toString("utf-8");
    void handleSyncRequest(text, address, computerName()).then(
      (answer) => socket.end(Buffer.from(frame(answer))),
      (error: unknown) => {
        logger.warn("sync", `request from ${address} not answered: ${error instanceof Error ? error.message : String(error)}`);
        socket.destroy();
      },
    );
  });
}

/** Starts listening. A port already taken (another copy of the app) is logged, not fatal. */
export function startSyncServer(): void {
  const server = net.createServer(serveConnection);
  server.on("error", (error) => logger.warn("sync", `sync server not started: ${error.message}`));
  server.listen(SYNC_PORT, "0.0.0.0", () => logger.info("sync", `listening on port ${SYNC_PORT}`));

  const udp = dgram.createSocket({ type: "udp4", reuseAddr: true });
  udp.on("error", (error) => {
    logger.warn("sync", `discovery not started: ${error.message}`);
    udp.close();
  });
  udp.on("message", (message, remote) => {
    if (message.toString("utf-8").trim() !== DISCOVERY_PROBE) return;
    logger.debug("sync", `discovery probe from ${remote.address}:${remote.port}`);
    void deviceId().then((id) => {
      const answer: DiscoveryAnswer = { app: "hibiki", v: PROTOCOL_VERSION, deviceId: id, name: computerName(), port: SYNC_PORT, kind: "computer" };
      udp.send(JSON.stringify(answer), remote.port, remote.address);
    }).catch((error: unknown) => logger.warn("sync", `discovery answer not sent: ${error instanceof Error ? error.message : String(error)}`));
  });
  udp.bind(DISCOVERY_PORT, "0.0.0.0", () => logger.info("sync", `answering discovery on port ${DISCOVERY_PORT}`));
}
