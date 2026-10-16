// The Android build's entry point (vite.android.config.ts swaps it in for src/main.tsx): the backend
// has to be up and window.hibiki set before any renderer module reads it.
import { installAndroidHibiki } from "../platform/android/bootstrap";
import { HibikiApp } from "../platform/android/native";

// The native launch splash (the same pulsing dot as #splash, which index.html hides on Android) stays
// up until React has put its first screen into #root, or the start has failed.
const hideSplash = () => void HibikiApp.hideSplash().catch(() => {});
const root = document.getElementById("root")!;
const firstScreen = new MutationObserver(() => {
  if (root.childNodes.length === 0) return;
  firstScreen.disconnect();
  hideSplash();
});
firstScreen.observe(root, { childList: true });

try {
  await installAndroidHibiki();
  await import("./src/main");
} catch (error) {
  hideSplash();
  throw error;
}
