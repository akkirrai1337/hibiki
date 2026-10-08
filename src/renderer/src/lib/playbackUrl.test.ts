import { describe, expect, it } from "vitest";
import { playbackUrl } from "./playbackUrl";

describe("playbackUrl", () => {
  it("uses HTTPS for protocol-relative provider streams", () => {
    expect(playbackUrl("//cloud.solodcdn.com/video/master.m3u8")).toBe("https://cloud.solodcdn.com/video/master.m3u8");
  });

  it("preserves explicit remote and downloaded-episode URLs", () => {
    expect(playbackUrl("https://cdn.example/master.m3u8")).toBe("https://cdn.example/master.m3u8");
    expect(playbackUrl("hibiki-download://local/C%3A%2Fepisodes%2Fone.mp4")).toBe("hibiki-download://local/C%3A%2Fepisodes%2Fone.mp4");
  });
});
