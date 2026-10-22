# Swift 风格参考

## 规范层级

Swift API Design Guidelines 是官方 API 命名基线；Swift 官方文档和 Swift Evolution 说明语言与并发语义。SwiftFormat、SwiftLint 以及 Ray Wenderlich/Kodeco 等属于工具或社区规范，具体行宽、括号和规则必须以项目配置为准。Apple 平台项目还应遵循现有 SDK 命名与生命周期约定。

## 格式与文件组织

- 常见格式是 4 个空格缩进、禁止 Tab；推荐 100–120 列软上限，但 Swift 官方不强制固定行宽。
- 文件先放 `import`，再放主要类型或扩展；按功能拆分文件，避免一个文件同时承载无关模型、网络和 UI。类型较大时可用 `Type+Feature.swift` 扩展文件，但扩展应保持主题单一。
- 类型、协议、actor、枚举和结果类型用 `UpperCamelCase`；属性、函数、参数和枚举 case 用 `lowerCamelCase`。缩写按 API Guidelines 处理，如 `url`、`id`，不要随意全大写。
- API 命名应在调用点读起来像自然短语：方法用动词，属性用名词；参数标签提供必要语义，避免重复基名。

## 类型、可变性与惯用法

优先值语义、`struct` 和 `enum`；需要身份、共享可变状态或 Obj-C/生命周期互操作时再用 `class`。默认 `let`，确需改变才用 `var`。用协议和组合表达能力，避免为复用而建立深继承层次。Optional 表示真实缺失；优先 `if let`、`guard let`、`map`/`flatMap`，不要以 `!` 掩盖不确定性。只有不变量在同一局部已验证且失败确实不可恢复时，才允许强制解包，并写明理由。

## 错误处理与并发

可预期失败使用 `throws` 和具体 `Error` 枚举；不要用 `NSError` 或布尔值丢失上下文。捕获时只处理能恢复的错误，其他错误继续传播。`async`/`await` 保持任务结构和取消传播；长任务检查 `Task.isCancelled` 或调用可取消 API。共享可变状态使用 actor 或明确隔离；不要假设 `Sendable` 类型天然安全。公共 API 文档应说明主线程、actor 隔离、所有权、失败、取消和副作用。

## 注释、工具与示例

Swift-DocC 注释用于公开 API；普通注释解释约束、性能取舍和生命周期。用 SwiftFormat/SwiftLint 做格式与 lint，编译时启用尽可能严格的并发检查，并运行 XCTest。

```swift
// good: 失败显式、调用点清晰
func loadUser(from id: User.ID) async throws -> User {
    try Task.checkCancellation()
    return try await repository.user(withID: id)
}
```

```swift
// bad: 强制解包掩盖输入不确定性
func name(from json: [String: Any]) -> String {
    return json["name"] as! String
}
```

来源：Swift API Design（<https://www.swift.org/documentation/api-design-guidelines/）；Swift> Book（<https://docs.swift.org/swift-book/）；Swift> Concurrency（<https://docs.swift.org/swift-book/documentation/the-swift-programming-language/concurrency/）；SwiftLint（https://realm.github.io/SwiftLint/）。>
