// Source probe: runs a scripted extension through the same executor the app uses and reports what
// actually works, with timings - so writing or fixing a source starts from facts, not guesses.
//
//   npx tsx scripts/probe-source.ts <id|all> [--dir <extensions dir>] [--json] [--skip-playback]
//   npx tsx scripts/probe-source.ts discover <url>     (give it a title page to see which sections it has)
//
// What it checks per source:
//   manifest   declared filters/sorts vs. what getSettings() really offers (drift = a control that
//              is hidden or a filter that silently does nothing)
//   filters    each option is sent as a real search and the result ids are compared with an
//              unfiltered baseline: APPLIED / NO-EFFECT / EMPTY / ERROR
//   details    getById over a mix of new, older and later-season titles: poster, genres, stills, related
//              and similar counts - plus a read of the site's own title page for those three sections,
//              so a block the site has and the source does not return is reported as MISSING
//   playback   first title -> groups -> first episode -> player links, then every EMBED link is
//              matched to a resolver by host and resolved (NODE resolvers only; BROWSER ones need
//              the app), each timed, and the first stream is fetched to prove it is reachable
//
// It runs in plain Node: fetch() falls back to sync-fetch (see globals.ts), so a source that needs
// challenge()/browserFetch() is reported as "needs app" rather than failing the whole run.
import fs from "node:fs";
import path from "node:path";
import { performance } from "node:perf_hooks";
import { fileURLToPath } from "node:url";
import * as cheerio from "cheerio";
import { executeExtensionCall } from "../src/main/extensions/execute";
import type { ExtensionMethod } from "../src/main/extensions/execute";

const here = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_DIR = path.resolve(here, "../../hibiki-sources/extensions");

type Status = "ok" | "warn" | "fail" | "skip";
interface Check {
  area: string;
  name: string;
  status: Status;
  ms?: number;
  detail?: string;
}

interface Ctx {
  dir: string;
  resolverDir: string;
  checks: Check[];
}

// ---------------------------------------------------------------------------------------------

function timed<T>(fn: () => T): { value?: T; error?: string; ms: number } {
  const start = performance.now();
  try {
    return { value: fn(), ms: Math.round(performance.now() - start) };
  } catch (error) {
    return { error: error instanceof Error ? error.message : String(error), ms: Math.round(performance.now() - start) };
  }
}

function invoke<T>(dir: string, sourceId: string, method: ExtensionMethod, ...args: unknown[]) {
  return timed(() => executeExtensionCall({ extensionsDir: dir, sourceId, method, args }) as T);
}

function push(ctx: Ctx, area: string, name: string, status: Status, ms?: number, detail?: string) {
  ctx.checks.push({ area, name, status, ms, detail });
}

const needsApp = (message: string) => /challenge|browserFetch|not implemented|bridge/i.test(message);

interface Manifest {
  id: string;
  website?: string;
  supportedFilters?: string[];
  resolverDependencies?: string[];
  capabilities?: string[];
}
interface Option { id: string; title: string }
interface FilterDef { id: string; title: string; type: "select" | "multi" | "tristate" | "text" | "range"; options?: Option[] }
interface Settings {
  sortOptions?: Option[];
  typeOptions?: Option[];
  statusOptions?: Option[];
  genreOptions?: Option[];
  filters?: FilterDef[];
}
type Title = { id: string; sourceId?: string; name?: string; russianName?: string; englishName?: string; originalName?: string };

const titleOf = (t: Title) => t.englishName || t.russianName || t.originalName || t.name || t.id;
const idsOf = (list: Title[]) => list.map((t) => t.id);

function sameSet(a: string[], b: string[]) {
  return a.length === b.length && a.every((id, i) => id === b[i]);
}

// ---------------------------------------------------------------------------------------------

function probeManifest(ctx: Ctx, manifest: Manifest, settings: Settings | null) {
  if (!settings) return;
  if ((settings.sortOptions ?? []).length === 0) push(ctx, "manifest", "sorts", "ok", undefined, "no sort orders offered (the catalog is one listing)");

  const defs = settings.filters ?? [];
  push(ctx, "manifest", "filters", defs.length ? "ok" : "warn", undefined, defs.length ? defs.map((d) => `${d.id}:${d.type}`).join(", ") : "no filters");
  for (const def of defs) {
    if ((def.type === "select" || def.type === "multi" || def.type === "tristate") && !(def.options?.length)) push(ctx, "manifest", `filter ${def.id}`, "warn", undefined, `${def.type} filter without options`);
  }
}

