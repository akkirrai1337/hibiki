import type { SyncDevice } from "@shared/types";

/**
 * Whether some device was paired at or after `since` - the pairing window opened then has done its
 * job (the computer closes it itself on a successful pairing) and the code can go from the screen.
 *
 * By time rather than by "the list got longer": pairing again with a device that is already in the
 * list replaces its entry, and the list stays as long as it was. The times are this device's own
 * clock on both sides: the pairing is recorded by the same device that shows the code.
 */
export function pairedSince(devices: readonly SyncDevice[] | undefined, since: number): boolean {
  return (devices ?? []).some((device) => device.pairedAt >= since);
}
