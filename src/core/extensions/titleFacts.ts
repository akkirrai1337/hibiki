// Facts a source left as text: many Aniyomi extensions have no fields for a title's year, type or
// episode count, so they write them into the description ("Год: 2017 Тип: ТВ Количество серий:
// 293 ... Описание: <the story>", or a trailing "**Rating:** PG-13" / "Type: TV | Episodes: 366"
// block) and into the name itself ("Ван Пис / One Piece [1-1180 из 10000+]"). This reads them back
// into AnimeTitle's own fields by one general rule, and leaves only the story as the description.
//
// The rule is deliberately narrow: facts are recognised only by a fixed set of labels (Russian,
// Ukrainian, English), only as a block at the start or the end of the text, and only when at least
// two of them are there - a colon inside the story itself is never mistaken for one.
import type { AnimeTitle, AnimeType } from "@shared/types";

type Field = "year" | "type" | "episodes" | "status" | "genres" | "rating" | "score" | "names" | "studio" | "ignored" | "story";

/** Labels, lower-cased, without the colon. "story" marks where the description proper begins. */
const LABELS: Array<[Field, string[]]> = [
  ["year", ["год", "год выхода", "год выпуска", "дата выхода", "рік", "рік виходу", "year", "released", "aired", "premiered", "выпуск"]],
  ["type", ["тип", "type", "формат", "format"]],
  ["episodes", ["количество серий", "кол-во серий", "серий", "серии", "эпизоды", "эпизодов", "кількість серій", "серій", "episodes"]],
  ["status", ["статус", "status"]],
  ["genres", ["жанр", "жанры", "жанри", "genre", "genres"]],
  ["rating", ["рейтинг", "возрастной рейтинг", "возрастные ограничения", "вікове обмеження", "rating", "age rating"]],
  ["score", ["оценка", "рейтинг mal", "score", "mal score", "mean score"]],
  ["names", ["альтернативное название", "другие названия", "оригинальное название", "назва", "other name", "other names", "synonyms", "japanese", "english"]],
  ["studio", ["студия", "студии", "студія", "studio", "studios"]],
  ["ignored", [
    "режиссёр", "режиссер", "режисер", "director", "автор оригинала", "автор", "author", "длительность", "продолжительность",
    "тривалість", "duration", "озвучка", "озвучивание", "озвучення", "перевод", "subtitles", "source", "первоисточник",
    "сезон", "season", "страна", "country", "producers", "licensors", "трансляция", "выходит", "следующий эпизод",
  ]],
  ["story", ["описание", "опис", "сюжет", "description", "synopsis", "summary", "plot"]],
];

const FIELD_BY_LABEL = new Map<string, Field>(LABELS.flatMap(([field, labels]) => labels.map((label): [string, Field] => [label, field])));
// Longest first, so "количество серий" wins over "серий" and "год выхода" over "год".
const LABEL_PATTERN = [...FIELD_BY_LABEL.keys()].sort((a, b) => b.length - a.length).map(escapeRegExp).join("|");
// A label is a whole word: at the start, or after whitespace / a separator; optionally in Markdown bold.
const LABEL_RE = new RegExp(`(^|[\\s|•·;,(])\\*{0,2}(${LABEL_PATTERN})\\*{0,2}\\s*:\\s*\\*{0,2}`, "giu");

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

interface Fact {
  field: Field;
  value: string;
}