function probeFilters(ctx: Ctx, sourceId: string, settings: Settings) {
  const base = invoke<Title[]>(ctx.dir, sourceId, "search", { limit: 20 });
  if (base.error) {
    push(ctx, "filters", "baseline (no query)", needsApp(base.error) ? "skip" : "warn", base.ms, base.error);
    return;
  }
  const baseIds = idsOf(base.value!);
  push(ctx, "filters", "baseline (no query)", baseIds.length > 0 ? "ok" : "warn", base.ms, `${baseIds.length} results`);
  if (baseIds.length === 0) return;

  // A filter that returns the baseline may simply match the default view (status=released on a
  // catalogue that lists finished shows first). So a no-effect result only counts as a failure
  // when no sibling option of the same filter changed anything either.
  const noEffect: Array<{ index: number; group: string }> = [];
  const applied = new Set<string>();
  const attempt = (group: string, label: string, request: Record<string, unknown>): string[] => {
    const r = invoke<Title[]>(ctx.dir, sourceId, "search", { limit: 20, ...request });
    if (r.error) { push(ctx, "filters", label, "fail", r.ms, r.error); return []; }
    const ids = idsOf(r.value!);
    if (ids.length === 0) { push(ctx, "filters", label, "warn", r.ms, "EMPTY (valid filter, or a broken alias?)"); return ids; }
    if (sameSet(ids, baseIds)) {
      noEffect.push({ index: ctx.checks.length, group });
      push(ctx, "filters", label, "fail", r.ms, "NO-EFFECT: same results as the unfiltered baseline");
      return ids;
    }
    applied.add(group);
    push(ctx, "filters", label, "ok", r.ms, `APPLIED (${ids.length} results)`);
    return ids;
  };

  const defs = settings.filters ?? [];
  const toRequest = (filters: Record<string, unknown>) => ({ filters });
  for (const def of defs) {
    const group = def.id;
    const options = (def.options ?? []).slice(0, 2);
    switch (def.type) {
      case "select":
        for (const o of options) attempt(group, `${def.id}=${o.id}`, toRequest({ [def.id]: o.id }));
        break;
      case "multi":
        for (const o of options) attempt(group, `${def.id}=${o.id}`, toRequest({ [def.id]: [o.id] }));
        break;
      case "tristate":
        {
          // Excluding an option only shows if the baseline contains some of it, so exclude the one
          // whose include-search overlaps the baseline the most (skip when none does).
          let best: { id: string; overlap: number } | null = null;
          for (const o of options) {
            const ids = attempt(group, `${def.id}=${o.id}`, toRequest({ [def.id]: { include: [o.id], exclude: [] } }));
            const overlap = ids.filter((id) => baseIds.includes(id)).length;
            if (overlap > 0 && (!best || overlap > best.overlap)) best = { id: o.id, overlap };
          }
          if (best) attempt(`${def.id}-exclude`, `${def.id} exclude ${best.id}`, toRequest({ [def.id]: { include: [], exclude: [best.id] } }));
          else if (options.length > 0) push(ctx, "filters", `${def.id} exclude`, "skip", undefined, "not verifiable: no tried option appears in the baseline results");
        }
        break;
      case "range":
        attempt(group, `${def.id} 2018-2020`, toRequest({ [def.id]: { from: 2018, to: 2020 } }));
        break;
      case "text":
        break;
    }
  }

  // The catalog sends the id of one of the source's own sort orders.
  const sorts = settings.sortOptions ?? [];
  // The first order is the source's default - the baseline itself - so it is not expected to differ.
  for (const sort of sorts.slice(1, 8)) attempt("sort", `sort=${sort.id}`, { sort: sort.id });

  for (const { index, group } of noEffect) {
    if (!applied.has(group)) continue;
    ctx.checks[index].status = "warn";
    ctx.checks[index].detail = "same as baseline, but another option of this filter did change results - probably matches the default view";
  }
}

// ---------------------------------------------------------------------------------------------

interface ResolverInfo { id: string; hosts: string[]; runtime: string }

function loadResolvers(resolverDir: string): ResolverInfo[] {
  if (!fs.existsSync(resolverDir)) return [];
  return fs
    .readdirSync(resolverDir)
    .filter((f) => f.endsWith(".manifest.json"))
    .map((f) => JSON.parse(fs.readFileSync(path.join(resolverDir, f), "utf-8")))
    .map((m) => ({ id: m.id as string, hosts: (m.hosts ?? []) as string[], runtime: (m.runtime ?? "NODE") as string }));
}

