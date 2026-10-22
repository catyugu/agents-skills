# 共享 Agent 指令

`~/.agents`（本仓库）是本机所有 Agent 的技能与 MCP server 的**唯一副本**。改这里的文件，不要再改各客户端目录里的副本。

## 规则

- 技能在 `skills/<类目>/<name>/SKILL.md`：`general/`、`research/tools/`、`research/knowledges/`、`software-development/`。目录名即技能名，`description` 写清触发场景；Codex 与 DSH 递归扫描，放对类目即可。
- MCP server 只写在 `mcp.json`（标准 `mcpServers` 结构）。客户端里的条目由 `agent-sync sync` 生成，不要手改。
- 依赖只加在两处：`package.json`（JavaScript）和 `python/pyproject.toml`（Python），并且必须提交 `pnpm-lock.yaml` / `python/uv.lock`。
- 不使用 `npm install -g`、`pip install`、不带版本号的 `npx` / `uvx`；需要什么工具就在这里加依赖，再用 `pnpm exec` / `uv run --project python --locked` 调用。
- 文件路径不要写死机器绝对路径：`mcp.json` 里用 `${env:AGENTS_HOME}`，sync 时解析成本机路径。

## 常用命令

```sh
uv run --project python --locked agent-sync sync        # 分发到各客户端
uv run --project python --locked agent-sync status      # 检查是否有漂移（0 干净，2 有动作）
uv run --project python --locked agent-sync doctor      # 环境与 Commons 体检
uv run --project python --locked agent-sync mcp list    # MCP server 在各客户端的状态
pnpm install --frozen-lockfile                          # 恢复 JavaScript 依赖
uv sync --project python --locked                       # 恢复 Python 依赖
```
