import { describe, expect, it } from "vitest";
import { splitMentions } from "./commentText";

describe("splitMentions", () => {
  it("turns the site's own reply markup into a mention", () => {
    expect(splitMentions("[ник]Rus921k[/ник] самое интересное уже прошло")).toEqual([
      { type: "mention", name: "Rus921k" },
      { type: "text", value: " самое интересное уже прошло" },
    ]);
  });

  it("keeps text on both sides of a mention", () => {
    expect(splitMentions("да, [ник]Grokraken[/ник] прав")).toEqual([
      { type: "text", value: "да, " },
      { type: "mention", name: "Grokraken" },
      { type: "text", value: " прав" },
    ]);
  });

  it("handles several mentions in one comment", () => {
    const parts = splitMentions("[ник]a[/ник] и [ник]b[/ник]");
    expect(parts.filter((part) => part.type === "mention")).toHaveLength(2);
  });

  it("keeps YummyAnime spoilers concealed until the comment UI explicitly reveals them", () => {
    expect(splitMentions('до [спойлер="Спойлер!"]герой погибает[/спойлер] после')).toEqual([
      { type: "text", value: "до " },
      { type: "spoiler", label: "Спойлер!", value: "герой погибает" },
      { type: "text", value: " после" },
    ]);
  });

  it("supports an unlabelled spoiler block", () => {
    expect(splitMentions("[спойлер]скрытый текст[/спойлер]")).toEqual([
      { type: "spoiler", label: "Спойлер", value: "скрытый текст" },
    ]);
  });

  it("leaves a comment without mentions exactly as written", () => {
    expect(splitMentions("серьёзно на самом интересном")).toEqual([
      { type: "text", value: "серьёзно на самом интересном" },
    ]);
    expect(splitMentions("")).toEqual([]);
  });

  it("leaves markup it does not know alone rather than half-interpreting it", () => {
    expect(splitMentions("[b]жирный[/b]")).toEqual([{ type: "text", value: "[b]жирный[/b]" }]);
  });
});