function resolverFor(resolvers: ResolverInfo[], url: string) {
  let host = "";
  try { host = new URL(url).hostname.toLowerCase(); } catch { return null; }
  return resolvers.find((r) => r.hosts.some((h) => host === h || host.endsWith(`.${h}`))) ?? null;
}

async function reachable(url: string, headers: Record<string, string> | null | undefined) {
  const started = performance.now();
  try {
    const res = await fetch(url, { headers: { ...(headers ?? {}), Range: "bytes=0-1023" }, signal: AbortSignal.timeout(10_000) });
    return { ok: res.status < 400, status: res.status, ms: Math.round(performance.now() - started) };
  } catch (error) {
    return { ok: false, status: 0, ms: Math.round(performance.now() - started), error: String(error) };
  }
}

type Details = { id: string; posterUrl?: string | null; description?: string | null; genres?: string[]; screenshots?: string[]; relatedAnime?: unknown[]; franchiseAnime?: unknown[]; similarAnime?: unknown[]; studios?: unknown[]; russianName?: string; englishName?: string; originalName?: string };

// ---------------------------------------------------------------------------------------------
// Blocks on a title page: does the SITE have a related / similar / stills section, whatever the source
// returns? Read off the page itself - headings, tabs and containers by name - so a block a source does
// not implement yet still shows up here, and "the site has none" is a finding rather than a guess.

const BLOCK_WORDS = {
  // Labels, not titles: a title that ends in "Season 2" is not a related-titles heading, so the season words only count as the whole label.
  related: /связан|пов.?язан|related|relations?|watch.?order|franchise|франшиз|sequels?|prequels?|порядок просмотра|^(?:other )?seasons$|^(?:інші )?сезони$|^сезоны$/i,
  similar: /похож|схож|similar|recommend|рекоменд|you might like|вам (?:также )?(?:понравится|может)|more like|дивіться також|смотрите также/i,
  stills: /кадры|кадри|скриншот|скріншот|screenshots?|screens\b|gallery|галере/i,
} as const;
type BlockKind = keyof typeof BLOCK_WORDS;

interface BlockScan { related: string[]; similar: string[]; stills: string[]; endpoints: string[] }

function scanBlocks(html: string): BlockScan {
  const $ = cheerio.load(html);
  const found: BlockScan = { related: [], similar: [], stills: [], endpoints: [] };
  const add = (kind: BlockKind, evidence: string) => { if (!found[kind].includes(evidence) && found[kind].length < 6) found[kind].push(evidence); };

  // 1. Headings, tabs and section titles, by their words.
  $("h1,h2,h3,h4,h5,.title,.head,.section-head,.heading,[data-tab],.tabs a,.tab a,.tab-title,.ep-sim-head").each((_, el) => {
    const text = $(el).clone().children("svg,script,style").remove().end().text().replace(/\s+/g, " ").trim();
    if (!text || text.length > 60) return;
    (Object.keys(BLOCK_WORDS) as BlockKind[]).forEach((kind) => { if (BLOCK_WORDS[kind].test(text)) add(kind, `heading "${text}"`); });
  });
  // 2. Containers named for them (id / class), with how many links they hold - a real list, not a label.
  $("[id],[class]").each((_, el) => {
    const name = `${$(el).attr("id") ?? ""} ${$(el).attr("class") ?? ""}`;
    if (name.length > 160) return;
    (Object.keys(BLOCK_WORDS) as BlockKind[]).forEach((kind) => {
      const named = kind === "related" ? /related(?!-news)|watch-?order|franchise|seasons?-?(?:grid|list)|\bfran\b/i : kind === "similar" ? /similar|recommend|reco\b|series-reco|ep-sim|related-news/i : /screen|gallery/i;
      if (!named.test(name)) return;
      if (/^(?:html|body)$/i.test(el.tagName)) return;
      // A card list is either full of links or, on some engines, each card sits inside its own link.
      const links = $(el).find("a[href]").length + ($(el).closest("a[href]").length ? 1 : 0);
      if (links >= 1) add(kind, `${el.tagName}${$(el).attr("id") ? "#" + $(el).attr("id") : ""}${($(el).attr("class") ?? "").split(/\s+/)[0] ? "." + ($(el).attr("class") ?? "").split(/\s+/)[0] : ""} (${links} links)`);
    });
  });
  // 3. Endpoints the page's own scripts call for them (lists that are filled in after load).
  for (const m of html.matchAll(/["'`]([^"'`\s]*(?:watch-?order|related|recommend|similar|franchise)[^"'`\s]*)["'`]/gi)) {
    if (/\.(?:css|js|png|jpe?g|svg|webp)(?:\?|$)/i.test(m[1]) || m[1].length > 90 || !/[/?]/.test(m[1])) continue;
    if (!found.endpoints.includes(m[1]) && found.endpoints.length < 5) found.endpoints.push(m[1]);
  }
  return found;
}

