# Google C++ Style Guide — 落地细则

Source: <https://google.github.io/styleguide/cppguide.html>
配套：clang-format 配置 + cpplint.py；先以仓库 .clang-format 为准。

## 1 缩进与行宽

- 空格缩进，默认 2 空格（Google 风格）；禁用 Tab。
- 行长 ≤ 80 列；超长用括号/续行符按最高语法层断开。
- 大括号：函数定义换行另起 `{`；控制语句如 `if`/`for` 大括号与语句同行。
- 指针/引用：类型与 `*`/`&` 之间加空格，即 `T* p`（`*` 靠类型）。命名参数为 `const T& arg`。
- 用 clang-format（.clang-format: Google 预设）统一，不要手写排版。

## 2 命名

常量性项目名：文件/类型 → snake_case；变量/函数按命名空间以下表格。常用 Google 风格表：

| 对象                                    | 规则                         | 示例                        |
| --------------------------------------- | ---------------------------- | --------------------------- |
| 类型（class/struct/enum/typedef/using） | 每个单词首字母大写，无下划线 | `MyClass`, `GameLoop`       |
| 变量（含局部、成员、形参）              | 小写下划线                   | `table_name`, `buffer_size` |
| 常量（constexpr/const 存储期）          | k 开头 + Camel               | `kDaysInWeek`               |
| 函数                                    | 小写下划线                   | `open_file()`, `set_name()` |
| 命名空间                                | 全小写，用分隔分组           | `my_namespace`, `absl`      |
| 宏（尽量少用）                          | 全大写下划线                 | `MY_MACRO_H_`               |

- 成员变量（Google 风格）保持小写下划线，不加下划线后缀（非 required）。

## 3 文件组织

- 头文件自包含，用 `#pragma once` 或 `#ifndef BASE64_H_ / #define ... / #endif` 保护。
- 头文件只放：声明、inline、模板、常量；实现放 `.cc`。`.h/.cc` 成对存在。
- Include 顺序（每段内字母序，段间空行）：
  1) 本文件同名头 → 2) C 库 → 3) C++ 标准库 → 4) 其他库 → 5) 项目头。
- 头文件不做内链；`.cc` 中用匿名命名空间或 `static` 做内链。

## 4 作用域、类型、指针

- 变量在最窄作用域、声明即初始化：`int i = f();` 而非先声明后赋值。
- 用 `int` 处理一般算术；需要固定宽度再用 `int64_t`/`uint32_t`；浮点用 `float`/`double`，禁 `long double`。
- 指针用 `nullptr`，字符用 `'\0'`。`auto` 只在使代码更清晰或安全时用，不为省字。
- 优先 `std::string_view` 代替 `const char*`；优先 `std::optional`/`std::variant` 表达可选/联合。

## 5 类与继承

- 数据成员默认 `private`；要暴露用访问器。
- 构造/析构不要调用虚函数；单参构造和转换运算符标 `explicit`。
- 拷贝/移动：不可拷贝就显式 delete；移动语义 `noexcept` 并保证安全。
- 组合优先于继承；非基类标 `final`；虚函数重写一律 `override`。

## 6 函数与参数

- 函数 ≤ ~40 行；醒目优先小函数。单行逻辑例外。
- 只读形参前置，输出形参后置；优先返回值而非输出参数。
- 非虚函数在对默认值确定是常量时才给默认实参；避免因默认实参产生的歧义重载。
- 输入+输出参数常可用引用 `T&`，需明确传递所有权用指针。

## 7 智能指针与 RAII

- 所有权转移/独占用 `std::unique_ptr`；共享需明确 justify 才用 `std::shared_ptr`；坚决不用 `auto_ptr`。
- 全局/静态对象禁用非平凡析构；`<stuff>` 用 constinit/constexpr。
- 资源获取即初始化（RAII），文件/互斥/锁都用 guard 对象；不要手动 `new`/裸 `delete`。

## 8 错误与异常

- Google 风格默认禁用异常；边界项目可能有异常，需读仓库配置。用错误码、`absl::Status`、`std::optional` 表达失败。
- 用 `status` / `result` 类型传递可恢复错误，不用异常做常规跳转。
- 宏尽量不用；需常量/枚举/内联函数替代宏若语义等价。

## 9 运算符与 misc

- 二元运算符非成员函数；需相等语义就定义 `operator==`，C++20 用 `operator<=>`。
- 不重载 `&&`/`||`/`,`/一元 `&`/字面量。
- 用 cpplint.py / clang-tidy 检查；修 warning 视为修复缺陷而非装饰。

## 10 反例（Good / Bad）

```cpp
// Bad：未初始化、原样裸指针、用输出参数
void fill(std::string* out); std::string s; fill(&s);

// Better：返回结果，声明即初始化
std::string s = make_name();           // 或返回值前置参数
auto name = compute_name();            // 清晰且安全
```

```cpp
// Bad：共享所有权滥用 + 全局非平凡析构
static std::shared_ptr<Foo> g_foo = std::make_shared<Foo>();

// Good：函数内临时、合理所有权
Foo make_foo() { return Foo{}; }
```

> 本页给出可执行、可量化的规则；未注明的极端规则以官方 Google C++ Style 原文为准，冲突时仓库 .clang-format/cpplint 配置优先。
