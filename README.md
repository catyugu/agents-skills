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
├── skills/                    技能，平铺一层，目录名即技能名
│   └── <name>/SKILL.md
├── docs/skills.md             技能分类与新增流程
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
    ├── agents.mjs             agent-sync 入口，变量只注入到这一个进程
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
node scripts/agents.mjs sync      # 分发到各客户端
node scripts/agents.mjs status    # 检查漂移（退出码 0 干净、2 有待处理、1 出错）
node scripts/agents.mjs doctor
node scripts/agents.mjs mcp list
node scripts/check.mjs            # 技能布局、mcp.json、审批策略
uv run --project python --locked python scripts/mcp_probe.py   # MCP 协议握手
```

## CI

`.github/workflows/reproduce.yml` 在 Ubuntu、Windows、macOS 上验证同一件事：把 checkout 克隆到 `$HOME/.agents`（客户端自己读的那个路径，不是 runner 的工作目录），建一个空的 `~/.codex` 假装客户端已安装，然后跑 bootstrap，再断言第二遍 `sync` 是空操作、`check.mjs` 全部通过、每个 MCP server 都能启动并答完 `initialize` 和 `tools/list`。

它证明不了的是具体客户端怎么解析：runner 上没装客户端，Chrome 和 Zotero 也不是 pnpm、uv 能装的运行条件，所以 MCP 检测停在握手，不调用任何工具。

## 依赖

| 用途 | 声明位置 | 锁文件 | 调用方式 |
| --- | --- | --- | --- |
| JavaScript MCP server | `package.json` devDependencies | `pnpm-lock.yaml` | `node node_modules/<pkg>/<入口>` |
| Python MCP server、agent-sync | `python/pyproject.toml` | `python/uv.lock` | `uv run --project python --locked <命令>` |

运行时不用仓库自己装：Node 用客户端自带的（`engines.node` 在 `engineStrict` 下强制校验），Python 用 `python/.python-version` 指定、由 uv 按需获取。CI 固定在 Node 24、pnpm 12.14.0、uv 0.9.22 上跑；`engines.node` 要求 `>=22.12.0 <25`（chrome-devtools-mcp 的下限），日常机器不必和 CI 完全同版本，但换大版本时请改这里并让 CI 一起走。

## 维护规范

改这个仓库时：

- 依赖只加在两处：`package.json`（JavaScript）与 `python/pyproject.toml`（Python），并提交 `pnpm-lock.yaml` / `python/uv.lock`。
- 不用 `npm install -g`、`pip install`、不带版本号的 `npx` / `uvx`；需要工具就在仓库里加依赖，用 `pnpm exec` / `uv run --project python --locked` 调用。
- 路径不写死：`mcp.json` 用 `${env:AGENTS_HOME}`，由 `scripts/agents.mjs` 注入。
- 技能平铺在 `skills/<name>/`（见下），MCP 只写在 `mcp.json`，删除走 `mcp remove`（见下）。

## 技能

技能平铺在 `skills/<name>/` 下，一个技能一层目录，目录名即技能名，必须等于 frontmatter 的 `name`。客户端按一级目录发现技能，套一层分类目录就会让一部分客户端看不到里面的技能，也让 agent-sync 的同步单位和你定义的技能单位对不上。分类只作为导航记录在 [`docs/skills.md`](docs/skills.md)，不参与校验。

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

## 不提交的东西

`node_modules/`、`python/.venv/`、`.state/`、各客户端目录里由 sync 生成的链接与配置段。这些都是机器本地状态，重建靠锁文件。

agent-sync 的锁文件默认落在 `~/.local/state/agent-sync/`；`scripts/agents.mjs` 把 `XDG_STATE_HOME` 指到本仓库的 `.state/`，让工作套件在文件系统上自包含。pnpm 和 uv 的下载缓存仍然走各自默认的共享位置——那是缓存，不是装进全局环境的软件，强行给每个套件单独一份只会多占磁盘、多下载。

## Windows

符号链接需要开发者模式或管理员权限，否则 `agent-sync sync` 会明确提示。

## 备注

本机把仓库放在 dotfiles 里、再用 `~/.agents` 符号链接指过来，所以 `doctor` 会提示 `AGENT_SYNC_HOME` 与 codex 实际读取的路径不一致。两个路径指向同一份文件，这条提示只是字面比较的结果，不是配置错误；仓库直接克隆到 `~/.agents` 时（CI 就是这么做的）不会出现。
