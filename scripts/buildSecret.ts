// A build-time value that is not to be committed (AniList's client id), looked up the way the Kotlin
// Hibiki's Gradle build looks up its own (buildSecret in its app/build.gradle.kts): the environment
// first (CI), then the repository's untracked .env files, then the user's ~/.gradle/gradle.properties,
// where the secrets of both apps live on a developer's machine.
import { existsSync, readFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { loadEnv } from "vite";

function gradleProperty(name: string): string | undefined {
  const home = process.env.GRADLE_USER_HOME ?? path.join(os.homedir(), ".gradle");
  const file = path.join(home, "gradle.properties");
  if (!existsSync(file)) return undefined;
  for (const line of readFileSync(file, "utf-8").split(/\r?\n/)) {
    const match = /^\s*([^#!=:\s]+)\s*[=:]\s*(.*?)\s*$/.exec(line);
    if (match && match[1] === name) return match[2];
  }
  return undefined;
}

export function buildSecret(name: string, root: string): string {
  return (process.env[name] ?? loadEnv("production", root, name)[name] ?? gradleProperty(name) ?? "").trim();
}
