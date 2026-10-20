// Writes the release's update-manifest.json: for every platform build found in <artifacts dir>,
// which file it is, how big, its SHA-256, and how the app applies it. The app reads this instead of
// guessing a platform's file from its name (see src/shared/appUpdateSelection.ts, core/updates.ts).
//
//   node scripts/make-update-manifest.mjs <version> <artifacts dir> [output file]
//
// Supporting another platform is one line in RULES below plus a build that produces a file with a
// matching name - the app itself needs no change. A file no rule matches (the macOS .dmg, a .deb)
// is simply not listed: it is still attached to the release for a manual download.
import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { readdir, stat, writeFile } from "node:fs/promises";
import path from "node:path";

/** `key` is what the app computes for itself: process.platform-process.arch, or "android". */
const RULES = [
  { pattern: /-setup\.exe$/i, key: () => "win32-x64", kind: "installer" },
  { pattern: /\.apk$/i, key: () => "android", kind: "apk" },
  { pattern: /-mac-(arm64|x64)\.zip$/i, key: (m) => `darwin-${m[1]}`, kind: "replace" },
  { pattern: /-linux-(x64|arm64)\.AppImage$/i, key: (m) => `linux-${m[1]}`, kind: "replace" },
];

async function* walk(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) yield* walk(full);
    else yield full;
  }
}

async function sha256(file) {
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest("hex");
}

const [version, artifactsDir, output = path.join(artifactsDir ?? ".", "update-manifest.json")] = process.argv.slice(2);
if (!version || !artifactsDir) {
  console.error("usage: make-update-manifest.mjs <version> <artifacts dir> [output file]");
  process.exit(2);
}

const platforms = {};
for await (const file of walk(artifactsDir)) {
  const name = path.basename(file);
  for (const rule of RULES) {
    const match = rule.pattern.exec(name);
    if (!match) continue;
    const key = rule.key(match);
    if (platforms[key]) {
      console.error(`Two files for ${key}: ${platforms[key].file} and ${name}`);
      process.exit(1);
    }
    platforms[key] = { file: name, size: (await stat(file)).size, sha256: await sha256(file), kind: rule.kind };
    break;
  }
}

if (Object.keys(platforms).length === 0) {
  console.error(`No platform builds found in ${artifactsDir}`);
  process.exit(1);
}

await writeFile(output, `${JSON.stringify({ version, platforms }, null, 2)}\n`);
console.log(`${output}: ${Object.keys(platforms).join(", ")}`);
