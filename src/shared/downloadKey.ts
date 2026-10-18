import type { DownloadKey } from "./types";

/** One string per download. An episode id alone is not one: several sources number episodes
 * "1", "2"... in every title, so two titles' downloads would take each other's place. */
export const downloadKeyId = ({ sourceId, animeId, episodeId }: DownloadKey): string => `${sourceId}\n${animeId}\n${episodeId}`;

/** Only the key's own fields, out of a request or a progress event. */
export const downloadKeyOf = ({ sourceId, animeId, episodeId }: DownloadKey): DownloadKey => ({ sourceId, animeId, episodeId });
