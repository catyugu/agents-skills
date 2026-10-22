---
name: skill-creator
description: Write new skills and rewrite existing ones so they trigger at the right moment and stay cheap to load. Use whenever the user wants a skill created, a checklist or workflow turned into one, an existing skill's description or structure improved, or a skill that never fires debugged — even if they never say the word "skill".
---

# Skill Creator

A skill is an instruction bundle loaded only when an agent recognizes the task it covers. Two pieces decide whether yours ever runs: the `description`, always in context, and the first screen of `SKILL.md`, in context once the skill triggers. Everything the agent reads is budget spent, so spend it on what it cannot infer.

The loop: capture the intent, interview for what is missing, write the draft, try it on real prompts, rewrite from what failed.

## Capture intent

The conversation often already holds the workflow. If the user says "turn this into a skill", mine the history first: what they asked for, the steps that worked, the corrections they made, the output shape they accepted. Ask only what the history cannot answer:

1. What should the agent be able to do after loading this?
2. What would the user say to trigger it?
3. What does a correct output look like — a file, a diff, a command, a fix?
4. Is the output verifiable (a transform, a schema, a failing test) or a matter of judgment (prose, design)?

Verifiable work deserves test cases; judgment calls rarely do. Say which one you think it is, but let the user decide.

## Interview, then search

Ask about edge cases, input formats, example files, and what "done" means. Check whether an MCP server or an existing skill already covers part of the job before writing instructions for it.

## Write the SKILL.md

Frontmatter takes `name` and `description`; `license`, `compatibility` and `metadata` are optional. Everything about *when to use it* belongs in the description, not the body: the agent reads the description before deciding to load anything else.

Make the description slightly pushy. Agents under-trigger skills far more often than they over-trigger them, so say what the skill does and then name the phrasings and contexts that should pull it in, including oblique ones.

## What goes in the body

- The workflow the agent cannot guess: order of operations, checks, the step it gets wrong by default.
- Nothing a competent agent already knows. "Read the file first" and "be careful" spend context and change no behavior.
- Keep the entry point under roughly 500 lines. Past that, move detail into `references/` files and say when to read each one, and put deterministic work in `scripts/` where executing is cheaper than describing.
- One skill, one job. Variants belong in `references/`, not in a body that argues with itself.

## Writing style

Write in the imperative and explain why a rule exists. An agent that understands the reason generalizes to the case you forgot; an agent following blind MUSTs does not. Every bullet should come from a failure you can name.

Show formats with short examples instead of describing them:

```markdown
## Commit message format
Input: added JWT login
Output: feat(auth): implement JWT-based login
```

Then reread the draft with fresh eyes and delete a third of it.

## Test on real prompts

Write two or three prompts the way a user would actually type them, show them to the user for sanity, then run them yourself with the skill available. Compare against the same prompts without it. The failures worth fixing are the run where the skill never triggered, and the run where it triggered and made the output worse.

## Validate and package

Run these from this directory:

```bash
uv run --project ~/.agents/python --locked python -m scripts.quick_validate ../my-skill
uv run --project ~/.agents/python --locked python -m scripts.package_skill ../my-skill
```

`quick_validate` checks the frontmatter against the spec: kebab-case `name`, description under 1024 characters, and no keys outside `name`, `description`, `license`, `compatibility`, `metadata`. `package_skill` writes a `.skill` zip beside the skill.

## What this copy leaves out

Upstream ships an evaluation harness — run logs, grader subagents, benchmark aggregation, a local review viewer, and a description optimizer that shells out to `claude -p`. All of it needs the Claude Code CLI, so this copy drops it. The cross-client path is the one above: write, run real prompts, revise.
