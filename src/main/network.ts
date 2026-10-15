// Settings > Network's desktop-only half: a proxy and DNS-over-HTTPS, for networks where a
// provider blocks the sites the sources live on. Both apply to the two ways this app goes online:
// Chromium (the player, posters, the hidden browser pages some sources need) and Node's undici
// (every request an extension makes, through platform/electron/http.ts).
import { app, session, type Session } from "electron";
import dns from "node:dns";
import { Agent, ProxyAgent, type Dispatcher } from "undici";
import type { DohProvider, NetworkConfig } from "@shared/types";
import { logger } from "../core/logger";
import { setHttpDispatcher } from "../platform/electron/http";

const DOH_SERVERS: Record<Exclude<DohProvider, "off">, { template: string; json: string }> = {
  cloudflare: { template: "https://cloudflare-dns.com/dns-query", json: "https://cloudflare-dns.com/dns-query" },
  google: { template: "https://dns.google/dns-query", json: "https://dns.google/resolve" },
  adguard: { template: "https://dns.adguard-dns.com/dns-query", json: "https://dns.adguard-dns.com/resolve" },
};

let applied = { proxy: "", doh: "off" as DohProvider };

// Every session gets the proxy, including ones created after it was set (a source's login window,
// the hidden browser pages): each partition is a session of its own.
const sessions = new Set<Session>();
app.on("session-created", (created) => {
  sessions.add(created);
  void applyProxyTo(created);
});

async function applyProxyTo(target: Session): Promise<void> {
  try {
    await target.setProxy(applied.proxy ? { proxyRules: applied.proxy } : { mode: "direct" });
  } catch (error) {
    logger.warn("network", `could not set the proxy on a session: ${error instanceof Error ? error.message : String(error)}`);
  }
}

// --- DNS-over-HTTPS for undici ----------------------------------------------------------------

interface DohAnswer {
  Answer?: Array<{ type: number; data: string; TTL: number }>;
}

const dohCache = new Map<string, { addresses: string[]; family: 4 | 6; expiresAt: number }>();

async function dohResolve(provider: Exclude<DohProvider, "off">, hostname: string): Promise<{ addresses: string[]; family: 4 | 6 }> {
  const cacheKey = `${provider}:${hostname}`;
  const cached = dohCache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) return cached;
  for (const [type, family] of [["A", 4], ["AAAA", 6]] as const) {
    // The resolver's own name goes through the system DNS, via the global fetch (no dispatcher).
    const response = await fetch(`${DOH_SERVERS[provider].json}?name=${encodeURIComponent(hostname)}&type=${type}`, {
      headers: { Accept: "application/dns-json" },
      signal: AbortSignal.timeout(5_000),
    });
    if (!response.ok) throw new Error(`DoH ${provider} answered ${response.status}`);
    const body = (await response.json()) as DohAnswer;
    const records = (body.Answer ?? []).filter((answer) => answer.type === (family === 4 ? 1 : 28));
    if (records.length > 0) {
      const ttl = Math.max(30, Math.min(...records.map((record) => record.TTL)));
      const entry = { addresses: records.map((record) => record.data), family, expiresAt: Date.now() + ttl * 1000 };
      dohCache.set(cacheKey, entry);
      return entry;
    }
  }
  throw new Error(`DoH ${provider}: no address for ${hostname}`);
}

type LookupCallback = (error: NodeJS.ErrnoException | null, address: string | dns.LookupAddress[], family?: number) => void;

/** A net.connect `lookup` that asks the DoH provider, and falls back to the system's DNS when the
 * provider can't be reached - a broken resolver must not take every source down with it. */
function dohLookup(provider: Exclude<DohProvider, "off">) {
  return (hostname: string, options: dns.LookupOptions, callback: LookupCallback): void => {
    dohResolve(provider, hostname).then(
      ({ addresses, family }) => {
        if (options.all) callback(null, addresses.map((address) => ({ address, family })));
        else callback(null, addresses[0], family);
      },
      (error) => {
        logger.warn("network", `DoH lookup of ${hostname} failed (${error instanceof Error ? error.message : String(error)}); using system DNS`);
        dns.lookup(hostname, options, callback as never);
      },
    );
  };
}

function dispatcherFor(config: NetworkConfig): Dispatcher | null {
  // SOCKS can't be spoken by undici; Chromium still uses it, and Node goes direct.
  if (config.proxy && /^https?:\/\//i.test(config.proxy)) return new ProxyAgent(config.proxy);
  if (config.doh !== "off") return new Agent({ connect: { lookup: dohLookup(config.doh) } });
  return null;
}

/** Applies the proxy and DoH from Settings > Network; a no-op when neither changed. */
export async function applyNetworkConfig(config: NetworkConfig): Promise<void> {
  const proxy = config.proxy.trim();
  if (proxy === applied.proxy && config.doh === applied.doh) return;
  applied = { proxy, doh: config.doh };
  dohCache.clear();

  await Promise.all([session.defaultSession, ...sessions].map(applyProxyTo));
  try {
    app.configureHostResolver(
      config.doh === "off"
        ? { secureDnsMode: "off" }
        : { secureDnsMode: "secure", secureDnsServers: [DOH_SERVERS[config.doh].template] },
    );
  } catch (error) {
    logger.warn("network", `could not configure DNS-over-HTTPS: ${error instanceof Error ? error.message : String(error)}`);
  }
  setHttpDispatcher(dispatcherFor({ ...config, proxy }));
  logger.info("network", `proxy ${proxy ? "on" : "off"}, DNS-over-HTTPS ${config.doh}`);
}

/** Drops Cloudflare's cookies from every session, so the hidden browser pages pass the check anew. */
export async function clearCloudflareCookies(): Promise<void> {
  for (const target of [session.defaultSession, ...sessions]) {
    const cookies = await target.cookies.get({});
    await Promise.all(
      cookies
        .filter((cookie) => cookie.name === "cf_clearance" || cookie.name.startsWith("__cf"))
        .map((cookie) => {
          const host = (cookie.domain ?? "").replace(/^\./, "");
          return target.cookies.remove(`http${cookie.secure ? "s" : ""}://${host}${cookie.path ?? "/"}`, cookie.name).catch(() => undefined);
        }),
    );
  }
}
