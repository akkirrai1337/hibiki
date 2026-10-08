// The Android build's entry point (vite.android.config.ts swaps it in for src/main.tsx): the backend
// has to be up and window.hibiki set before any renderer module reads it.
import { installAndroidHibiki } from "../platform/android/bootstrap";

await installAndroidHibiki();
await import("./src/main");
