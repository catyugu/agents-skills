# C 风格参考

## 规范层级

ISO C（如 C17/C23）规定语言和库语义，不规定团队的缩进、命名和行宽。GNU C Coding Standards、Linux kernel CodingStyle、CERT C 是不同目标下的参考：GNU 偏向 GNU 项目，Kernel 规则针对内核，CERT 关注安全；都不是所有 C 项目的官方强制标准。必须优先服从仓库 `.clang-format`、编译器版本和本地 API 约定。

## 格式与文件/模块组织

- 推荐 4 个空格缩进；Linux kernel 使用 Tab（每级约 8 列）。项目一旦选定不可混用。行宽常见目标是 80–100 列，但不是 ISO 要求。
- 控制语句和函数定义使用一致的大括号风格；多行条件按逻辑层级对齐。即使只有一条语句也建议加括号，避免后续修改引入错误。
- 头文件使用 include guard 或 `#pragma once`（后者为常见编译器扩展）；头文件只暴露必要声明。源文件内部辅助函数和变量使用 `static`，减少链接可见性。
- 按“公共头文件、私有头文件、标准库、系统库、项目头文件”分组（若项目工具另有排序则遵循工具）。模块接口写在 `.h`，实现写在 `.c`；避免头文件包含实现细节和循环依赖。

## 命名与类型

函数和变量通常用 `snake_case`，宏和编译期常量用 `UPPER_SNAKE_CASE`，类型可用项目统一的 `_t` 或不带后缀；不能把社区习惯写成 C 语言硬规则。名称应体现单位、所有权和生命周期，如 `timeout_ms`、`buffer_len`。避免单字母变量，循环索引等局部例外应保持短作用域。使用 `size_t` 表示对象大小，使用固定宽度整数时包含 `<stdint.h>` 并说明协议/存储语义；注意有符号与无符号比较。

优先 `const`、枚举和 `static const`，谨慎使用宏。不要用 `typedef` 隐藏指针或数组复杂性；指针星号的空格按本项目统一。函数参数、返回值和结构体字段应表达空指针、所有权及是否可修改的契约，必要时在头文件注释中写明。

## 错误处理、资源与并发

检查 `malloc`、文件、系统调用和库函数返回值；错误路径要释放已获得资源并保留原始错误信息。常见模式是单一 `goto cleanup` 出口，但标签应按逆序释放资源，且不能跳过变量初始化。不要把 `-1`、`NULL` 等哨兵与有效值混淆。不要静默忽略编译器警告；每个 suppress 都应有局部理由。

共享状态需明确锁、原子操作或线程所有权；`volatile` 不是线程同步原语。信号处理器中只调用异步信号安全函数。避免数据竞争、未定义行为、越界和 use-after-free；可用 ThreadSanitizer、AddressSanitizer 和静态分析验证。

## 注释、工具与示例

注释解释硬件约束、布局、锁顺序、未定义行为规避和算法不变量。公共头文件可采用 Doxygen，但不要生成与代码不符的文档。推荐 `clang-format`、`clang-tidy`/Cppcheck、`-Wall -Wextra -Wpedantic`（具体告警集依项目调整）和 sanitizer。

```c
/* good: 检查溢出并由调用方拥有返回缓冲区 */
int read_name(char *dst, size_t dst_size, const char *src);

int copy_name(char *dst, size_t dst_size, const char *src) {
    size_t len = strlen(src);
    if (len >= dst_size) return -1;
    memcpy(dst, src, len + 1);
    return 0;
}
```

```c
/* bad: 无界复制，且忽略失败 */
void copy(char *dst, const char *src) { strcpy(dst, src); }
```

来源：ISO C 概览（<https://www.iso.org/standard/74528.html）；GNU> 标准（<https://www.gnu.org/prep/standards/）；Linux> CodingStyle（<https://www.kernel.org/doc/html/latest/process/coding-style.html）；CERT> C（<https://wiki.sei.cmu.edu/confluence/display/c）。>
