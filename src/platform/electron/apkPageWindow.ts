// A page an APK source loads, as a hidden window (see apkPages.ts for who asks). Pages live in a session
// of their own: nothing here touches the cookies, caches or request hooks of the app's own session,
// and what they set is handed back to the host explicitly.
import { BrowserWindow, session, type Session, type WebContents } from "electron";
import { BRIDGE_BINDING, interfaceScript, parseBridgeCall, type PageDriver, type PageDriverFactory, type PageEvents } from "./apkPages";
import { logger } from "../../core/logger";

const PARTITION = "apk-pages";
const RESOURCE_MAIN_FRAME = "mainFrame";

/** Pages by the id of their web contents: the session-wide request hook has to tell whose request it sees. */
const live = new Map<number, PageEvents>();
let hooked: Session | null = null;

function pageSession(): Session {
  const ses = session.fromPartition(PARTITION);
  if (hooked !== ses) {
    hooked = ses;
    ses.webRequest.onBeforeSendHeaders({ urls: ["*://*/*"] }, (details, callback) => {
      const events = details.webContentsId !== undefined ? live.get(details.webContentsId) : undefined;
      if (events) {
        events.request({
          url: details.url,
          method: details.method,
          headers: details.requestHeaders,
          mainFrame: details.resourceType === RESOURCE_MAIN_FRAME,
        });
      }
      // A player starting to play would only cost bandwidth and make noise: the address is all that is wanted.
      callback(details.resourceType === "media" ? { cancel: true } : { requestHeaders: details.requestHeaders });
    });
  }
  return ses;
}

/** "name=value; Path=/" lines for what the page's session now holds for `url`, to be put in the host's cookie store. */
async function cookieLines(ses: Session, url: string): Promise<string[]> {
  try {
    const cookies = await ses.cookies.get({ url });
    return cookies.map((cookie) => `${cookie.name}=${cookie.value}; Path=${cookie.path || "/"}`);
  } catch {
    return [];
  }
}

async function seedCookies(ses: Session, url: string, header: string) {
  for (const pair of header.split(";")) {
    const index = pair.indexOf("=");
    if (index <= 0) continue;
    try {
      await ses.cookies.set({ url, name: pair.slice(0, index).trim(), value: pair.slice(index + 1).trim() });
    } catch {
      // A cookie the browser refuses is not worth failing the page for.
    }
  }
}

export const createElectronPageDriver: PageDriverFactory = (events): PageDriver => {
  const ses = pageSession();
  const window = new BrowserWindow({
    show: false,
    width: 1280,
    height: 720,
    webPreferences: { session: ses, sandbox: true, contextIsolation: true, backgroundThrottling: false, autoplayPolicy: "document-user-activation-required" },
  });
  const contents: WebContents = window.webContents;
  const id = contents.id;
  live.set(id, events);
  contents.setAudioMuted(true);
  // Players open pop-unders and extra windows; none of them is the page that was asked for.
  contents.setWindowOpenHandler(() => ({ action: "deny" }));

  let currentUrl = "";
  // addJavascriptInterface: the objects go into every new document through the DevTools protocol (the
  // window stays sandboxed, with no preload of its own), and their calls come back as a binding. A load
  // waits for them, since an extension adds its interface right before loading the page that uses it.
  let interfacesReady: Promise<void> = Promise.resolve();
  let debuggerAttached = false;
  // The blank document above is the bridge's, not something the extension loaded: it hears nothing of it.
  let warmingUp = false;
  const attachDebugger = async () => {
    if (debuggerAttached) return;
    // A window that has loaded nothing yet has no renderer, and the protocol's commands wait for one
    // forever (seen: Runtime.enable never answered). A blank document gives it one.
    if (!contents.getURL()) {
      warmingUp = true;
      await contents.loadURL("about:blank").finally(() => { warmingUp = false; });
    }
    contents.debugger.attach("1.3");
    debuggerAttached = true;
    contents.debugger.on("message", (_event, method, params: { name?: string; payload?: string }) => {
      if (method !== "Runtime.bindingCalled" || params.name !== BRIDGE_BINDING || !params.payload) return;
      const call = parseBridgeCall(params.payload);
      if (call) events.jsCall(call.name, call.method, call.args);
    });
    await contents.debugger.sendCommand("Runtime.enable");
    await contents.debugger.sendCommand("Runtime.addBinding", { name: BRIDGE_BINDING });
    await contents.debugger.sendCommand("Page.enable");
  };
  contents.on("did-finish-load", () => {
    if (warmingUp) return;
    void cookieLines(ses, currentUrl || contents.getURL()).then((cookies) => events.finished(contents.getURL() || currentUrl, cookies));
  });
  contents.on("did-fail-load", (_event, code, description, url, isMainFrame) => {
    if (warmingUp) return;
    // -3 is a navigation that was cancelled by the next one (a redirect, a stop): not a failure.
    if (isMainFrame && code !== -3) events.failed(url || currentUrl, code, description);
  });

  return {
    load(url, options) {
      currentUrl = url;
      void Promise.all([seedCookies(ses, url, options.cookies), interfacesReady]).then(() => {
        if (window.isDestroyed()) return;
        const extraHeaders = Object.entries(options.headers)
          .filter(([name]) => name.toLowerCase() !== "user-agent")
          .map(([name, value]) => `${name}: ${value}`)
          .join("\n");
        contents.loadURL(url, { userAgent: options.userAgent || undefined, extraHeaders }).catch((error: Error) => {
          // loadURL also rejects for a navigation that was superseded; the event above reports the real failures.
          if (!/ERR_ABORTED/.test(error.message)) events.failed(url, -2, error.message);
        });
      });
    },
    loadData(baseUrl, data, mimeType, userAgent) {
      currentUrl = baseUrl;
      void interfacesReady.then(() => window.isDestroyed() ? undefined : contents.loadURL(`data:${mimeType};charset=utf-8,${encodeURIComponent(data)}`, { baseURLForDataURL: baseUrl, userAgent: userAgent || undefined })).catch(() => undefined);
    },
    async evaluate(script) {
      if (window.isDestroyed()) return null;
      const value: unknown = await contents.executeJavaScript(script);
      // A WebView hands back JSON text; undefined has none.
      return value === undefined ? null : JSON.stringify(value);
    },
    stop() {
      if (!window.isDestroyed()) contents.stop();
    },
    addInterface(name, methods) {
      interfacesReady = interfacesReady
        .then(async () => {
          if (window.isDestroyed()) return;
          await attachDebugger();
          await contents.debugger.sendCommand("Page.addScriptToEvaluateOnNewDocument", { source: interfaceScript(name, methods) });
        })
        .catch((error: unknown) => logger.warn("apk-pages", `javascript interface ${name} not added: ${error}`));
    },
    close() {
      live.delete(id);
      if (!window.isDestroyed()) window.destroy();
    },
  };
};
