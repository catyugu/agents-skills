---
name: code-writing-and-style
description: Karpathy coding principles — think before coding, simplicity, surgical changes, goal-driven execution.
category: development
version: "1.4"
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
- You are about to add an abstraction, layer, or indirection and need the
  rules that say when it is not justified

For per-language naming/formatting/style conventions (C++, Python, Rust, Go, Java,
JS/TS, C#, C, Bash, Markdown), load the `language-style-guide` skill instead — this
skill is about how to approach writing code, not how a given language likes to be
formatted.

---

# Karpathy-style Coding Principles

Five behavioral guidelines to reduce common LLM coding mistakes. Tradeoff: bias
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

## 5. Abstraction Discipline

The concrete form of section 2. Apply it while writing, and again in the
simplification pass before calling the work done.

- Prefer the simplest implementation that makes the business logic explicit.
- Do not introduce abstractions for hypothetical future requirements.
- Do not introduce an interface unless there are multiple meaningful
  implementations or a real dependency boundary.
- Do not introduce factories, registries, managers, providers, or plugin
  systems without a concrete present need.
- Prefer plain functions and value types over class hierarchies.
- Prefer data tables over polymorphism when differences are primarily
  configuration.
- Keep core business decisions visible in control flow.
- Avoid pass-through layers.
- Every abstraction must justify what current complexity it removes.
- Prefer some duplication over a premature or incorrect abstraction.
- After implementation, perform a simplification pass and remove unnecessary
  abstractions.
