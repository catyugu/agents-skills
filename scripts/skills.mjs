#!/usr/bin/env node
// Generate `skills/` from `library/`: the flat view every client scans.
//
// A client discovers a skill at `skills/<name>/SKILL.md`, and some of them stop
// at that depth, so a category directory hides everything beneath it. The
// categories therefore live in `library/`, one directory per skill at whatever
// depth reads well, and every skill appears in `skills/` as a directory link
// into it. One editable copy, no duplication, and a view that stays one level
// deep. The view is generated, so it is not committed.
//
// Usage:
//   node scripts/skills.mjs           create or repair the view
//   node scripts/skills.mjs --check   report drift only, exit 2, change nothing
import {
  existsSync,
  lstatSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  readlinkSync,
  realpathSync,
  rmdirSync,
  symlinkSync,
  unlinkSync,
} from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const library = join(root, 'library')
const view = join(root, 'skills')
const reportOnly = process.argv.includes('--check')
const win32 = process.platform === 'win32'

const failures = []
const actions = []
const label = (path) => relative(root, path).replaceAll('\\', '/')

// Frontmatter `name`, read the same way check.mjs reads it.
function skillName(file) {
  const match = /^name:[ \t]*(.+)$/m.exec(readFileSync(file, 'utf8'))
  return match?.[1].trim().replace(/^(['"])(.*)\1$/, '$2')
}

// A skill is the first directory down a branch that holds a SKILL.md; whatever
// sits beneath it belongs to that skill, not to the catalogue.
function discover(directory, found = []) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (!entry.isDirectory() || entry.name.startsWith('.')) continue
    const path = join(directory, entry.name)
    const file = join(path, 'SKILL.md')
    if (existsSync(file)) {
      const name = skillName(file)
      if (name !== entry.name) {
        failures.push(`${label(path)}: frontmatter name is "${name}", the directory says "${entry.name}"`)
      } else {
        found.push({ name, path })
      }
      continue
    }
    discover(path, found)
  }
  return found
}

// Reads a link as it actually resolves; a broken one falls back to its raw
// target so the view can still tell a stale skill link from a foreign entry.
function linkTarget(path) {
  try {
    if (!lstatSync(path).isSymbolicLink()) return null
  } catch {
    return null
  }
  try {
    return realpathSync(path)
  } catch {
    return readlinkSync(path)
  }
}

const sameTarget = (a, b) => (win32 ? a.toLowerCase() === b.toLowerCase() : a === b)
const insideLibrary = (target) => sameTarget(target.slice(0, libraryReal.length + 1), libraryReal + sep)

// On Windows a recursive delete follows a junction into the skill it points at
// and takes the library copy with it. Neither of these does.
function removeLink(path) {
  try {
    unlinkSync(path)
  } catch {
    rmdirSync(path)
  }
}

function createLink(source, path) {
  const target = realpathSync(source)
  mkdirSync(dirname(path), { recursive: true })
  if (win32) symlinkSync(target, path, 'junction')
  else symlinkSync(relative(dirname(path), target), path, 'dir')
}

if (!existsSync(library)) {
  console.error('library/ is missing: it is the only place a skill lives')
  process.exit(1)
}

const libraryReal = realpathSync(library)
const skills = new Map()
for (const skill of discover(library)) {
  const previous = skills.get(skill.name)
  if (previous) failures.push(`"${skill.name}" is defined twice: ${label(previous.path)} and ${label(skill.path)}`)
  else skills.set(skill.name, skill)
}

if (failures.length > 0) {
  for (const message of failures) console.error(`fail ${message}`)
  console.error(`\n${failures.length} problem(s) in library/; the view was left alone`)
  process.exit(1)
}

// Reconcile the view against the library. A link that can be read but does not
// resolve into library/ is somebody else's entry, or a hand-made one: name a
// skill after it and the run fails instead of deleting it.
const pending = new Map([...skills].map(([name, skill]) => [name, realpathSync(skill.path)]))
const seen = existsSync(view) ? readdirSync(view, { withFileTypes: true }).map((entry) => entry.name) : []

for (const name of seen) {
  const path = join(view, name)
  const target = linkTarget(path)
  const expected = pending.get(name)

  if (target === null) {
    if (expected !== undefined) failures.push(`${label(path)} is not a link: remove it, or rename the skill`)
    continue
  }
  if (!insideLibrary(target)) {
    if (expected !== undefined) failures.push(`${label(path)} links outside library/: remove it, or rename the skill`)
    continue
  }
  if (expected === undefined) {
    actions.push({ kind: 'remove', name })
  } else if (sameTarget(target, expected)) {
    pending.delete(name)
  } else {
    actions.push({ kind: 'relink', name, target: skills.get(name).path })
    pending.delete(name)
  }
}

for (const [name, target] of pending) actions.push({ kind: 'create', name, target: skills.get(name).path })

if (failures.length > 0) {
  for (const message of failures) console.error(`fail ${message}`)
  console.error(`\n${failures.length} problem(s) in the view; nothing was changed`)
  process.exit(1)
}

const describe = (action) =>
  action.kind === 'remove'
    ? `remove skills/${action.name}: the skill is gone or moved`
    : `${action.kind} skills/${action.name} -> ${label(action.target)}`

if (actions.length === 0) {
  console.log(`skills: ${skills.size} skills under library/, the view matches`)
  process.exit(0)
}

if (reportOnly) {
  for (const action of actions) console.log(`drift ${describe(action)}`)
  console.error(`\n${actions.length} drift(s): run node scripts/skills.mjs`)
  process.exit(2)
}

for (const action of actions) {
  const path = join(view, action.name)
  if (action.kind !== 'create') removeLink(path)
  if (action.kind !== 'remove') createLink(action.target, path)
  console.log(describe(action))
}
console.log(`\nskills: ${skills.size} skills under library/, the view rebuilt`)
