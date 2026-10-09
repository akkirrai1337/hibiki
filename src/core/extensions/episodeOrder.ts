import type { PlaybackGroup } from "@shared/types";

/**
 * Episodes first to last. Everything after a source answers - the next episode in the player, the
 * title page's "Up next" - walks a group's list in order, but APK sources (Aniyomi's convention)
 * list the newest episode first. A list that runs from a higher number down to a lower one is
 * turned around as a whole; anything else keeps the source's own order, specials and all.
 */
export function chronologicalGroups(groups: PlaybackGroup[]): PlaybackGroup[] {
  return groups.map((group) => {
    const { episodes } = group;
    if (episodes.length < 2 || !(episodes[0].number > episodes[episodes.length - 1].number)) return group;
    return { ...group, episodes: [...episodes].reverse() };
  });
}
