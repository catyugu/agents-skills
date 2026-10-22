#!/usr/bin/env node
// What a fresh clone must satisfy on every platform, run identically on Windows,
// macOS and Linux so CI can compare the three.
//
// Part 1 is repo invariants: skill frontmatter an agent can route on, and an
// mcp.json that never carries a machine path. Part 2 checks the result of a real
// `node scripts/agents.mjs sync` when a client config exists; a machine without
// one skips it instead of failing.
import { existsSync, lstatSync, readdirSync, readFileSync, realpathSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const failures = []

const fail = (message) => {
  failures.push(message)
  console.log(`FAIL ${message}`)
}
const pass = (message) => console.log(`ok   ${message}`)

console.log(`dot-agents at ${root}`)
console.log(`platform ${process.platform}, node ${process.version}`)

function* skillFiles(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) yield* skillFiles(full)
    else if (entry.name === 'SKILL.md') yield full
  }
}

// 1. Every skill carries the frontmatter agent-sync and the clients route on.
const skills = [...skillFiles(join(root, 'skills'))]
for (const file of skills) {
  const name = relative(join(root, 'skills'), dirname(file)).split(sep).join('/')
  const text = readFileSync(file, 'utf8')
  if (text.includes('\r')) fail(`${name}: SKILL.md has CRLF line endings`)
  const frontmatter = /^---\r?\n([\s\S]*?)\r?\n---/.exec(text)
  if (!frontmatter) {
    fail(`${name}: SKILL.md has no frontmatter`)
    continue
  }
  for (const key of ['name', 'description']) {
    if (!new RegExp(`^${key}:`, 'm').test(frontmatter[1])) {
      fail(`${name}: frontmatter has no ${key}`)
    }
  }
}
pass(`skills: ${skills.length} SKILL.md files carry name and description`)

// 2. mcp.json stays portable: placeholders resolve from the wrapper, never a path.
const mcpRaw = readFileSync(join(root, 'mcp.json'), 'utf8')
const mcp = JSON.parse(mcpRaw)
const servers = Object.keys(mcp.mcpServers ?? {})
if (servers.length === 0) fail('mcp.json declares no servers')

const suppliedByWrapper = new Set(['AGENTS_HOME'])
for (const [, variable] of mcpRaw.matchAll(/\$\{env:([A-Z0-9_]+)\}/g)) {
  if (!suppliedByWrapper.has(variable)) {
    fail(`mcp.json uses ${variable}, which scripts/agents.mjs does not set`)
  }
}
for (const pattern of [/\b[A-Za-z]:[\\/]/, /\/home\//, /\/Users\//]) {
  if (pattern.test(mcpRaw)) fail(`mcp.json carries a machine path (${pattern})`)
}
for (const name of servers) {
  const server = mcp.mcpServers[name]
  if (typeof server.command !== 'string' && typeof server.url !== 'string') {
    fail(`mcp.json server ${name} has neither command nor url`)
  }
}
pass(`mcp.json: ${servers.length} servers, no machine path, placeholders ${[...suppliedByWrapper].join(', ')}`)

// 3. The rendered client config, when this machine has one.
const clientDir = join(homedir(), '.codex')
const configPath = join(clientDir, 'config.toml')
if (!existsSync(configPath)) {
  console.log('skip rendered config: no ~/.codex/config.toml (run node scripts/agents.mjs sync first)')
} else {
  const rendered = readFileSync(configPath, 'utf8')
  if (rendered.includes('${env:')) fail('rendered config still holds an unexpanded ${env:} placeholder')
  for (const name of servers) {
    if (!rendered.includes(`[mcp_servers.${name}]`)) fail(`rendered config has no mcp_servers.${name}`)
  }
  if (!rendered.includes(root)) fail(`rendered config does not resolve paths into ${root}`)

  const instructionLink = join(clientDir, 'AGENTS.md')
  if (!existsSync(instructionLink)) {
    fail('~/.codex/AGENTS.md is missing')
  } else if (!lstatSync(instructionLink).isSymbolicLink()) {
    fail('~/.codex/AGENTS.md is not a symlink')
  } else if (realpathSync(instructionLink) !== realpathSync(join(root, 'AGENTS.md'))) {
    fail('~/.codex/AGENTS.md does not point at this repo')
  }
  pass(`client: ${servers.length} servers rendered, instructions linked into the repo`)
}

if (failures.length > 0) {
  console.log(`\n${failures.length} check(s) failed`)
  process.exit(1)
}
console.log('\nall checks passed')
