import type { CSSProperties } from "react";

// How the player's own subtitle overlay (VideoPlayer, data-subtitles) looks: a size, a typeface, a
// colour and what sits behind the letters. Kept as a few named choices rather than free values, so
// every combination stays readable over any picture.

export const SUBTITLE_SIZES = [75, 100, 125, 150, 200] as const;
export type SubtitleSize = (typeof SUBTITLE_SIZES)[number];

export const SUBTITLE_FONTS = ["default", "sans", "serif", "mono", "casual"] as const;
export type SubtitleFont = (typeof SUBTITLE_FONTS)[number];

export const SUBTITLE_COLORS = ["white", "yellow", "cyan", "green", "pink"] as const;
export type SubtitleColor = (typeof SUBTITLE_COLORS)[number];

/** "box": a dark plate, "translucent": a lighter one, "outline": letters edged in black, "shadow": a soft drop shadow. */
export const SUBTITLE_BACKGROUNDS = ["box", "translucent", "outline", "shadow"] as const;
export type SubtitleBackground = (typeof SUBTITLE_BACKGROUNDS)[number];

export interface SubtitleStyle {
  size: SubtitleSize;
  font: SubtitleFont;
  color: SubtitleColor;
  background: SubtitleBackground;
}

/** What the overlay looked like before it could be changed. */
export const DEFAULT_SUBTITLE_STYLE: SubtitleStyle = { size: 100, font: "default", color: "white", background: "box" };

export const SUBTITLE_COLOR_VALUES: Record<SubtitleColor, string> = {
  white: "#ffffff",
  yellow: "#ffe14d",
  cyan: "#6be4ff",
  green: "#8ef59b",
  pink: "#ff9ec7",
};

// Families every host has something for: Windows names first, then Android's, then the generic.
const FONT_FAMILIES: Record<SubtitleFont, string | undefined> = {
  default: undefined,
  sans: 'Arial, Roboto, "Helvetica Neue", sans-serif',
  serif: 'Georgia, "Times New Roman", "Noto Serif", serif',
  mono: 'Consolas, "Cascadia Mono", "Roboto Mono", monospace',
  casual: '"Comic Sans MS", "Comic Neue", casual, cursive',
};

// 1.125rem is the overlay's text-lg, the size at 100%.
const BASE_REM = 1.125;

const OUTLINE = "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 3px #000";
const SHADOW = "0 2px 4px rgba(0,0,0,.95), 0 0 12px rgba(0,0,0,.6)";

/** A stored value from an older or hand-edited preference, put back inside the known choices. */
export function normalizeSubtitleStyle(value: Partial<SubtitleStyle> | null | undefined): SubtitleStyle {
  const pick = <T>(list: readonly T[], v: unknown, fallback: T): T => (list.includes(v as T) ? (v as T) : fallback);
  return {
    size: pick(SUBTITLE_SIZES, value?.size, DEFAULT_SUBTITLE_STYLE.size),
    font: pick(SUBTITLE_FONTS, value?.font, DEFAULT_SUBTITLE_STYLE.font),
    color: pick(SUBTITLE_COLORS, value?.color, DEFAULT_SUBTITLE_STYLE.color),
    background: pick(SUBTITLE_BACKGROUNDS, value?.background, DEFAULT_SUBTITLE_STYLE.background),
  };
}

/** The overlay box's inline style for a choice. */
export function subtitleBoxStyle(style: SubtitleStyle): CSSProperties {
  const plate = style.background === "box" ? "rgba(0,0,0,.75)" : style.background === "translucent" ? "rgba(0,0,0,.4)" : "transparent";
  return {
    fontSize: `${(BASE_REM * style.size) / 100}rem`,
    fontFamily: FONT_FAMILIES[style.font],
    color: SUBTITLE_COLOR_VALUES[style.color],
    backgroundColor: plate,
    // A lighter plate lets the picture through, so its letters get the shadow too.
    textShadow: style.background === "outline" ? OUTLINE : style.background === "box" ? undefined : SHADOW,
    // A plate's shadow would show as a dark halo around nothing once the plate is gone.
    boxShadow: plate === "transparent" ? "none" : undefined,
  };
}
