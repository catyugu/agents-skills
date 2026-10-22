#!/usr/bin/env node
// Run agent-sync with everything it needs resolved inside this repo.
//
// agent-sync expands ${env:AGENTS_HOME} in mcp.json only at sync time, and it
// keeps its own config at $XDG_CONFIG_HOME/agent-sync/. Both variables are set
// here, for this process tree only, so the machine keeps no agent-tooling state
// outside ~/.agents:
//   AGENTS_HOME     -> this repo, resolved into client configs at sync
//   XDG_CONFIG_HOME -> <repo>/.config, where the tweaks file lives
//   AGENT_SYNC_HOME -> this repo as the Commons, so nothing else is synced
//
// Usage: node scripts/agents.mjs [agent-sync args...]   (default: sync)
import { spawnSync } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const [command = 'sync', ...rest] = process.argv.slice(2)
const args = ['run', '--project', join(root, 'python'), '--locked', 'agent-sync', command, ...rest]

console.log(`$ agent-sync ${[command, ...rest].join(' ')}`)
const result = spawnSync('uv', args, {
  cwd: root,
  stdio: 'inherit',
  env: {
    ...process.env,
    AGENTS_HOME: root,
    AGENT_SYNC_HOME: root,
    XDG_CONFIG_HOME: join(root, '.config'),
  },
})
process.exit(result.status ?? 1)
