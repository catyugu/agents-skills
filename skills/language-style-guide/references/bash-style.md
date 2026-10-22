# Bash 风格参考

## 规范层级

Bash Reference Manual 是 Bash 语义的官方来源，但没有统一的官方格式规范。Google Shell Style Guide 是社区/公司规范，ShellCheck 是静态分析工具；POSIX Shell 规范与 Bash 扩展是不同目标。脚本必须明确自己是 Bash 还是可移植 `sh`，并优先服从项目 CI、ShellCheck 配置和目标运行环境。

## 格式与文件组织

- Bash 脚本使用 `#!/usr/bin/env bash`（若部署环境固定，也可使用明确路径）；每级缩进 2 个空格或 4 个空格，项目内统一。建议控制行宽在 80–100 列。
- 文件组织建议为 shebang、简短说明、严格模式/选项、常量、函数、参数解析、`main` 调用。可执行脚本的主流程放进 `main`，避免加载文件时立即产生副作用。
- 变量和函数通常用 `snake_case`；环境变量、只读常量和导出接口用 `UPPER_SNAKE_CASE`。局部变量必须 `local`；名称表达路径、单位和是否数组。函数应说明 stdout 输出、返回码和副作用。

## 语法与安全惯用法

默认引用参数和命令替换：`"$var"`、`"$(command)"`；只有刻意进行分词/通配时才不加引号。参数列表使用数组：`cmd -- "${args[@]}"`，不要拼接字符串再 eval。使用 `[[ ... ]]`、`(( ... ))` 和 `case`；数字比较不要依赖字符串运算。使用 `printf` 不用不一致的 `echo`，读取文本用 `IFS= read -r`。不要把 Bash 脚本标成 `sh`。

`set -e` 的行为有例外，不能代替设计错误处理；按脚本契约选择 `set -u`、`set -o pipefail`，并测试 unset 变量和条件命令。不要以 `|| true` 隐藏失败，除非明确记录“失败可忽略”的原因。临时文件用 `mktemp`，路径和用户输入一律引用；敏感值避免出现在命令行和日志。

## 错误处理、并发与清理

在有意义的边界检查命令返回值，错误输出写 stderr（`printf '%s\n' ... >&2`）。用 `trap` 清理临时目录、锁和子进程，且 trap 靠近资源创建处。后台任务保存 PID，并用 `wait` 检查退出码；避免竞争写同一文件，必要时使用锁工具。管道错误要能传播，命令替换失败不可悄悄变成空字符串。

## 注释、工具与示例

注释解释兼容性、退出码、信号和清理原因。推荐 ShellCheck、shfmt、Bats-core；CI 中固定 shell 版本并运行真实脚本测试。

```bash
#!/usr/bin/env bash
set -u -o pipefail

copy_file() {
  local src=$1 dst=$2
  [[ -f "$src" ]] || { printf 'missing: %s\n' "$src" >&2; return 1; }
  cp -- "$src" "$dst"
}
```

```bash
# bad: 未引用分词、eval 注入风险、忽略失败
file=$1; eval "rm $file" || true
```

来源：Bash Manual（<https://www.gnu.org/software/bash/manual/）；Google> Shell Style Guide（<https://google.github.io/styleguide/shellguide.html）；ShellCheck（https://www.shellcheck.net/）；POSIX> Shell（<https://pubs.opengroup.org/onlinepubs/9699919799/utilities/contents.html）。>