// How many title pages of one site are read for its sections.
const SITE_PAGES = 8;

const DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36";

async function fetchText(url: string): Promise<{ status: number; html: string } | null> {
  try {
    const res = await fetch(url, { headers: { "User-Agent": DESKTOP_UA, "Accept-Language": "en-US,en;q=0.9" }, redirect: "follow", signal: AbortSignal.timeout(20_000) });
    return { status: res.status, html: await res.text() };
  } catch {
    return null;
  }
}

/** The title's own page on its site, found by trying the usual shapes of an id and checking the title's name is on it. */
async function findTitlePage(website: string, id: string, names: string[]): Promise<{ url: string; html: string } | null> {
  if (/^\d+$/.test(id)) return null; // numeric ids are API records; their sites are apps with no page to read
  const clean = id.replace(/^\/+|\/+$/g, "");
  const base = website.replace(/\/+$/, "");
  const shapes = clean.endsWith(".html") || clean.includes("/") ? [`${base}/${clean}`, `${base}/${clean}/`] : [`${base}/anime/${clean}`, `${base}/watch/${clean}`, `${base}/${clean}`, `${base}/anime/${clean}/`, `${base}/${clean}/`];
  const wanted = names.map((n) => n.toLowerCase().slice(0, 14)).filter((n) => n.length >= 3);
  for (const url of shapes) {
    const page = await fetchText(url);
    // Cloudflare's script tag (challenge-platform) is on every proxied page; only an interstitial is a block.
    if (!page || page.status !== 200 || /<title>\s*Just a moment/i.test(page.html)) continue;
    const text = page.html.toLowerCase();
    if (wanted.length === 0 || wanted.some((n) => text.includes(n))) return { url, html: page.html };
  }
  return null;
}

