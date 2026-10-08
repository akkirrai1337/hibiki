export interface PlaybackHeaderSession {
  headers: Readonly<Record<string, string>>;
  origins: Set<string>;
  touchedAt: number;
  registeredAt: number;
}

function urlOrigin(url: string): string | null {
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

/**
 * Owns the forbidden request headers needed by one active playback link. Sessions are explicit so
 * switching streams cannot leave credentials attached to a CDN forever, while redirect targets
 * remain part of the same session and inherit the exact same headers.
 */
export class PlayerHeaderRegistry {
  private readonly sessions = new Map<string, PlaybackHeaderSession>();
  private readonly sessionIdsByOrigin = new Map<string, string[]>();

  constructor(private readonly ttlMs = 30 * 60_000) {}

  register(sessionId: string, url: string, headers: Record<string, string>, now = Date.now()): boolean {
    const origin = urlOrigin(url);
    if (!origin) return false;
    this.unregister(sessionId);
    const session: PlaybackHeaderSession = {
      headers: Object.freeze({ ...headers }),
      origins: new Set([origin]),
      touchedAt: now,
      registeredAt: now,
    };
    this.sessions.set(sessionId, session);
    this.addOrigin(sessionId, origin);
    this.sweep(now);
    return true;
  }

  /** Adds another resource origin (for example a remote WebVTT file) to an existing playback
   * session without replacing the session's headers or its already-known origins. */
  registerOrigin(sessionId: string, url: string, now = Date.now()): boolean {
    const origin = urlOrigin(url);
    const session = this.sessions.get(sessionId);
    if (!origin || !session) return false;
    session.touchedAt = now;
    if (!session.origins.has(origin)) {
      session.origins.add(origin);
      this.addOrigin(sessionId, origin);
    }
    this.sweep(now);
    return true;
  }

  unregister(sessionId: string): void {
    const session = this.sessions.get(sessionId);
    if (!session) return;
    this.sessions.delete(sessionId);
    for (const origin of session.origins) {
      const ids = this.sessionIdsByOrigin.get(origin)?.filter((id) => id !== sessionId) ?? [];
      if (ids.length > 0) this.sessionIdsByOrigin.set(origin, ids);
      else this.sessionIdsByOrigin.delete(origin);
    }
  }

  headersFor(url: string, now = Date.now()): Readonly<Record<string, string>> | undefined {
    const origin = urlOrigin(url);
    const session = origin ? this.latestSession(origin) : undefined;
    // Touched *before* the sweep, not after. The other way round, the very request that proves a
    // session is still in use was the one that expired it: sweep() measured the gap since the
    // previous request, so any two consecutive requests further apart than the TTL killed the
    // session between them - a video paused longer than that resumed with its playback headers
    // already gone, and the segments that followed went out bare. Refreshing first makes the TTL
    // what it reads as, a sliding "unused for this long" window.
    if (session) session.touchedAt = now;
    this.sweep(now);
    return session?.headers;
  }

  /** The headers of the playback session that was registered first and is still alive, for a
   * request to an origin nobody registered. HLS segment hosts are not knowable up front: a repackager
   * spreads one stream over rotating subdomains (`k9uq6.pressforyes.online`, `72cus.…`) that no
   * playlist URL, redirect or registration ever mentions, and each answers 403 to a request without
   * the Referer/Origin the stream was captured with. Oldest, not newest: a subtitle track with headers
   * of its own registers a session *after* its stream, and must not stand in for it. */
  playbackHeaders(now = Date.now()): Readonly<Record<string, string>> | undefined {
    let oldest: PlaybackHeaderSession | undefined;
    for (const session of this.sessions.values()) {
      if (!oldest || session.registeredAt < oldest.registeredAt) oldest = session;
    }
    if (oldest) oldest.touchedAt = now;
    this.sweep(now);
    return oldest?.headers;
  }

  followRedirect(fromUrl: string, toUrl: string, now = Date.now()): boolean {
    const sourceOrigin = urlOrigin(fromUrl);
    const destinationOrigin = urlOrigin(toUrl);
    if (!sourceOrigin || !destinationOrigin) return false;
    const sessionId = this.latestSessionId(sourceOrigin);
    if (!sessionId) return false;
    const session = this.sessions.get(sessionId);
    if (!session) return false;
    // Same ordering rule as headersFor() above: refresh, then sweep.
    session.touchedAt = now;
    this.sweep(now);
    if (session.origins.has(destinationOrigin)) return false;
    session.origins.add(destinationOrigin);
    this.addOrigin(sessionId, destinationOrigin);
    return true;
  }

  private addOrigin(sessionId: string, origin: string): void {
    const ids = (this.sessionIdsByOrigin.get(origin) ?? []).filter((id) => id !== sessionId);
    ids.push(sessionId);
    this.sessionIdsByOrigin.set(origin, ids);
  }

  private latestSessionId(origin: string): string | undefined {
    const ids = this.sessionIdsByOrigin.get(origin);
    return ids?.[ids.length - 1];
  }

  private latestSession(origin: string): PlaybackHeaderSession | undefined {
    const id = this.latestSessionId(origin);
    return id ? this.sessions.get(id) : undefined;
  }

  private sweep(now: number): void {
    for (const [id, session] of this.sessions) {
      if (now - session.touchedAt >= this.ttlMs) this.unregister(id);
    }
  }
}
