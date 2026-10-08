// The Android build: the same renderer as desktop, bundled for a Capacitor WebView. Instead of the
// Electron preload, android-entry.ts brings core up in-process (src/platform/android/bootstrap.ts).
// The extension worker reuses desktop's own sandbox globals (src/main/extensions/globals.ts); the
// two Node built-ins they touch are swapped for browser shims here.
import { readFileSync } from "node:fs";
import { fileURLToPath, URL } from "node:url";
import path from "node:path";
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import { TanStackRouterVite } from "@tanstack/router-plugin/vite";
import { buildSecret } from "./scripts/buildSecret";

const root = fileURLToPath(new URL(".", import.meta.url));
const shim = (name: string) => path.join(root, `src/platform/android/shims/${name}.ts`);
// See vite.config.ts.
const anilistClientId = buildSecret("ANILIST_CLIENT_ID", root);
const { version } = JSON.parse(readFileSync(path.join(root, "package.json"), "utf-8")) as { version: string };

const androidEntry: Plugin = {
  name: "hibiki-android-entry",
  // "pre": before Vite turns the module script into a hashed asset reference.
  transformIndexHtml: {
    order: "pre",
    handler: (html) => {
      const swaps: Array<[string, string]> = [
        ['<script type="module" src="/src/main.tsx"></script>', '<script type="module" src="/android-entry.ts"></script>'],
        // The page runs edge to edge and pads itself by env(safe-area-inset-*) (globals.css).
        ['<meta name="viewport" content="width=device-width, initial-scale=1.0" />', '<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />'],
        // `mobile:` utilities (tailwind.config.js) and the mobile layout key off this class, present
        // from the first paint so nothing lays out the desktop way first.
        ['<html lang="ru">', '<html lang="ru" class="mobile">'],
      ];
      for (const [from, to] of swaps) {
        if (!html.includes(from)) throw new Error(`index.html no longer contains ${from} - update vite.android.config.ts`);
        html = html.replace(from, to);
      }
      return html;
    },
  },
};

const aliases = {
  "@": path.join(root, "src/renderer/src"),
  "@shared": path.join(root, "src/shared"),
  "node:module": shim("module"),
  "node:zlib": shim("zlib"),
};

export default defineConfig({
  root: "src/renderer",
  define: { __APP_VERSION__: JSON.stringify(version), __ANILIST_CLIENT_ID__: JSON.stringify(anilistClientId) },
  resolve: { alias: aliases },
  plugins: [
    TanStackRouterVite({
      routesDirectory: "src/routes",
      generatedRouteTree: "src/routeTree.gen.ts",
      autoCodeSplitting: true,
    }),
    react(),
    androidEntry,
  ],
  worker: { format: "es", plugins: () => [] },
  build: {
    outDir: path.join(root, "dist-android"),
    emptyOutDir: true,
    target: "es2022",
  },
});
