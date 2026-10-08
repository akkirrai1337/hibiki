/** Fetch API headers as HttpResponse wants them: lower-cased names, one joined value per header,
 * except set-cookie, which undici folds into getSetCookie() and is kept one entry per cookie. */
export function headerRecord(headers: Headers): Record<string, string[]> {
  const record: Record<string, string[]> = {};
  headers.forEach((value, key) => {
    if (key.toLowerCase() !== "set-cookie") record[key.toLowerCase()] = [value];
  });
  const setCookies = typeof headers.getSetCookie === "function" ? headers.getSetCookie() : [];
  if (setCookies.length > 0) record["set-cookie"] = setCookies;
  return record;
}
