import { describe, expect, it } from "vitest";
import { isGenericDubTitle } from "./dubTitle";

describe("isGenericDubTitle", () => {
  it("matches the source's own generic placeholder, case/whitespace-insensitively", () => {
    expect(isGenericDubTitle("Episodes")).toBe(true);
    expect(isGenericDubTitle("episodes")).toBe(true);
    expect(isGenericDubTitle("  Episodes  ")).toBe(true);
  });
  it("is false for a real dub name and for nothing at all", () => {
    expect(isGenericDubTitle("AniLibria")).toBe(false);
    expect(isGenericDubTitle("Episodes 2")).toBe(false);
    expect(isGenericDubTitle(null)).toBe(false);
    expect(isGenericDubTitle(undefined)).toBe(false);
    expect(isGenericDubTitle("")).toBe(false);
  });
});
