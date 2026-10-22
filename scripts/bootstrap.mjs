#!/usr/bin/env node
// Rebuild the whole ~/.agents toolchain from the committed lockfiles.
//
// Everything installed here lands inside this repository. The only change made
// outside it is the AGENTS_HOME user environment variable: mcp.json must stay
// home-agnostic, and agent-sync resolves ${env:AGENTS_HOME} into this machine's
// absolute path when it renders a client's config.
import { spawnSync } from 'node:child_process'
import { appendFileSync, existsSync, readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const isWindows = process.platform === 'win32'

function run(command, args, env = {}) {
  const line = [command, ...args].join(' ')
  console.log(`\n$ ${line}`)
  // Arguments here never contain spaces, so the shell only supplies the PATHEXT
  // lookup that pnpm.cmd needs on Windows.
  const result = spawnSync(command, args, {
    cwd: root,
    stdio: 'inherit',
    shell: true,
    env: { ...process.env, AGENTS_HOME: root, ...env },
  })
  if (result.status !== 0) {
    console.error(`\nfailed: ${line}`)
    process.exit(result.status ?? 1)
  }
}

function persistAgentsHome() {
  if (process.env.AGENTS_HOME === root) {
    console.log(`\nAGENTS_HOME already points at ${root}`)
    return
  }
  if (isWindows) {
    run('setx', ['AGENTS_HOME', root])
    console.log('AGENTS_HOME written to the user environment; open a new shell to see it.')
    return
  }
  const marker = '# dot-agents: mcp.json renders ${env:AGENTS_HOME}'
  const line = `export AGENTS_HOME="${root}"\n`
  for (const name of ['.profile', '.bashrc', '.zshrc']) {
    const rc = join(homedir(), name)
    if (!existsSync(rc)) continue
    const text = readFileSync(rc, 'utf8')
    if (text.includes(marker)) {
      console.log(`${name} already sets AGENTS_HOME`)
      continue
    }
    appendFileSync(rc, `\n${marker}\n${line}`)
    console.log(`${name}: AGENTS_HOME added`)
  }
}

console.log(`dot-agents at ${root}`)
persistAgentsHome()
run('pnpm', ['install', '--frozen-lockfile'])
run('uv', ['sync', '--project', 'python', '--locked'])
run('uv', ['run', '--project', 'python', '--locked', 'agent-sync', 'sync'])
run('uv', ['run', '--project', 'python', '--locked', 'agent-sync', 'doctor'])
