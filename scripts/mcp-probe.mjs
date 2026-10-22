#!/usr/bin/env node
// Start every MCP server declared in mcp.json and speak the stdio protocol to
// it: initialize, notifications/initialized, tools/list. One line per server.
//
// This proves the server starts from the committed lockfiles and answers the
// handshake. It calls no tool: chrome needs a running Chrome and zotero needs
// the Zotero desktop app, and neither is something pnpm or uv can install.
import { spawn } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const timeout = Number(process.env.MCP_PROBE_TIMEOUT_MS ?? 120_000)

function expand(value) {
  return value.replace(/\$\{env:([A-Z0-9_]+)\}/g, (_, name) => {
    const resolved = process.env[name] ?? (name === 'AGENTS_HOME' ? root : undefined)
    if (resolved === undefined) throw new Error(`${name} is not set`)
    return resolved
  })
}

function describe(error) {
  return error instanceof Error ? error.message : String(error)
}

async function probe(name, spec) {
  const child = spawn(expand(spec.command), (spec.args ?? []).map(expand), {
    cwd: root,
    env: {
      ...process.env,
      ...Object.fromEntries(Object.entries(spec.env ?? {}).map(([key, value]) => [key, expand(value)])),
    },
    stdio: ['pipe', 'pipe', 'pipe'],
  })

  const tail = []
  child.stderr.setEncoding('utf8')
  child.stderr.on('data', (chunk) => {
    for (const line of chunk.split('\n')) if (line.trim()) tail.push(line.trim())
    while (tail.length > 8) tail.shift()
  })

  const pending = new Map()
  let nextId = 1
  let buffer = ''
  let exitCode = null

  const settle = (error) => {
    for (const { reject } of pending.values()) reject(error)
    pending.clear()
  }

  const exited = new Promise((resolve) => {
    const finish = (code) => {
      if (exitCode !== null) return
      exitCode = code
      resolve()
    }
    child.on('exit', (code) => {
      settle(new Error(`server exited with code ${code} before answering`))
      finish(code)
    })
    // A spawn that never started the process reports 'error' and no 'exit'.
    child.on('error', (error) => {
      settle(error)
      finish(-1)
    })
  })
  child.stdout.setEncoding('utf8')
  child.stdout.on('data', (chunk) => {
    buffer += chunk
    let end
    while ((end = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, end).trim()
      buffer = buffer.slice(end + 1)
      if (!line) continue
      let message
      try {
        message = JSON.parse(line)
      } catch {
        continue // server logging on stdout, not a protocol frame
      }
      const waiter = pending.get(message.id)
      if (!waiter) continue
      pending.delete(message.id)
      if (message.error) waiter.reject(new Error(`${message.error.code}: ${message.error.message}`))
      else waiter.resolve(message.result)
    }
  })

  const request = (method, params) =>
    new Promise((resolve, reject) => {
      const id = nextId++
      pending.set(id, { resolve, reject })
      try {
        child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`)
      } catch (error) {
        pending.delete(id)
        reject(error)
      }
    })

  // Closing stdin is how an MCP server is told to stop; a server that ignores
  // EOF gets killed, and the exit is awaited either way.
  const stop = async () => {
    if (exitCode !== null) return
    child.stdin.end()
    const kill = setTimeout(() => child.kill(), 3000)
    await exited
    clearTimeout(kill)
  }

  const timer = setTimeout(() => settle(new Error(`no answer within ${timeout} ms`)), timeout)
  try {
    const server = await request('initialize', {
      protocolVersion: '2025-06-18',
      capabilities: {},
      clientInfo: { name: 'dot-agents probe', version: '0' },
    })
    child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' })}\n`)
    const list = await request('tools/list', {})
    const tools = Array.isArray(list?.tools) ? list.tools : []
    if (tools.length === 0) throw new Error('tools/list returned no tools')
    return `${name}: ${tools.length} tools, ${server?.serverInfo?.name ?? 'unnamed'} ${server?.serverInfo?.version ?? ''}`.trim()
  } catch (error) {
    throw new Error(`${name}: ${describe(error)}${tail.length ? `\n     ${tail.join('\n     ')}` : ''}`)
  } finally {
    clearTimeout(timer)
    await stop()
  }
}

const { mcpServers = {} } = JSON.parse(readFileSync(join(root, 'mcp.json'), 'utf8'))
const names = Object.keys(mcpServers)
if (names.length === 0) {
  console.error('mcp.json declares no servers')
  process.exit(1)
}

let failed = 0
for (const name of names) {
  try {
    console.log(`ok   ${await probe(name, mcpServers[name])}`)
  } catch (error) {
    failed += 1
    console.error(`FAIL ${describe(error)}`)
  }
}

if (failed > 0) {
  console.error(`\n${failed} of ${names.length} MCP server(s) failed the handshake`)
  process.exit(1)
}
console.log(`\n${names.length} MCP server(s) started and answered initialize + tools/list`)
