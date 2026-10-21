// APK sources (Aniyomi extensions) on the desktop. Their code is Dalvik bytecode, so it runs in a
// JVM process of its own (apk-host/), started the first time something asks for it and spoken to
// over loopback HTTP with a token only this process knows. See apk-host/.../Server.kt for the
// routes; the port below turns them into what core/extensions expects (ApkSourcesPort).
import { spawn, type ChildProcess } from "node:child_process";
import { randomBytes } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import type { ApkSourcesPort } from "../types";
import type { SourceInfo } from "@shared/types";
import { logger } from "../../core/logger";

/** The scheme the page loads APK sources' pictures through (main/index.ts serves it from the host). */
export const APK_IMAGE_SCHEME = "hibiki-apk-image";
const IMAGE_BASE = `${APK_IMAGE_SCHEME}://img`;
const ICON_BASE = `${APK_IMAGE_SCHEME}://icon`;
const TOKEN_HEADER = "X-Hibiki-Token";
const START_TIMEOUT_MS = 45_000;

export interface ApkHostLocation {
  /** The `java` executable. */
  java: string;
  /** The directory holding the host's jars. */
  libDir: string;
}

export interface LocateOptions {
  /** Where a packaged app keeps its resources, or null in development. */
  resourcesPath: string | null;
  /** The project folder in development. */
  appPath: string;
  platform: NodeJS.Platform;
  env: NodeJS.ProcessEnv;
  exists: (path: string) => boolean;
}

/**
 * Finds the host's jars and a Java to run them with. A packaged app carries both (the jars and a
 * trimmed runtime under resources/apk-host); in development the jars come from the Gradle build
 * (`gradlew installDist` in apk-host/) and Java from HIBIKI_JAVA, JAVA_HOME or the PATH. Null when
 * either is missing, which leaves APK sources switched off rather than broken.
 */
export function locateApkHost(options: LocateOptions): ApkHostLocation | null {
  const { resourcesPath, appPath, platform, env, exists } = options;
  const exe = platform === "win32" ? "java.exe" : "java";
  const bundled = resourcesPath ? path.join(resourcesPath, "apk-host") : null;
  if (bundled && exists(path.join(bundled, "lib"))) {
    const java = path.join(bundled, "runtime", "bin", exe);
    return exists(java) ? { java, libDir: path.join(bundled, "lib") } : null;
  }
  const libDir = path.join(env.HIBIKI_APK_HOST_DIR ?? path.join(appPath, "apk-host", "build", "install", "apk-host"), "lib");
  if (!exists(libDir)) return null;
  const candidates = [env.HIBIKI_JAVA, env.JAVA_HOME ? path.join(env.JAVA_HOME, "bin", exe) : undefined];
  for (const candidate of candidates) if (candidate && exists(candidate)) return { java: candidate, libDir };
  // Left to the PATH; whether it exists is found out when it is started.
  return { java: exe, libDir };
}

export function systemLocateOptions(appPath: string, resourcesPath: string | null): LocateOptions {
  return { resourcesPath, appPath, platform: process.platform, env: process.env, exists: (p) => fs.existsSync(p) };
}

/** What the host answers a failed call with. */
export class ApkHostError extends Error {}

/**
 * The host process: started on first use, restarted if it dies, stopped with the app. All calls
 * go through [request]; [fetchRaw] is for pictures, which are streamed on to the page.
 */
export class ApkHostProcess {
  private child: ChildProcess | null = null;
  private starting: Promise<{ port: number; token: string }> | null = null;
  private readonly startedListeners: Array<() => void> = [];
  private readonly stoppedListeners: Array<() => void> = [];

  constructor(
    private readonly location: ApkHostLocation,
    private readonly dataDir: string,
  ) {}

