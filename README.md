# dot-agents

`~/.agents` 是一个自包含、可复现的 Git 仓库：技能（skills）、MCP servers 以及运行它们的依赖全部声明在这里，版本由 pnpm 和 uv 的锁文件固定。除 Git、pnpm、uv 和 Agent 客户端本身，不需要任何全局安装，也不改系统级开发环境。

## 布局

```text
~/.agents/
├── skills/                    自己维护的技能，允许按类目嵌套
│   ├── general/<name>/SKILL.md
│   ├── research/tools/<name>/SKILL.md
│   ├── software-development/<name>/SKILL.md
│   ├── ...
├── mcp.json                   MCP server 的唯一配置源（标准 mcpServers 结构）
├── agent-sync.toml            各客户端的差异项（agent-sync 自己的配置，链接到 ~/.config/agent-sync/）
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
└── scripts/bootstrap.mjs      新机器一键复现
```

## 新机器

```powershell
git clone git@github.com:catyugu/dot-agents.git "$HOME\.agents"
node "$HOME\.agents\scripts\bootstrap.mjs"
```

bootstrap 依次做五件事：把 `AGENTS_HOME` 写成本机用户环境变量、`pnpm install --frozen-lockfile`、`uv sync --project python --locked`、`agent-sync sync`、`agent-sync doctor`。之后日常只需要一条命令：

```powershell
uv run --project python --locked agent-sync sync
```

检查漂移用 `agent-sync status`（退出码 0 干净、2 有待处理、1 出错）。

## 为什么要有 AGENTS_HOME

`mcp.json` 要在多台机器间共享，所以不能写死 `C:\Users\...`。agent-sync 只支持 `${env:VAR}` 一种变量展开，且只在 `sync` 那一刻解析：它把 `${env:AGENTS_HOME}` 换成运行机器的绝对路径，写进各客户端自己的配置。变量缺失时 agent-sync 直接报错并放弃全部写入，不会留下半截配置。

## 依赖

| 用途 | 声明位置 | 锁文件 | 调用方式 |
| --- | --- | --- | --- |
| JavaScript MCP server | `package.json` devDependencies | `pnpm-lock.yaml` | `node node_modules/<pkg>/<入口>` |
| Python MCP server、agent-sync | `python/pyproject.toml` | `python/uv.lock` | `uv run --project python --locked <命令>` |

运行时不用仓库自己装：Node 用客户端自带的（`engines.node` 在 `engineStrict` 下强制校验），Python 用 `python/.python-version` 指定、由 uv 按需获取。这样既没有第二个 Node，也不会让 MCP server 跑在与客户端不同的解释器上。

## 技能布局与分发

技能允许按类目嵌套。要留意 agent-sync 的边界：它把 `skills/` 下的一级目录当成一个条目。本机装的 Codex 原生读 Commons，所以不下发技能链接；将来若有客户端必须靠符号链接（Claude Code 之类），它拿到的是整个类目目录，而不是单个技能，届时需要改成扁平布局或在 `scripts/` 里补一个按 SKILL.md 逐个建链的分发步骤。

## 加一个 skill

```powershell
mkdir skills\general\my-skill
```

写 `skills\general\my-skill\SKILL.md`，frontmatter 至少包含 `name` 和 `description`。放对类目即可，不需要额外注册。

## 加一个 MCP server

- JavaScript：`pnpm add -D <pkg>`，然后在 `mcp.json` 里写 `command: node` 加 `node_modules/<pkg>/<入口文件>`。
- Python：`uv add --project python <pkg>`，然后写 `command: uv`、`args: ["run", "--project", "${env:AGENTS_HOME}/python", "--locked", "<可执行名>"]`。
- 需要密钥的用 `${env:VAR}`，token 不进仓库。
- 客户端侧的差异项（超时、审批模式）写进 `agent-sync.toml` 的 `[mcp.<name>.tweaks.<agent>]`，不要手改客户端配置。

## 分发到哪里

agent-sync 只认它注册表里的客户端，本机当前装的是 Codex：`~/.codex/AGENTS.md` 成为指向本仓库的符号链接，`~/.codex/config.toml` 里的 `mcp_servers` 由键合并维护。

DSH 直接读 `~/.agents/skills`，它的两个 MCP 连接写在 profile 的 cordis patch 里（`~/.dsh/default-workspace/dsh-chrome-devtools` 与 `dsh-zotero-mcp` 的 `cordis.patch.yml`），命令指向这个仓库里的可执行文件，而不是全局安装。这两处写的是本机绝对路径，仓库搬位置时要一起改。

DSH 自带的那批技能（`~/.dsh/skills` 下的 docx、pdf、xlsx 等）不由本仓库管理，它们随 DSH 的插件走，不要往这里搬。

## 不提交的东西

`node_modules/`、`python/.venv/`、`~/.config/zotero-mcp/config.json`（本地 API 授权）、各客户端目录里由 sync 生成的链接与配置段、`~/.local/state/agent-sync/`。这些都是机器本地状态，重建靠锁文件。

## Windows

符号链接需要开发者模式或管理员权限，否则 `agent-sync sync` 会明确提示。`AGENTS_HOME` 由 `setx` 写入用户环境变量，重开终端生效。