/** The description split into its facts and the story, or null when it has no such block. */
export function parseDescriptionFacts(description: string): { story: string | null; facts: Fact[] } | null {
  const matches = [...description.matchAll(LABEL_RE)];
  if (matches.length === 0) return null;
  // Each label's value runs to the next label.
  const segments = matches.map((match, i) => {
    const labelStart = match.index + match[1].length;
    const valueStart = match.index + match[0].length;
    const valueEnd = i + 1 < matches.length ? matches[i + 1].index + matches[i + 1][1].length : description.length;
    return { field: FIELD_BY_LABEL.get(match[2].toLowerCase())!, labelStart, valueStart, valueEnd };
  });
  const factCount = segments.filter((s) => s.field !== "story").length;
  const storyLabel = segments.find((s) => s.field === "story");

  // A block at the start: facts first, then (optionally) "Описание:" and the story.
  const leading = description.slice(0, segments[0].labelStart).trim() === "";
  if (leading && (factCount >= 2 || (storyLabel && factCount >= 1))) {
    const facts: Fact[] = [];
    let story: string | null = null;
    for (const segment of segments) {
      const value = description.slice(segment.valueStart, segment.valueEnd).trim();
      if (segment.field === "story") {
        story = description.slice(segment.valueStart).trim() || null;
        break;
      }
      // Without a "story" label, the story is whatever follows the last fact's first line.
      const isLast = segment === segments[segments.length - 1];
      if (isLast && !storyLabel) {
        const [first, ...rest] = value.split(/\n+/);
        facts.push({ field: segment.field, value: first.trim() });
        story = rest.join("\n").trim() || null;
      } else {
        facts.push({ field: segment.field, value });
      }
    }
    return { story, facts };
  }

  // A block at the end: the story, then facts that each take the rest of their line (or segment).
  const trailingFacts = segments.filter((s) => s.field !== "story");
  if (trailingFacts.length >= 2) {
    const blockStart = trailingFacts[0].labelStart;
    const before = description.slice(0, blockStart);
    // Only when the block is on its own lines - never facts woven into a sentence.
    if (before.trim() !== "" && !/\n\s*$/.test(before) && !/^\s*$/.test(before.split("\n").pop() ?? "")) return null;
    const facts = trailingFacts.map((segment) => ({
      field: segment.field,
      value: description.slice(segment.valueStart, segment.valueEnd).replace(/\s*\|\s*$/, "").trim(),
    }));
    return { story: before.trim() || null, facts };
  }
  return null;
}

// Whole words:  knows only Latin letters, so the end of a word is spelled out.
const WORD_END = String.raw`(?=$|[\s,.;:()/-])`;
const TYPE_ALIASES: Array<[RegExp, AnimeType]> = [
  [new RegExp(`^(тв|tv|тв-сериал|tv series|сериал|серіал)${WORD_END}`, "i"), "tv"],
  [new RegExp(`^(фильм|movie|film|полнометражный|фільм)${WORD_END}`, "i"), "movie"],
  [new RegExp(`^ova${WORD_END}`, "i"), "ova"],
  [new RegExp(`^ona${WORD_END}`, "i"), "ona"],
  [new RegExp(`^(спешл|special|спецвыпуск|sp)${WORD_END}`, "i"), "special"],
];

function readType(value: string): AnimeType | null {
  const trimmed = value.trim();
  return TYPE_ALIASES.find(([pattern]) => pattern.test(trimmed))?.[1] ?? null;
}

function readStatus(value: string): string | null {
  const v = value.toLowerCase();
  if (/онгоинг|выходит|ongoing|airing|виходить/.test(v)) return "ongoing";
  if (/анонс|announced|upcoming|not yet/.test(v)) return "announced";
  if (/вышел|заверш|released|finished|completed|вийшов|завершено/.test(v)) return "released";
  return null;
}

// "[1-293 из 293]", "[1-1180 из 10000+]", "[1-12 of 12]", "[12 из 24]": what is out of what is planned.
const EPISODE_BRACKET = /\[\s*(?:(\d+)\s*[-–]\s*)?(\d+)\s*(?:из|of|з)\s*(\d+)(\+?)\s*\]/i;

/** A name with its bracketed episode counts and trailing notes taken off, and split "Русское / Original". */
export function parseTitleName(name: string): { russian?: string; original: string; available?: number; total?: number } {
  let base = name;
  let available: number | undefined;
  let total: number | undefined;
  const counts = base.match(EPISODE_BRACKET);
  if (counts) {
    available = Number(counts[2]);
    if (!counts[4]) total = Number(counts[3]);
    base = base.slice(0, counts.index).trim();
  } else {
    // Other trailing bracket notes ("[1181 серия - в 2027 году]") are not part of the name either.
    base = base.replace(/(\s*\[[^\]]*\])+\s*$/, "").trim();
  }
  const parts = base.split(/\s+\/\s+/);
  if (parts.length === 2) {
    const cyrillic = /[а-яёіїєґ]/i;
    const [first, second] = parts;
    if (cyrillic.test(first) && !cyrillic.test(second)) return { russian: first, original: second, available, total };
    if (cyrillic.test(second) && !cyrillic.test(first)) return { russian: second, original: first, available, total };
  }
  return { original: base, available, total };
}

