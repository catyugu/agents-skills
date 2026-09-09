# agents-skills

通用 agent skills 仓库。每个 skill 一个目录，内含 SKILL.md（YAML frontmatter + 说明），可附 references/、templates/、scripts/。

## 目录结构

- general/ 通用类（联网搜索、笔记、问答、教学等）
- research/ 科研类（tools/ 为论文检索、写作等工具 skill，knowledges/ 为领域知识）
- software-development/ 软件开发类（代码风格、GitHub 流程、TDD、调试等）
- local/ 本地项目专用 skill（被 .gitignore 排除，不进本仓库）

## 说明

- general/free-web-search 含 node 脚本，使用前在该目录 npm install（node_modules 已忽略）
- 新增 skill：目录名用小写连字符，SKILL.md 的 description 写清触发场景
