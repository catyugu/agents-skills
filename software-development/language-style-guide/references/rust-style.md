# Rust 风格细则

Source: <https://doc.rust-lang.org/style-guide/>
工具：`cargo fmt`（rustfmt）为格式基准；`cargo clippy` 做惯例检查；`cargo fmt --check` 进 CI。

## 1 缩进与排版（rustfmt 输出为准）

- 缩进 4 空格（rustfmt 默认；早期版本曾 2 空格，依项目 rustfmt.toml 的 `edition` 与 `tab_spaces` 而定）。
- 导入按组字母序排序，rustfmt/编辑器可自动排。
- 大括号：`fn`/结构体/`match` 臂用块；控制语句 `{}` 与条件同行。
- 工具未覆盖的命名分节不手排；提交前 `cargo fmt`，别在生产留格式差异。

## 2 命名

| 对象                 | 规则                                    | 示例                            |
| -------------------- | --------------------------------------- | ------------------------------- |
| 函数/方法/局部变量   | `snake_case`                            | `calculate_speed`, `item_count` |
| 类型/结构/枚举/trait | `UpperCamelCase`                        | `struct HttpRequest`            |
| 常量/静态            | `SCREAMING_SNAKE`                       | `const DIAL_TIMEOUT`            |
| 模块/包(crate)       | `snake_case`                            | `my_module`                     |
| 泛型参数             | 短大写                                  | `<T>`                           |
| trait 惯例前缀       | `Error`/`Into`/`From`/`AsRef`/`TryFrom` | 依标准库惯例                    |

- 布尔/谓词用动词或 `is_/has_/can_`；不必要时不加 `get_`（字段/构造常用 `new`）。

## 3 类型、所有权、错误

- 用 `Result<T, E>` 表达可恢复错误，`Option<T>` 表达缺失；`?` 传播，避免长串手动 match。
- 错误类型实现 `std::error::Error` + `Display`；用 `thiserror`/`anyhow` 按场景（库 vs 应用）。
- 所有权可见：`&T` 只读、`&mut T` 可变、`T` 传入即转移；`Clone` 显式调用，不隐式拷贝。
- 非必要不使用 `unsafe`；确需时用 `// SAFETY:` 说明不变量、调用约束、别名/生命周期前提。
- 优先不可变 `let`，序列需可变时用 `let mut`；优先迭代器处理集合，复杂控制流可用循环。

## 4 结构、枚举、trait

- 用枚举编码多种状态；对布尔/二态考虑枚举还是 `bool` 依领域语义。
- 派生常用 `#[derive(Debug, Clone, PartialEq)]` 等按需，不堆砌。
- trait 保持内聚、最小接口；公开 API 补 `#\[doc\]` 文档，私有不强制。

## 5 注释与模块组织

- 函数/结构公开项写三斜杠文档 `///`；记录合约、Panic、错误、特性(features)。
- 模块文件：小型 crate 单 `lib.rs`，较大分 `mod` 子目录 + `pub use` 重导出。
- 内联注释讲 why；被悬挂的临时代码与格式用 `#[allow(...)]` 只给局部理由，不全局静默。

## 6 Clippy 与测试（实际运行）

- `cargo clippy -- -D warnings` 作为门禁；aggressive 类 lint 按需 `#[allow(clippy::...)]` 并注释原因。
- 单元测试 `#[cfg(test)] mod tests`；文档示例 `/// ```rust` 由 `cargo test` 编译执行。
- 提交前 `cargo fmt --check` + `cargo clippy -- -D warnings` + `cargo test` 全绿。

## 7 反例

```rust
// Bad：用 expect 放宽 Option 且无理由
let v = map.get(k).expect("should exist");

// Good：显式处理缺失，或给本条 expect 写清前提
let v = match map.get(k) { Some(x) => x, None => return Err(NotFound) };
```

```rust
// Bad：滥用 unsafe
unsafe { std::mem::transmute::<u32, f32>(bits) }

// Good：用安全数学/方法，确需时注明 SAFETY 不变量
```

> 未注明情形以 rustfmt 输出与 Rust API Guidelines（<https://rust-lang.github.io/api-guidelines/）为准；crate> 内约定优先。
