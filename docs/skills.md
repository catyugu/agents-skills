# 技能分类

技能目录本身不记录分类：`skills/<name>/SKILL.md` 平铺一层，目录名就是技能名。分类只在这张表里维护，新增或改名技能时顺手改一行。

| 技能                                 | 类目     | 用途                                 |
| ------------------------------------ | -------- | ------------------------------------ |
| `humanizer`                          | 通用     | 去掉英文里的 AI 腔，补回人味         |
| `humanizer-zh`                       | 通用     | 去掉中文里的模板腔                   |
| `qa`                                 | 通用     | 对话式 QA，边聊边把问题整理成 issue  |
| `teach`                              | 通用     | 在本仓库语境里讲清一个新概念或新技能 |
| `write-a-skill`                      | 通用     | 按渐进披露的结构新写一个技能         |
| `hpc-numerical-methods-fem-fvm`      | 研究     | FEM/FVM 与 HPC 开源项目速查          |
| `autocomsol`                         | 研究     | 写、改、验 COMSOL Java 模型          |
| `evidence-grounded-report-authoring` | 研究     | 用已验证的项目证据展开报告           |
| `code-writing-and-style`             | 软件开发 | 编码风格与改动纪律                   |
| `language-style-guide`               | 软件开发 | 各语言官方风格约定                   |

## 为什么不按类目分层

客户端按 `skills/` 的一级目录发现技能。套一层 `general/`、`research/tools/` 这类目录，有的客户端会继续递归，有的就什么都看不到；`agent-sync` 也把一级目录当成同步单位，结果它数出 3 个条目，实际有 10 个技能。所以分类只作为文档存在。

## 新增一个技能

```bash
mkdir skills/my-skill
```

写 `skills/my-skill/SKILL.md`，frontmatter 需要 `name` 和 `description`，`name` 必须等于目录名，全仓库不重名。`node scripts/check.mjs` 校验这几条；改完在这个表里补一行。
