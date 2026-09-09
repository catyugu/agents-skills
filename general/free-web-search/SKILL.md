---
name: free-web-search
description: Free, unlimited web search and content extraction. General web search via a rotating chain of free, open-source / community-run engines (SearXNG public instances → Mwmbl → Bing fallback) with cross-engine dedup and re-ranking in --source all mode. Plus specialized sources (GitHub, arXiv, Hacker News, Wikipedia, Marginalia). No API keys, no signup, no quotas. Use for any web search, looking up docs, facts, papers, code, discussions, or fetching a URL.
---

# Web Search (free, unlimited, no API key)

All sources are public, no-key endpoints. Nothing to sign up for, nothing to pay, no quotas. Failures fail loudly with an explicit warning so you can see which engines worked and which didn't.

## Sources

General web search uses a **rotating chain** of engines. The first one that returns results wins; the rest are skipped. The chain is ordered best-quality → broadest-fallback:

1. **SearXNG** (`searx.gnous.eu` and other community instances) — primary. SearXNG is an open-source metasearch engine run by volunteers. It aggregates Google, DuckDuckGo, Brave, Bing, Startpage, etc. on the server side, so we get high-quality results without hitting any one engine's anti-bot directly. A curated list of ~12 public instances is tried in order. The result's `Source` field transparently shows which backends the instance used (e.g. `searxng(google,duckduckgo,brave)`).
2. **Mwmbl** (`mwmbl.org`) — fallback. Open-source, non-profit, community-curated crawler. Smaller index but high quality for technical queries, and very stable.
3. **Bing** — last-resort fallback. No API key, works without auth, but result quality is mediocre (low relevance, lots of SEO spam). Kept as a safety net so the tool never silently fails.

In `--source all` mode, **all three** are queried in parallel; results are deduped by URL and re-ranked by cross-engine agreement (a page appearing in multiple engines wins). The same merge also includes dedicated specialized sources.

Specialized (independent of the general search), all free with no key:
- **GitHub** — repository search via the public REST API (60/hr unauthenticated; set `GITHUB_TOKEN` for 5000/hr).
- **arXiv** — academic paper search via the public OAI-PMH-style endpoint.
- **Hacker News** — story/comment search via Algolia's free HN API.
- **Wikipedia** — article search via the MediaWiki action API.
- **Marginalia** (`search.marginalia.nu`) — open-source "small web" search. Has a JS anti-bot gate; works intermittently, may need a browser solve.

Page content extraction uses Mozilla Readability + jsdom + turndown (no headless browser needed).

## Setup

Install dependencies once:

```bash
cd {baseDir}
npm install
```

No API keys or accounts are required. Optional:

- `GITHUB_TOKEN` — raises GitHub search from 60/hr to 5000/hr.

## Web search

```bash
{baseDir}/search.js "query"                         # General web search via the SearXNG→Mwmbl→Bing chain
{baseDir}/search.js "query" -n 15                   # Up to 30 results (default 10)
{baseDir}/search.js "query" --content               # Fetch page content as markdown
{baseDir}/search.js "query" --freshness pw          # HN/GitHub time filter: pd | pw | pm | py
{baseDir}/search.js "query" --source github         # Code search
{baseDir}/search.js "query" --source arxiv          # Academic papers
{baseDir}/search.js "query" --source hn             # Hacker News
{baseDir}/search.js "query" --source wiki           # Wikipedia
{baseDir}/search.js "query" --source searx          # SearXNG instances only
{baseDir}/search.js "query" --source mwmbl          # Mwmbl only
{baseDir}/search.js "query" --source marginalia     # Marginalia (small web)
{baseDir}/search.js "query" --source bing           # Bing only
{baseDir}/search.js "query" --source all            # All engines in parallel, deduped + re-ranked
```

### Options

- `-n <num>` — Number of results per source (default 10, max 30).
- `--content` — Fetch and include markdown content from the first few web/wiki result URLs.
- `--freshness <period>` — `pd` past day, `pw` past week, `pm` past month, `py` past year. Applied to HN and GitHub.
- `--source <name>` — `web` (default, the SearXNG→Mwmbl→Bing chain), or pick a specific engine (`searx`, `mwmbl`, `bing`, `marginalia`), or a specialized source (`github`, `arxiv`, `hn`, `wiki`), or `all` for parallel merge.

## Fetch a single URL

```bash
{baseDir}/content.js https://example.com/article
```

Returns the readable content as markdown (Readability-based extraction).

## Output format

```
--- Result 1 ---
Title: Page Title
Link: https://example.com/page
Source: searxng(google,duckduckgo) (example.com › page)
Snippet: Description from search results
Content:
  (only with --content)
  Markdown extracted from the page...

---
Primary engine: searxng:searx.gnous.eu
```

- `Source` shows the engine that returned the result. For SearXNG, the engines the instance used are listed in parentheses, e.g. `searxng(google,duckduckgo,brave)`. For `--source all`, multi-engine matches show as `searxng+mwmbl+bing`, indicating the page appeared in all three (higher confidence).
- For cross-engine dedup, results that show up in multiple engines are ranked first.

## When to use

- Looking up documentation, APIs, or any web content.
- Searching for facts, news, recent events.
- Searching code on GitHub.
- Searching academic papers on arXiv.
- Searching discussions on Hacker News.
- Searching Wikipedia for encyclopedic info.
- Searching the "small web" with Marginalia.
- Fetching and reading a specific URL.

## Notes

- All specialized sources are independent — failure of one does not block others when using `--source all` (failed sources are listed under "Warnings", working ones still return results).
- The default general web search goes through SearXNG → Mwmbl → Bing. The `Primary engine` line at the end tells you which one actually answered.
- SearXNG instance availability varies (community-run, no SLA). The curated instance list tries the most reliable ones first; the tool falls through automatically.
- Page content extraction uses Mozilla Readability. Some sites (e.g. heavy JS SPAs) may not yield useful markdown; in that case fall back to a browser-based approach.
- This skill is completely free and unlimited in practice. No API keys, no accounts, no quotas — the underlying endpoints are public services anyone can hit. Be a good citizen: don't hammer them, use the data you get.
