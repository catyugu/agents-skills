# 技能分类

技能目录本身不记录分类：`skills/<name>/SKILL.md` 平铺一层，目录名就是技能名。分类只是给人看的导航，不参与校验，所以这张表落后于目录也不会让任何检查失败。

| 技能                                 | 类目     | 用途                                 |
| ------------------------------------ | -------- | ------------------------------------ |
| `humanizer`                          | 通用     | 去掉英文里的 AI 腔，补回人味         |
| `humanizer-zh`                       | 通用     | 去掉中文里的模板腔                   |
| `qa`                                 | 通用     | 对话式 QA，边聊边把问题整理成 issue  |
| `teach`                              | 通用     | 在本仓库语境里讲清一个新概念或新技能 |
| `dot-agents`                         | 通用     | 维护本仓库本身：同步、MCP、技能      |
| `skill-creator`                      | 通用     | 从零写技能，或按真实提示词重写       |
| `hpc-numerical-methods-fem-fvm`      | 研究     | FEM/FVM 与 HPC 开源项目速查          |
| `autocomsol`                         | 研究     | 写、改、验 COMSOL Java 模型          |
| `evidence-grounded-report-authoring` | 研究     | 用已验证的项目证据展开报告           |
| `paper-lookup`                       | 研究     | 18 个学术数据库检索、DOI 与开放全文  |
| `literature-review`                  | 研究     | 系统性文献搜集、筛选、综合           |
| `paper-corpus-rag`                   | 研究     | 本地论文库索引与跨论文证据追溯       |
| `citation-verification`              | 研究     | 核对引用是否真实、DOI 与元数据是否一致 |
| `scientific-critical-thinking`       | 研究     | 批判性评估方法、证据与统计有效性     |
| `hypothesis-generation`              | 研究     | 把发现转成可证伪假设与验证计划       |
| `experiment-suite`                   | 研究     | 实验设计、可运行代码、结果与来源     |
| `ara-research-manager`               | 研究     | 持久化研究决策、失败路线与证据来源   |
| `code-writing-and-style`             | 软件开发 | 编码风格与改动纪律                   |
| `language-style-guide`               | 软件开发 | 各语言官方风格约定                   |

## 为什么不按类目分层

客户端按 `skills/` 的一级目录发现技能。套一层 `general/`、`research/tools/` 这类目录，有的客户端会继续递归，有的就什么都看不到；`agent-sync` 也把一级目录当成同步单位，套了分类它数出的条目数就和实际技能数对不上。所以分类只作为文档存在。

## 新增一个技能

```bash
mkdir skills/my-skill
```

写 `skills/my-skill/SKILL.md`，frontmatter 需要 `name` 和 `description`，`name` 必须等于目录名，全仓库不重名；`node scripts/check.mjs` 只校验这三条，不校验这张表。
