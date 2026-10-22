# dot-agents

[![reproduce](https://github.com/catyugu/dot-agents/actions/workflows/reproduce.yml/badge.svg)](https://github.com/catyugu/dot-agents/actions/workflows/reproduce.yml)

`~/.agents` 是一个自包含、可复现的 Git 仓库：技能、MCP servers 以及运行它们的依赖全部声明在这里，版本由 pnpm 和 uv 的锁文件固定。客户端只读取，不持有原件。

## 前置要求

Git、node 运行时、pnpm、uv。

## 布局

```text
~/.agents/
├── library/                   技能源码，按类目分层
│   ├── general/<name>/SKILL.md
│   └── research/literature/<name>/SKILL.md
├── skills/                    生成的平铺视图：每个技能一个目录链接，不提交
├── mcp.json                   MCP server 的唯一配置源（标准 mcpServers 结构）
├── .config/agent-sync/agent-sync.toml   各客户端的差异项
├── AGENTS.md                  跨项目的全局指令，由 agent-sync 分发
├── package.json               JavaScript 依赖声明
├── pnpm-lock.yaml
├── pnpm-workspace.yaml
├── node_modules/              本地安装，不提交
├── python/
│   ├── pyproject.toml         Python 依赖声明（agent-sync、MCP SDK、Python MCP server）
│   ├── uv.lock
│   ├── .python-version
│   └── .venv/                 本地环境，不提交
├── .state/                    agent-sync 的锁文件，不提交
└── scripts/
    ├── agents.mjs             agent-sync 入口，先重建技能视图
    ├── skills.mjs             library/ 到 skills/ 的目录映射
    ├── bootstrap.mjs          新机器一键复现
    ├── check.mjs              仓库特有的断言
    └── mcp_probe.py           逐个 MCP server 的协议握手
```

## 新机器

```bash
git clone git@github.com:catyugu/dot-agents.git "$HOME/.agents"
node "$HOME/.agents/scripts/bootstrap.mjs"
```

bootstrap 依次做四件事：`pnpm install --frozen-lockfile`、`uv sync --project python --locked`、`agent-sync sync`、`agent-sync doctor`。它不写任何环境变量，也不往仓库外放配置文件。之后日常都走同一个入口：

```bash
node scripts/agents.mjs sync      # 重建技能视图并分发到各客户端
node scripts/agents.mjs status    # 检查漂移（退出码 0 干净、2 有待处理、1 出错）
node scripts/agents.mjs doctor
node scripts/agents.mjs mcp list
node scripts/check.mjs            # 技能布局、mcp.json、审批策略
uv run --project python --locked python scripts/mcp_probe.py   # MCP 协议握手
```

## 技能

技能源码放在 `library/<类目>/<name>/`，类目可以任意分层，那是给人看的组织方式。客户端只认 `skills/<name>/SKILL.md`，而且不是每个客户端都会递归，所以 `skills/` 是一层平铺的生成视图：每个技能一个指回 `library/` 的目录链接，没有副本。`skills/` 不提交，`sync` 会先重建它。

```bash
node scripts/skills.mjs           # 单独重建视图
node scripts/skills.mjs --check   # 只检查是否过期：0 干净、2 有待处理、1 出错
```

技能名必须等于目录名，且全仓库唯一，由 `check.mjs` 校验。增删、改名或移动技能之后跑一次 `sync` 就够了。

## MCP 生命周期

删除一个 server 用 `mcp remove`，它同时清掉 `mcp.json` 里的定义、tweaks 里的条目和各客户端渲染出来的配置：

```bash
node scripts/agents.mjs mcp remove <name>
```

不要只从 `mcp.json` 里手删：agent-sync 不记录每个条目的历史所有权，手删的定义不会再被渲染，但各客户端里已经存在的条目会留在原地。想暂时停用、以后还要恢复的用 `mcp disable <name>`。

只加不改的日常操作不需要这一步：`sync` 会按键合并，客户端里的其他键原样保留。

## 加一个 MCP server

- JavaScript：`pnpm add -D <pkg>`，然后在 `mcp.json` 里写 `command: node` 加 `node_modules/<pkg>/<入口文件>`。
- Python：`uv add --project python <pkg>`，然后写 `command: uv`、`args: ["run", "--project", "${env:AGENTS_HOME}/python", "--locked", "<可执行名>"]`。
- 需要密钥的用 `${env:VAR}`，token 不进仓库。
- 客户端侧的差异项（超时、审批模式）写进 `.config/agent-sync/agent-sync.toml` 的 `[mcp.<name>.tweaks.<agent>]`，不要手改客户端配置。

## 依赖

| 用途 | 声明位置 | 锁文件 | 调用方式 |
| --- | --- | --- | --- |
| JavaScript MCP server | `package.json` devDependencies | `pnpm-lock.yaml` | `node node_modules/<pkg>/<入口>` |
| Python MCP server、agent-sync | `python/pyproject.toml` | `python/uv.lock` | `uv run --project python --locked <命令>` |

运行时不用仓库自己装：Node 用客户端自带的，Python 由 uv 按 `python/.python-version` 获取。`engines.node` 要求 `>=22.12.0 <25`（chrome-devtools-mcp 的下限），换大版本时改这里。

## 维护规范

- 依赖只加在两处：`package.json`（JavaScript）与 `python/pyproject.toml`（Python），并提交 `pnpm-lock.yaml` / `python/uv.lock`。
- 不用 `npm install -g`、`pip install`、不带版本号的 `npx` / `uvx`；需要工具就在仓库里加依赖，用 `pnpm exec` / `uv run --project python --locked` 调用。
- 路径不写死：`mcp.json` 用 `${env:AGENTS_HOME}`，由 `scripts/agents.mjs` 注入。
- 技能源码只放 `library/`，`skills/` 是生成的，不要手改；MCP 只写在 `mcp.json`，删除走 `mcp remove`。

## 不提交

`node_modules/`、`python/.venv/`、`.state/`、`skills/`，以及 sync 写进各客户端目录的配置。这些都是机器本地状态，重建靠锁文件。

agent-sync 的锁文件默认落在 `~/.local/state/agent-sync/`；`scripts/agents.mjs` 把 `XDG_STATE_HOME` 指到本仓库的 `.state/`，让工作套件在文件系统上自包含。pnpm 和 uv 的下载缓存仍然走各自默认的共享位置——那是缓存，不是装进全局环境的软件。
