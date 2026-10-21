/**
 * The decisions VideoPlayer makes about a stream, apart from the engines that carry them out: what an
 * error means (retry, renew the token, move to another link, give up), where playback starts, and
 * whether a stream that has gone quiet is slow or dead. VideoPlayer wires hls.js / dash.js / <video>
 * to these and does what they say; the rules themselves are plain functions with tests
 * (streamPolicy.test.ts), since each of them was once a bug seen live.
 */

/** Statuses a CDN answers an expired stream token with. */
export const TOKEN_EXPIRED_STATUSES: ReadonlySet<number> = new Set([401, 403, 410]);

/**
 * Statuses that mean "ask again later". Anything else a server answered with has been looked up and
 * refused, and retrying it on a backoff only spent twenty-odd seconds before reaching the fallback. 500
 * and Cloudflare's 52x stay retryable: a CDN edge hiccuping on one segment is not the stream being gone.
 */
export const RETRYABLE_STATUSES: ReadonlySet<number> = new Set([408, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524, 525, 526, 527]);

export const MAX_NETWORK_RETRIES = 3;
export const MAX_MEDIA_RETRIES = 3;
/** hls.js asks again, without end, for a segment that parses to no media (Kodik, after a seek). */
export const MAX_EMPTY_FRAGMENT_REPEATS = 4;
/** Token renewals since the last segment that loaded: a renewed link refused at once is not a token problem. */
export const MAX_TOKEN_RENEWALS = 1;

/** An hls.js error, in the terms the policy needs. */
export interface StreamError {
  fatal: boolean;
  kind: "network" | "media" | "other";
  /** hls.js's `details`, also the reason shown when giving up. */
  details: string;
  /** HTTP status the request was answered with, when it was. */
  status?: number;
  /** The master playlist itself failed (load error or timeout). */
  manifest: boolean;
  /** hls.js's MANIFEST_LOAD_ERROR exactly: a refused master is a dead link, never an expired token. */
  manifestLoadError?: boolean;
  /** A segment that parsed to no media, by its sequence number. */
  emptyFragment?: string;
}

export type StreamAction =
  /** hls.js handles it itself, or a renewal already under way will. */
  | { kind: "ignore" }
  /** Renew the stream's token beside the playing buffer (StreamErrorPolicy.renewalSettled when done). */
  | { kind: "renew"; reason: string }
  /** Try again after `delayMs`: reload the manifest (redirect target resolved) or resume loading. */
  | { kind: "retry"; delayMs: number; reloadManifest: boolean; attempt: number }
  | { kind: "recoverMedia" }
  /** This stream is done: ask for another link, and show `reason` when there is none. */
  | { kind: "fail"; reason: string; answeredStatus?: number };

/** One stream's error budget. A new stream gets a new one. */
export class StreamErrorPolicy {
  private networkRetries = 0;
  private mediaRetries = 0;
  private emptyFragment = { sn: "", count: 0 };
  private tokenRenewals = 0;
  private renewing = false;
  private stalledDuringRenewal = false;

  /** @param canRenew a segment has played already, the stream has no separate audio, and someone can renew it. */
  decide(error: StreamError, canRenew: boolean): StreamAction {
    if (!error.fatal && error.emptyFragment !== undefined) {
      const sn = error.emptyFragment;
      this.emptyFragment = this.emptyFragment.sn === sn ? { sn, count: this.emptyFragment.count + 1 } : { sn, count: 1 };
      if (this.emptyFragment.count >= MAX_EMPTY_FRAGMENT_REPEATS) return { kind: "fail", reason: `fragment ${sn} has no media` };
    }

    // Mid-play, a segment the CDN now refuses is a token that has run out (Alloha's, six minutes in).
    // Started on the first refusal, fatal or not: hls.js retries a 403 segment on its own for half a
    // minute first, and those retries then already go to the renewed link.
    const tokenRefused = error.kind === "network" && !error.manifestLoadError &&
      error.status !== undefined && TOKEN_EXPIRED_STATUSES.has(error.status) && canRenew;
    if (tokenRefused && !this.renewing && this.tokenRenewals < MAX_TOKEN_RENEWALS) {
      this.tokenRenewals += 1;
      this.renewing = true;
      this.stalledDuringRenewal = error.fatal;
      return { kind: "renew", reason: error.details || "playback failed" };
    }
    // Gave up while the renewal is on its way: it restarts loading once it lands.
    if (tokenRefused && this.renewing) {
      if (error.fatal) this.stalledDuringRenewal = true;
      return { kind: "ignore" };
    }
    if (!error.fatal) return { kind: "ignore" };

    switch (error.kind) {
      case "network": {
        // A 4xx/5xx on a level or segment is as definitive as one on the master: retrying every
        // MegaPlay 403 three times turned a finite fallback list into minutes of loading.
        const answered = error.status !== undefined && error.status > 0 && !RETRYABLE_STATUSES.has(error.status);
        this.networkRetries += 1;
        if (answered) return { kind: "fail", reason: error.details || "playback failed", answeredStatus: error.status };
        if (this.networkRetries > MAX_NETWORK_RETRIES) return { kind: "fail", reason: error.details || "playback failed" };
        return { kind: "retry", delayMs: 500 * 2 ** (this.networkRetries - 1), reloadManifest: error.manifest, attempt: this.networkRetries };
      }
      case "media":
        // An uncapped recoverMediaError() on a genuinely broken fragment re-threw the same error
        // forever, which looks exactly like a freeze.
        this.mediaRetries += 1;
        if (this.mediaRetries > MAX_MEDIA_RETRIES) return { kind: "fail", reason: error.details || "playback failed" };
        return { kind: "recoverMedia" };
      default:
        return { kind: "fail", reason: error.details || "playback failed" };
    }
  }

