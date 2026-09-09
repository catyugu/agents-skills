---
name: code-writing-and-style
description: Karpathy coding principles — think before coding, simplicity, surgical changes, goal-driven execution.
category: development
version: "1.3"
author: "Agnes"
license: "MIT"
metadata:
  hermes:
    tags: [coding, style, guidelines, karpathy, principles]
    related_skills: [language-style-guide]
---

## When to Use

Load this skill when:
- Writing code in any language and you want to apply Karpathy-style behavioral
  principles (think first, simplicity, surgical changes, goal-driven work)
- Reviewing your own or others' code for these meta-level habits
- You need the mindset principles that apply regardless of language

For per-language naming/formatting/style conventions (C++, Python, Rust, Go, Java,
JS/TS, C#, C, Bash, Markdown), load the `language-style-guide` skill instead — this
skill is about how to approach writing code, not how a given language likes to be
formatted.

---

# Karpathy-style Coding Principles

Four behavioral guidelines to reduce common LLM coding mistakes. Tradeoff: bias
toward caution over speed.

## 1. Think Before Coding
- Don't assume. Don't hide confusion. Surface tradeoffs.
- State assumptions explicitly before implementing.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First
- Minimum code that solves the problem. Nothing speculative.
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.
- Ask: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes
- Touch only what you must. Clean up only your own mess.
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.
- When your changes create orphans: remove imports/variables/functions that YOUR
  changes made unused.
- The test: every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution
- Define success criteria. Loop until verified.
- Transform tasks into verifiable goals:
  - "Add validation" → "Write tests for invalid inputs, then make them pass"
  - "Fix the bug" → "Write a test that reproduces it, then make it pass"
  - "Refactor X" → "Ensure tests pass before and after"
- For multi-step tasks, state a brief plan with verification points.
