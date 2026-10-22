# dot-agents

[![reproduce](https://github.com/catyugu/dot-agents/actions/workflows/reproduce.yml/badge.svg)](https://github.com/catyugu/dot-agents/actions/workflows/reproduce.yml)

`~/.agents` 是一个自包含、可复现的 Git 仓库：技能、MCP servers 以及运行它们的依赖全部声明在这里，版本由 pnpm 和 uv 的锁文件固定。

## 前置要求

要求环境具备：

- Git
- node 运行时
- pnpm
- uv

## 布局

```text
~/.agents/
├── skills/                    自己维护的技能，允许按类目嵌套
│   ├── general/<name>/SKILL.md
│   ├── research/{tools,knowledges}/<name>/SKILL.md
│   └── software-development/<name>/SKILL.md
├── mcp.json                   MCP server 的唯一配置源（标准 mcpServers 结构）
├── .config/agent-sync/agent-sync.toml   各客户端的差异项
├── AGENTS.md                  共享指令，由 agent-sync 分发
├── package.json               JavaScript 依赖声明
├── pnpm-lock.yaml
├── pnpm-workspace.yaml
├── node_modules/              本地安装，不提交
├── python/
│   ├── pyproject.toml         Python 依赖声明（agent-sync、Python MCP server）
│   ├── uv.lock
│   ├── .python-version
│   └── .venv/                 本地环境，不提交
└── scripts/
    ├── agents.mjs             agent-sync 入口，变量只注入到这一个进程
    ├── bootstrap.mjs          新机器一键复现
    └── check.mjs              仓库与渲染结果的断言
```

## 新机器

```powershell
git clone git@github.com:catyugu/dot-agents.git "$HOME\.agents"
node "$HOME\.agents\scripts\bootstrap.mjs"
```

bootstrap 依次做四件事：`pnpm install --frozen-lockfile`、`uv sync --project python --locked`、`agent-sync sync`、`agent-sync doctor`。它不写任何环境变量，也不往仓库外放配置文件。之后日常都走同一个入口：

```powershell
node scripts\agents.mjs sync      # 分发到各客户端
node scripts\agents.mjs status    # 检查漂移（退出码 0 干净、2 有待处理、1 出错）
node scripts\agents.mjs doctor
node scripts\agents.mjs mcp list
```

## 依赖

| 用途 | 声明位置 | 锁文件 | 调用方式 |
| --- | --- | --- | --- |
| JavaScript MCP server | `package.json` devDependencies | `pnpm-lock.yaml` | `node node_modules/<pkg>/<入口>` |
| Python MCP server、agent-sync | `python/pyproject.toml` | `python/uv.lock` | `uv run --project python --locked <命令>` |

运行时不用仓库自己装：Node 用客户端自带的（`engines.node` 在 `engineStrict` 下强制校验），Python 用 `python/.python-version` 指定、由 uv 按需获取。

## 技能

技能按类目嵌套，目录名即技能名。agent-sync 把 `skills/` 下的一级目录当成一个条目，所以给需要符号链接的客户端下发时拿到的是整个类目；那类客户端接入时需要改成扁平布局，或补一个按 SKILL.md 逐个建链的步骤。

```powershell
mkdir skills\general\my-skill
```

写 `skills\general\my-skill\SKILL.md`，frontmatter 至少包含 `name` 和 `description`。

## 加一个 MCP server

- JavaScript：`pnpm add -D <pkg>`，然后在 `mcp.json` 里写 `command: node` 加 `node_modules/<pkg>/<入口文件>`。
- Python：`uv add --project python <pkg>`，然后写 `command: uv`、`args: ["run", "--project", "${env:AGENTS_HOME}/python", "--locked", "<可执行名>"]`。
- 需要密钥的用 `${env:VAR}`，token 不进仓库。
- 客户端侧的差异项（超时、审批模式）写进 `.config/agent-sync/agent-sync.toml` 的 `[mcp.<name>.tweaks.<agent>]`，不要手改客户端配置。

## 不提交的东西

`node_modules/`、`python/.venv/`、各客户端目录里由 sync 生成的链接与配置段、`~/.local/state/agent-sync/`。这些是机器本地状态，重建靠锁文件。

## Windows

符号链接需要开发者模式或管理员权限，否则 `agent-sync sync` 会明确提示。
