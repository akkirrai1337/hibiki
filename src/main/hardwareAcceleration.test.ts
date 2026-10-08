import { describe, expect, it } from "vitest";
import { parseHardwareAcceleration } from "./hardwareAcceleration";

describe("parseHardwareAcceleration", () => {
  it("turns off only on an explicit false", () => {
    expect(parseHardwareAcceleration('{"enabled":false}')).toBe(false);
    expect(parseHardwareAcceleration('{"enabled":true}')).toBe(true);
    expect(parseHardwareAcceleration("{}")).toBe(true);
    expect(parseHardwareAcceleration('{"enabled":0}')).toBe(true);
  });
  it("keeps it on for a damaged file", () => {
    expect(parseHardwareAcceleration("")).toBe(true);
    expect(parseHardwareAcceleration("not json")).toBe(true);
  });
});
