import { describe, expect, it } from "vitest";
import { cappedLogSink, LOG_HARD_LIMIT_BYTES, LOG_SOFT_LIMIT_BYTES, logRotation } from "./logFiles";

describe("cappedLogSink", () => {
  it("writes everything until the soft limit, then only problems, then nothing", () => {
    let total = 0;
    const chunks: string[] = [];
    const sink = cappedLogSink((text) => {
      total += text.length;
      chunks.push(text);
    });
    const line = "x".repeat(1023);
    while (total < LOG_SOFT_LIMIT_BYTES) sink(line, "info");

    const before = chunks.length;
    sink(line, "info");
    sink(line, "debug");
    // The notice only: info and debug are dropped from now on.
    expect(chunks.length).toBe(before + 1);
    expect(chunks.at(-1)).toContain("only warnings and errors");

    sink("boom", "error");
    expect(chunks.at(-1)).toBe("boom\n");

    while (total < LOG_HARD_LIMIT_BYTES) sink(line, "warn");
    sink("late", "error");
    expect(chunks.at(-1)).toContain("nothing more is written");
    const stopped = chunks.length;
    sink("later", "error");
    expect(chunks.length).toBe(stopped);
    expect(total).toBeLessThan(LOG_HARD_LIMIT_BYTES + 4096);
  });
});

describe("logRotation", () => {
  it("moves the oldest out of the way first, so nothing is overwritten before it moves", () => {
    expect(logRotation((name) => `/logs/${name}`)).toEqual([
      ["/logs/hibiki.1.log", "/logs/hibiki.2.log"],
      ["/logs/hibiki.log", "/logs/hibiki.1.log"],
    ]);
  });
});
