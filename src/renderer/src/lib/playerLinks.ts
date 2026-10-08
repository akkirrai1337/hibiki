import type { PlayerLink } from "@shared/types";

// Everything about *which* of an episode's links should play, in one place. This logic used to be
// split between the watch route (which link to start on) and VideoPlayer's settings menu (which
// link a manual pick maps to), with neither half aware of the other - which is how picking 720p on
// Alloha could silently land on Kodik's 720p, and how resuming an Alloha episode played Kodik for
// a moment first. Pure functions on purpose: this is the part that was impossible to test while it
// lived inside component bodies.

// Mirrors Android's PlaybackResolver: prefer a directly playable stream over handing the user a
// third-party embed page. This app doesn't run per-provider embed extractors in the renderer, so
// the ordering approximates it by preferring whichever direct link a source already returned.
const LINK_TYPE_PRIORITY: Record<string, number> = { DIRECT_HLS: 0, DIRECT_MP4: 0, DIRECT_DASH: 0, EMBED: 1 };
const RETIRED_PLAYER_NAMES = new Set(["cvh", "sibnet"]);

export interface PlaybackPreference {
  translation?: string | null;
  playerName?: string | null;
}

/** The starting pick when there is nothing saved to honour: a direct stream. An embed is only ever
 * an unresolved candidate (see resolveEmbedLinks in the main process) - the player has no way to
 * play a third-party page, so it is never a default and never something to fall back to. */
export function pickDefaultLink<T extends { type: string }>(links: T[] | undefined): T | undefined {
  if (!links) return undefined;
  return [...links.filter((link) => link.type !== "EMBED")].sort((a, b) => (LINK_TYPE_PRIORITY[a.type] ?? 0) - (LINK_TYPE_PRIORITY[b.type] ?? 0))[0];
}

/**
 * The link matching what was last watched through. Both halves of the preference count, so a dub
 * that is still available wins over neither matching; returns undefined when nothing matches at
 * all, which is the caller's cue to fall back to pickDefaultLink().
 */
export function pickPreferredLink(links: PlayerLink[], preference: PlaybackPreference): PlayerLink | undefined {
  const { translation, playerName } = preference;
  if (!translation && !playerName) return undefined;
  // CVH/Sibnet were removed because they ultimately hand Chromium their unstable iframe page.
  // A persisted choice for either must not degrade into a translation-only partial match: that
  // would make an old saved player silently dictate the next provider instead of using the
  // source's current priority (Kodik is first for YummyAnime, for example).
  if (playerName && RETIRED_PLAYER_NAMES.has(playerName.trim().toLowerCase())) return undefined;
  return links
    .map((link) => ({
      link,
      score: (link.translation === translation ? 1 : 0) + (link.playerName === playerName ? 1 : 0),
    }))
    .filter((candidate) => candidate.score > 0)
    .sort((a, b) => b.score - a.score)[0]?.link;
}

/** Distinct, non-empty values of one field across a set of links. */
function distinctValues(links: PlayerLink[], pick: (link: PlayerLink) => string | null | undefined): string[] {
  return [...new Set(links.map(pick).filter((value): value is string => !!value))];
}

export function translationOptions(links: PlayerLink[]): string[] {
  return distinctValues(links, (link) => link.translation);
}

export function playerOptions(links: PlayerLink[]): string[] {
  return distinctValues(links, (link) => link.playerName);
}

/** Same provider, same dub: every audio track and quality of what is playing. */
function linksForCurrentDub(links: PlayerLink[], current: PlayerLink): PlayerLink[] {
  return links.filter(
    (link) => link.playerName === current.playerName && link.translation === current.translation,
  );
}

const sameAudioTrack = (a: PlayerLink, b: PlayerLink) => (a.audioTrack ?? null) === (b.audioTrack ?? null);

/**
 * The links that belong to the stream currently playing - same provider, same dub, same audio
 * track. A quality is a rendition of one provider's stream, so mixing every link of the episode
 * into one list is what made an Alloha 720p pick jump to Kodik.
 */
export function linksForCurrentStream(links: PlayerLink[], current: PlayerLink): PlayerLink[] {
  return linksForCurrentDub(links, current).filter((link) => sameAudioTrack(link, current));
}

/** Audio tracks of the current dub, in the order the resolver reported them (its default first). */
export function audioTrackOptions(links: PlayerLink[], current: PlayerLink): string[] {
  return distinctValues(linksForCurrentDub(links, current), (link) => link.audioTrack);
}

