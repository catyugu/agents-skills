# Effective Go / 官方 Go 风格细则

Source: <https://go.dev/doc/effective_go>
工具：`gofmt`（格式为准）、`goimports`（导入排序）、`go vet`（静态检查）；CI 跑 `gofmt -l` 拒绝未格式化。

## 1 格式化与布局

- 缩进 1 Tab；行长无硬上限（以 gofmt 断行结果为准）。
- 语句用冒号分组、`if`/`for`/`switch` 大括号必需；函数体首行 `{`。
- 用 `gofmt`/`goimports` 统一，不手排；`go vet` 修真实缺陷而非装饰。

## 2 命名

| 对象     | 规则                      | 示例                          |
| -------- | ------------------------- | ----------------------------- |
| 包       | 短小、小写、无下划线      | `http`, `errors`, `atomic`    |
| 导出标识 | `MixedCaps`，首字母大写   | `ComputeHash`, `MaxSize`      |
| 私有标识 | `mixedCaps`，小写开头     | `cachedValue`                 |
| 常量     | `MixedCaps`（不用全大写） | `const MaxReplyLines = 20`    |
| 接口     | 单方法以 `-er` 后缀       | `Read`, io.Reader, `Stringer` |

- 包名避免冗余重复包名（`http.Server` 而非 `http.HTTPServer`）；用 `Self` 谦让命名冲突。

## 3 文件与包组织

- 一包一目录；小文件聚焦单一职责（`decode.go` 等按职责拆分）。
- 导出从使用方出发：只导出必须被外部使用的符号；`_test.go` 同包或 `_test` 包均可。
- `init()` 尽量减少/清理由：全局副作用需显式；别依赖 init 顺序。

## 4 控制流与惯用

- 尽量少用 `else`：依早期 return 减少嵌套。

```go
// Instead of
// func f(x int) string { ... if x>0 { return "p" } else { return "n" } }
func f(x int) string {
    if x > 0 { return "p" }
    return "n"
}
```

- 用 `range` 惯用：`for i, v := range xs`；索引仅按需。
- 空判断直接 `if len(s) == 0` 或 `if s == ""`（依语义，不用非惯用技巧）。
- 用 `:=` 简短声明；类型显式仅在需要时（`var n int64 = 1`）。

## 5 错误处理（核心）

- 错误是值：`return fmt.Errorf("load config: %w", err)` 用 `%w` 包装以支持 `errors.Is/As`。
- 用 `errors.Is(err, io.EOF)`、`errors.As` 处理分类，不靠字符串匹配。
- `panic` 仅表达编程错误；常规失败用 error。`errors.New` 建简单哨兵错误。
- 若函数可能失败，错误值放最后返回值：`func ReadAll(r io.Reader) ([]byte, error)`。

## 6 类型、接口、组合

- 定义语义能力强的小接口，由调用方声明所需最小接口；实现方默认返回具体类型。
- 零值偏好：设计使零值安全可用（如 `var buf bytes.Buffer`）。
- 组合优先继承：结构体内嵌共享行为；`sync.Once`/`sync.Mutex` 内嵌时指出需指针/值语义。
- 公开字段/方法用 `// 注释` 文档（首词为符号名）。

## 7 并发

- 用 goroutine + channel 表达并发；明确 goroutine 的所有权、取消（context.Context）、退出时机。
- 避免启动后不关闭的 goroutine；用完 `close(ch)` 或配合 `for range` 收敛。
- 数据竞争用 `go run -race`/`go test -race` 检测。

## 8 注释与示例

- 包级、导出标识、公共 API 写文档注释；补 `func ExampleEval()` 到 `_test.go` 做可执行示例。
- 注释讲 why（性能、并发、边界）；不做句子逐行翻译。

## 9 反例

```go
// Bad：字符串错误、无条件 panic
panic("bad input")

// Good：返回可传递、可包装的 error
err := validate(v); if err != nil { return fmt.Errorf("validate: %w", err) }
```

```go
// Bad：宽接口暴露实现细节
func Handle(r *bigRepository struct) 

// Good：调用方最小接口
func Handle(r interface{ Sum() int }) 
```

> 更细规则见 Go Code Review Comments（<https://go.dev/wiki/CodeReviewComments）与> Effective Go；仓库 gofmt/vet 配置优先。
