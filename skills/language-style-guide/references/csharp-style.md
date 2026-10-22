# Microsoft C# 编码约定细则

Source: <https://learn.microsoft.com/en-us/dotnet/csharp/fundamentals/coding-style/coding-conventions>
工具：.editorconfig + Roslyn analyzers / dotnet format；先读仓库 .editorconfig。

## 1 缩进与排版

- 缩进 4 空格；禁制表符；行长通常按仓库/EditorConfig（如 120）。
- 用 `dotnet format` / IDE 格式统一；命名空间/大括号按 C# 主流风格（可配置）。
- `var`：类型右侧明显时用 `var`；不明显时显式类型（偏好 `var` 当类型从右表达清楚）。

## 2 命名

| 对象                                  | 规则          | 示例                                           |
| ------------------------------------- | ------------- | ---------------------------------------------- |
| 命名空间/类型/方法/属性/事件/公开字段 | `PascalCase`  | `OrderService`, `ComputeTotal()`, `MaxRetries` |
| 局部变量/参数/私有字段(约定)          | `camelCase`   | `itemCount`, `_cache`                          |
| 常量                                  | `PascalCase`  | `const int DefaultPort = 8080`                 |
| 接口                                  | `I` 前缀      | `IRepository`                                  |
| 泛型类型                              | `T`/`TEntity` | `List<T>`                                      |

- 布尔属性 `IsValid`/`HasChanged`；`_` 前缀私有字段为项目约定（也可用无前缀 + 风格统一）。

## 3 文件与成员

- 每类一个文件；一个文件一个公开顶层类型（除非嵌套）。
- 成员顺序：常量→字段→构造函数→属性→方法（可按可访问性再分层）。
- 公开成员显式 `public`（不依赖容器默认）；`static` 类/方法用于无状态工具。

## 4 控制流与空值

- 空值处理用可空引用类型（`string?`）+ 明确：`?.`/`??`/`??=`；开启 `<Nullable>enable`。
- 避免通配/宽异常捕获；`catch` 应处理或 `throw;`；用 `when` 过滤少数异常分支时明确原因。
- 用 `async/await`；异步代码禁止 `.Result`/`.Wait()` 阻塞（避免死锁）；`ConfigureAwait(false)` 在库代码按场景用。
- `switch` 表达式/模式匹配让分支穷尽（`default` 或禁止漏项）。

## 5 资源与并发

- `using`/`await using` 管理 IDisposable/IAsyncDisposable；资源生命周期贴近获取点。
- 线程安全：共享可变状态用锁/`Concurrent` 集合/不可变；避免裸 `Thread`/`Task.Run` 无声泄漏。
- 异步流 `IAsyncEnumerable<T>` 适合逐项生成；错误/取消传播到调用方。

## 6 类型与 API

- 值类型/不可变用 `record`；需身份与生命周期用 `class`；DTO 不可变属性。
- 避免暴露可变集合/数组字段；返回只读视图（`IReadOnlyList<T>`）。
- 参数尽量少，多可选用命名可选参数或 builder；公开 API 写 XML 文档（`/// <summary>`, `<param>`, `<returns>`, `<exception>`）。

## 7 反例

```csharp
// Bad：可空隐患 + 手写空检查冗长、易漏
string name = obj.Profile?.Name ?? "";   // 已安全；但下面漏判空
Console.WriteLine(obj?.Profile?.Name?.ToUpper() ?? "n/a");

// Good：统一显式空传播；避免 .Value 未判空
var name = obj?.Profile?.Name;
```

```csharp
// Bad：同步阻塞异步
var result = await GetAsync().Result;   // 死锁/线程阻塞
// Good
var result = await GetAsync();
```

```csharp
// Bad：空 catch
try { await SaveAsync(); } catch (Exception) { }  // 吞全部
// Good：捕获具体且处理或重抛
try { await SaveAsync(); } catch (TimeoutException ex) when (policy.AllowsRetry) { await RetryAsync(); }
```

> 细项以 Microsoft 官方 + 仓库 .editorconfig/analyzer 为准；风格与编译器建议冲突时给注释理由。
