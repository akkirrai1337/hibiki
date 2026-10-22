import { describe, expect, it } from "vitest";
import { DEFAULT_SUBTITLE_STYLE, normalizeSubtitleStyle, subtitleBoxStyle } from "./subtitleStyle";

describe("subtitle style", () => {
  it("looks like the overlay always did by default", () => {
    const box = subtitleBoxStyle(DEFAULT_SUBTITLE_STYLE);
    expect(box.fontSize).toBe("1.125rem");
    expect(box.color).toBe("#ffffff");
    expect(box.backgroundColor).toBe("rgba(0,0,0,.75)");
    expect(box.fontFamily).toBeUndefined();
    expect(box.textShadow).toBeUndefined();
  });

  it("scales the size and drops the plate for an outline", () => {
    const box = subtitleBoxStyle({ ...DEFAULT_SUBTITLE_STYLE, size: 150, background: "outline" });
    expect(box.fontSize).toBe("1.6875rem");
    expect(box.backgroundColor).toBe("transparent");
    expect(box.boxShadow).toBe("none");
    expect(box.textShadow).toContain("#000");
  });

  it("puts unknown stored values back to the defaults, field by field", () => {
    expect(normalizeSubtitleStyle(undefined)).toEqual(DEFAULT_SUBTITLE_STYLE);
    expect(normalizeSubtitleStyle({ size: 999 as never, color: "yellow" })).toEqual({ ...DEFAULT_SUBTITLE_STYLE, color: "yellow" });
  });
});
