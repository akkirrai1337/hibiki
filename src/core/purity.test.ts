// src/core runs unchanged on every platform (Electron today, an Android WebView next), so it may
// reach the host only through src/platform/types.ts. This guards the boundary: an import of
// Electron, a Node built-in or desktop-only code from core fails here instead of at the first
// Android build.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const coreDir = path.dirname(fileURLToPath(import.meta.url));
const srcDir = path.dirname(coreDir);

const FORBIDDEN_PACKAGES = [/^electron$/, /^node:/, /^better-sqlite3$/, /^undici$/, /^sync-fetch$/];
// Relative imports may stay inside core, or reach the shared types and the platform contract.
const ALLOWED_OUTSIDE = [path.join(srcDir, "shared"), path.join(srcDir, "platform", "types.ts")];

/** Test support (`*.testkit.ts`) may use Node - it is only ever imported by tests, which the check
 * below pins down. */
const TESTKIT = ".testkit.ts";

function sourceFiles(dir: string, { withTestkit = false } = {}): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(full, { withTestkit });
    if (!entry.name.endsWith(".ts") || entry.name.endsWith(".test.ts")) return [];
    return entry.name.endsWith(TESTKIT) && !withTestkit ? [] : [full];
  });
}

function importsOf(file: string): string[] {
  const text = fs.readFileSync(file, "utf-8");
  const specifiers: string[] = [];
  for (const match of text.matchAll(/(?:import|export)\s[^;]*?from\s+["']([^"']+)["']|import\(\s*["']([^"']+)["']\s*\)/g)) {
    specifiers.push(match[1] ?? match[2]);
  }
  return specifiers;
}

function violation(file: string, specifier: string): string | null {
  if (FORBIDDEN_PACKAGES.some((pattern) => pattern.test(specifier))) return specifier;
  if (specifier.startsWith("@shared/")) return null;
  if (!specifier.startsWith(".")) return null;
  const target = path.resolve(path.dirname(file), specifier);
  const resolved = fs.existsSync(`${target}.ts`) ? `${target}.ts` : target;
  if (resolved.startsWith(coreDir + path.sep)) return null;
  if (ALLOWED_OUTSIDE.some((allowed) => resolved === allowed || resolved.startsWith(allowed + path.sep))) return null;
  return specifier;
}

describe("core purity", () => {
  it("imports nothing platform-specific", () => {
    const problems = sourceFiles(coreDir).flatMap((file) =>
      importsOf(file)
        .map((specifier) => violation(file, specifier))
        .filter((specifier): specifier is string => specifier !== null)
        .map((specifier) => `${path.relative(srcDir, file)} -> ${specifier}`),
    );
    expect(problems).toEqual([]);
  });

  it("keeps test support out of anything that ships", () => {
    const problems = sourceFiles(coreDir)
      .flatMap((file) => importsOf(file).filter((specifier) => specifier.includes(".testkit")).map((specifier) => `${path.relative(srcDir, file)} -> ${specifier}`));
    expect(problems).toEqual([]);
    // ...and the test support itself is where the exemption applies: nothing else in core is called that.
    expect(sourceFiles(coreDir, { withTestkit: true }).filter((file) => file.endsWith(TESTKIT)).map((file) => path.basename(file))).toEqual(["syncDevices.testkit.ts"]);
  });
});
