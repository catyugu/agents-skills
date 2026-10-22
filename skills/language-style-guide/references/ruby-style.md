# Ruby 风格参考

## 规范层级

Ruby 语言参考和标准库文档是语义来源；Ruby Style Guide（rubystyle.guide）和 RuboCop 默认规则是社区规范，不是 Ruby 官方强制格式。RuboCop 的规则可按项目配置启用、禁用或改写，不能把某一默认 cop 视为普遍真理。优先读取 `.rubocop.yml`、`.ruby-version`、Gemfile 和邻近代码。

## 格式与文件/模块组织

- 使用 2 个空格缩进，禁止 Tab；通常每行不超过 80–100 列，超长表达式按语义拆分。
- 类、模块、方法和顶层声明之间留一个空行；模块/类嵌套和 `private` 分区保持稳定。文件名使用 `snake_case.rb`，类/模块用 `CamelCase`，并让路径反映命名空间（如 `billing/invoice.rb`）。
- 方法、变量和文件用 `snake_case`；类、模块和常量用 `CamelCase`/`SCREAMING_SNAKE_CASE`。谓词方法以 `?` 结尾；带显著危险或原地修改语义的方法才使用 `!`，`!` 不是“会失败”的通用标记。
- Gem/library 入口应保持薄，依赖在明确边界加载；避免巨大的全局 helper 和循环 require。一个类应围绕一个职责，相关小型对象可放同一文件但不应模糊公共 API。

## 语言惯用法

优先使用迭代器和块（`map`、`select`、`each`）表达集合变换，但普通 `while`/`for` 在状态机或复杂控制流中可能更清楚。使用 guard clause 减少嵌套；不要为了“函数式”把简单流程改成难读的链。稳定标识可用 Symbol，但外部输入不可直接 `to_sym` 以免符号/内存问题。关键接口使用 keyword arguments 和小对象；元编程应局部、可测试并有理由。

## 错误处理与并发

只 rescue 具体的 `StandardError` 子类或更窄异常；不要 rescue `Exception`，也不要 rescue 后返回 `nil` 掩盖故障。异常用于真正异常或边界失败，不用于普通分支。确保文件、锁和连接在 `ensure` 或块 API 中释放。线程、Fiber、Ractor 和异步 gem 的安全模型不同；共享可变对象要明确锁或所有权，不要假定 Ruby 实现都提供相同并发保证。

## 注释、工具与示例

YARD 注释适用于公共库 API；注释应说明不变量、副作用、yield 契约和兼容性。推荐 RuboCop（格式与 lint）、RBS/Sorbet（若项目采用类型系统）、Bundler 和测试框架。

```ruby
# good: 谓词命名、guard clause、明确异常
class UserRepository
  def find!(id)
    user = User.find_by(id: id)
    raise NotFoundError, "user #{id} not found" unless user
    user
  end
end
```

```ruby
# bad: 捕获所有异常并静默返回
begin
  load_config
rescue Exception
  nil
end
```

来源：Ruby Style Guide（<https://rubystyle.guide/）；RuboCop（https://docs.rubocop.org/rubocop/）；Ruby> Documentation（<https://www.ruby-lang.org/en/documentation/）。>
