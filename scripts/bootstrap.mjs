#!/usr/bin/env node
// Rebuild the whole ~/.agents toolchain from the committed lockfiles.
//
// Everything installed here lands inside this repository. The only changes made
// outside it are the two that client integration needs:
//   - AGENTS_HOME, a user environment variable: mcp.json must stay home-agnostic,
//     and agent-sync resolves ${env:AGENTS_HOME} into this machine's absolute path
//     when it renders a client's config.
//   - $XDG_CONFIG_HOME/agent-sync/agent-sync.toml, a symlink onto this repo's copy,
//     because agent-sync keeps its own config out of the Commons.
import { spawnSync } from 'node:child_process'
import {
  appendFileSync,
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  realpathSync,
  symlinkSync,
  unlinkSync,
} from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const isWindows = process.platform === 'win32'

function quote(token) {
  return /[\s"]/.test(token) ? `"${token.replaceAll('"', '\\"')}"` : token
}

function run(command, args) {
  // Only pnpm needs a shell on Windows, for its PATHEXT lookup of pnpm.cmd; the
  // arguments never matter enough to risk, so build the line ourselves.
  const line = [command, ...args].map(quote).join(' ')
  console.log(`\n$ ${line}`)
  const result = spawnSync(line, {
    cwd: root,
    stdio: 'inherit',
    shell: true,
    env: { ...process.env, AGENTS_HOME: root },
  })
  if (result.status !== 0) {
    console.error(`\nfailed: ${line}`)
    process.exit(result.status ?? 1)
  }
}

function persistAgentsHome() {
  if (process.env.AGENTS_HOME === root) {
    console.log(`AGENTS_HOME already points at ${root}`)
    return
  }
  if (isWindows) {
    run('setx', ['AGENTS_HOME', root])
    console.log('AGENTS_HOME written to the user environment; open a new shell to see it.')
    return
  }
  const marker = '# dot-agents: mcp.json renders ${env:AGENTS_HOME}'
  for (const name of ['.profile', '.bashrc', '.zshrc']) {
    const rc = join(homedir(), name)
    if (!existsSync(rc)) continue
    if (readFileSync(rc, 'utf8').includes(marker)) {
      console.log(`${name} already sets AGENTS_HOME`)
      continue
    }
    appendFileSync(rc, `\n${marker}\nexport AGENTS_HOME="${root}"\n`)
    console.log(`${name}: AGENTS_HOME added`)
  }
}

function linkAgentSyncConfig() {
  const configHome = process.env.XDG_CONFIG_HOME || join(homedir(), '.config')
  const target = join(root, 'agent-sync.toml')
  const link = join(configHome, 'agent-sync', 'agent-sync.toml')
  mkdirSync(dirname(link), { recursive: true })

  if (existsSync(link) || isDanglingLink(link)) {
    if (!lstatSync(link).isSymbolicLink()) {
      console.log(`kept ${link}: a real file there is not ours to replace`)
      return
    }
    if (existsSync(link) && realpathSync(link) === realpathSync(target)) {
      console.log(`${link} already points at this repo`)
      return
    }
    unlinkSync(link)
  }

  try {
    symlinkSync(target, link, 'file')
    console.log(`linked ${link} -> ${target}`)
  } catch (error) {
    console.log(
      `could not link ${link}: ${error.message}\n` +
        'On Windows enable Developer Mode (Settings > System > For developers) and rerun.',
    )
  }
}

function isDanglingLink(path) {
  try {
    return lstatSync(path).isSymbolicLink()
  } catch {
    return false
  }
}

console.log(`dot-agents at ${root}`)
persistAgentsHome()
linkAgentSyncConfig()
run('pnpm', ['install', '--frozen-lockfile'])
run('uv', ['sync', '--project', 'python', '--locked'])
run('uv', ['run', '--project', 'python', '--locked', 'agent-sync', 'sync'])
run('uv', ['run', '--project', 'python', '--locked', 'agent-sync', 'doctor'])
