#!/usr/bin/env node
// Free, unlimited web search via public, no-key endpoints. No signup, no quota.
//
// General web search uses a chain of community-run, no-paid-tier engines
// (rotated automatically):
//   1. SearXNG public instances (open-source meta-search; uses Google/DDG/etc.
//      as backends on the instance side, so we get Google-quality results
//      without hitting Google directly)
//   2. Mwmbl (mwmbl.org) — open-source, non-profit, independent crawler
//   3. Bing — last-resort fallback (works without auth, quality is mediocre)
//
// Each engine is tried in order; the first one that returns results wins.
// Engines that fail are recorded as warnings so the user can see what
// happened. `--source all` queries every available engine in parallel and
// merges / dedupes / re-ranks by cross-engine agreement.
//
// Additional specialized sources (independent of the general search):
//   - GitHub repository search
//   - arXiv paper search
//   - Hacker News (Algolia) search
//   - Wikipedia (MediaWiki action API) search
//   - Marginalia (open-source "small web" search)
//
// Page content extraction uses Mozilla Readability + jsdom + turndown.
//
// Usage:
//   search.js "query"                         # general web search (engine chain)
//   search.js "query" -n 15                   # more results
//   search.js "query" --content               # Fetch page content as markdown
//   search.js "query" --freshness pw          # HN/GitHub time filter
//   search.js "query" --source github         # github | arxiv | hn | wiki | searx | mwmbl | marginalia | bing | web | all

import { Readability } from "@mozilla/readability";
import { JSDOM } from "jsdom";
import TurndownService from "turndown";
import { gfm } from "turndown-plugin-gfm";

const args = process.argv.slice(2);

const fetchContent = args.includes("--content");
if (fetchContent) args.splice(args.indexOf("--content"), 1);

let numResults = 10;
const nIdx = args.indexOf("-n");
if (nIdx !== -1 && args[nIdx + 1]) {
	numResults = Math.max(1, Math.min(30, parseInt(args[nIdx + 1], 10)));
	args.splice(nIdx, 2);
}

let freshness = null;
const fIdx = args.indexOf("--freshness");
if (fIdx !== -1 && args[fIdx + 1]) {
	freshness = args[fIdx + 1];
	args.splice(fIdx, 2);
}

let source = "web";
const sIdx = args.indexOf("--source");
if (sIdx !== -1 && args[sIdx + 1]) {
	source = args[sIdx + 1].toLowerCase();
	args.splice(sIdx, 2);
}

const query = args.join(" ").trim();

if (!query) {
	console.log("Usage: search.js <query> [options]");
	console.log("");
	console.log("Options:");
	console.log("  -n <num>              Number of results per source (default 10, max 30)");
	console.log("  --content             Fetch and include page content as markdown");
	console.log("  --freshness <period>  HN/GitHub time filter: pd | pw | pm | py");
	console.log("  --source <name>       web (default) | searx | mwmbl | marginalia | bing | github | arxiv | hn | wiki | all");
	console.log("");
	console.log("Examples:");
	console.log('  search.js "nodejs async await"');
	console.log('  search.js "rust ownership" -n 15');
	console.log('  search.js "react hooks" --source github');
	console.log('  search.js "transformer architecture" --source arxiv');
	console.log('  search.js "show hn" --source hn');
	console.log('  search.js "Alan Turing" --source wiki');
	console.log('  search.js "rust async" --source all');
	process.exit(1);
}

// ---------- shared helpers ----------

const USER_AGENTS = [
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
];

function pickUA(seed = 0) {
	return USER_AGENTS[seed % USER_AGENTS.length];
}