// What the source returns, over enough titles to tell "the source has none" from "these titles have none":
// the newest (least filled in), the catalog's default listing (older), and titles that look like a later
// season, which are the ones a franchise block is for.
async function probeDetails(ctx: Ctx, sourceId: string, manifest: Manifest) {
  const seenIds = new Set<string>();
  const pool: Title[] = [];
  const take = (list: Title[], n: number) => { for (const t of list) { if (pool.length >= 14 || n <= 0) break; if (seenIds.has(t.id)) continue; seenIds.add(t.id); pool.push(t); n--; } };
  take(invoke<Title[]>(ctx.dir, sourceId, "latest", 8).value ?? [], 3);
  take(invoke<Title[]>(ctx.dir, sourceId, "search", { limit: 8 }).value ?? [], 4);
  for (const query of ["season 2", "2nd season", "2 сезон", "часть 2", "II"]) take(invoke<Title[]>(ctx.dir, sourceId, "search", { query, limit: 6 }).value ?? [], 2);
  if (pool.length === 0) return;

  const tally = { related: 0, similar: 0, stills: 0 };
  const most = { related: 0, similar: 0, stills: 0 };
  let checked = 0;
  // The title that best shows each section, so the site page read for it is one where the section can exist.
  const best: Partial<Record<BlockKind, { title: Title; details: Details; count: number }>> = {};
  let first: { title: Title; details: Details } | null = null;
  const visited: { title: Title; details: Details }[] = [];
  for (const title of pool.slice(0, 12)) {
    const r = invoke<Details>(ctx.dir, sourceId, "getById", title.id);
    if (r.error) { push(ctx, "details", `getById(${title.id})`, needsApp(r.error) ? "skip" : "fail", r.ms, r.error); continue; }
    const d = r.value!;
    checked++;
    const counts = {
      related: new Set([...(d.relatedAnime ?? []), ...(d.franchiseAnime ?? [])].map((x) => (x as { id?: string }).id ?? "")).size,
      similar: (d.similarAnime ?? []).length,
      stills: (d.screenshots ?? []).filter((u) => typeof u === "string" && u).length,
    };
    (Object.keys(counts) as BlockKind[]).forEach((k) => { if (counts[k] > 0) tally[k]++; most[k] = Math.max(most[k], counts[k]); });
    first = first ?? { title, details: d };
    visited.push({ title, details: d });
    (Object.keys(counts) as BlockKind[]).forEach((k) => { if (counts[k] > (best[k]?.count ?? 0)) best[k] = { title, details: d, count: counts[k] }; });
    push(ctx, "details", `getById(${title.id})`, d.posterUrl ? "ok" : "warn", r.ms,
      `poster ${d.posterUrl ? "yes" : "NO"}, description ${d.description ? "yes" : "no"}, genres ${d.genres?.length ?? 0}, stills ${counts.stills}, related ${counts.related}, similar ${counts.similar}`);
  }
  if (checked === 0) return;

  // The site's own pages for those titles: which of the three sections they have at all, read from the
  // markup, headings and scripts - whatever the source does or does not return.
  let site: BlockScan | null = null;
  const siteUrls: string[] = [];
  let siteNote = "";
  const website = (manifest as { website?: string }).website;
  const targets = new Map<string, { title: Title; details: Details }>();
  for (const k of ["related", "similar", "stills"] as BlockKind[]) { const b = best[k]; if (b) targets.set(b.title.id, b); }
  if (targets.size === 0 && first) targets.set(first.title.id, first);
  // A page is cheap to read and a section can be on some titles only, so the rest of the sample is read too:
  // "none on the site" then means none across all of them, not on the one page that happened to be opened.
  for (const v of visited) if (targets.size < SITE_PAGES) targets.set(v.title.id, v);
  if (website) {
    for (const { title, details } of [...targets.values()].slice(0, SITE_PAGES)) {
      const names = [details.russianName, details.englishName, details.originalName, title.englishName, title.russianName, title.originalName].filter((n): n is string => !!n);
      const page = await findTitlePage(website, title.id, names);
      if (!page) { siteNote = /^\d+$/.test(title.id) ? "numeric ids: the site is an app, nothing to read" : "the title's page was not reachable (challenge, or an id shape not tried)"; continue; }
      const scanned = scanBlocks(page.html);
      siteUrls.push(page.url);
      site = site ?? { related: [], similar: [], stills: [], endpoints: [] };
      for (const k of ["related", "similar", "stills", "endpoints"] as const) for (const e of scanned[k]) if (!site[k].includes(e) && site[k].length < 6) site[k].push(e);
    }
  }
  if (siteUrls.length) siteNote = `${siteUrls.length} pages read, e.g. ${siteUrls[0]}`;

  const labels: Record<BlockKind, string> = { related: "related", similar: "similar", stills: "stills" };
  (Object.keys(labels) as BlockKind[]).forEach((kind) => {
    const has = tally[kind] > 0;
    const evidence = site ? site[kind] : [];
    const endpoints = kind === "stills" || !site ? [] : site.endpoints.filter((e) => (kind === "related" ? /related|watch-?order|franchise/i : /recommend|similar/i).test(e));
    const siteHas = evidence.length > 0 || endpoints.length > 0;
    const siteText = site ? (siteHas ? `site page has: ${[...evidence, ...endpoints.map((e) => `script -> ${e}`)].join("; ")}` : "no such block found on the site page") : `site not scanned (${siteNote})`;
    const detail = `${tally[kind]}/${checked} titles (up to ${most[kind]}) - ${siteText}`;
    if (has) push(ctx, "details", labels[kind], "ok", undefined, detail);
    else if (siteHas) push(ctx, "details", labels[kind], "warn", undefined, `MISSING: ${detail}`);
    else push(ctx, "details", labels[kind], "skip", undefined, `none returned; ${detail}`);
  });
  if (site && siteNote) push(ctx, "details", "site page", "ok", undefined, siteNote);
}

type Link = { url: string; type: string; playerName?: string | null; translation?: string | null; headers?: Record<string, string> | null; quality?: string | null };