  private start(): Promise<{ port: number; token: string }> {
    this.starting ??= new Promise((resolve, reject) => {
      const token = randomBytes(24).toString("hex");
      fs.mkdirSync(this.dataDir, { recursive: true });
      const child = spawn(
        this.location.java,
        ["-Xmx768m", "-Dfile.encoding=UTF-8", "-cp", path.join(this.location.libDir, "*"), "app.hibiki.apkhost.MainKt", "--data", this.dataDir, "--token", token, "--image-base", IMAGE_BASE],
        { stdio: ["pipe", "pipe", "pipe"], windowsHide: true },
      );
      this.child = child;
      let output = "";
      let settled = false;
      const fail = (message: string) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        this.reset(child);
        child.kill();
        reject(new ApkHostError(message));
      };
      const timer = setTimeout(() => fail("The APK source host did not start in time"), START_TIMEOUT_MS);
      child.stdout!.setEncoding("utf8");
      child.stdout!.on("data", (chunk: string) => {
        if (settled) return;
        output += chunk;
        const ready = /^READY (\d+)$/m.exec(output);
        if (ready) {
          settled = true;
          clearTimeout(timer);
          resolve({ port: Number(ready[1]), token });
          // After the promise settles, so a listener that calls the host finds it ready.
          queueMicrotask(() => this.startedListeners.forEach((listener) => listener()));
        }
      });
      child.stderr!.setEncoding("utf8");
      child.stderr!.on("data", (chunk: string) => {
        for (const line of chunk.split(/\r?\n/)) if (line.trim()) logger.debug("apk-host", line);
      });
      child.on("error", (error) => fail(`The APK source host could not be started: ${error.message}`));
      child.on("exit", (code) => {
        logger.info("apk-host", `exited with ${code}`);
        fail(`The APK source host exited (${code}) before it was ready`);
        this.reset(child);
      });
    });
    return this.starting;
  }

  /** Called each time the host process has started and is ready for requests. */
  onStarted(listener: () => void) {
    this.startedListeners.push(listener);
  }

  /** Called when the host process is gone: it exited, or was stopped. */
  onStopped(listener: () => void) {
    this.stoppedListeners.push(listener);
  }

  private reset(child: ChildProcess) {
    if (this.child === child) {
      this.child = null;
      this.starting = null;
      this.stoppedListeners.forEach((listener) => listener());
    }
  }

  /**
   * Closing its stdin is how the host knows to quit; the kill is for one that does not. Resolves once
   * the process is gone (its files are not held any longer).
   */
  stop(): Promise<void> {
    const child = this.child;
    if (!child) return Promise.resolve();
    this.child = null;
    this.starting = null;
    this.stoppedListeners.forEach((listener) => listener());
    return new Promise((resolve) => {
      if (child.exitCode !== null) return resolve();
      child.once("exit", () => resolve());
      child.stdin?.end();
      setTimeout(() => child.kill(), 2_000).unref();
    });
  }

  async fetchRaw(pathAndQuery: string, init: RequestInit = {}): Promise<Response> {
    const { port, token } = await this.start();
    const headers = new Headers(init.headers);
    headers.set(TOKEN_HEADER, token);
    return fetch(`http://127.0.0.1:${port}${pathAndQuery}`, { ...init, headers });
  }

  async request<T>(method: "GET" | "POST", route: string, body?: unknown): Promise<T> {
    const response = await this.fetchRaw(route, {
      method,
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let json: unknown;
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      throw new ApkHostError(`The APK source host answered something unreadable (${response.status})`);
    }
    if (!response.ok) throw new ApkHostError((json as { error?: string }).error ?? `The APK source host answered ${response.status}`);
    return json as T;
  }
}

interface ExtensionsListing {
  sources: Array<SourceInfo & { packageName: string }>;
  extensions: Array<{ packageName: string; iconPath: string | null }>;
}

/** The address the page loads an extension's icon from (main/index.ts serves it from the host). */
export function apkIconUrl(packageName: string): string {
  return `${ICON_BASE}/${encodeURIComponent(packageName)}`;
}

/** The calls ExtensionRuntime makes of an APK source, each as the host's route for it. */
export function apkSourcesPort(host: ApkHostProcess): ApkSourcesPort {
  const call = <T>(sourceId: string, route: string, body: Record<string, unknown> = {}) =>
    host.request<T>("POST", `/source/${encodeURIComponent(sourceId)}/${route}`, body);
  const items = async <T>(sourceId: string, route: string, body: Record<string, unknown>) => (await call<{ items: T[] }>(sourceId, route, body)).items;
  return {
    list: async () => {
      const { sources, extensions } = await host.request<ExtensionsListing>("GET", "/extensions");
      const hasIcon = new Set(extensions.filter((extension) => extension.iconPath).map((extension) => extension.packageName));
      return sources.map((source) => ({ ...source, iconUrl: hasIcon.has(source.packageName) ? apkIconUrl(source.packageName) : null }));
    },
    install: async (url, packageName) => {
      await host.request("POST", "/install", { url, packageName });
    },
    uninstall: async (packageName) => {
      await host.request("POST", "/uninstall", { packageName });
    },
    applyClearance: async (url, session) => {
      await host.request("POST", "/clearance", { url, userAgent: session.userAgent, cookies: session.cookies });
    },
    resetClearances: async () => {
      await host.request("POST", "/clearance/reset", {});
    },
    call: async (sourceId, method, args) => {
      switch (method) {
        case "search": return items(sourceId, "search", { request: args[0] ?? {} });
        case "latest": return items(sourceId, "latest", { limit: Number(args[0] ?? 24) });
        case "getById": return call(sourceId, "getById", { id: String(args[0]) });
        case "getPlaybackGroups": return items(sourceId, "playbackGroups", { titleId: String(args[0]) });
        // (titleId, groupId, episodeId): an Aniyomi episode is found by its own id; the title is
        // for reading its number and name when the episode list hasn't been loaded this run.
        case "getPlayerLinks": return items(sourceId, "playerLinks", { titleId: String(args[0]), episodeId: String(args[2]) });
        case "getSettings": return call(sourceId, "filterCatalog");
        case "readSettings": return call(sourceId, "readSettings");
        case "writeSetting": return call(sourceId, "writeSetting", { key: String(args[0]), value: args[1] == null ? null : String(args[1]) });
        default: throw new Error(`APK sources do not support ${method}()`);
      }
    },
  };
}

let instance: ApkHostProcess | null = null;

/** The running application's host, or null when this install cannot run APK sources. */
export function getApkHost(): ApkHostProcess | null {
  return instance;
}

export function setApkHost(host: ApkHostProcess | null) {
  instance = host;
}
