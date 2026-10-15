// Hands Settings > Network to the host at start and on every change: core applies the download
// limit and request timeout, the desktop host also the proxy and DNS-over-HTTPS.
import { hibiki } from "@/lib/hibiki";
import { useNetworkPrefsStore } from "@/stores/networkPrefsStore";
import { startTrafficStats } from "@/lib/trafficStats";

function push(): void {
  const s = useNetworkPrefsStore.getState();
  void hibiki.network
    ?.configure({ maxDownloads: s.maxDownloads, requestTimeoutMs: s.requestTimeoutSeconds * 1000, proxy: s.proxy, doh: s.doh })
    .catch(() => undefined);
}

export function startNetworkSync(): void {
  push();
  useNetworkPrefsStore.subscribe((state, previous) => {
    if (
      state.maxDownloads !== previous.maxDownloads ||
      state.requestTimeoutSeconds !== previous.requestTimeoutSeconds ||
      state.proxy !== previous.proxy ||
      state.doh !== previous.doh
    ) push();
  });
  startTrafficStats();
}
