// The one place core learns which platform it is running on. The host installs its Platform once
// at startup, before anything in core runs; core modules then reach the host only through
// getPlatform(), never by importing platform code themselves.
import type { Platform } from "../platform/types";

let current: Platform | null = null;

export function installPlatform(platform: Platform): void {
  current = platform;
}

export function getPlatform(): Platform {
  if (!current) throw new Error("No platform installed - call installPlatform() at startup");
  return current;
}
