---
name: dot-agents
description: Maintain ~/.agents, the one Git-versioned copy of skills, MCP servers and shared instructions that agent-sync fans out to Codex, Claude Code, Gemini CLI and other clients. Use whenever the task touches that environment — writing or editing a skill there, adding or removing an MCP server, syncing changes to clients, explaining "everything is in sync" or a failed sync, or rebuilding the toolchain on a new machine — even when the user only names a skill or a server and never mentions the repo.
---

# dot-agents

`~/.agents` is the single copy every agent reads from. Clients hold links or merged config, never originals, so editing a client directory is always a mistake: the next `sync` overwrites it, and `status` reports drift until it is undone.

## The loop every change goes through

Edit files in the repo, then:

```bash
node scripts/check.mjs        # repo invariants: layout, frontmatter, mcp.json, approval policy
node scripts/agents.mjs sync  # rebuild the skills view, then fan out to clients
node scripts/agents.mjs status  # 0 clean, 2 drift remains, 1 error
```

`skills.mjs` on its own rebuilds or checks the view without touching any client:

```bash
node scripts/skills.mjs           # library/ -> skills/, the flat view clients scan
node scripts/skills.mjs --check   # 0 clean, 2 stale, 1 error (never repairs)
```

Adding or changing an MCP server also needs a handshake, because a rendered config proves nothing about whether the server starts:

```bash
uv run --project python --locked python scripts/mcp_probe.py
```

`bootstrap.mjs` does all of it plus the dependency install on a new machine. `doctor` reports what this machine has, and `mcp list` shows each server's state per client.

## What lives where

- `library/<category>/<name>/SKILL.md` — the only place a skill exists, at whatever category depth reads well. The directory name must equal the frontmatter `name`, and names are unique repo-wide.
- `skills/<name>/` — the flat view clients scan: one directory link per skill, generated from `library/` by `scripts/skills.mjs`. Never edit it by hand and never commit it; some clients stop at that single level, which is why the view is flat while the library is not.
- `mcp.json` — the only place a server is declared, in the standard `mcpServers` shape. Paths use `${env:AGENTS_HOME}`, which `scripts/agents.mjs` expands at sync time, so nothing carries a machine path.
- `AGENTS.md` — cross-project preferences, distributed to every client. Repo maintenance rules belong in `README.md`; writing them here makes every other project read them too.
- `package.json` and `python/pyproject.toml` — dependencies, with their lockfiles committed. Install tools there and call them through `pnpm exec` or `uv run --project python --locked`, rather than `npm i -g`, `pip install`, or an unpinned `npx` / `uvx`.

## Adding a server

Add the dependency first — `pnpm add -D <pkg>` or `uv add --project python <pkg>` — then write the entry: `command: node` with `${env:AGENTS_HOME}/node_modules/<pkg>/<entry>` for JavaScript, or `command: uv` with `["run", "--project", "${env:AGENTS_HOME}/python", "--locked", "<executable>"]` for Python. Secrets arrive as `${env:VAR}`; tokens never enter the repo.

Client differences such as timeouts or approval policy go in `.config/agent-sync/agent-sync.toml` under `[mcp.<name>.tweaks.<agent>]`. Keep the approval direction straight: `prompt` asks every time, `approve` never asks.

## Removing a server

`node scripts/agents.mjs mcp remove <name>` clears the definition, its tweaks and every client's config in one step. Editing a server out of `mcp.json` by hand leaves orphan entries in clients, because agent-sync does not record which entries it once created. Reach for `mcp disable <name>` when it should come back later.

## When something looks wrong

- `status` reports drift that `sync` does not clear: another writer in the client config conflicts with the rendered section. `doctor` names it.
- `doctor` warns that `AGENT_SYNC_HOME` and the client's read path differ: the repo is reached through a symlink, as in a dotfiles layout. Same files, different spelling of the path; harmless.
- `sync` cannot create its client symlinks: on Windows that needs Developer Mode or an elevated shell. The skills view is unaffected — it uses junctions.
- A client cannot see a skill: run `node scripts/skills.mjs --check`. A stale view names the missing link; an entry that is not a link means a name collision, which the run reports instead of overwriting.

## Boundaries

This repo keeps the configuration and the toolchain reproducible. It does not install COMSOL, TeX or any other professional software a skill may drive.
