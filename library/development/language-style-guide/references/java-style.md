# Google Java Style — 落地细则

Source: <https://google.github.io/styleguide/javaguide.html>
工具：google-java-format 或 IntelliJ Google 模板；先读仓库 formatter/checkstyle 配置。

## 1 缩进与行宽

- 缩进 2 空格；禁 Tab；行最长 100 列（Google Java Style 默认）。
- 大括号：类/方法/控制块首 `{` 与声明同行；必要处换行断开条件。
- 用 google-java-format 统一；不手排，冲突以 formatter 输出为准。

## 2 命名

| 对象                            | 规则               | 示例                          |
| ------------------------------- | ------------------ | ----------------------------- |
| 类/接口/枚举/注解               | `UpperCamelCase`   | `HttpClient`, `RecordType`    |
| 方法                            | `lowerCamelCase`   | `getValue()`, `sendRequest()` |
| 字段/变量                       | `lowerCamelCase`   | `itemCount`, `cache`          |
| 常量（static final/有效 final） | `CONSTANT_CASE`    | `MAX_TIMEOUT_MS`              |
| 包                              | 全小写，通常反域名 | `com.example.project`         |
| 泛型类型参数                    | 单大写             | `<T>`, `<K, V>`               |

- 布尔谓词方法用 `isXxx`/`hasXxx`/`canXxx`；避免非必要缩写。

## 3 文件与类组织

- 每源文件至多一个公开顶层类型；类成员顺序：static → instance，字段 → 构造 → 方法。
- 一个公开类型名与文件名一致（`Foo.java` 含 `class Foo`）。
- import 不使用时删除；通配 import 禁止（除非明确仓库允许）；import 静态成员仅少量时用。

## 4 作用域、空值与不可变性

- 局部变量声明即初始化；`final` 用在语义上不可变的值。
- 优先 `Optional<T>` 表达可能缺失，避免返回 `null` 造成调用方隐式 NPE；入参空值处理用显式校验或 `Objects.requireNonNull`。
- 字段最好 `private`；需要只读暴露用 `final` + 访问器；公开同构值类型可用 record。
- 用 `@Nullable`/`@NonNull` 标注契约（若项目启用 null 检查工具）。

## 5 控制流与错误

- 优先 `if/else`、`switch` 表达式使分支全部显式；避免深层嵌套（早 return / 卫语句）。
- 异常：运行时异常表示编程错误、检查异常表达可预期业务失败；`catch` 捕获后要么处理要么 `throw`，禁止空 catch。
- 用 `try-with-resources` 管理资源（`try (var r = open())`），避免手动 close 漏处理。
- 不在 catch 中吞掉异常改返回哨兵值除非契约明确。

## 6 类型与 API

- 参数尽量少；用 builder 管理多可选/必填字段；返回不可变集合（`List.of`/`Collections.unmodifiableList`）便于并发推理。
- 组合优先继承；final 非基类；重写方法用 `@Override`。
- 并发：共享可变状态用 `synchronized`/`ReentrantLock`/`ConcurrentHashMap` 等显式工具；优先不可变 + `final`。

## 7 注释与 Javadoc

- 导出标识（public/protected）写 Javadoc；第一句为功能摘要，随后 `@param`/`@return`/`@throws` 简述。
- 注释讲 why：并发约束、不变量、边界、性能、调用方契约；不做逐行翻译。
- 反对为私有方法写仪式化 Javadoc；仅在语义不显然时补一行说明。

## 8 反例

```java
// Bad：返回可能为 null 的集合、调用方易 NPE
List<String> names() { return maybeEmpty() ? null : list; }

// Good：空集合而非 null，缺失用 Optional
List<String> names() { return getListOrElse(List.of()); }
Optional<Config> config() { return Optional.ofNullable(fetch()); }
```

```java
// Bad：空 catch 吞错
try { risky(); } catch (IOException e) { /* nothing */ }
// Bad：用手动 close，异常时可能泄漏
// Good：try-with-resources
try (Connection c = open()) { use(c); } catch (SQLException e) { throw new StorageException(e); }
```

> 未注明极端规则以官方 Google Java Style 原文与仓库 formatter/checkstyle 为准。
