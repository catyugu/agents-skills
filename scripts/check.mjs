#!/usr/bin/env node
// Repo-specific invariants that no upstream tool checks for us:
//   - skills/ holds one directory per skill, directory name == frontmatter name
//   - mcp.json stays portable: no machine paths, only ${env:AGENTS_HOME}
//   - the Codex tweaks keep the browser approval prompt on
//   - a synced client config matches all of the above (when this machine has one)
//
// Drift between the Commons and a client is `node scripts/agents.mjs status`'s
// job, and the MCP protocol is `scripts/mcp_probe.py`'s. This file only asserts
// what neither of them can see.
import { existsSync, lstatSync, readdirSync, readFileSync, realpathSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const failures = []

const fail = (message) => {
  failures.push(message)
  console.log(`FAIL ${message}`)
}
const pass = (message) => console.log(`ok   ${message}`)
// Windows paths compare case-insensitively; two spellings of one directory are
// not a difference worth failing on.
const samePath = (value) => (process.platform === 'win32' ? value.toLowerCase() : value)

console.log(`dot-agents at ${root}`)
console.log(`platform ${process.platform}, node ${process.version}`)

const frontmatterValue = (text, key) => {
  const match = new RegExp(`^${key}:[ \\t]*(.+)$`, 'm').exec(text)
  return match ? match[1].trim().replace(/^(['"])(.*)\1$/, '$2') : null
}

// 1. One directory per skill, at the depth every client scans.
const skillsRoot = join(root, 'skills')
const skillNames = new Map()
let before = failures.length
for (const entry of readdirSync(skillsRoot, { withFileTypes: true })) {
  const label = `skills/${entry.name}`
  if (!entry.isDirectory()) {
    fail(`${label}: not a skill directory (skills/ holds one directory per skill)`)
    continue
  }
  const file = join(skillsRoot, entry.name, 'SKILL.md')
  if (!existsSync(file)) {
    fail(`${label}: no SKILL.md (nested categories are invisible to some clients)`)
    continue
  }
  const text = readFileSync(file, 'utf8')
  if (text.includes('\r')) fail(`${label}: SKILL.md has CRLF line endings`)
  const name = frontmatterValue(text, 'name')
  const description = frontmatterValue(text, 'description')
  if (!name) fail(`${label}: frontmatter has no name`)
  if (!description) fail(`${label}: frontmatter has no description`)
  if (name && name !== entry.name) fail(`${label}: frontmatter name is "${name}"`)
  if (name) {
    if (skillNames.has(name)) fail(`${label}: skill name "${name}" is also used by ${skillNames.get(name)}`)
    else skillNames.set(name, label)
  }
}
if (failures.length === before) pass(`skills: ${skillNames.size} skills, one directory each, name matches the directory`)

// 2. mcp.json stays portable: placeholders resolve from the wrapper, never a path.
before = failures.length
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
if (failures.length === before) {
  pass(`mcp.json: ${servers.length} servers, no machine path, placeholders ${[...suppliedByWrapper].join(', ')}`)
}

// 3. Codex reads `approval_mode = "approve"` as "already approved" and never
// prompts, which is the opposite of asking before every browser action.
before = failures.length
const tweaksPath = join(root, '.config', 'agent-sync', 'agent-sync.toml')
const preApproved = /approval_mode[ \t]*=[ \t]*"approve"/
if (preApproved.test(readFileSync(tweaksPath, 'utf8'))) {
  fail('.config/agent-sync/agent-sync.toml marks a tool "approve": Codex never prompts for it (use "prompt")')
}
if (failures.length === before) pass('agent-sync.toml: no tool is pre-approved')

// The rendered server, not the tweaks file, is what Codex actually reads.
const section = (text, header) => {
  const lines = text.split('\n')
  const start = lines.indexOf(header)
  if (start < 0) return null
  const end = lines.findIndex((line, index) => index > start && line.startsWith('['))
  return lines.slice(start + 1, end < 0 ? lines.length : end).join('\n')
}

// 4. The rendered client config, when this machine has one.
before = failures.length
const clientDir = join(homedir(), '.codex')
const configPath = join(clientDir, 'config.toml')
if (!existsSync(configPath)) {
  console.log('skip rendered config: no ~/.codex/config.toml (run node scripts/agents.mjs sync first)')
} else {
  const rendered = readFileSync(configPath, 'utf8')
  if (rendered.includes('${env:')) fail('rendered config still holds an unexpanded ${env:} placeholder')
  if (preApproved.test(rendered)) fail('rendered config marks a tool "approve": Codex never prompts for it')

  for (const name of servers) {
    const body = section(rendered, `[mcp_servers.${name}]`)
    if (body === null) {
      fail(`rendered config has no [mcp_servers.${name}]`)
      continue
    }
    if (JSON.stringify(mcp.mcpServers[name]).includes('${env:AGENTS_HOME}') && !samePath(body).includes(samePath(root))) {
      fail(`rendered [mcp_servers.${name}] does not point into ${root}`)
    }
  }

  const chrome = section(rendered, '[mcp_servers.chrome]')
  const browserGuard = 'default_tools_approval_mode = "prompt"'
  if (chrome !== null && !chrome.includes(browserGuard)) {
    fail(`rendered [mcp_servers.chrome] does not set ${browserGuard}`)
  }

  const instructionLink = join(clientDir, 'AGENTS.md')
  if (!existsSync(instructionLink)) {
    fail('~/.codex/AGENTS.md is missing')
  } else if (!lstatSync(instructionLink).isSymbolicLink()) {
    fail('~/.codex/AGENTS.md is not a symlink')
  } else if (samePath(realpathSync(instructionLink)) !== samePath(realpathSync(join(root, 'AGENTS.md')))) {
    fail('~/.codex/AGENTS.md does not point at this repo')
  }
}
if (failures.length === before) {
  pass('client: servers rendered into this repo, browser approval prompt on, instructions linked')
}

if (failures.length > 0) {
  console.log(`\n${failures.length} check(s) failed`)
  process.exit(1)
}
console.log('\nall checks passed')
