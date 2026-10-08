/** One run of a comment's text: plain words, a mention, or concealed source markup. */
export type CommentPart =
  | { type: "text"; value: string }
  | { type: "mention"; name: string }
  | { type: "spoiler"; label: string; value: string };

// The site's own markup for "replying to": its editor writes it, its API returns it verbatim, and
// only its web client ever turned it back into a name. Left as raw brackets it was the first thing
// the eye landed on in a thread ("[ник]Rus921k[/ник] самое интересное уже прошло").
const MENTION = /\[ник\]([^[]+)\[\/ник\]/g;
const SPOILER = /\[спойлер(?:\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\]]*)))?\s*\]([\s\S]*?)\[\/спойлер\s*\]/gi;

/**
 * Splits a comment into text, mentions, and YummyAnime spoiler blocks.
 *
 * Deliberately not a general markup parser: these are the two tags YummyAnime actually emits, and
 * a tag this app does not know must be left exactly as written rather than half-interpreted.
 */
export function splitMentions(text: string): CommentPart[] {
  const parts: CommentPart[] = [];
  let index = 0;
  const appendText = (value: string) => {
    let mentionIndex = 0;
    for (const mention of value.matchAll(MENTION)) {
      if (mention.index > mentionIndex) parts.push({ type: "text", value: value.slice(mentionIndex, mention.index) });
      parts.push({ type: "mention", name: mention[1] });
      mentionIndex = mention.index + mention[0].length;
    }
    if (mentionIndex < value.length) parts.push({ type: "text", value: value.slice(mentionIndex) });
  };

  for (const match of text.matchAll(SPOILER)) {
    appendText(text.slice(index, match.index));
    const label = match[1] ?? match[2] ?? match[3]?.trim() ?? "Спойлер";
    parts.push({ type: "spoiler", label: label || "Спойлер", value: match[4] });
    index = match.index + match[0].length;
  }
  appendText(text.slice(index));
  return parts;
}