/** Another audio track of the same dub, in the quality playing now when it has it. */
export function pickLinkForAudioTrack(links: PlayerLink[], current: PlayerLink, audioTrack: string): PlayerLink | undefined {
  const candidates = linksForCurrentDub(links, current).filter((link) => link.audioTrack === audioTrack);
  return candidates.find((link) => link.quality === current.quality) ?? candidates[0];
}

// -Infinity, so labels carrying no resolution at all ("auto", "source", ...) still sort last now
// that the list runs highest-first.
function qualityValue(label: string): number {
  const match = label.match(/\d{3,4}/);
  return match ? Number.parseInt(match[0], 10) : Number.NEGATIVE_INFINITY;
}

/** Qualities of the current stream, descending by resolution (labels without a number sort last). */
export function qualityOptions(links: PlayerLink[], current: PlayerLink): string[] {
  return distinctValues(linksForCurrentStream(links, current), (link) => link.quality)
    .sort((a, b) => qualityValue(b) - qualityValue(a) || a.localeCompare(b));
}

/**
 * Switching one dimension (dub, provider, quality) while keeping the other two wherever possible:
 * picking a different dub shouldn't reset an already-chosen provider if that combination exists.
 * Returns undefined when nothing carries the requested value.
 */
export function pickLinkForDimension(
  links: PlayerLink[],
  current: PlayerLink,
  changed: Partial<Pick<PlayerLink, "translation" | "playerName" | "quality">>,
): PlayerLink | undefined {
  const target = { translation: current.translation, playerName: current.playerName, quality: current.quality, ...changed };
  const entries = Object.entries(changed) as [keyof typeof changed, string | null | undefined][];
  return links
    .filter((link) => entries.every(([key, value]) => link[key] === value))
    .map((link) => ({
      link,
      score:
        (link.translation === target.translation ? 1 : 0) +
        (link.playerName === target.playerName ? 1 : 0) +
        (link.quality === target.quality ? 1 : 0) +
        (sameAudioTrack(link, current) ? 1 : 0),
    }))
    .sort((a, b) => b.score - a.score)[0]?.link;
}

/** A quality pick stays inside the stream that is playing - see linksForCurrentStream(). */
export function pickLinkForQuality(
  links: PlayerLink[],
  current: PlayerLink,
  quality: string,
): PlayerLink | undefined {
  return linksForCurrentStream(links, current).find((link) => link.quality === quality);
}

/**
 * An EMBED link resolves into a whole set of renditions, so the pick that got us there has to
 * survive the expansion - taking the default outright is what made an explicit quality choice look
 * like it snapped back to whatever the source lists first.
 */
export function pickResolvedLink(playable: PlayerLink[], requested: PlayerLink): PlayerLink | undefined {
  // The audio track first: a quality that only exists in another track's sound is not the pick.
  const sameTrack = requested.audioTrack ? playable.filter((link) => link.audioTrack === requested.audioTrack) : [];
  const candidates = sameTrack.length > 0 ? sameTrack : playable;
  const sameQuality = requested.quality
    ? candidates.find((link) => link.quality === requested.quality)
    : undefined;
  return sameQuality ?? pickDefaultLink(candidates);
}

/**
 * The provider a URL belongs to, rather than the exact host serving it.
 *
 * Kodik hands out p12/p13/p14.solodcdn.com for the very same library, and scoring by full origin
 * read those as three unrelated routes - enough to send a fallback down to 360p on a neighbouring
 * edge node while a working 480p sat on the one that had just failed. The last two labels are a
 * rough stand-in for the registrable domain; where it is too rough, two genuinely distinct
 * providers merely stop being credited for the difference, which is the harmless direction.
 */
function linkProvider(url: string): string | null {
  try {
    const labels = new URL(url).hostname.split(".");
    return labels.length <= 2 ? labels.join(".") : labels.slice(-2).join(".");
  } catch {
    return null;
  }
}

/** Picks a genuinely different route after one stream has exhausted its retries. */
export function pickPlaybackFallback(
  links: PlayerLink[],
  failed: PlayerLink,
  excludedUrls: ReadonlySet<string>,
): PlayerLink | undefined {
  const failedProvider = linkProvider(failed.url);
  return links
    .filter((link) => link.url !== failed.url && !excludedUrls.has(link.url))
    .map((link, index) => {
      const provider = linkProvider(link.url);
      const score =
        (provider && failedProvider && provider !== failedProvider ? 100 : 0) +
        (link.translation === failed.translation ? 20 : 0) +
        (link.playerName === failed.playerName ? 10 : 0) +
        (link.type !== "EMBED" ? 5 : 0) +
        (sameAudioTrack(link, failed) ? 2 : 0);
      return { link, index, score };
    })
    .sort((a, b) => b.score - a.score || a.index - b.index)[0]?.link;
}
