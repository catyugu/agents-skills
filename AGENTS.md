# 共享 Agent 指令

`~/.agents`（本仓库）是本机所有 Agent 的技能与 MCP server 的**唯一副本**。改这里的文件，不要再改各客户端目录里的副本。

## 规则

- 技能在 `skills/<name>/SKILL.md`，平铺一层，不再套类目目录：客户端只按一级目录发现技能，套一层就会让部分客户端看不到。目录名必须等于 frontmatter 的 `name`。类目记在 `docs/skills.md`。
- MCP server 只写在 `mcp.json`（标准 `mcpServers` 结构）。客户端里的条目由 `agent-sync sync` 生成，不要手改。
- 依赖只加在两处：`package.json`（JavaScript）和 `python/pyproject.toml`（Python），并且必须提交 `pnpm-lock.yaml` / `python/uv.lock`。
- 不使用 `npm install -g`、`pip install`、不带版本号的 `npx` / `uvx`；需要什么工具就在这里加依赖，再用 `pnpm exec` / `uv run --project python --locked` 调用。
- 文件路径不要写死机器绝对路径：`mcp.json` 里用 `${env:AGENTS_HOME}`，由 `scripts/agents.mjs` 注入。
- 删 MCP server 用 `node scripts/agents.mjs mcp remove <name>`，不要只从 `mcp.json` 里删：agent-sync 不记录历史所有权，从 `mcp.json` 删掉的条目会留在客户端配置里。

## 常用命令

```sh
node scripts/agents.mjs sync        # 分发到各客户端
node scripts/agents.mjs status      # 检查是否有漂移（0 干净，2 有动作）
node scripts/agents.mjs doctor      # 环境与 Commons 体检
node scripts/agents.mjs mcp list    # MCP server 在各客户端的状态
node scripts/check.mjs              # 仓库与渲染结果断言
node scripts/mcp-probe.mjs          # 逐个 MCP server 走一次 initialize + tools/list
pnpm install --frozen-lockfile      # 恢复 JavaScript 依赖
uv sync --project python --locked   # 恢复 Python 依赖
```
