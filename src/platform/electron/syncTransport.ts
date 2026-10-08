// SyncTransportPort on the computer: the connecting half of device sync, for pairing with another
// computer (the listening half is main/sync.ts). The same framing and discovery probe as the phone's
// HibikiSyncPlugin.java, over Node's sockets.
import dgram from "node:dgram";
import net from "node:net";
import os from "node:os";
import { DISCOVERY_PORT, DISCOVERY_PROBE, MAX_MESSAGE_BYTES, frame } from "../../core/sync/protocol";
import type { SyncTransportPort } from "../types";

/** Every IPv4 network's broadcast address, then the general one (some routers drop that). */
function broadcastAddresses(): string[] {
  const out = new Set<string>();
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries ?? []) {
      if (entry.family !== "IPv4" || entry.internal) continue;
      const ip = entry.address.split(".").map(Number);
      const mask = entry.netmask.split(".").map(Number);
      out.add(ip.map((part, i) => (part & mask[i]) | (~mask[i] & 255)).join("."));
    }
  }
  out.add("255.255.255.255");
  return [...out];
}

export const electronSyncTransport: SyncTransportPort = {
  request(host, port, message, timeoutMs) {
    return new Promise((resolve, reject) => {
      const socket = net.connect({ host, port });
      let buffer = Buffer.alloc(0);
      let done = false;
      const finish = (error: Error | null, answer?: string) => {
        if (done) return;
        done = true;
        socket.destroy();
        if (error) reject(error);
        else resolve(answer!);
      };
      socket.setTimeout(timeoutMs, () => finish(new Error("timed out")));
      socket.on("connect", () => socket.write(Buffer.from(frame(message))));
      socket.on("error", (error) => finish(error));
      socket.on("data", (chunk) => {
        buffer = Buffer.concat([buffer, chunk as Buffer]);
        if (buffer.length < 4) return;
        const length = buffer.readUInt32BE(0);
        if (length > MAX_MESSAGE_BYTES) return finish(new Error("answer too large"));
        if (buffer.length >= 4 + length) finish(null, buffer.subarray(4, 4 + length).toString("utf-8"));
      });
      socket.on("close", () => finish(new Error("connection closed before an answer")));
    });
  },

  discover(timeoutMs) {
    return new Promise((resolve) => {
      const found = new Map<string, { deviceId: string; name: string; host: string; port: number; kind: "computer" | "phone" }>();
      const socket = dgram.createSocket("udp4");
      const finish = () => {
        try {
          socket.close();
        } catch {
          // Already closed.
        }
        resolve([...found.values()]);
      };
      socket.on("error", finish);
      socket.on("message", (message, remote) => {
        try {
          const answer = JSON.parse(message.toString("utf-8")) as { app?: string; deviceId?: string; name?: string; port?: number; kind?: string };
          if (answer.app !== "hibiki" || !answer.deviceId) return;
          const kind = answer.kind === "phone" ? "phone" : "computer";
          found.set(answer.deviceId, { deviceId: answer.deviceId, name: answer.name ?? "hibiki", host: remote.address, port: answer.port ?? 47652, kind });
        } catch {
          // Not ours.
        }
      });
      socket.bind(() => {
        socket.setBroadcast(true);
        for (const address of broadcastAddresses()) socket.send(DISCOVERY_PROBE, DISCOVERY_PORT, address, () => {});
        setTimeout(finish, timeoutMs);
      });
    });
  },

  deviceName: async () => os.hostname() || "hibiki",
};
