#!/usr/bin/env node
// Rebuild the whole ~/.agents toolchain from the committed lockfiles.
//
// Everything this installs lands inside this repository, and nothing is written
// into the machine's environment: scripts/agents.mjs supplies AGENTS_HOME and
// XDG_CONFIG_HOME to agent-sync alone, and agent-sync renders each client's own
// config from mcp.json. That per-client config is the only state outside the
// repo, and `agent-sync revert <agent>` removes it.
import { spawnSync } from 'node:child_process'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))

function quote(token) {
  return /[\s"]/.test(token) ? `"${token.replaceAll('"', '\\"')}"` : token
}

function run(command, args) {
  // Only pnpm needs a shell on Windows, for its PATHEXT lookup of pnpm.cmd.
  const line = [command, ...args].map(quote).join(' ')
  console.log(`\n$ ${line}`)
  const result = spawnSync(line, { cwd: root, stdio: 'inherit', shell: true, env: process.env })
  if (result.status !== 0) {
    console.error(`\nfailed: ${line}`)
    process.exit(result.status ?? 1)
  }
}

console.log(`dot-agents at ${root}`)
run('pnpm', ['install', '--frozen-lockfile'])
run('uv', ['sync', '--project', 'python', '--locked'])
run('node', ['scripts/agents.mjs', 'sync'])
run('node', ['scripts/agents.mjs', 'doctor'])
