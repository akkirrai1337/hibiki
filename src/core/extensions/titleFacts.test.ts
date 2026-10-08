import { describe, expect, it } from "vitest";
import type { AnimeTitle } from "@shared/types";
import { cleanStory, parseDescriptionFacts, parseTitleName, withTitleFacts } from "./titleFacts";

const title = (fields: Partial<AnimeTitle>): AnimeTitle => ({ id: "1", sourceId: "apk:1", ...fields });

describe("parseTitleName", () => {
  it("takes the episode counts and the Russian name off an Animevost name", () => {
    expect(parseTitleName("Боруто: Новое поколение Наруто / Boruto: Naruto Next Generations [1-293 из 293]")).toEqual({
      russian: "Боруто: Новое поколение Наруто",
      original: "Boruto: Naruto Next Generations",
      available: 293,
      total: 293,
    });
  });

  it("leaves an open-ended total unknown and drops trailing notes", () => {
    expect(parseTitleName("Ван Пис / One Piece [1-1180 из 10000+] [1181 серия - в 2027 году]")).toEqual({
      russian: "Ван Пис",
      original: "One Piece",
      available: 1180,
      total: undefined,
    });
  });

  it("keeps a plain name as it is", () => {
    expect(parseTitleName("Frieren: Beyond Journey's End")).toEqual({ original: "Frieren: Beyond Journey's End" });
  });
});

describe("parseDescriptionFacts", () => {
  const animevost = "Год: 2017\nТип: ТВ\nКоличество серий: 293 (25 мин.)\nРежиссёр: Абэ Норийки\nОписание: И снова нас возвращают в этот прекрасный и огромный мир.";

  it("reads a leading block up to the story label", () => {
    expect(parseDescriptionFacts(animevost)).toEqual({
      story: "И снова нас возвращают в этот прекрасный и огромный мир.",
      facts: [
        { field: "year", value: "2017" },
        { field: "type", value: "ТВ" },
        { field: "episodes", value: "293 (25 мин.)" },
        { field: "ignored", value: "Абэ Норийки" },
      ],
    });
  });

  it("reads the same block written on one line", () => {
    expect(parseDescriptionFacts(animevost.replace(/\n/g, " "))?.story).toBe("И снова нас возвращают в этот прекрасный и огромный мир.");
  });

  it("reads a trailing \"Key: value | Key: value\" line", () => {
    const parsed = parseDescriptionFacts("A boy and his sword.\n\nType: TV | Aired: Oct 5, 2004 | Rating: PG-13 | Episodes: 366");
    expect(parsed?.story).toBe("A boy and his sword.");
    expect(parsed?.facts.map((f) => [f.field, f.value])).toEqual([["type", "TV"], ["year", "Oct 5, 2004"], ["rating", "PG-13"], ["episodes", "366"]]);
  });

  it("reads a trailing Markdown block", () => {
    const parsed = parseDescriptionFacts("Second season.\n\n**Rating:** PG 13\n**Studios:** Bones");
    expect(parsed?.story).toBe("Second season.");
    expect(parsed?.facts).toEqual([{ field: "rating", value: "PG 13" }, { field: "studio", value: "Bones" }]);
  });

  it("leaves a story with a colon in it alone", () => {
    expect(parseDescriptionFacts("He said: no. Then the year: unknown, the type of man he was.")).toBeNull();
    expect(parseDescriptionFacts("Just a story without facts.")).toBeNull();
  });
});

describe("cleanStory", () => {
  it("cuts a franchise list and the site's guest notice off the story", () => {
    const story = "Сын Наруто готов показать миру, насколько достоин батьки. Это аниме состоит из: Наруто - ТВ (220 эп.), первый сериал, адаптация манги, 2002 Наруто OVA-1 - OVA (1 эп.), дополнение к сериалу, 2003 Наруто (фильм первый) - п/ф, дополнение к сериалу, 2004 Информация Посетители, находящиеся в группе Гости, не могут оставлять комментарии к данной публикации.";
    expect(cleanStory(story)).toBe("Сын Наруто готов показать миру, насколько достоин батьки.");
  });

  it("keeps a story that only mentions a heading-like word", () => {
    const story = "Хронология: события идут после первого сезона, и герои - взрослые люди.";
    expect(cleanStory(story)).toBe(story);
  });
});

describe("withTitleFacts", () => {
  it("fills the fields from the name and the description, keeping only the story", () => {
    const result = withTitleFacts(title({
      originalName: "Боруто: Новое поколение Наруто / Boruto: Naruto Next Generations [1-293 из 293]",
      description: "Год: 2017\nТип: ТВ\nКоличество серий: 293 (25 мин.)\nРежиссёр: Абэ Норийки\nОписание: И снова нас возвращают.",
      genres: ["приключения"],
      status: "completed",
    }));
    expect(result).toMatchObject({
      russianName: "Боруто: Новое поколение Наруто",
      originalName: "Boruto: Naruto Next Generations",
      availableEpisodeCount: 293,
      episodeCount: 293,
      year: 2017,
      type: "tv",
      description: "И снова нас возвращают.",
      genres: ["приключения"],
      status: "released",
    });
  });

  it("reads a type followed by more words, and not a word that merely starts like one", () => {
    expect(withTitleFacts(title({ originalName: "X", description: "Тип: ТВ (12 эп.)\nГод: 2020\nОписание: s" })).type).toBe("tv");
    expect(withTitleFacts(title({ originalName: "X", description: "Тип: Spinoff\nГод: 2020\nОписание: s" })).type).toBeUndefined();
  });

  it("does not overwrite what the source filled itself", () => {
    const result = withTitleFacts(title({ originalName: "X", year: 2020, type: "movie", description: "Год: 2017\nТип: ТВ\nОписание: story" }));
    expect(result).toMatchObject({ year: 2020, type: "movie", description: "story" });
  });

  it("returns an unremarkable title unchanged", () => {
    const plain = title({ originalName: "Frieren", description: "A mage outlives her party.", status: "ongoing" });
    expect(withTitleFacts(plain)).toEqual(plain);
  });
});
