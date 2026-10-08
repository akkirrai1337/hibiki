import plugin from "tailwindcss/plugin";

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  // hover: only where a real pointer can hover. A phone keeps :hover on whatever was last tapped,
  // which left cards stuck showing their hover overlay; with a mouse nothing changes.
  future: { hoverOnlyWhenSupported: true },
  content: ["./src/renderer/index.html", "./src/renderer/src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: "rgb(var(--color-bg) / <alpha-value>)",
        surface: "rgb(var(--color-surface) / <alpha-value>)",
        border: "rgb(var(--color-border) / <alpha-value>)",
        text: "rgb(var(--color-text) / <alpha-value>)",
        muted: "rgb(var(--color-muted) / <alpha-value>)",
        accent: "rgb(var(--color-accent) / <alpha-value>)",
        "accent-fg": "rgb(var(--color-accent-fg) / <alpha-value>)",
        "accent-text": "rgb(var(--color-accent-text) / <alpha-value>)",
        "accent-solid": "rgb(var(--color-accent-solid) / <alpha-value>)",
        "accent-solid-fg": "rgb(var(--color-accent-solid-fg) / <alpha-value>)",
      },
      borderRadius: {
        xl: "0.875rem",
      },
    },
  },
  plugins: [
    // `mobile:` applies only inside the Android build (it puts class="mobile" on <html>, see
    // vite.android.config.ts) - never in a narrow desktop window, so the desktop layout is untouched.
    plugin(({ addVariant }) => addVariant("mobile", ".mobile &")),
  ],
};
