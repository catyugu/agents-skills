#!/usr/bin/env node
// What a fresh clone must satisfy on every platform, run identically on Windows,
// macOS and Linux so CI can compare the three.
//
// Part 1 is repo invariants: a skills/ layout every client can discover, skill
// frontmatter an agent can route on, an mcp.json that never carries a machine
// path, and a tweaks file that does not quietly disable an approval prompt.
// Part 2 checks the result of a real `node scripts/agents.mjs sync` when a
// client config exists; a machine without one skips it instead of failing.
import { existsSync, lstatSync, readdirSync, readFileSync, realpathSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const failures = []
// Windows paths compare case-insensitively; the same directory reached through
// two spellings is not a difference worth failing on.
const samePath = (value) => (process.platform === 'win32' ? value.toLowerCase() : value)

const fail = (message) => {
  failures.push(message)
  console.log(`FAIL ${message}`)
}
const pass = (message) => console.log(`ok   ${message}`)

console.log(`dot-agents at ${root}`)
console.log(`platform ${process.platform}, node ${process.version}`)

// Plain `key: value` pairs at the top level, plus their indented blocks (a
// `metadata:` mapping is the only nesting any skill uses). Refusing anything
// else keeps this parser honest instead of half-implementing YAML.
function parseFrontmatter(text, label) {
  const lines = text.replaceAll('\r\n', '\n').split('\n')
  if (lines[0].trim() !== '---') {
    fail(`${label}: SKILL.md does not open with ---`)
    return null
  }
  const end = lines.indexOf('---', 1)
  if (end < 0) {
    fail(`${label}: SKILL.md frontmatter is not closed with ---`)
    return null
  }
  const fields = new Map()
  let openBlock = null
  for (const line of lines.slice(1, end)) {
    if (line.trim() === '') continue
    if (/^[ \t]/.test(line)) {
      if (openBlock === null) fail(`${label}: indented frontmatter line outside a block: ${line.trim()}`)
      continue
    }
    const pair = /^([A-Za-z][\w-]*):[ \t]*(.*)$/.exec(line)
    openBlock = null
    if (!pair) {
      fail(`${label}: frontmatter line is not a key: value pair: ${line}`)
      continue
    }
    const [, key, raw] = pair
    if (fields.has(key)) fail(`${label}: frontmatter repeats ${key}`)
    fields.set(key, raw.trim().replace(/^(['"])(.*)\1$/, '$2'))
    if (raw.trim() === '') openBlock = key
  }
  return fields
}

// 1. One directory per skill. A category directory around a skill hides it from
// clients that scan a single level, and it makes agent-sync's sync unit
// (skills/<first level>) disagree with the skill unit.
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
  const fields = parseFrontmatter(text, label)
  if (!fields) continue
  for (const key of ['name', 'description']) {
    if (!fields.get(key)) fail(`${label}: frontmatter has no ${key}`)
  }
  const name = fields.get('name')
  if (name && name !== entry.name) {
    fail(`${label}: frontmatter name is "${name}", directory is "${entry.name}"`)
  }
  if (name) {
    if (skillNames.has(name)) fail(`${label}: skill name "${name}" is also used by ${skillNames.get(name)}`)
    else skillNames.set(name, label)
  }
}
if (failures.length === before) pass(`skills: ${skillNames.size} skills, one directory each, name matches the directory`)

before = failures.length
const skillIndex = readFileSync(join(root, 'docs', 'skills.md'), 'utf8')
for (const name of skillNames.keys()) {
  if (!skillIndex.includes(`\`${name}\``)) fail(`docs/skills.md has no row for skill "${name}"`)
}
if (failures.length === before) pass('docs/skills.md: every skill appears in the index')

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
if (failures.length === before) pass('agent-sync.toml: no tool is pre-approved, so the browser guard stays on')

// 4. The rendered client config, when this machine has one.
const clientDir = join(homedir(), '.codex')
const configPath = join(clientDir, 'config.toml')
if (!existsSync(configPath)) {
  console.log('skip rendered config: no ~/.codex/config.toml (run node scripts/agents.mjs sync first)')
} else {
  const rendered = readFileSync(configPath, 'utf8')
  if (rendered.includes('${env:')) fail('rendered config still holds an unexpanded ${env:} placeholder')
  if (preApproved.test(rendered)) fail('rendered config marks a tool "approve": Codex never prompts for it')
  for (const name of servers) {
    if (!rendered.includes(`[mcp_servers.${name}]`)) fail(`rendered config has no mcp_servers.${name}`)
  }
  if (!samePath(rendered).includes(samePath(root))) fail(`rendered config does not resolve paths into ${root}`)

  const instructionLink = join(clientDir, 'AGENTS.md')
  if (!existsSync(instructionLink)) {
    fail('~/.codex/AGENTS.md is missing')
  } else if (!lstatSync(instructionLink).isSymbolicLink()) {
    fail('~/.codex/AGENTS.md is not a symlink')
  } else if (samePath(realpathSync(instructionLink)) !== samePath(realpathSync(join(root, 'AGENTS.md')))) {
    fail('~/.codex/AGENTS.md does not point at this repo')
  }
  pass(`client: ${servers.length} servers rendered, instructions linked into the repo`)
}

if (failures.length > 0) {
  console.log(`\n${failures.length} check(s) failed`)
  process.exit(1)
}
console.log('\nall checks passed')
