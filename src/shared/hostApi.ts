// What extension scripts may rely on from this app, as one number.
//
// A source or resolver update that needs something this build doesn't have (a new host global, a
// new field it reads) would install fine and then break - for whoever updated before updating the
// app. A manifest can say what it needs with `"hostApi": N`; this build installs it only when N is
// at most HOST_API, and otherwise keeps the version it has. A manifest without `hostApi` is never
// checked: it is how every extension was written before this existed.
//
// Raise HOST_API by one whenever extension scripts get something new to rely on, and add it to the
// table in hibiki-sources' scripts/build_index.py, which refuses to publish a script using it
// without the matching `hostApi`.
//
//   1  everything scripts had when this was introduced: fetch, fetchAll, storage, challenge,
//      browserFetch, Jsoup, Gzip, Url, AnimeTitle, console, preferredLanguage; the `dataSaver`
//      flag on a resolver's link.
export const HOST_API = 1;

/** The `hostApi` a manifest asks for, or null when it asks for nothing (or nothing readable). */
export function requiredHostApi(manifest: { hostApi?: unknown } | null | undefined): number | null {
  const value = manifest?.hostApi;
  return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : null;
}

/** Whether this build can run something declaring `manifest.hostApi`. No declaration: yes. */
export function supportsHostApi(manifest: { hostApi?: unknown } | null | undefined): boolean {
  const required = requiredHostApi(manifest);
  return required === null || required <= HOST_API;
}
