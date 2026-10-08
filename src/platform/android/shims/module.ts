// Browser stand-in for node:module, only as much as desktop's extension code uses: jsoupShim.ts
// loads cheerio through createRequire() to keep it lazy. sync-fetch (globals.ts's no-host fallback)
// has no browser equivalent and must never be reached here - the worker always bridges fetch().
import * as cheerio from "cheerio";

export function createRequire(_from: string) {
  return (id: string): unknown => {
    if (id === "cheerio") return cheerio;
    throw new Error(`require("${id}") is not available in the browser runtime`);
  };
}
