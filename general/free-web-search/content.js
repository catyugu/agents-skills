#!/usr/bin/env node
// Fetch a URL and extract its readable content as markdown.
// Uses Readability + jsdom + turndown. No API keys required.

import { Readability } from "@mozilla/readability";
import { JSDOM } from "jsdom";
import TurndownService from "turndown";
import { gfm } from "turndown-plugin-gfm";

const url = process.argv[2];

if (!url) {
	console.log("Usage: content.js <url>");
	console.log("");
	console.log("Extracts readable content from a webpage as markdown.");
	console.log("");
	console.log("Examples:");
	console.log("  content.js https://example.com/article");
	console.log("  content.js https://en.wikipedia.org/wiki/Alan_Turing");
	process.exit(1);
}

const BROWSER_UA =
	"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

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

try {
	const response = await fetch(url, {
		headers: {
			"User-Agent": BROWSER_UA,
			"Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
			"Accept-Language": "en-US,en;q=0.9",
		},
		signal: AbortSignal.timeout(20000),
		redirect: "follow",
	});

	if (!response.ok) {
		console.error(`HTTP ${response.status}: ${response.statusText}`);
		process.exit(1);
	}

	const html = await response.text();
	const dom = new JSDOM(html, { url });
	const article = new Readability(dom.window.document).parse();

	if (article && article.content) {
		if (article.title) console.log(`# ${article.title}\n`);
		if (article.byline) console.log(`*${article.byline}*\n`);
		console.log(htmlToMarkdown(article.content));
		process.exit(0);
	}

	// Fallback
	const doc = new JSDOM(html, { url });
	const body = doc.window.document;
	body.querySelectorAll("script, style, noscript, nav, header, footer, aside").forEach((el) => el.remove());
	const title = body.querySelector("title")?.textContent?.trim();
	const main = body.querySelector("main, article, [role='main'], .content, #content") || body.body;
	if (title) console.log(`# ${title}\n`);
	const text = main?.innerHTML || "";
	if (text.trim().length > 100) {
		console.log(htmlToMarkdown(text));
	} else {
		console.error("Could not extract readable content from this page.");
		process.exit(1);
	}
} catch (e) {
	console.error(`Error: ${e.message}`);
	process.exit(1);
}