  /** The renewal is over (either way). True when hls.js stopped loading meanwhile and must be restarted. */
  renewalSettled(): boolean {
    this.renewing = false;
    const restart = this.stalledDuringRenewal;
    this.stalledDuringRenewal = false;
    return restart;
  }

  /** The manifest parsed: the network is answering again. */
  manifestParsed(): void {
    this.networkRetries = 0;
  }

  /** A segment arrived: whatever token renewal came before it worked. */
  fragmentLoaded(): void {
    this.tokenRenewals = 0;
  }

  /** A segment reached the buffer: recovery actually worked, the budgets start over. */
  fragmentBuffered(): void {
    this.networkRetries = 0;
    this.mediaRetries = 0;
  }
}

/** dash.js retries transient errors itself; past a few, or "nothing in the manifest plays", the stream is done. */
export class DashErrorPolicy {
  static readonly MAX_ERRORS = 3;
  private errors = 0;

  /** True when the stream should be given up on. */
  error(noStreams: boolean): boolean {
    this.errors += 1;
    return noStreams || this.errors > DashErrorPolicy.MAX_ERRORS;
  }

  initialized(): void {
    this.errors = 0;
  }
}

/** A switch to another stream in flight: where the old one was, and whether it was playing. */
export interface PendingSwitch {
  fromUrl: string | null;
  position: number;
  resume: boolean;
}

/**
 * Where a newly loaded stream starts, in seconds (0: the beginning), and why. A switch carries the old
 * stream's position over; a retry of the same stream goes back to where it failed; otherwise the
 * episode's saved progress. hls.js is told this up front (or it downloads segment 1 for nothing) and
 * the element seeks to it once the metadata is in, so both must agree.
 */
export function startPosition(
  linkUrl: string | undefined,
  pendingSwitch: PendingSwitch | null,
  retryPositionMs: number | null,
  startPositionMs: number | null | undefined,
): { seconds: number; from: "switch" | "retry" | "progress" } {
  if (pendingSwitch && pendingSwitch.fromUrl !== linkUrl) return { seconds: pendingSwitch.position, from: "switch" };
  if (retryPositionMs !== null) return { seconds: retryPositionMs / 1000, from: "retry" };
  return { seconds: (startPositionMs ?? 0) / 1000, from: "progress" };
}

/** A position clamped to the stream's length, so a resume never lands past its end. */
export function clampToDuration(seconds: number, duration: number): number {
  return Math.min(seconds, Math.max(0, duration - 1));
}

/**
 * What a load timeout means once it fires. "wait" - bytes arrived in the window, the stream is slow,
 * not dead (seen on mobile data: a slowly downloading stream was switched away from as if it had hung);
 * "healthy" - a startup that outlived the timer but can already play; "fail" - nothing at all came.
 */
export function stallVerdict(stage: "startup" | "buffering", bytesArrived: boolean, canPlay: boolean): "wait" | "healthy" | "fail" {
  if (bytesArrived) return "wait";
  if (stage === "startup" && canPlay) return "healthy";
  return "fail";
}
