// Backs the accent-color picker in Settings (see routes/settings.tsx) - the app's actual default
// pink, kept here (not just in globals.css) so a "Reset to default" control and the preset swatch
// row both have a single source of truth for it instead of a second hardcoded copy drifting from
// the CSS one over time.
export const DEFAULT_ACCENT = "#ec4899";
// The phone app starts plain white instead (black in the light theme, where white would vanish);
// the desktop keeps its pink.
export const DEFAULT_MOBILE_ACCENT = "#ffffff";
export const DEFAULT_MOBILE_ACCENT_LIGHT = "#000000";

// A handful of ready-made options, Discord-picker style, alongside the free-form custom swatch -
// covers the common picks without forcing everyone through the OS color dialog for a simple swap.
// White and black lead the row - the plainest, most requested options, ahead of the color wheel.
export const ACCENT_PRESETS = ["#ffffff", "#000000", "#ec4899", "#8b5cf6", "#3b82f6", "#06b6d4", "#22c55e", "#eab308", "#f97316", "#ef4444"];

function hexToRgbTriplet(hex: string): string | null {
  const match = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!match) return null;
  const int = Number.parseInt(match[1], 16);
  return `${(int >> 16) & 255} ${(int >> 8) & 255} ${int & 255}`;
}

// Relative luminance via the sRGB->linear formula, thresholded the same way most contrast-picker
// implementations do - the accent is now user-chosen and can land anywhere from near-white yellow
// to near-black indigo, so a single fixed "text on accent" color can't be assumed legible anymore.
function isLight(triplet: string): boolean {
  const [r, g, b] = triplet.split(" ").map((c) => Number(c) / 255);
  const linear = (c: number) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  const luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
  return luminance > 0.5;
}

const ACCENT_FG_DARK = "29 12 21"; // near-black, matches the app's original #1d0c15 on-pink text
const ACCENT_FG_LIGHT = "255 255 255";

// Sets --color-accent (and its matching --color-accent-fg/--color-accent-text) directly on the
// root element, overriding whatever globals.css's light/dark block gave them - `null` removes the
// inline override so the CSS-defined defaults (which already themselves differ slightly between
// light and dark, see globals.css) take back over.
//
// `theme` is needed for --color-accent-text specifically: white and black were added as accent
// presets (see ACCENT_PRESETS) on top of the colorful ones this already handled fine, but a white
// accent in the light theme (or black in dark) has the same lightness classification as the page's
// own background - `text-accent` badges/labels sitting directly on that background (not on a solid
// accent fill, which --color-accent-fg already covers) went essentially invisible: a white badge
// tint over an already near-white page, with white text on top of that. Falling back to the
// theme's own primary text color in exactly that case keeps those badges legible - a plain neutral
// label instead of a colored one, which is the honest outcome for an accent with no real hue to
// begin with, rather than trying to manufacture contrast that isn't there.
export function applyAccentColor(color: string | null, theme: "light" | "dark"): void {
  const root = document.documentElement;
  const triplet = color ? hexToRgbTriplet(color) : null;
  if (triplet) {
    root.style.setProperty("--color-accent", triplet);
    const accentIsLight = isLight(triplet);
    root.style.setProperty("--color-accent-fg", accentIsLight ? ACCENT_FG_DARK : ACCENT_FG_LIGHT);
    const blendsIn = accentIsLight === (theme === "light");
    root.style.setProperty("--color-accent-text", blendsIn ? "var(--color-text)" : triplet);
    // A selection filled with an accent that blends into the page (white on light, black on dark)
    // would vanish the same way: it takes the text colour instead, with the page colour on it.
    root.style.setProperty("--color-accent-solid", blendsIn ? "var(--color-text)" : triplet);
    root.style.setProperty("--color-accent-solid-fg", blendsIn ? "var(--color-bg)" : accentIsLight ? ACCENT_FG_DARK : ACCENT_FG_LIGHT);
  } else {
    root.style.removeProperty("--color-accent");
    root.style.removeProperty("--color-accent-fg");
    root.style.removeProperty("--color-accent-text");
    root.style.removeProperty("--color-accent-solid");
    root.style.removeProperty("--color-accent-solid-fg");
  }
}

export interface BackgroundThemePreset {
  id: string;
  gradient: string;
}

// Discord-picker style once again (see its own profile "Preview Theme" panel) - a diagonal
// gradient painted behind the whole window (see the fixed layer in __root.tsx), with the
// Sidebar/TitleBar/page backgrounds turned translucent + blurred (bg-app-bg/bg-app-surface, see
// globals.css) so it actually shows through the chrome instead of being fully hidden behind it.
export const BACKGROUND_THEME_PRESETS: BackgroundThemePreset[] = [
  { id: "sunset", gradient: "linear-gradient(135deg, #f97316, #ec4899)" },
  { id: "candy", gradient: "linear-gradient(135deg, #f472b6, #a78bfa)" },
  { id: "violet", gradient: "linear-gradient(135deg, #8b5cf6, #6366f1)" },
  { id: "ocean", gradient: "linear-gradient(135deg, #06b6d4, #3b82f6)" },
  { id: "aurora", gradient: "linear-gradient(135deg, #22d3ee, #6366f1, #ec4899)" },
  { id: "forest", gradient: "linear-gradient(135deg, #22c55e, #14b8a6)" },
  { id: "gold", gradient: "linear-gradient(135deg, #f59e0b, #ef4444)" },
  { id: "midnight", gradient: "linear-gradient(135deg, #334155, #0f172a)" },
];

// The id a user-picked pair of stops (see uiStore's customBackgroundGradient) is stored under,
// rather than one of BACKGROUND_THEME_PRESETS' own fixed ids - same angle as every preset above, so
// a custom pick reads as "one more swatch in the row", not a visually distinct kind of theme.
export const CUSTOM_BACKGROUND_THEME_ID = "custom";
export function customBackgroundGradientCss(stops: { from: string; to: string }): string {
  return `linear-gradient(135deg, ${stops.from}, ${stops.to})`;
}

// Picking a gradient (any id, including "custom") makes the Sidebar/TitleBar and each page's own
// background translucent + blurred (see .bg-theme-active in globals.css), so the gradient painted
// behind them in __root.tsx actually shows through instead of being hidden behind fully opaque
// chrome - there is no separate toggle for this, it is just what "having a background" means. The
// gradient itself is still painted directly by __root.tsx (from BACKGROUND_THEME_PRESETS or a
// custom pair), not read back off this CSS var anywhere - kept in sync here anyway so anything
// that only has access to computed style (not React state) can still tell a theme is selected.
export function applyBackgroundTheme(id: string | null): void {
  const root = document.documentElement;
  const preset = id ? BACKGROUND_THEME_PRESETS.find((p) => p.id === id) : undefined;
  root.classList.toggle("bg-theme-active", id !== null);
  root.style.removeProperty("--app-bg-alpha"); // Clear values left by older versions.
  if (preset) root.style.setProperty("--app-theme-gradient", preset.gradient);
  else root.style.removeProperty("--app-theme-gradient");
}
