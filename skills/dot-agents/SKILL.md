---
name: dot-agents
description: Maintain ~/.agents, the Git-versioned single copy of skills, MCP servers and shared instructions that agent-sync fans out to Codex, Claude Code, Gemini CLI and other clients. Use when adding, editing or deleting a skill or MCP server, syncing the Commons to clients, diagnosing drift or a failed sync, or rebuilding the toolchain on a new machine.
---

# dot-agents

`~/.agents` 是所有 Agent 的技能与 MCP server 的唯一副本：改这里，再同步到各客户端；不要在客户端目录里手改副本。

## 心智模型

- 事实来源是仓库里的 `skills/<name>/`、`mcp.json`、`AGENTS.md`。
- `agent-sync` 把能符号链接的链接到客户端，MCP 和 hooks 按键合并进客户端自己的配置文件，客户端里其他键原样保留。
- 依赖由 `pnpm-lock.yaml` 与 `python/uv.lock` 固定，重建环境靠锁文件而不是重装工具。
- 客户端里由 `sync` 生成的部分不要手改，下一次 `sync` 会覆盖。

## 常用命令

| 目的 | 命令 |
| --- | --- |
| 分发到各客户端 | `node scripts/agents.mjs sync` |
| 看漂移（0 干净 / 2 有待处理 / 1 出错） | `node scripts/agents.mjs status` |
| 环境与 Commons 体检 | `node scripts/agents.mjs doctor` |
| 各客户端的 MCP 状态 | `node scripts/agents.mjs mcp list` |
| 仓库不变量断言 | `node scripts/check.mjs` |
| MCP 协议握手 | `uv run --project python --locked python scripts/mcp_probe.py` |
| 新机器一键复现 | `node scripts/bootstrap.mjs` |

## 加一个技能

```bash
mkdir skills/<name>
# 写 skills/<name>/SKILL.md，frontmatter 至少 name 和 description
node scripts/check.mjs && node scripts/agents.mjs sync
```

`name` 必须等于目录名，全仓库不重名；目录只能有一层，套分类目录会让部分客户端看不到里面的技能。分类是导航文档，记在 `docs/skills.md`，不影响发现。

## 加一个 MCP server

- JavaScript：`pnpm add -D <pkg>`，`mcp.json` 里写 `command: node`，参数用 `${env:AGENTS_HOME}/node_modules/<pkg>/<入口>`。
- Python：`uv add --project python <pkg>`，写 `command: uv` 与 `args: ["run", "--project", "${env:AGENTS_HOME}/python", "--locked", "<可执行名>"]`。
- 密钥用 `${env:VAR}`，token 不进仓库。
- 客户端差异（审批、超时）写进 `.config/agent-sync/agent-sync.toml` 的 `[mcp.<name>.tweaks.<agent>]`，不要手改客户端配置。

审批语义容易写反：`approval_mode = "prompt"` 是每次询问，`approve` 是永不询问。

改完依次跑 `sync`、握手检测、`status`，三步都干净才算接入完成。

## 删一个 MCP server

```bash
node scripts/agents.mjs mcp remove <name>
```

它同时清掉 `mcp.json` 里的定义、tweaks 里的条目和各客户端里的配置。只手删 `mcp.json` 会留下客户端里的孤儿条目；只想暂时停用、以后恢复的用 `mcp disable <name>`。

## 排查

- `status` 报漂移：先跑 `sync`；反复漂移用 `doctor` 看客户端里是否有自己的配置在冲突。
- `doctor` 警告 `AGENT_SYNC_HOME` 与客户端读取路径不一致：仓库不是直接放在 `~/.agents`（例如放在 dotfiles 里再符号链接过来）。内容相同，只是路径字面不同，不影响使用。
- `sync` 说无法创建符号链接：Windows 需要开发者模式或管理员权限。
- 某个客户端看不到技能：确认是 `skills/<name>/SKILL.md` 一层结构，且 `check.mjs` 通过。

## 边界

- 本仓库保证的是套件自身可复现，不负责安装 COMSOL、TeX 这类专业软件。
- 依赖只加在 `package.json` 与 `python/pyproject.toml`；不用 `npm install -g`、`pip install`、不带版本号的 `npx` / `uvx`。
