// The pages APK sources load. An extension's WebView (it loads a player's page and watches the requests
// it makes for a video's address) has no browser to run in inside the APK host process, so the
// application runs each page as a hidden window and reports back - see apk-host/.../RemotePages.kt for
// the protocol. This file is the protocol; the window itself is apkPageWindow.ts.
import type { ApkHostProcess } from "./apkHost";
import { logger } from "../../core/logger";

export interface PageRequestInfo {
  url: string;
  method: string;
  headers: Record<string, string>;
  mainFrame: boolean;
}

/** What a page tells the host. */
export interface PageEvents {
  request(info: PageRequestInfo): void;
  finished(url: string, setCookies: string[]): void;
  failed(url: string, code: number, description: string): void;
}

/** One hidden page. */
export interface PageDriver {
  load(url: string, options: { headers: Record<string, string>; userAgent: string; cookies: string }): void;
  loadData(baseUrl: string, data: string, mimeType: string, userAgent: string): void;
  /** The script's result as JSON text, the form a WebView's evaluateJavascript gives it. */
  evaluate(script: string): Promise<string | null>;
  stop(): void;
  close(): void;
}

export type PageDriverFactory = (events: PageEvents) => PageDriver;

interface Command {
  cmd: "load" | "loadData" | "evaluate" | "stop" | "close";
  page: number;
  call?: number;
  url?: string;
  baseUrl?: string;
  data?: string;
  mimeType?: string;
  script?: string;
  headers?: Record<string, string>;
  userAgent?: string;
  cookies?: string;
}

/** A page that nobody closed (the extension crashed, its timeout fired first) is closed after this long. */
const PAGE_LIFETIME_MS = 3 * 60_000;

/**
 * Runs the host's pages: reads its command stream, keeps a driver per page, sends events back. The
 * stream is opened whenever the host (re)starts; when it ends the pages go with it.
 */
export class PageBridge {
  private readonly pages = new Map<number, { driver: PageDriver; timer: NodeJS.Timeout }>();
  private connection: AbortController | null = null;

  constructor(
    private readonly host: ApkHostProcess,
    private readonly createDriver: PageDriverFactory,
  ) {
    host.onStarted(() => void this.connect());
    host.onStopped(() => this.disconnect());
  }

  private async connect() {
    this.disconnect();
    const connection = new AbortController();
    this.connection = connection;
    try {
      const response = await this.host.fetchRaw("/pages/commands", { signal: connection.signal });
      if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);
      const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
      let buffer = "";
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value;
        let newline: number;
        while ((newline = buffer.indexOf("\n")) >= 0) {
          const line = buffer.slice(0, newline).trim();
          buffer = buffer.slice(newline + 1);
          if (line) this.dispatch(line);
        }
      }
    } catch (error) {
      if (!connection.signal.aborted) logger.warn("apk-pages", `command stream ended: ${error}`);
    } finally {
      if (this.connection === connection) this.disconnect();
    }
  }

  private disconnect() {
    this.connection?.abort();
    this.connection = null;
    for (const [id] of this.pages) this.closePage(id);
  }

  private closePage(id: number) {
    const page = this.pages.get(id);
    if (!page) return;
    this.pages.delete(id);
    clearTimeout(page.timer);
    try {
      page.driver.close();
    } catch (error) {
      logger.warn("apk-pages", `closing page ${id} failed: ${error}`);
    }
  }

  private send(event: Record<string, unknown>) {
    this.host.request("POST", "/pages/event", event).catch((error: unknown) => logger.debug("apk-pages", `event not delivered: ${error}`));
  }

  private page(id: number): PageDriver {
    const existing = this.pages.get(id);
    if (existing) return existing.driver;
    const driver = this.createDriver({
      request: (info) => this.send({ page: id, type: "request", ...info }),
      finished: (url, setCookies) => this.send({ page: id, type: "finished", url, setCookies }),
      failed: (url, code, description) => this.send({ page: id, type: "error", url, code, description }),
    });
    this.pages.set(id, { driver, timer: setTimeout(() => this.closePage(id), PAGE_LIFETIME_MS) });
    return driver;
  }

  /** Public for the tests; the stream calls it once per line. */
  dispatch(line: string) {
    let command: Command;
    try {
      command = JSON.parse(line) as Command;
    } catch {
      logger.warn("apk-pages", `unreadable command: ${line.slice(0, 120)}`);
      return;
    }
    try {
      switch (command.cmd) {
        case "load":
          logger.debug("apk-pages", `page ${command.page}: load ${command.url}`);
          this.page(command.page).load(command.url ?? "about:blank", { headers: command.headers ?? {}, userAgent: command.userAgent ?? "", cookies: command.cookies ?? "" });
          break;
        case "loadData":
          this.page(command.page).loadData(command.baseUrl ?? "about:blank", command.data ?? "", command.mimeType ?? "text/html", command.userAgent ?? "");
          break;
        case "evaluate":
          void this.page(command.page)
            .evaluate(command.script ?? "")
            .catch(() => null)
            .then((result) => this.send({ page: command.page, type: "evaluated", call: command.call, result }));
          break;
        case "stop":
          this.pages.get(command.page)?.driver.stop();
          break;
        case "close":
          this.closePage(command.page);
          break;
      }
    } catch (error) {
      logger.warn("apk-pages", `${command.cmd} failed: ${error}`);
    }
  }

  get openPages(): number {
    return this.pages.size;
  }
}