function cleanHtml(s) {
	return (s || "")
		.replace(/<[^>]+>/g, "")
		.replace(/&nbsp;/g, " ")
		.replace(/&amp;/g, "&")
		.replace(/&lt;/g, "<")
		.replace(/&gt;/g, ">")
		.replace(/&quot;/g, '"')
		.replace(/&#39;/g, "'")
		.replace(/&rsaquo;/g, "›")
		.replace(/&raquo;/g, "»")
		.replace(/&laquo;/g, "«")
		.replace(/&hellip;/g, "…")
		.replace(/&#8211;/g, "–")
		.replace(/&#8212;/g, "—")
		.replace(/&#8217;/g, "'")
		.replace(/\s+/g, " ")
		.trim();
}

function htmlToMarkdown(html) {
	const turndown = new TurndownService({ headingStyle: "atx", codeBlockStyle: "fenced" });
	turndown.use(gfm);
	turndown.addRule("removeEmptyLinks", {
		filter: (node) => node.nodeName === "A" && !node.textContent?.trim(),
		replacement: () => "",
	});
	return turndown
		.turndown(html)
		.replace(/\[\s*\]\([^)]*\)/g, "")
		.replace(/ +/g, " ")
		.replace(/\s+,/g, ",")
		.replace(/\s+\./g, ".")
		.replace(/\n{3,}/g, "\n\n")
		.trim();
}

async function fetchPageContent(url) {
	try {
		const response = await fetch(url, {
			headers: {
				"User-Agent": pickUA(),
				"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
				"Accept-Language": "en-US,en;q=0.9",
			},
			signal: AbortSignal.timeout(20000),
			redirect: "follow",
		});
		if (!response.ok) return `(HTTP ${response.status} ${response.statusText})`;
		const html = await response.text();
		const dom = new JSDOM(html, { url });
		const article = new Readability(dom.window.document).parse();
		if (article && article.content) {
			return htmlToMarkdown(article.content).substring(0, 6000);
		}
		const doc = new JSDOM(html, { url });
		const body = doc.window.document;
		body.querySelectorAll("script, style, noscript, nav, header, footer, aside").forEach((el) => el.remove());
		const main = body.querySelector("main, article, [role='main'], #content, .content") || body.body;
		const text = main?.textContent || "";
		if (text.trim().length > 100) return text.trim().substring(0, 6000);
		return "(could not extract readable content)";
	} catch (e) {
		return `(error fetching page: ${e.message})`;
	}
}

function normalizeUrl(u) {
	try {
		const url = new URL(u);
		url.hash = "";
		// Drop tracking noise
		for (const k of [...url.searchParams.keys()]) {
			if (/^(utm_|fbclid|gclid|mc_eid|icid|trk)/i.test(k)) url.searchParams.delete(k);
		}
		// Lowercase host
		url.hostname = url.hostname.toLowerCase();
		// Strip "www." for dedup
		if (url.hostname.startsWith("www.")) url.hostname = url.hostname.slice(4);
		// Drop trailing slash on path (except for root)
		if (url.pathname.length > 1 && url.pathname.endsWith("/")) url.pathname = url.pathname.slice(0, -1);
		return url.toString();
	} catch {
		return u;
	}
}

function urlKey(u) {
	try {
		const url = new URL(u);
		return (url.hostname + url.pathname).toLowerCase().replace(/\/+$/, "");
	} catch {
		return u;
	}
}

// Merge multiple result lists. Dedupes by URL key, re-ranks by cross-engine
// agreement, picks the best title/snippet. Each result gets a `sources` array
// listing every engine that returned it.
function mergeResults(lists) {
	const buckets = new Map(); // key -> { url, title, snippet, displayedUrl, sources:Set, score }
	for (const { engine, results } of lists) {
		for (const r of results) {
			if (!r || !r.url) continue;
			const key = urlKey(r.url);
			let b = buckets.get(key);
			if (!b) {
				b = {
					url: r.url,
					title: r.title || "",
					snippet: r.snippet || "",
					displayedUrl: r.displayedUrl || r.url.replace(/^https?:\/\//, ""),
					sources: new Set(),
					score: 0,
				};
				buckets.set(key, b);
			}
			b.sources.add(engine);
			// Prefer the longest non-empty title / snippet
			if ((r.title || "").length > (b.title || "").length) b.title = r.title;
			if ((r.snippet || "").length > (b.snippet || "").length) b.snippet = r.snippet;
		}
	}
	const out = [];
	for (const b of buckets.values()) {
		// Score = (number of engines reporting it) * 100 + (snippet length / 50)
		const snipLen = (b.snippet || "").length;
		b.score = b.sources.size * 100 + Math.min(60, snipLen / 50);
		out.push({
			url: b.url,
			title: b.title,
			snippet: b.snippet,
			displayedUrl: b.displayedUrl,
			source: [...b.sources].join("+"),
			_sources: [...b.sources],
			score: b.score,
		});
	}
	out.sort((a, b) => b.score - a.score || a.url.localeCompare(b.url));
	return out;
}

// ---------- SearXNG (community-run open-source meta-search) ----------
//
// Curated list of public SearXNG instances. Many are behind anti-bot
// protection or rate-limited from datacenter IPs; we try them in order and
// take the first one that returns real results. New instances can be added
// without code changes.
//
// We deliberately avoid JSON output: most instances disable it, and HTML
// parsing is reliable across themes.

const SEARXNG_INSTANCES = [
	// Verified-working at the time of writing. Order: most stable / lowest
	// anti-bot pressure first; we rotate through the rest on failure.
	"https://searx.gnous.eu",
	"https://searx.mbuf.net",
	"https://searx.be",
	"https://search.disroot.org",
	"https://baresearch.org",
	"https://etsi.me",
	"https://priv.au",
	"https://search.hbubli.cc",
	"https://search.inetol.net",
	"https://searx.tiekoetter.com",
	"https://search.bus-hit.me",
	"https://searxng.shreven.org",
];

function isSearxngBotBlock(html, status) {
	if (status === 403 || status === 429 || status === 418 || status === 503) return true;
	// Real challenge pages contain anti-bot markers in the body, not in
	// backend engine status tables. Look for a challenge page specifically.
	if (/<title>\s*(making sure you.?re not a bot|just a moment|attention required|anubis|go-away|captcha challenge)/i.test(html))
		return true;
	if (/<form[^>]*action="[^"]*anubis[^"]*"|challenge-form/i.test(html)) return true;
	// Anubis / go-away markers as actual page chrome
	if (/class="[^"]*anubis[^"]*"|\.within\.website\/x\/cmd\/anubis|go-away/i.test(html)) return true;
	// Real CAPTCHA interstitial
	if (/<h1[^>]*>\s*(captcha|robot check|attention required)/i.test(html)) return true;
	return false;
}

function parseSearxng(html) {
	// Modern SearXNG (default + simple themes): <article class="result ..."> with
	// url_header anchor, h3 title, p.content snippet, and a <div class="engines"> tag.
	const results = [];
	const articles = html.split(/<article class="result/).slice(1);
	for (const part of articles) {
		const block = part.split(/<\/article>/)[0];
		// url_header anchor: attribute order is not stable across SearXNG themes
		// (e.g. href= may come before or after class=), so grab href loosely.
		const urlMatch = block.match(
			/<a[^>]*\bclass="url_header"[^>]*\bhref="([^"]+)"[\s\S]*?>([\s\S]*?)<\/a>/,
		) || block.match(
			/<a[^>]*\bhref="([^"]+)"[^>]*\bclass="url_header"[\s\S]*?>([\s\S]*?)<\/a>/,
		);
		if (!urlMatch) continue;
		const url = urlMatch[1];
		const displayed = cleanHtml(urlMatch[2]).replace(/^https?:\/\//, "").replace(/^www\./, "");
		const titleMatch = block.match(/<h3[^>]*>\s*<a[^>]*>([\s\S]*?)<\/a>/);
		const title = titleMatch ? cleanHtml(titleMatch[1]) : "";
		const snipMatch = block.match(/<p class="content"[^>]*>([\s\S]*?)<\/p>/);
		const snippet = snipMatch ? cleanHtml(snipMatch[1]) : "";
		const enginesMatch = block.match(/<div class="engines">([\s\S]*?)<\/div>/);
		const engines = enginesMatch
			? [...enginesMatch[1].matchAll(/<span>([^<]+)<\/span>/g)].map((m) => m[1].trim())
			: [];
		if (url && title) {
			results.push({
				title,
				url,
				snippet,
				displayedUrl: displayed,
				source: engines.length ? `searxng(${engines.join(",")})` : "searxng",
			});
		}
	}
	return results;
}

async function trySearxngInstance(instance, query, limit) {
	const url = `${instance.replace(/\/$/, "")}/search?q=${encodeURIComponent(query)}`;
	const r = await fetch(url, {
		headers: {
			"User-Agent": pickUA(),
			"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
			"Accept-Language": "en-US,en;q=0.9",
		},
		signal: AbortSignal.timeout(20000),
		redirect: "follow",
	});
	if (!r.ok) throw new Error(`SearXNG ${instance} HTTP ${r.status}`);
	const html = await r.text();
	if (isSearxngBotBlock(html, r.status)) throw new Error(`SearXNG ${instance} bot block`);
	const results = parseSearxng(html);
	if (results.length === 0) throw new Error(`SearXNG ${instance} no results`);
	return { instance, results: results.slice(0, limit) };
}

async function searchSearxng(query, opts = {}) {
	const { limit = 10 } = opts;
	const errors = [];
	for (const inst of SEARXNG_INSTANCES) {
		try {
			const { instance, results } = await trySearxngInstance(inst, query, limit);
			return { results, engine: `searxng:${instance.replace(/^https?:\/\//, "")}` };
		} catch (e) {
			errors.push(e.message);
		}
	}
	throw new Error(`All SearXNG instances failed: ${errors[0] || "unknown"}`);
}

// ---------- Mwmbl (open-source, non-profit) ----------

function parseMwmbl(html) {
	// Mwmbl serves Svelte HTML; results are <a href="..." target="_self"> wrapping
	// a card with title (data-slot="card-title") and description.
	const results = [];
	const blocks = html.split(/<a href="(https?:\/\/[^"]+)"[^>]*target="_self"/).slice(1);
	for (let i = 0; i < blocks.length; i += 2) {
		const url = blocks[i];
		const body = blocks[i + 1] || "";
		// Title: data-slot="card-title" or text-2xl/line-clamp-2 div
		const titleMatch =
			body.match(/data-slot="card-title"[^>]*>([\s\S]*?)<\/div>/) ||
			body.match(/class="text-2xl[^"]*"[^>]*>([\s\S]*?)<\/div>/) ||
			body.match(/<h\d[^>]*>([\s\S]*?)<\/h\d>/);
		const title = titleMatch ? cleanHtml(titleMatch[1]) : "";
		const snipMatch = body.match(/data-slot="card-description"[^>]*>([\s\S]*?)<\/p>/);
		const snippet = snipMatch ? cleanHtml(snipMatch[1]) : "";
		// Site name for display
		const siteMatch = body.match(/<span>([a-z0-9.-]+\.[a-z]{2,})<\/span>/i);
		const displayedUrl = siteMatch
			? siteMatch[1] + (url.match(/^https?:\/\/[^\/]+(.*)$/)?.[1] || "")
			: url.replace(/^https?:\/\//, "");
		if (url && title) {
			results.push({ title, url, snippet, displayedUrl, source: "mwmbl" });
		}
	}
	return results;
}

async function searchMwmbl(query, opts = {}) {
	const { limit = 10 } = opts;
	const url = `https://mwmbl.org/?q=${encodeURIComponent(query)}`;
	const r = await fetch(url, {
		headers: {
			"User-Agent": pickUA(),
			"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
			"Accept-Language": "en-US,en;q=0.9",
		},
		signal: AbortSignal.timeout(20000),
		redirect: "follow",
	});
	if (!r.ok) throw new Error(`Mwmbl HTTP ${r.status}`);
	const html = await r.text();
	if (/no results|0 results|nothing found/i.test(html)) return [];
	const results = parseMwmbl(html);
	return { results: results.slice(0, limit), engine: "mwmbl" };
}

// ---------- Marginalia (open-source, "small web") ----------
//
// Marginalia shows a brief "Wait A Moment" page for bot-looking traffic
// with a JS-redirected URL containing an sst session token. For simple GET
// requests it sometimes serves real results directly. We try once and fail
// loudly if blocked — the user can manually solve the captcha in a browser
// if they want this source.

function parseMarginalia(html) {
	// Marginalia renders results as <article>...</article> in the no-JS
	// fallback. Modern layout: <a class="result-link"> containing the URL
	// and the page title, plus a paragraph of description.
	const results = [];
	const articles = html.split(/<article[^>]*>/).slice(1);
	for (const part of articles) {
		const block = part.split(/<\/article>/)[0];
		const titleMatch = block.match(/<a[^>]*class="[^"]*result-link[^"]*"[^>]*>([\s\S]*?)<\/a>/);
		if (!titleMatch) continue;
		const urlMatch = block.match(/<a[^>]*class="[^"]*result-link[^"]*"[^>]*href="([^"]+)"/);
		const descMatch = block.match(/<p[^>]*class="[^"]*result-description[^"]*"[^>]*>([\s\S]*?)<\/p>/);
		if (!urlMatch) continue;
		results.push({
			title: cleanHtml(titleMatch[1]),
			url: urlMatch[1],
			snippet: descMatch ? cleanHtml(descMatch[1]) : "",
			displayedUrl: urlMatch[1].replace(/^https?:\/\//, ""),
			source: "marginalia",
		});
	}
	return results;
}

async function searchMarginalia(query, opts = {}) {
	const { limit = 10 } = opts;
	const url = `https://search.marginalia.nu/search?query=${encodeURIComponent(query)}`;
	const r = await fetch(url, {
		headers: {
			"User-Agent": pickUA(),
			"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
			"Accept-Language": "en-US,en;q=0.9",
		},
		signal: AbortSignal.timeout(20000),
		redirect: "follow",
	});
	if (!r.ok) throw new Error(`Marginalia HTTP ${r.status}`);
	const html = await r.text();
	if (/wait a moment|making sure you.?re not a bot|countdown/i.test(html)) {
		throw new Error("Marginalia anti-bot challenge (solved in browser; not scriptable)");
	}
	return parseMarginalia(html).slice(0, limit);
}

// ---------- Bing (last-resort fallback) ----------

function decodeBingRedirect(href) {
	try {
		const unescaped = href.replace(/&amp;/g, "&");
		const u = new URL(unescaped);
		if (u.hostname !== "www.bing.com" || !u.pathname.startsWith("/ck/a")) return href;
		const raw = u.searchParams.get("u");
		if (!raw) return href;
		const stripped = raw.replace(/^a\d/, "");
		const pad = (4 - (stripped.length % 4)) % 4;
		const decoded = Buffer.from(stripped + "=".repeat(pad), "base64").toString("utf8");
		if (/^https?:\/\//.test(decoded)) return decoded;
		return href;
	} catch {
		return href;
	}
}

function parseBing(html) {
	const results = [];
	const blocks = html.split(/<li class="b_algo"/);
	for (let i = 1; i < blocks.length; i++) {
		const block = blocks[i].split(/<\/li>/)[0];
		const titleMatch = block.match(/<h2[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>\s*<\/h2>/);
		if (!titleMatch) continue;
		const url = decodeBingRedirect(titleMatch[1]);
		const title = cleanHtml(titleMatch[2]);
		const snippetMatch = block.match(/<p[^>]*>([\s\S]*?)<\/p>/);
		const snippet = snippetMatch ? cleanHtml(snippetMatch[1]) : "";
		const citeMatch = block.match(/<cite>([\s\S]*?)<\/cite>/);
		const displayedUrl = citeMatch
			? cleanHtml(citeMatch[1]).replace(/^https?:\/\//, "")
			: url.replace(/^https?:\/\/(www\.)?/, "");
		if (url && title) results.push({ title, url, snippet, displayedUrl, source: "bing" });
	}
	return results;
}

async function searchBing(query, opts = {}) {
	const { limit = 10 } = opts;
	const url = `https://www.bing.com/search?q=${encodeURIComponent(query)}`;
	const r = await fetch(url, {
		headers: {
			"User-Agent": pickUA(),
			"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
			"Accept-Language": "en-US,en;q=0.9",
			"Accept-Encoding": "gzip, deflate, br",
			"Sec-Ch-Ua": '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
			"Sec-Ch-Ua-Mobile": "?0",
			"Sec-Ch-Ua-Platform": '"Linux"',
			"Sec-Fetch-Dest": "document",
			"Sec-Fetch-Mode": "navigate",
			"Sec-Fetch-Site": "none",
			"Sec-Fetch-User": "?1",
			"Upgrade-Insecure-Requests": "1",
		},
		signal: AbortSignal.timeout(20000),
	});
	if (!r.ok) throw new Error(`Bing HTTP ${r.status}`);
	const html = await r.text();
	if (/captcha|verify you are a human|unusual traffic|automated queries/i.test(html)) {
		throw new Error("Bing anti-bot challenge");
	}
	return parseBing(html).slice(0, limit);
}

// ---------- GitHub ----------

async function searchGitHub(query, opts = {}) {
	const { limit = 10 } = opts;
	const params = new URLSearchParams({ q: query, per_page: String(Math.min(limit, 30)) });
	const headers = {
		"User-Agent": "pi-web-search",
		"Accept": "application/vnd.github+json",
	};
	if (process.env.GITHUB_TOKEN) headers["Authorization"] = `Bearer ${process.env.GITHUB_TOKEN}`;
	const r = await fetch(`https://api.github.com/search/repositories?${params}`, {
		headers,
		signal: AbortSignal.timeout(15000),
	});
	if (!r.ok) throw new Error(`GitHub HTTP ${r.status}: ${(await r.text()).slice(0, 200)}`);
	const data = await r.json();
	return (data.items || []).map((r) => ({
		title: `${r.full_name} — ${r.description || "(no description)"}`,
		url: r.html_url,
		snippet: `★ ${r.stargazers_count?.toLocaleString() || 0} · ${r.language || "?"} · ${
			r.license?.spdx_id || "no license"
		} · updated ${r.updated_at?.slice(0, 10) || "?"}`,
		displayedUrl: r.html_url.replace("https://", ""),
		source: "github",
	}));
}

// ---------- arXiv ----------

function parseArxivXml(xml) {
	const results = [];
	const entries = xml.split(/<entry>/).slice(1);
	for (const entry of entries) {
		const id = entry.match(/<id>([^<]+)<\/id>/)?.[1]?.trim();
		const title = cleanHtml(entry.match(/<title>([\s\S]*?)<\/title>/)?.[1] || "");
		const summary = cleanHtml(entry.match(/<summary>([\s\S]*?)<\/summary>/)?.[1] || "");
		const published = entry.match(/<published>([^<]+)<\/published>/)?.[1]?.slice(0, 10) || "";
		const authors = [...entry.matchAll(/<author>\s*<name>([^<]+)<\/name>/g)]
			.map((m) => m[1].trim())
			.slice(0, 4)
			.join(", ");
		if (!id) continue;
		results.push({
			title,
			url: id,
			snippet: `${authors}${published ? ` · ${published}` : ""}\n${summary.slice(0, 400)}`,
			displayedUrl: id.replace(/^https?:\/\//, ""),
			source: "arxiv",
		});
	}
	return results;
}

async function searchArxiv(query, opts = {}) {
	const { limit = 10 } = opts;
	const params = new URLSearchParams({
		search_query: `all:${query}`,
		start: "0",
		max_results: String(Math.min(limit, 20)),
	});
	const r = await fetch(`http://export.arxiv.org/api/query?${params}`, {
		signal: AbortSignal.timeout(20000),
		headers: { "User-Agent": pickUA() },
	});
	if (!r.ok) throw new Error(`arXiv HTTP ${r.status}`);
	return parseArxivXml(await r.text());
}

// ---------- Hacker News (Algolia) ----------

async function searchHackerNews(query, opts = {}) {
	const { limit = 10, freshness = null } = opts;
	const params = new URLSearchParams({ query, hitsPerPage: String(Math.min(limit, 30)) });
	if (freshness) {
		const days = { pd: 1, pw: 7, pm: 31, py: 365 }[freshness];
		if (days) {
			const since = Math.floor(Date.now() / 1000) - days * 86400;
			params.set("numericFilters", `created_at_i>${since}`);
		}
	}
	const r = await fetch(`https://hn.algolia.com/api/v1/search?${params}`, {
		signal: AbortSignal.timeout(15000),
	});
	if (!r.ok) throw new Error(`HN HTTP ${r.status}`);
	const data = await r.json();
	return (data.hits || []).map((h) => ({
		title: h.title || h.story_title || "(untitled)",
		url: h.url || `https://news.ycombinator.com/item?id=${h.objectID}`,
		snippet: `${h.points ?? 0} points · ${h.num_comments ?? 0} comments · ${
			h.author || "?"
		} · ${h.created_at?.slice(0, 10) || ""}`,
		displayedUrl: (h.url || `news.ycombinator.com/item?id=${h.objectID}`).replace(/^https?:\/\//, ""),
		source: "hackernews",
	}));
}

// ---------- Wikipedia ----------

async function searchWikipedia(query, opts = {}) {
	const { limit = 10 } = opts;
	const endpoint =
		"https://en.wikipedia.org/w/api.php?" +
		new URLSearchParams({
			action: "query",
			list: "search",
			srsearch: query,
			srlimit: String(Math.min(limit, 20)),
			format: "json",
			origin: "*",
		});
	const r = await fetch(endpoint, {
		headers: { "User-Agent": pickUA() },
		signal: AbortSignal.timeout(15000),
	});
	if (!r.ok) throw new Error(`Wikipedia HTTP ${r.status}`);
	const data = await r.json();
	const hits = data?.query?.search || [];
	const titles = hits.map((h) => h.title);
	let urlMap = {};
	if (titles.length) {
		const infoResp = await fetch(
			"https://en.wikipedia.org/w/api.php?" +
				new URLSearchParams({
					action: "query",
					prop: "info",
					titles: titles.join("|"),
					inprop: "url",
					format: "json",
					origin: "*",
				}),
			{ headers: { "User-Agent": pickUA() } },
		);
		if (infoResp.ok) {
			const info = await infoResp.json();
			const pages = info?.query?.pages || {};
			for (const p of Object.values(pages)) if (p.title) urlMap[p.title] = p.fullurl;
		}
	}
	return hits.map((h) => ({
		title: h.title,
		url: urlMap[h.title] || `https://en.wikipedia.org/wiki/${encodeURIComponent(h.title.replace(/ /g, "_"))}`,
		snippet: cleanHtml(h.snippet || ""),
		displayedUrl: `en.wikipedia.org/wiki/${encodeURIComponent(h.title.replace(/ /g, "_"))}`,
		source: "wikipedia",
	}));
}

// ---------- orchestrator ----------

// General web search: try SearXNG → Mwmbl → Bing, take the first one that works.
async function searchWeb(query, opts) {
	const errors = [];
	for (const fn of [searchSearxng, searchMwmbl, searchBing]) {
		try {
			const r = await fn(query, opts);
			if (r && r.results && r.results.length) {
				return {
					results: r.results,
					engine: r.engine || (fn === searchSearxng ? "searxng" : fn === searchMwmbl ? "mwmbl" : "bing"),
				};
			}
		} catch (e) {
			errors.push(e.message);
		}
	}
	throw new Error(`All general web engines failed: ${errors.join(" | ")}`);
}

async function runSource(name, query, opts) {
	switch (name) {
		case "web":
			return await searchWeb(query, opts);
		case "searx":
			return await searchSearxng(query, opts);
		case "mwmbl":
			return await searchMwmbl(query, opts);
		case "marginalia":
			return await searchMarginalia(query, opts);
		case "bing":
			return { results: await searchBing(query, opts), engine: "bing" };
		case "github":
			return await searchGitHub(query, opts);
		case "arxiv":
			return await searchArxiv(query, opts);
		case "hn":
			return await searchHackerNews(query, opts);
		case "wiki":
			return await searchWikipedia(query, opts);
		case "all": {
			// Web chain + specialized sources, all in parallel. For the web
			// side, fan out to every general engine (not just first-success)
			// so merge can dedupe and re-rank across engines.
			const webTasks = [
				["searxng", searchSearxng(query, opts).catch((e) => ({ __error: e.message }))],
				["mwmbl", searchMwmbl(query, opts).catch((e) => ({ __error: e.message }))],
				["bing", searchBing(query, opts).catch((e) => ({ __error: e.message }))],
			];
			const specTasks = [
				["github", searchGitHub(query, opts).catch((e) => ({ __error: e.message }))],
				["arxiv", searchArxiv(query, opts).catch((e) => ({ __error: e.message }))],
				["hn", searchHackerNews(query, opts).catch((e) => ({ __error: e.message }))],
				["wiki", searchWikipedia(query, opts).catch((e) => ({ __error: e.message }))],
			];
			const allTasks = [...webTasks, ...specTasks];
			const settled = await Promise.all(allTasks.map(async ([n, p]) => [n, await p]));
			const webLists = [];
			const specResults = [];
			const errors = [];
			for (const [n, r] of settled) {
				if (r && r.__error) {
					errors.push(`${n}: ${r.__error}`);
					continue;
				}
				// Sources may return either { results: [...] } or a bare array.
				const items = r && Array.isArray(r.results) ? r.results : Array.isArray(r) ? r : null;
				if (!items || !items.length) continue;
				if (webTasks.find(([m]) => m === n)) {
					webLists.push({ engine: n, results: items });
				} else {
					for (const item of items) specResults.push({ ...item, source: item.source || n });
				}
			}
			const merged = mergeResults(webLists);
			// Interleave: web (merged) first, then specialized
			const out = [...merged, ...specResults];
			if (out.length === 0 && errors.length) throw new Error(errors.join("; "));
			return { results: out, warnings: errors };
		}
		default:
			throw new Error(`Unknown source: ${name}`);
	}
}

function printResult(r, idx) {
	console.log(`--- Result ${idx} ---`);
	console.log(`Title: ${r.title}`);
	console.log(`Link: ${r.url}`);
	console.log(`Source: ${r.source}${r.displayedUrl ? ` (${r.displayedUrl})` : ""}`);
	if (r.snippet) console.log(`Snippet: ${r.snippet}`);
	if (r.content) console.log(`Content:\n${r.content}`);
	console.log("");
}

const sourcesToRun = source === "all" ? ["all"] : [source];
const allResults = [];
const warnings = [];
let primaryEngine = null;

for (const s of sourcesToRun) {
	try {
		const out = await runSource(s, query, { limit: numResults, freshness });
		if (out && out.results) {
			allResults.push(...out.results);
			if (out.engine) primaryEngine = out.engine;
			if (out.warnings) warnings.push(...out.warnings);
		} else if (Array.isArray(out)) {
			allResults.push(...out);
		}
	} catch (e) {
		warnings.push(`${s}: ${e.message}`);
	}
}

if (fetchContent && allResults.length) {
	// For merged (multi-source) results, expand source list; for single
	// results, only fetch content for the two sources that benefit most
	// from page extraction (SearXNG results often point to blog posts and
	// wiki articles that read well as markdown).
	const targetable = (src) => /searxng/.test(src) || /wikipedia/.test(src) || /marginalia/.test(src);
	const targets = allResults
		.filter((r) => targetable(r.source))
		.slice(0, Math.min(5, allResults.length));
	for (const r of targets) r.content = await fetchPageContent(r.url);
}

if (warnings.length && allResults.length === 0) {
	console.error("All sources failed:");
	for (const w of warnings) console.error("  - " + w);
	process.exit(1);
}

if (allResults.length === 0) {
	console.error("No results found.");
	process.exit(0);
}

for (let i = 0; i < allResults.length; i++) {
	printResult(allResults[i], i + 1);
}

if (warnings.length || primaryEngine) {
	console.log("---");
	if (primaryEngine && (source === "web" || source === "searx" || source === "mwmbl" || source === "bing")) {
		console.log(`Primary engine: ${primaryEngine}`);
	}
	if (warnings.length) {
		console.log("Warnings:");
		for (const w of warnings) console.log("  - " + w);
	}
}
