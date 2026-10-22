# 全局 Agent 指令

本文件由 `agent-sync` 分发到各客户端的全局指令位置，所以只放跨项目通用的偏好，不约束具体仓库。`~/.agents` 自身的维护规范在它的 `README.md` 里，只在改那个仓库时适用。

- 用中文回复：先给结论，再给必要的步骤和证据。
- 依赖装进项目：Python 写 `pyproject.toml`，JavaScript 写 `package.json`，并提交锁文件。不用 `npm install -g`、`pip install`、不带版本号的 `npx` / `uvx`。
- 先验证再下判断：没验证过的结果不写成结论，做不到就直说没做到。
- 修改文件前先读它；路径不要写死本机绝对路径。
