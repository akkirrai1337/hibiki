import { describe, expect, it } from "vitest";
import { originalOfResizedImage } from "./imageProxy";

describe("originalOfResizedImage", () => {
  it("unwraps a resized poster to its original", () => {
    const original = "https://animevost.org/uploads/posts/2026-06/1782052741_1.png";
    expect(originalOfResizedImage(`https://images.weserv.nl/?url=${encodeURIComponent(original)}&w=400&output=webp`)).toBe(original);
  });

  it("adds the scheme the proxy lets a source leave out", () => {
    expect(originalOfResizedImage("https://wsrv.nl/?url=cdn.example.com/a.jpg&w=400")).toBe("https://cdn.example.com/a.jpg");
  });

  it("leaves every other URL alone", () => {
    expect(originalOfResizedImage("https://static.yani.tv/posters/mega/1.avif")).toBeNull();
    expect(originalOfResizedImage("https://images.weserv.nl/")).toBeNull();
    expect(originalOfResizedImage("not a url")).toBeNull();
  });
});