async function probePlayback(ctx: Ctx, sourceId: string, manifest: Manifest) {
  const latest = invoke<Title[]>(ctx.dir, sourceId, "latest", 12);
  if (latest.error) push(ctx, "catalog", "latest(12)", needsApp(latest.error) ? "skip" : "fail", latest.ms, latest.error);
  else push(ctx, "catalog", "latest(12)", latest.value!.length ? "ok" : "warn", latest.ms, `${latest.value!.length} titles`);

  let pool = latest.value ?? [];
  if (pool.length === 0) {
    const s = invoke<Title[]>(ctx.dir, sourceId, "search", { limit: 12 });
    pool = s.value ?? [];
  }
  if (pool.length === 0) return;

  const query = titleOf(pool[0]).split(/\s+/)[0];
  const search = invoke<Title[]>(ctx.dir, sourceId, "search", { query, limit: 10 });
  if (search.error) push(ctx, "catalog", `search("${query}")`, "fail", search.ms, search.error);
  else push(ctx, "catalog", `search("${query}")`, search.value!.length ? "ok" : "warn", search.ms, `${search.value!.length} titles`);

  // First title that yields an episode. Some catalog entries (announcements) have none.
  const resolvers = loadResolvers(ctx.resolverDir);
  for (const title of pool.slice(0, 4)) {
    const groups = invoke<Array<{ id: string; title: string; episodes: Array<{ id: string; number: number }> }>>(ctx.dir, sourceId, "getPlaybackGroups", title.id);
    if (groups.error) {
      push(ctx, "playback", `getPlaybackGroups(${title.id})`, needsApp(groups.error) ? "skip" : "fail", groups.ms, groups.error);
      continue;
    }
    const group = groups.value!.find((g) => g.episodes.length > 0);
    if (!group) {
      push(ctx, "playback", `getPlaybackGroups(${title.id})`, "warn", groups.ms, "no episodes");
      continue;
    }
    const episodeCount = groups.value!.reduce((n, g) => n + g.episodes.length, 0);
    push(ctx, "playback", `getPlaybackGroups(${title.id})`, "ok", groups.ms, `${groups.value!.length} groups, ${episodeCount} episodes`);

    const episode = group.episodes[0];
    const links = invoke<Link[]>(ctx.dir, sourceId, "getPlayerLinks", title.id, group.id, episode.id);
    if (links.error) return push(ctx, "playback", "getPlayerLinks", needsApp(links.error) ? "skip" : "fail", links.ms, links.error);
    const list = links.value!;
    const embeds = list.filter((l) => l.type === "EMBED").length;
    push(ctx, "playback", "getPlayerLinks", list.length ? "ok" : "fail", links.ms, `${list.length} links (${list.length - embeds} direct, ${embeds} embed)`);

    // Unique host x translation is enough: a dozen mirrors of one provider prove nothing extra.
    const seen = new Set<string>();
    for (const link of list) {
      let host = "?";
      try { host = new URL(link.url).hostname; } catch { /* keep "?" */ }
      const key = `${link.type}|${host}`;
      if (seen.has(key)) continue;
      seen.add(key);
      const label = `${link.type} ${host}${link.playerName ? ` [${link.playerName}]` : ""}`;

      let streams: Link[] = [link];
      if (link.type === "EMBED") {
        const resolver = resolverFor(resolvers, link.url);
        if (!resolver) { push(ctx, "resolve", label, "warn", undefined, "no resolver for this host - would fall back to an iframe"); continue; }
        if (!(manifest.resolverDependencies ?? []).includes(resolver.id)) push(ctx, "manifest", `resolver ${resolver.id}`, "warn", undefined, "used by a link but missing from resolverDependencies");
        if (resolver.runtime === "BROWSER") { push(ctx, "resolve", label, "skip", undefined, `${resolver.id}: BROWSER runtime (hidden window, ~2-5s) - run inside the app`); continue; }
        const r = invoke<Array<Link & { type: string }>>(ctx.resolverDir, resolver.id, "resolve", JSON.stringify(link));
        if (r.error) { push(ctx, "resolve", label, needsApp(r.error) ? "skip" : "fail", r.ms, `${resolver.id}: ${r.error}`); continue; }
        streams = r.value!;
        push(ctx, "resolve", label, streams.length ? "ok" : "fail", r.ms, `${resolver.id}: ${streams.length} streams [${streams.map((s) => s.quality ?? "?").join(", ")}]`);
        if (streams.length === 0) continue;
      }
      const first = streams[0];
      if (/^DIRECT_|^HLS$|^MP4$|^DASH$/.test(first.type)) {
        const reach = await reachable(first.url, first.headers);
        push(ctx, "stream", label, reach.ok ? "ok" : "fail", reach.ms, `HTTP ${reach.status || "ERR"} ${first.url.slice(0, 90)}`);
      }
    }
    return;
  }
}

// ---------------------------------------------------------------------------------------------