// Site furniture some extensions scrape along with the story.
const BOILERPLATE = [
  /\s*(Информация\s+)?Посетители,\s*находящиеся в группе Гости,\s*не могут оставлять комментарии к данной публикации\.?/giu,
  /\s*(Внимание!?\s*)?Для того,? чтобы оставить комментарий,? (необходимо|нужно) (зарегистрироваться|авторизоваться)[^.]*\.?/giu,
];

// The heading of a franchise / watch-order list the site appends to the story.
const LIST_HEADING = /((?:Это|Данное)\s+аниме\s+состоит\s+из|Порядок\s+просмотра|Хронология(?:\s+просмотра)?|Франшиза|Связанные\s+(?:аниме|тайтлы|релизы)|Watch\s+order|Franchise|Related\s+(?:anime|titles))\s*:/iu;
// One entry of such a list: "Наруто - ТВ (220 эп.)", "... - п/ф", "... - OVA (1 эп.)".
const LIST_ENTRY = /\s[-–—]\s*(?:ТВ|TV|OVA|ONA|п\/ф|к\/ф|фильм|спешл|special|movie|сериал)(?=$|[\s,.;:()])/giu;

/** The story without site furniture and without a franchise list tacked onto its end. */
export function cleanStory(story: string | null | undefined): string | null {
  if (!story) return story ?? null;
  let text = story;
  for (const pattern of BOILERPLATE) text = text.replace(pattern, "");
  const heading = LIST_HEADING.exec(text);
  // Only a real list - at least two "Title - TYPE" entries after the heading; the cut starts at the heading.
  if (heading && (text.slice(heading.index).match(LIST_ENTRY) ?? []).length >= 2) {
    text = text.slice(0, heading.index);
  }
  return text.trim() || null;
}

/**
 * The same title with the facts its source left as text moved into its fields. Fields the source
 * did fill are kept as they are; an unparseable description or name is returned untouched.
 */
export function withTitleFacts(title: AnimeTitle): AnimeTitle {
  const next: AnimeTitle = { ...title };
  if (title.originalName) {
    const name = parseTitleName(title.originalName);
    next.originalName = name.original;
    if (name.russian && !title.russianName) next.russianName = name.russian;
    if (name.available !== undefined && title.availableEpisodeCount == null) next.availableEpisodeCount = name.available;
    if (name.total !== undefined && title.episodeCount == null) next.episodeCount = name.total;
  }
  const parsed = title.description ? parseDescriptionFacts(title.description) : null;
  if (title.description) next.description = cleanStory(parsed ? parsed.story : title.description);
  if (parsed) {
    for (const { field, value } of parsed.facts) {
      if (!value) continue;
      switch (field) {
        case "year": {
          const year = value.match(/\b(19|20)\d{2}\b/)?.[0];
          if (year && next.year == null) next.year = Number(year);
          break;
        }
        case "type": {
          const type = readType(value);
          if (type && next.type == null) next.type = type;
          break;
        }
        case "episodes": {
          const count = value.match(/\d+/)?.[0];
          if (count && next.episodeCount == null && !/\+/.test(value.split(/\s/)[0])) next.episodeCount = Number(count);
          break;
        }
        case "status": {
          const status = readStatus(value);
          if (status && (next.status == null || next.status === "completed")) next.status = status;
          break;
        }
        case "genres":
          if (!next.genres?.length) next.genres = value.split(/\s*[,;/]\s*/).map((g) => g.trim()).filter(Boolean);
          break;
        case "rating":
          if (!/^\d+([.,]\d+)?(\s*\/\s*10)?$/.test(value) && next.ageRating == null) next.ageRating = value.split(/\n/)[0].trim();
          break;
        case "names":
          next.synonyms = [...new Set([...(next.synonyms ?? []), ...value.split(/\s*[,;]\s*/).map((n) => n.trim()).filter(Boolean)])];
          break;
        default:
          break;
      }
    }
  }
  // Aniyomi's "finished" is the app's "released".
  if (next.status === "completed") next.status = "released";
  return next;
}
