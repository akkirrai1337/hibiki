import { describe, expect, it } from "vitest";
import { createStreamRebase, directoryOf } from "./streamRebase";

describe("streamRebase", () => {
  it("takes the directory without query or fragment", () => {
    expect(directoryOf("https://a.cdn/tok1/master.m3u8?x=1#y")).toBe("https://a.cdn/tok1/");
  });

  it("sends requests under the old directory to the fresh one", () => {
    const rebase = createStreamRebase();
    expect(rebase.rewrite("https://a.cdn/tok1/seg-87.m4s")).toBe("https://a.cdn/tok1/seg-87.m4s");
    rebase.rebase("https://a.cdn/tok1/master.m3u8", "https://b.cdn/tok2/master.m3u8");
    expect(rebase.rewrite("https://a.cdn/tok1/seg-87.m4s")).toBe("https://b.cdn/tok2/seg-87.m4s");
    expect(rebase.rewrite("https://a.cdn/tok1/720/index.m3u8")).toBe("https://b.cdn/tok2/720/index.m3u8");
    // Anything else - another host, a subtitle - goes where it was going.
    expect(rebase.rewrite("https://subs.cdn/en.vtt")).toBe("https://subs.cdn/en.vtt");
  });

  it("always rebases from the original, however many renewals later", () => {
    const rebase = createStreamRebase();
    rebase.rebase("https://a.cdn/tok1/master.m3u8", "https://a.cdn/tok2/master.m3u8");
    rebase.rebase("https://a.cdn/tok1/master.m3u8", "https://a.cdn/tok3/master.m3u8");
    expect(rebase.rewrite("https://a.cdn/tok1/seg-9.m4s")).toBe("https://a.cdn/tok3/seg-9.m4s");
  });
});
