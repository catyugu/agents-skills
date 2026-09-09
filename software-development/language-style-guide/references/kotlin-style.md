# Kotlin 风格参考

## 规范层级

Kotlin 官方 Coding Conventions 是基础参考，Android Kotlin Style Guide 在 Android 项目中进一步规定格式；两者都不替代仓库的 `ktlint`、`detekt`、`ktfmt` 或 IDE 配置。协程 API 的行为以 kotlinx.coroutines 文档为准，风格建议（例如是否使用某种 scope function）属于社区/项目判断，不是编译器规则。

## 格式与组织

- 使用 4 个空格缩进，不用 Tab；通常一行不超过 100–120 列，具体以 formatter 配置为准。
- 顶层顺序建议为 package、imports、常量、类型、扩展和函数；导入按工具自动排序，不使用通配符导入，除非项目明确允许。
- 文件名通常使用 `UpperCamelCase.kt` 表示主要类型；只包含顶层函数或属性时可用描述性 `lowerCamelCase.kt`。一个文件可放相关扩展，但避免无边界的“工具箱”文件。
- 类体成员顺序应稳定：属性、初始化块、构造函数/工厂、公开方法、私有实现；这是一种可读性约定，不是语言要求。

## 命名与惯用法

类型、接口、注解、枚举和对象使用 `UpperCamelCase`；函数、局部变量和属性使用 `lowerCamelCase`；常量通常使用 `SCREAMING_SNAKE_CASE`，但项目也可能规定 `lowerCamelCase`。布尔值使用 `is/has/can/should`。优先 `val`，只有需要重新赋值才用 `var`；优先不可变集合接口和局部不可变数据。

使用数据类表达值对象，sealed class/interface 表达封闭状态，extension function 表达与接收者紧密相关的操作。避免过度使用 `let`、`also`、`apply`、`run`、`with`：每个调用都应让接收者和返回值一眼可见。表达式函数体适用于简单逻辑，分支复杂时使用块体。避免 `!!`；用可空类型、Elvis、早返回和显式校验表达缺失。

## 错误处理与并发

抛出具体异常，或用 `Result`/领域 sealed 类型表达可预期失败；不要捕获后返回一个无法区分的默认值。公共 API 记录异常、线程、资源和取消语义。协程必须有结构化并发的父 `CoroutineScope`；不要在全局或生命周期外随意 `GlobalScope.launch`。并行任务使用 `coroutineScope`/`supervisorScope` 明确失败传播；阻塞 I/O 放在合适 dispatcher，取消点不可被吞掉。

## 注释、工具与示例

KDoc 说明公共契约、参数、返回值、异常和线程/取消语义；注释解释原因，不重复类型。使用 `ktfmt`/`ktlint` 格式化，`detekt` 做静态检查，并运行单元测试和编译器警告检查。

```kotlin
// good: 不可变、无强制解包、结构化并发
suspend fun loadAll(ids: List<String>): List<User> = coroutineScope {
    ids.map { id -> async { repository.load(id) } }.awaitAll()
}
```

```kotlin
// bad: 非结构化 scope，失败和生命周期不可控
fun refresh(ids: List<String>) {
    GlobalScope.launch { ids.forEach { cache[it] = api.load(it) } }
}
```

来源：Kotlin Conventions（<https://kotlinlang.org/docs/coding-conventions.html）；Android> Kotlin Style（<https://developer.android.com/kotlin/style-guide）；Coroutines> Guide（<https://kotlinlang.org/docs/coroutines-guide.html）；ktlint（https://pinterest.github.io/ktlint/）。>