async function probeSource(dir: string, sourceId: string, skipPlayback: boolean): Promise<Check[]> {
  const ctx: Ctx = { dir, resolverDir: path.join(dir, "extractors"), checks: [] };
  const manifestPath = path.join(dir, `${sourceId}.manifest.json`);
  if (!fs.existsSync(manifestPath)) throw new Error(`No manifest at ${manifestPath}`);
  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf-8")) as Manifest;

  const settingsCall = invoke<Settings>(dir, sourceId, "getSettings");
  if (settingsCall.error) push(ctx, "manifest", "getSettings()", needsApp(settingsCall.error) ? "skip" : "fail", settingsCall.ms, settingsCall.error);
  const settings = settingsCall.value ?? null;
  if (settings) {
    push(ctx, "manifest", "getSettings()", "ok", settingsCall.ms,
      `sorts ${settings.sortOptions?.length ?? 0}, types ${settings.typeOptions?.length ?? 0}, statuses ${settings.statusOptions?.length ?? 0}, genres ${settings.genreOptions?.length ?? 0}, custom ${settings.filters?.length ?? 0}`);
    probeManifest(ctx, manifest, settings);
    probeFilters(ctx, sourceId, settings);
  }
  await probeDetails(ctx, sourceId, manifest);
  if (!skipPlayback) await probePlayback(ctx, sourceId, manifest);
  return ctx.checks;
}

const ICON: Record<Status, string> = { ok: "PASS", warn: "WARN", fail: "FAIL", skip: "SKIP" };

function printReport(sourceId: string, checks: Check[]) {
  console.log(`\n=== ${sourceId} ${"=".repeat(Math.max(3, 60 - sourceId.length))}`);
  for (const c of checks) {
    const ms = c.ms === undefined ? "      " : `${String(c.ms).padStart(5)}ms`;
    console.log(`  ${ICON[c.status]}  ${c.area.padEnd(9)} ${ms}  ${c.name}${c.detail ? `  - ${c.detail}` : ""}`);
  }
  const count = (s: Status) => checks.filter((c) => c.status === s).length;
  console.log(`  -> ${count("ok")} pass, ${count("warn")} warn, ${count("fail")} fail, ${count("skip")} skip`);
}

// ---------------------------------------------------------------------------------------------
// discover: what does this site offer that a source could use?

const KNOWN_EMBED_HOSTS = ["kodik", "aniboom", "alloha", "vk.com", "vkvideo", "ok.ru", "sibnet", "dailymotion", "ashdi", "megaplay", "vidwish", "krussdomi", "moonanime", "streamwish", "filemoon", "mixdrop", "doodstream", "voe.sx", "streamtape", "mp4upload", "megacloud", "rapid-cloud", "kwik"];

