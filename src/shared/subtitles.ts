// Chromium's native <track> element only ever parses WebVTT - a source (or a user's own local
// file) handing over SRT or ASS/SSA needs converting client-side first, there is no browser API
// for it. Kept as a standalone module (no imports) so it stays trivially unit-testable and safe to
// import from a player effect without dragging in anything else.

export type SubtitleFormat = "vtt" | "srt" | "ass" | "unknown";

/** By file extension only - a source's subtitle URL rarely carries a real Content-Type, and a
 * user picking a local file only ever gives us a filename anyway. */
export function subtitleFormatFromUrl(url: string): SubtitleFormat {
  const path = (() => {
    try {
      return new URL(url).pathname;
    } catch {
      return url;
    }
  })();
  const extension = /\.([a-z0-9]+)$/i.exec(path)?.[1]?.toLowerCase();
  if (extension === "vtt") return "vtt";
  if (extension === "srt") return "srt";
  if (extension === "ass" || extension === "ssa") return "ass";
  return "unknown";
}

/** SRT and WebVTT cues are otherwise identical (timing + plain/basic-tag text) - the only real
 * differences are the header line and the comma vs. period millisecond separator. A leading
 * numeric cue-index line (SRT's own convention) is left in place; WebVTT accepts it as an optional
 * cue identifier rather than requiring it stripped. */
export function srtToVtt(srt: string): string {
  const body = srt
    .replace(/^﻿/, "")
    .replace(/\r\n?/g, "\n")
    .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, "$1.$2")
    .trim();
  return `WEBVTT\n\n${body}\n`;
}

function assTimeToVtt(time: string): string {
  // H:MM:SS.CS (centiseconds) - VTT wants HH:MM:SS.mmm.
  const match = /^(\d+):(\d{2}):(\d{2})\.(\d{2})$/.exec(time.trim());
  if (!match) return "00:00:00.000";
  const [, h, m, s, cs] = match;
  return `${h.padStart(2, "0")}:${m}:${s}.${cs}0`;
}

/** No styling, positioning or karaoke effects - same ceiling Android's own player has (see
 * PlayerScreen.kt, which renders a single plain overlay regardless of source formatting). Only
 * pulls the Start/End/Text columns out of the [Events] section's Dialogue lines, strips `{...}`
 * override tags and turns `\N`/`\n` into real line breaks, and drops everything else (styles,
 * script info, fonts) a browser has no use for anyway. */
export function assToVtt(ass: string): string {
  const lines = ass.replace(/^﻿/, "").replace(/\r\n?/g, "\n").split("\n");
  const eventsStart = lines.findIndex((line) => /^\[events\]/i.test(line.trim()));
  if (eventsStart === -1) return "WEBVTT\n";

  let format: string[] | null = null;
  const cues: string[] = [];
  for (let i = eventsStart + 1; i < lines.length; i++) {
    const line = lines[i];
    if (/^\[/.test(line.trim())) break; // next section
    const formatMatch = /^Format:\s*(.+)$/i.exec(line);
    if (formatMatch) {
      format = formatMatch[1].split(",").map((field) => field.trim().toLowerCase());
      continue;
    }
    const dialogueMatch = /^Dialogue:\s*(.+)$/i.exec(line);
    if (!dialogueMatch || !format) continue;
    const fieldCount = format.length;
    const parts = dialogueMatch[1].split(",");
    // The Text field (always last) can itself contain commas - re-join whatever split() cut off
    // the end back into it instead of losing everything past the (fieldCount - 1)th comma.
    if (parts.length > fieldCount) parts.splice(fieldCount - 1, parts.length - fieldCount + 1, parts.slice(fieldCount - 1).join(","));
    const startIndex = format.indexOf("start");
    const endIndex = format.indexOf("end");
    const textIndex = format.indexOf("text");
    if (startIndex === -1 || endIndex === -1 || textIndex === -1) continue;
    const text = (parts[textIndex] ?? "")
      .replace(/\{[^}]*\}/g, "")
      .replace(/\\N/gi, "\n")
      .replace(/\\h/gi, " ")
      .trim();
    if (!text) continue;
    cues.push(`${assTimeToVtt(parts[startIndex])} --> ${assTimeToVtt(parts[endIndex])}\n${text}`);
  }
  return `WEBVTT\n\n${cues.join("\n\n")}\n`;
}

/** Converts whatever format a subtitle happens to be in to WebVTT text - the one thing a native
 * <track> element can actually play. `vtt` passes through untouched (the common case, worth
 * skipping a needless round-trip for); `unknown` is treated as VTT too, on the theory that a
 * source with no recognizable extension is far more likely serving a bare .vtt master playlist
 * companion than something genuinely exotic. */
export function toVtt(format: SubtitleFormat, text: string): string {
  if (format === "srt") return srtToVtt(text);
  if (format === "ass") return assToVtt(text);
  return text;
}
