import { describe, expect, it } from "vitest";
import { assToVtt, srtToVtt, subtitleFormatFromUrl, subtitleTextToVtt, toVtt } from "./subtitles";

describe("subtitleFormatFromUrl", () => {
  it("detects vtt", () => {
    expect(subtitleFormatFromUrl("https://cdn.example/subs/ru.vtt")).toBe("vtt");
  });

  it("detects srt", () => {
    expect(subtitleFormatFromUrl("https://cdn.example/subs/ru.srt?token=1")).toBe("srt");
  });

  it("detects ass and ssa", () => {
    expect(subtitleFormatFromUrl("https://cdn.example/subs/full.ass")).toBe("ass");
    expect(subtitleFormatFromUrl("local/full.ssa")).toBe("ass");
  });

  it("falls back to unknown with no recognizable extension", () => {
    expect(subtitleFormatFromUrl("https://cdn.example/subs/ru")).toBe("unknown");
  });

  it("does not choke on an unparsable url", () => {
    expect(subtitleFormatFromUrl("not a url.srt")).toBe("srt");
  });
});

describe("srtToVtt", () => {
  it("converts timestamps and adds the WEBVTT header", () => {
    const srt = "1\n00:00:01,000 --> 00:00:04,500\nHello there\n\n2\n00:00:05,000 --> 00:00:06,000\nSecond line\n";
    const vtt = srtToVtt(srt);
    expect(vtt.startsWith("WEBVTT\n\n")).toBe(true);
    expect(vtt).toContain("00:00:01.000 --> 00:00:04.500");
    expect(vtt).toContain("Hello there");
    expect(vtt).toContain("00:00:05.000 --> 00:00:06.000");
  });

  it("strips a leading BOM and normalizes CRLF", () => {
    const srt = "﻿1\r\n00:00:01,000 --> 00:00:02,000\r\nHi\r\n";
    expect(srtToVtt(srt)).not.toContain("﻿");
    expect(srtToVtt(srt)).not.toContain("\r");
  });
});

describe("assToVtt", () => {
  const sample = [
    "[Script Info]",
    "Title: test",
    "",
    "[Events]",
    "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
    "Dialogue: 0,0:00:01.50,0:00:04.00,Default,,0,0,0,,{\\an8}Hello, world!",
    "Dialogue: 0,0:00:05.00,0:00:06.20,Default,,0,0,0,,Line one\\NLine two",
    "",
  ].join("\n");

  it("extracts cues with converted timestamps and stripped override tags", () => {
    const vtt = assToVtt(sample);
    expect(vtt.startsWith("WEBVTT\n\n")).toBe(true);
    expect(vtt).toContain("00:00:01.500 --> 00:00:04.000");
    expect(vtt).toContain("Hello, world!");
    expect(vtt).not.toContain("{\\an8}");
  });

  it("keeps a comma inside the text field intact", () => {
    expect(assToVtt(sample)).toContain("Hello, world!");
  });

  it("turns \\N into a real line break", () => {
    const vtt = assToVtt(sample);
    expect(vtt).toContain("Line one\nLine two");
  });

  it("returns a bare header when there is no [Events] section", () => {
    expect(assToVtt("[Script Info]\nTitle: x\n")).toBe("WEBVTT\n");
  });
});

describe("toVtt", () => {
  it("passes vtt and unknown text through untouched", () => {
    expect(toVtt("vtt", "WEBVTT\n\nfoo")).toBe("WEBVTT\n\nfoo");
    expect(toVtt("unknown", "WEBVTT\n\nfoo")).toBe("WEBVTT\n\nfoo");
  });

  it("dispatches srt and ass through their own converters", () => {
    expect(toVtt("srt", "1\n00:00:01,000 --> 00:00:02,000\nHi\n")).toContain("00:00:01.000");
    expect(toVtt("ass", "[Events]\nFormat: Start, End, Text\nDialogue: 0:00:01.00,0:00:02.00,Hi\n")).toContain("00:00:01.000");
  });
});

describe("subtitleTextToVtt", () => {
  it("passes WebVTT through", () => {
    const vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n";
    expect(subtitleTextToVtt(vtt)).toBe(vtt);
  });

  it("reads SRT and ASS by their content, whatever the address said", () => {
    expect(subtitleTextToVtt("1\n00:00:01,000 --> 00:00:02,000\nHi\n")).toContain("00:00:01.000 --> 00:00:02.000");
    const ass = "[Script Info]\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hi\n";
    expect(subtitleTextToVtt(ass)).toMatch(/^WEBVTT[\s\S]*00:00:01\.000 --> 00:00:02\.000/);
  });

  it("refuses what is not a subtitle file", () => {
    expect(subtitleTextToVtt("<html><body>403 Forbidden</body></html>")).toBeNull();
    expect(subtitleTextToVtt("")).toBeNull();
  });
});
