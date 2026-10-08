// A Jsoup-compatible surface backed by cheerio, so the Hibiki extension payloads
// (hibiki-sources/extensions/*.js) — written against Jsoup's `Document`/`Element`/`Elements`
// API on Android's Rhino runtime — run unmodified under Node. Method names/shapes here mirror
// RhinoExtensionRuntime.kt's JsoupBinding + the subset of org.jsoup.nodes.Element that the
// extension payloads actually call (select/selectFirst/text/attr/absUrl/html/parent/...).
import { createRequire } from "node:module";
import type { Cheerio, CheerioAPI } from "cheerio";
import type { Element as DomElement } from "domhandler";

const require = createRequire(import.meta.url);
let cheerioModule: typeof import("cheerio") | null = null;

// JSON-backed sources never touch Jsoup. Loading Cheerio lazily keeps its large parser tree out of
// their cold worker path while HTML-backed sources pay the import cost once, on their first parse.
function loadCheerio(): typeof import("cheerio") {
  cheerioModule ??= require("cheerio") as typeof import("cheerio");
  return cheerioModule;
}

function resolveUrl(baseUrl: string, relative: string): string {
  try {
    return new URL(relative, baseUrl).toString();
  } catch {
    return relative;
  }
}

export class ElementShim {
  constructor(
    private readonly $: CheerioAPI,
    private readonly el: DomElement,
    private readonly baseUri: string,
  ) {}

  private node(): Cheerio<DomElement> {
    return this.$(this.el);
  }

  select(selector: string): ElementsShim {
    return new ElementsShim(this.$, this.node().find(selector).toArray() as DomElement[], this.baseUri);
  }

  selectFirst(selector: string): ElementShim | null {
    const found = this.node().find(selector).first();
    return found.length > 0 ? new ElementShim(this.$, found[0] as DomElement, this.baseUri) : null;
  }

  text(): string {
    return this.node().text().trim();
  }

  ownText(): string {
    return this.node()
      .contents()
      .filter((_, n) => n.type === "text")
      .text()
      .trim();
  }

  html(): string {
    return this.node().html() ?? "";
  }

  outerHtml(): string {
    return this.$.html(this.node());
  }

  attr(name: string): string | null {
    const value = this.node().attr(name);
    return value === undefined ? null : value;
  }

  hasAttr(name: string): boolean {
    return this.node().attr(name) !== undefined;
  }

  absUrl(name: string): string {
    const raw = this.attr(name);
    if (!raw) return "";
    return resolveUrl(this.baseUri, raw);
  }

  /**
   * Jsoup's own `Element.data()` takes no argument and returns the *content* of a data-carrying
   * element - the text inside a `<script>` or `<style>`. That is what the extensions call it for
   * (reading an `application/ld+json` block, see animego.js/hentaimama.js), and returning a
   * `data-undefined` attribute instead made every such lookup come back empty: on AnimeGo the
   * JSON-LD parse silently failed and the title reported "details schema is missing".
   *
   * The single-argument form is kept for the dataset accessor some payloads use.
   */
  data(name?: string): string | null {
    if (name !== undefined) return this.attr(`data-${name}`);
    const tag = this.tagName().toLowerCase();
    if (tag !== "script" && tag !== "style") return this.node().text() ?? "";
    // cheerio keeps a script's body as a raw text child rather than as text() content.
    return this.node().contents().toArray()
      .map((child) => ("data" in child ? String(child.data ?? "") : ""))
      .join("");
  }

  hasClass(name: string): boolean {
    return this.node().hasClass(name);
  }

  id(): string {
    return this.attr("id") ?? "";
  }

  tagName(): string {
    return this.el.type === "tag" ? this.el.name : "";
  }

  val(): string {
    return String(this.node().val() ?? "");
  }

  /**
   * Jsoup's `Element.closest(selector)` - the nearest self-or-ancestor matching the selector.
   * animego.js's filterOptions() walks from a checkbox up to its `.form-check` wrapper with it, so
   * without this the whole settings/filters call threw instead of returning options.
   */
  closest(selector: string): ElementShim | null {
    const found = this.node().closest(selector).first();
    return found.length > 0 ? new ElementShim(this.$, found[0] as DomElement, this.baseUri) : null;
  }

  parent(): ElementShim | null {
    const p = this.node().parent();
    return p.length > 0 ? new ElementShim(this.$, p[0] as DomElement, this.baseUri) : null;
  }

  nextElementSibling(): ElementShim | null {
    const n = this.node().nextAll().first();
    return n.length > 0 ? new ElementShim(this.$, n[0] as DomElement, this.baseUri) : null;
  }

  previousElementSibling(): ElementShim | null {
    const p = this.node().prevAll().first();
    return p.length > 0 ? new ElementShim(this.$, p[0] as DomElement, this.baseUri) : null;
  }
}

export class ElementsShim {
  constructor(
    private readonly $: CheerioAPI,
    private readonly els: DomElement[],
    private readonly baseUri: string,
  ) {}

  size(): number {
    return this.els.length;
  }

  get(i: number): ElementShim {
    return new ElementShim(this.$, this.els[i], this.baseUri);
  }

  first(): ElementShim | null {
    return this.els.length > 0 ? this.get(0) : null;
  }

  last(): ElementShim | null {
    return this.els.length > 0 ? this.get(this.els.length - 1) : null;
  }

  select(selector: string): ElementsShim {
    const out: DomElement[] = [];
    for (const el of this.els) out.push(...(this.$(el).find(selector).toArray() as DomElement[]));
    return new ElementsShim(this.$, out, this.baseUri);
  }

  text(): string {
    return this.els.map((el) => this.$(el).text().trim()).join(" ").trim();
  }

  attr(name: string): string | null {
    if (this.els.length === 0) return null;
    const value = this.$(this.els[0]).attr(name);
    return value === undefined ? null : value;
  }

  /** Non-Jsoup convenience so extensions/host code can iterate with `for...of` too. */
  [Symbol.iterator](): Iterator<ElementShim> {
    let i = 0;
    const self = this;
    return {
      next(): IteratorResult<ElementShim> {
        if (i >= self.els.length) return { value: undefined, done: true };
        return { value: self.get(i++), done: false };
      },
    };
  }
}

export class DocumentShim extends ElementShim {}

export const JsoupBinding = {
  parse(html: string, baseUri?: string): DocumentShim {
    const $ = loadCheerio().load(html);
    const root = $.root().children().first();
    const rootEl = (root.length > 0 ? root[0] : $("body")[0]) as DomElement;
    return new DocumentShim($, rootEl, baseUri ?? "");
  },

  parseBodyFragment(html: string, baseUri?: string): DocumentShim {
    return JsoupBinding.parse(html, baseUri);
  },

  resolve(baseUrl: string, relative: string): string {
    return resolveUrl(baseUrl, relative);
  },
};
