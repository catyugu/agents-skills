---
name: kokkos-cpp-library-skeleton
description: Use when scaffolding a C++20 Kokkos/CMake library.
---

# C++20 + Kokkos + CMake 库骨架

适用：新建或扩展一个以 Kokkos 为执行/内存模型的 C++20 科学计算库（CMake + CPM + GoogleTest + Ninja）。

## 落地顺序

1. 目录：`cmake/`、`include/<lib>/<module>/`、`src/`、`tests/`、`examples/`、`benchmarks/`、`docs/`。
2. 顶层 `CMakeLists.txt`：`cxx_std_20`、`CMAKE_EXPORT_COMPILE_COMMANDS`、`CMAKE_BUILD_TYPE` 兜底、
   `configure_file(cmake/config.h.in -> ${CMAKE_BINARY_DIR}/config.h)`、`GNUInstallDirs`、
   `include(cmake/<lib>Options.cmake)` 与 `include(cmake/Dependencies.cmake)`，最后配置安装导出。
3. 选项拆两个 INTERFACE 目标：
   - `<lib>_options`：使用方必须满足的条件（`cxx_std_20`、`/permissive- /utf-8 /bigobj`、linux `-fPIC`、`<LIB>_DEBUG` 宏）
   - `<lib>_warnings`：`-Wall -Wextra -Wpedantic -Werror` / `/W4 /WX`，只以 PRIVATE 链接，
     避免把警告需求通过 PUBLIC 传播给使用方。
4. 依赖集中在 `cmake/Dependencies.cmake`：CPM 拉取 Kokkos 与 googletest，Kokkos 版本用 `CACHE STRING` 固定，
   并给一个 `-D<LIB>_USE_SYSTEM_KOKKOS=ON` 分支走 `find_package(Kokkos)`。

## 已验证的坑

- `install(EXPORT)` 要求所有被导出目标的依赖也在导出集内：`<lib>_options` / `<lib>_warnings`
  必须一起 `install(TARGETS ... EXPORT <lib>Targets)`，否则 configure 阶段直接报错。
- Kokkos 由 CPM 加入时，其自身安装规则会随 `cmake --install` 一起导出（`lib/cmake/Kokkos`），
  因此 `<lib>Config.cmake` 里写 `find_dependency(Kokkos)` 对使用方可行；用系统 Kokkos 构建时使用方需自备。
- clang-cl + MSVC ABI：消费端项目必须与安装时相同的 `CMAKE_BUILD_TYPE`，
  否则链接报 `_ITERATOR_DEBUG_LEVEL` mismatch。
- googletest 自带 `/WX`：较新 clang 上其 char8_t 打印路径触发 `-Wcharacter-conversion`，
  在依赖声明里给 gtest 目标补 `-Wno-character-conversion`（PRIVATE），不要放宽自己的告警。
- 把 KokkosKernels 也当必需依赖时，Windows 上不能用 clang++（GNU 驱动）：KokkosKernels 按
  `CMAKE_CXX_SIMULATE_ID == MSVC` 判定 MSVC ABI，而 clang++ 的 SIMULATE_ID 同样是 MSVC，于是它注入
  MSVC 语法开关 `/EHsc`，GNU 驱动把 `/EHsc` 当输入文件报 `no such file or directory: '/EHsc'`。
  在 Dependencies.cmake 里前置检查 `WIN32 + Clang + FRONTEND_VARIANT != MSVC` 并 FATAL_ERROR 给出替代方案
  （clang-cl / cl.exe / 系统包），不要让错误以依赖内部的编译命令形式出现。
- 显式传 `CMAKE_CXX_COMPILER` 时 CMake 不会把选择镜像到 C 编译器：googletest 的 `project()` 未声明
  LANGUAGES，会顺带探测 C，PATH 里的 `clang.exe`(GNU) 配上 MSVC 风格默认 flag 直接让 C 编译器探测失败。
  C 与 C++ 一起显式指定。
- 编译数据库里只有依赖（CPM 拉取的 Kokkos/KokkosKernels）源文件的条目时，clangd 对 include/ 下的公共头文件
  会退回任意一条命令，丢掉 `-I<repo>/include`，报 `pp_file_not_found` 误报。用仓库根 `.clangd` 修：
  `If: PathMatch: include/.*` + `CompileFlags.Add: -I../include`（相对路径按编译命令的工作目录即 CMake
  二进制目录解析，所以构建目录必须是一级的 `build/`）。验证：`clangd --check=<header>` 应输出
  `All checks completed, 0 errors`。
- 测试二进制若用 Kokkos：不要链 `gtest_main`，自己写 `main`：`InitGoogleTest` → `Kokkos::initialize` → `RUN_ALL_TESTS` → `finalize`。
- 无独立 clang-format 时用 `uv tool run --from clang-format clang-format -i ...`；格式化后重新构建确认未引入告警。

## 内核与基准

- 热路径只允许 `Kokkos::parallel_for/parallel_reduce` + `KOKKOS_LAMBDA`，lambda 内按值捕获 `Kokkos::View`。
- 只在默认内存空间是主机内存时才与手写循环对比，用 `if constexpr (std::is_same_v<Memory, HostMemory>)` 守卫，
  不要打印未测量的伪数据。
- 基准取多轮中的最优一轮（共享/虚拟化机器单次测量波动可达 30%），同时打印裸 Kokkos kernel 与手写循环
  作为零开销抽象的对照，并带可从解析解校验的 checksum。
- 首次测得的异常慢值（比手写循环低一个数量级）通常是机器状态噪声，先重复测量再下结论。

## 验证清单

- Debug 与 Release 都配置、构建、`ctest` 通过；构建零警告（`/WX`、`-Werror` 生效）。
- 示例输出与解析解一致；基准给出可复现带宽。
- `cmake --install --prefix <tmp>` 后，用一个独立的 `find_package(<lib>)` 小项目做消费端 smoke test。
