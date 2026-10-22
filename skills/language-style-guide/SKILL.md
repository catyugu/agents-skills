---
name: language-style-guide
description: Apply official conventions for code style by language.
version: "1.1"
author: "Agnes, Hermes Agent"
license: "MIT"
metadata:
  hermes:
    tags: [style, style-guide, code-style, coding, formatting]
    related_skills: [code-writing-and-style]
---

## When to Use

写/审代码时加载此技能。先匹配仓库现有配置，再参考对应语言的官方或主流规范；不要把不同生态的偏好混用。

## 通用原则

- **项目约定优先**：先读 `.editorconfig`、formatter、linter、CI 配置和邻近代码。
- **自动化优先**：使用项目指定 formatter；不要手工制造格式差异。
- **命名表意**：名称表达领域概念、单位、所有权、生命周期和副作用。
- **函数专注**：保持短小；拆分应降低认知负担，而不是追求固定行数。
- **错误显式**：区分可恢复错误、缺失值、异常和不可达状态；不静默吞错。
- **注释说 why**：解释约束、权衡、不变量、线程/资源/安全语义，不复述代码。
- **验证闭环**：格式化、静态检查、测试和构建均以真实工具输出为准。

## 语言参考

- **C++**: `references/google-cpp-style.md`
- **Python**: `references/pep8-style.md`
- **Rust**: `references/rust-style.md`
- **Go**: `references/go-style.md`
- **Java**: `references/java-style.md`
- **JavaScript / TypeScript**: `references/javascript-style.md`
- **C**: `references/c-style.md`
- **C#**: `references/csharp-style.md`
- **Kotlin**: `references/kotlin-style.md`
- **Swift**: `references/swift-style.md`
- **PHP**: `references/php-style.md`
- **Ruby**: `references/ruby-style.md`
- **Bash**: `references/bash-style.md`
- **Markdown**: `references/markdown-style.md`

参考文件记录稳定的命名、格式、API 设计和错误处理原则；具体版本、项目规则和工具参数以官方文档及仓库配置为准。

## 执行顺序

1. 识别语言、版本、框架和仓库级工具配置。
2. 读取对应参考文件，只采用与目标代码相关的规则。
3. 遵循现有代码的局部约定；发现冲突时以明确的仓库配置和编译器/formatter 行为为准。
4. 让改动保持局部、可读、可测试；不为风格清理无关代码。
5. 运行 formatter、linter、测试或构建，并报告真实结果。

## 行为原则

加载 `code-writing-and-style` 获取更高层的 Karpathy 风格原则：先思考、保持简单、外科手术式变更、目标驱动。
