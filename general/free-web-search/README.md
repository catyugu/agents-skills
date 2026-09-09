# free-web-search

Free, unlimited web search and content extraction. No API keys, no signup, no quotas.

General web search uses a **rotating chain** of free, open-source / community-run engines (SearXNG public instances → Mwmbl → Bing fallback) with cross-engine dedup and re-ranking in `--source all` mode. Plus specialized sources (GitHub, arXiv, Hacker News, Wikipedia, Marginalia).

> Design constraint: every source is a public, no-key endpoint. Nothing to sign up for, nothing to pay, no quotas. The tool is designed to fail loudly so you always know which engine answered.

## Quick start

```bash
npm install
./search.js "rust async tokio"              # general web search
./search.js "rust async tokio" --source all # merge results from every engine
./search.js "Alan Turing" --source wiki     # pick a specific source
./content.js https://example.com/article   # extract a single page as markdown
```

No API keys required. `GITHUB_TOKEN` is the only optional env var (raises GitHub search from 60/hr to 5000/hr).

## Why this exists

Bing's web search is the default in many agent frameworks and the quality is mediocre: low relevance, lots of SEO spam, no ability to filter. This skill replaces Bing with a chain of higher-quality sources that you can actually run unattended.

I tested every free search endpoint I could find. The honest list:

- **DDG, Mojeek, Ecosia, Qwant, Startpage, Yep, Yandex, Phind, You.com, Andi** — all blocked behind anti-bot / require JS / captcha when accessed programmatically.
- **Most public SearXNG instances** — also behind Anubis/anti-bot from datacenter IPs, or rate-limited.
- **SearXNG (`searx.gnous.eu` and ~11 others)** — open-source metasearch that aggregates Google / DuckDuckGo / Brave / Bing on the server side, so we get high-quality results without hitting any one engine's anti-bot directly. The curated instance list rotates through the most reliable ones; new ones can be added with no code changes.
- **Mwmbl (mwmbl.org)** — 100% open-source, non-profit, community-curated crawler. Smaller index but high quality for technical queries, and rock-solid stability (10 requests in a row, 100% success).
- **Bing** — kept as a last-resort fallback. No API key, works without auth, but quality is mediocre. Only used if both SearXNG and Mwmbl fail.

In `--source all` mode, all three are queried in parallel; results are deduped by URL and re-ranked by cross-engine agreement (a page appearing in multiple engines wins). A `Source: searxng+mwmbl+bing` tag in the output means the page was found by all three — high-confidence.

## Sources

| Source        | Free?   | Quality | Rate-limited? | Notes |
|---------------|---------|---------|---------------|-------|
| SearXNG       | ✅ yes  | high    | per-instance  | primary; ~12-instance rotation |
| Mwmbl         | ✅ yes  | decent  | no (in practice) | open-source, non-profit |
| Bing          | ✅ yes  | low     | no            | last-resort fallback |
| GitHub        | ✅ yes  | high    | 60/hr (5000 with `GITHUB_TOKEN`) | REST API |
| arXiv         | ✅ yes  | high    | no            | OAI-PMH endpoint |
| Hacker News   | ✅ yes  | high    | no            | Algolia API |
| Wikipedia     | ✅ yes  | high    | no            | MediaWiki action API |
| Marginalia    | ✅ yes  | niche   | intermittent  | "small web"; JS anti-bot gate |

`free-web-search` is itself free and open-source. No paid tier exists, no signup, no tracking, no analytics, no quota. Be a good citizen and don't hammer the underlying endpoints.

## Install

```bash
git clone https://github.com/catyugu/free-web-search
cd free-web-search
npm install
```

## Usage

```bash
# General web search (SearXNG → Mwmbl → Bing chain)
./search.js "nodejs async await"
./search.js "nodejs async await" -n 15
./search.js "nodejs async await" --content

# A specific source
./search.js "react hooks" --source github
./search.js "transformer architecture" --source arxiv
./search.js "show hn" --source hn
./search.js "Alan Turing" --source wiki
./search.js "small web" --source marginalia

# All engines in parallel, deduped, re-ranked
./search.js "rust async" --source all

# HN time filter
./search.js "rust async" --source hn --freshness pw
```

### Options

- `-n <num>` — results per source (default 10, max 30)
- `--content` — fetch and include page markdown for the first few results
- `--freshness <period>` — `pd` | `pw` | `pm` | `py` (HN, GitHub)
- `--source <name>` — `web` (default chain) | `searx` | `mwmbl` | `bing` | `marginalia` | `github` | `arxiv` | `hn` | `wiki` | `all`

## Output

```
--- Result 1 ---
Title: Page Title
Link: https://example.com/page
Source: searxng(google,duckduckgo) (example.com › page)
Snippet: Description from search results

---
Primary engine: searxng:searx.gnous.eu
Warnings:
  - wiki: Wikipedia HTTP 429
```

`Source` is transparent about which engine answered. For SearXNG, the engines the instance used are listed in parentheses (`searxng(google,duckduckgo,brave)`). For `--source all`, multi-engine matches show as `searxng+mwmbl+bing` (higher confidence).

## License

MIT