async function discover(rawUrl: string) {
  const url = new URL(rawUrl).toString();
  console.log(`Discovering ${url}\n`);
  const started = performance.now();
  const res = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36", "Accept-Language": "en-US,en;q=0.9" }, redirect: "follow" });
  const html = await res.text();
  const ms = Math.round(performance.now() - started);
  const cloudflare = res.headers.get("cf-mitigated") === "challenge" || /Just a moment|cf-chl|challenge-platform/i.test(html) || (res.status === 403 && /cloudflare/i.test(res.headers.get("server") ?? ""));
  console.log(`HTTP ${res.status} in ${ms}ms, ${html.length} bytes, server: ${res.headers.get("server") ?? "?"}${res.redirected ? `, redirected to ${res.url}` : ""}`);
  console.log(`Cloudflare challenge: ${cloudflare ? "YES - a source will need challenge()" : "no"}`);

  const $ = cheerio.load(html);
  const engine: string[] = [];
  if (/dle_root|DataLife Engine|\/engine\/ajax\//i.test(html)) engine.push("DataLife Engine (search: /index.php?do=search, ajax under /engine/ajax)");
  if (/wp-content|wp-json|dooplay/i.test(html)) engine.push(`WordPress${/dooplay/i.test(html) ? " + DooPlay theme (search: /?s=, API: /wp-json/dooplayer/)" : ""}`);
  if ($("script#__NEXT_DATA__").length) engine.push("Next.js (data is inline JSON in #__NEXT_DATA__ - no HTML scraping needed)");
  if (/window\.__NUXT__|__NUXT_DATA__/.test(html)) engine.push("Nuxt (inline state payload)");
  if (/<script[^>]+type="application\/json"/i.test(html)) engine.push("inline application/json blocks present");
  console.log(`Engine: ${engine.join("; ") || "unknown"}`);

  // On a title page: which of the related / similar / stills sections it has.
  const blocks = scanBlocks(html);
  for (const kind of ["related", "similar", "stills"] as const) console.log(`${kind.padEnd(8)} ${blocks[kind].length ? blocks[kind].join("; ") : "-"}`);
  if (blocks.endpoints.length) console.log(`         endpoints in scripts: ${blocks.endpoints.join(", ")}`);

  const apis = new Set<string>();
  for (const m of html.matchAll(/["'`](\/(?:api|ajax|engine\/ajax|wp-json)\/[A-Za-z0-9_\-./{}$?=&]*)["'`]/g)) apis.add(m[1]);
  if (apis.size) console.log(`\nAPI paths mentioned in the page (${apis.size}):\n  ${[...apis].slice(0, 25).join("\n  ")}`);

  const controls: string[] = [];
  $("select[name]").each((_, el) => {
    const options = $(el).find("option").map((__, o) => $(o).attr("value")).get().filter(Boolean);
    controls.push(`select  ${$(el).attr("name")}  (${options.length} options: ${options.slice(0, 6).join(", ")}${options.length > 6 ? ", ..." : ""})`);
  });
  const checkboxNames = new Map<string, number>();
  $("input[type=checkbox][name], input[type=radio][name]").each((_, el) => {
    const name = $(el).attr("name")!;
    checkboxNames.set(name, (checkboxNames.get(name) ?? 0) + 1);
  });
  for (const [name, n] of checkboxNames) controls.push(`choice  ${name}  (${n} inputs)`);
  const facets = new Set<string>();
  $("a[href]").each((_, el) => {
    const m = ($(el).attr("href") ?? "").match(/[?&](genre|genres|year|season|status|type|category|producer|sort|order|format)(?:\[\])?=([^&]+)/i) ?? ($(el).attr("href") ?? "").match(/\/(genre|genres|year|season|status|type|category|producer|tag)\/([^/?#]+)/i);
    if (m) facets.add(`${m[1].toLowerCase()}`);
  });
  if (facets.size) controls.push(`link facets: ${[...facets].join(", ")}`);
  console.log(`\nFilter-like controls (${controls.length}):\n  ${controls.slice(0, 30).join("\n  ") || "(none in the server-rendered HTML - filters may be client-side or in an API)"}`);

  const hosts = new Map<string, string>();
  $("iframe[src], iframe[data-src], [data-embed], [data-src*='//']").each((_, el) => {
    const raw = $(el).attr("src") ?? $(el).attr("data-src") ?? $(el).attr("data-embed");
    if (!raw) return;
    try { hosts.set(new URL(raw, url).hostname, raw); } catch { /* not a URL */ }
  });
  for (const known of KNOWN_EMBED_HOSTS) if (html.toLowerCase().includes(known)) hosts.set(known, "(mentioned in page source)");
  if (hosts.size) console.log(`\nPlayer hosts:\n  ${[...hosts].map(([h, u]) => `${h}  ${u.slice(0, 80)}`).join("\n  ")}`);

  const search = $("form[action*=search], form[action*='?s'], input[type=search], input[name=q], input[name=story], input[name=s], input[name=query]").first();
  if (search.length) console.log(`\nSearch input: <${search.prop("tagName")?.toLowerCase()} name="${search.attr("name") ?? ""}" action="${search.attr("action") ?? search.closest("form").attr("action") ?? ""}">`);
}

// ---------------------------------------------------------------------------------------------

async function main() {
  const argv = process.argv.slice(2);
  if (argv[0] === "discover") {
    if (!argv[1]) throw new Error("usage: probe-source.ts discover <url>");
    return discover(argv[1]);
  }
  const flag = (name: string) => argv.includes(name);
  const dirIndex = argv.indexOf("--dir");
  const dir = dirIndex >= 0 ? path.resolve(argv[dirIndex + 1]) : DEFAULT_DIR;
  const target = argv.find((a, i) => !a.startsWith("--") && argv[i - 1] !== "--dir");
  if (!target) {
    console.error("usage: probe-source.ts <id|all> [--dir <extensions dir>] [--json] [--skip-playback]\n       probe-source.ts discover <url>");
    process.exit(2);
  }
  const ids = target === "all"
    ? fs.readdirSync(dir).filter((f) => f.endsWith(".manifest.json")).map((f) => f.replace(/\.manifest\.json$/, ""))
    : [target];

  const all: Record<string, Check[]> = {};
  for (const id of ids) {
    all[id] = await probeSource(dir, id, flag("--skip-playback"));
    if (!flag("--json")) printReport(id, all[id]);
  }
  if (flag("--json")) console.log(JSON.stringify(all, null, 2));
  if (Object.values(all).some((checks) => checks.some((c) => c.status === "fail"))) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(2);
});
