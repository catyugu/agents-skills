# Markdown 风格参考

## 规范层级

CommonMark 规范定义一组可移植的 Markdown 语法基线，不规定文档语气、目录结构、行宽或项目术语。GitHub Flavored Markdown（GFM）增加表格、任务列表、自动链接等扩展；不同渲染器可能不同。markdownlint 的默认规则和 GitHub 文档风格属于社区/平台规范，应以仓库 `.markdownlint*`、渲染器和发布平台为准。

## 文档结构与格式

- 每篇文档通常只有一个一级标题 `#`，标题层级按 `#` → `##` → `###` 递进，不跳级；标题前后留一个空行。
- 使用 UTF-8；文件名推荐小写 `kebab-case.md`，但已有仓库命名优先。行宽常见目标 80–120 列；长段落是否手工换行取决于项目 formatter，不能把软换行当成 CommonMark 要求。
- 列表项使用 `-`（或仓库统一的 `*`），缩进 2 或 4 个空格保持一致；有明确顺序的流程才用 `1.`。列表、引用、代码围栏前后留空行，嵌套层级尽量不超过 3 层。
- 代码块使用围栏并标注语言（如 ```bash）；命令、文件名、变量和短代码使用反引号。示例应最小且可运行，危险命令标明前置条件和影响。

## 链接、图片与表格

链接文字应描述目标，不写“点击这里”；重复 URL 可使用引用式链接。外部链接应避免跟踪参数，必要时说明版本或访问日期。图片使用有意义的 alt 文本，装饰图可为空；避免把关键信息只放在图片里。表格只用于真正的二维数据，表头必须清楚；复杂内容用列表或小节更易读。GFM 任务列表、脚注和表格并非所有 Markdown 渲染器都支持，跨平台文档应提供纯文本可读替代。

## 内容与可维护性

段落围绕一个主题，先给结论再给细节；术语、大小写和产品名保持一致。命令、API 参数、版本号必须与可执行代码和当前行为同步。用 admonition/HTML 前先确认目标渲染器支持。不要在 Markdown 中嵌入未审计的原始 HTML、脚本或用户可控内容。目录、锚点和相对链接在重命名后应由检查工具验证。

## 注释、工具与示例

Markdown 没有通用注释语法；HTML 注释 `<!-- ... -->` 可能出现在源文件但通常不应承载唯一信息。推荐 markdownlint 检查标题、空行、列表和尾随空格，Prettier 或 remark 统一格式，并在目标平台预览链接、表格和代码块。

```markdown
## 安装

运行以下命令：

```bash
npm install
```

详见 [贡献指南][contributing]。

[contributing]: ./CONTRIBUTING.md

```markdown
<!-- bad: 无语言标签、无描述链接、层级跳跃 -->
# Tool
### Details
点击[这里](https://example.test/?utm_source=x)查看。
```

来源：CommonMark（<https://spec.commonmark.org/current/）；GFM（https://github.github.com/gfm/）；markdownlint（https://github.com/DavidAnson/markdownlint）；Prettier> Markdown（<https://prettier.io/docs/en/options.html）。>
