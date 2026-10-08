import fs from "node:fs";
import path from "node:path";
import { app } from "electron";

// Read before the app is ready, when the renderer's localStorage (where every other setting lives)
// is not reachable yet - so this one setting has a file of its own next to the user's data.
//
// Off, Chromium draws on the CPU and its GPU process shrinks to almost nothing: measured on the home
// page, about 430 MB in total against 660-700 MB with it on. The price is CPU load while scrolling
// and playing video, which is why it is an opt-in "save memory" switch, not the default.
const FILE_NAME = "hardware-acceleration.json";

const filePath = (): string => path.join(app.getPath("userData"), FILE_NAME);

/** Anything other than an explicit `false` keeps acceleration on: a missing or damaged file must
 * never be what turns it off. */
export function parseHardwareAcceleration(raw: string): boolean {
  try {
    return (JSON.parse(raw) as { enabled?: unknown }).enabled !== false;
  } catch {
    return true;
  }
}

export function isHardwareAccelerationEnabled(): boolean {
  try {
    return parseHardwareAcceleration(fs.readFileSync(filePath(), "utf8"));
  } catch {
    return true;
  }
}

export function setHardwareAccelerationEnabled(enabled: boolean): void {
  fs.writeFileSync(filePath(), JSON.stringify({ enabled }));
}
