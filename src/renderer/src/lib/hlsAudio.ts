// Audio tracks inside one HLS stream. Some players put every dub of an episode in a single master
// playlist as audio renditions (KickAssAnime's: "English" and "Japanese" side by side, Japanese
// marked default) and let their page pick one; hls.js always took the default, so choosing the
// English dub still played Japanese. Which one the link means is said by its audio track or dub name.

export interface HlsAudioTrackLike {
  name?: string;
  lang?: string;
}

let languageNames: Intl.DisplayNames | null = null;

function languageName(code: string): string | null {
  try {
    languageNames ??= new Intl.DisplayNames(["en"], { type: "language" });
    return languageNames.of(code) ?? null;
  } catch {
    return null;
  }
}

const normalize = (value: string) => value.trim().toLowerCase();

/** The track `wanted` (a track or dub name like "English" or "English (dub)") names, or -1. */
export function matchAudioTrack(tracks: HlsAudioTrackLike[], wanted: string | null | undefined): number {
  if (!wanted) return -1;
  const want = normalize(wanted);
  const names = tracks.map((track) => [track.name, track.lang, track.lang ? languageName(track.lang) : null]
    .filter((value): value is string => !!value)
    .map(normalize));
  // A whole name first, then one the wanted name starts with ("english (dub)" -> "english").
  const exact = names.findIndex((candidates) => candidates.includes(want));
  if (exact >= 0) return exact;
  return names.findIndex((candidates) => candidates.some((name) => name.length >= 2 && want.startsWith(name)));
}

/** A track's name for the menu. */
export function audioTrackLabel(track: HlsAudioTrackLike, index: number): string {
  return track.name || (track.lang ? languageName(track.lang) ?? track.lang : null) || `Audio ${index + 1}`;
}
