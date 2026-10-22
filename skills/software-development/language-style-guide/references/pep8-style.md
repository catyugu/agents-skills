# PEP 8 — Python 风格落地细则

Source: <https://peps.python.org/pep-0008/>
配套：black/ruff-format 或项目 formatter + ruff/flake8；先读仓库配置。

## 1 缩进

- 一级 4 空格，禁 Tab；括号内隐式续行，不用反斜杠续行。
- 悬挂缩进加 4 空格区分内容；`.pyproject`/`.editorconfig` 常覆盖 max-line。

## 2 行宽

- 代码 max 79 列，docstring/注释 max 72（PEP 8 默认）；
  项目常见放宽为 black 的 88 或 ruff 配置的更大值——以仓库设置为准。
- 在最高语法层断开（逗号/二元运算符后），用括号隐式续行。

## 3 命名

| 对象                | 规则                    | 示例                                      |
| ------------------- | ----------------------- | ----------------------------------------- |
| 包/模块             | `lower_with_underscore` | `my_package`, `utils.py`                  |
| 类/异常/类型别名    | `CapWords`              | `HTMLElement`, `class MyError(Exception)` |
| 函数/方法/变量      | `lower_with_underscore` | `get_data()`, `item_count`                |
| 常量                | `UPPER_SNAKE`           | `MAX_TIMEOUT`                             |
| 受保护              | `_single`               | `_internal()`                             |
| 私有(name-mangling) | `__double`              | `__cache`                                 |
| Magic               | `__x__`                 | `__init__`, `__len__`                     |
| 类型变量            | `Cap`                   | `T = TypeVar('T')`                        |

- 布尔名用 `is_/has_/can_/should_` 前缀；避免 `l`、`O`、`I` 易混字符；异常类后缀 `Error`。

## 4 import

- 每行一个 import；分组（段间空行）：`__future__` → 标准库 → 第三方 → 本地。
- `from x import a` 优于 `import x.a` 再全名；别名仅用于惯用缩写（如 numpy as np）。
- 相对 import 仅在包内使用；`__all__` 明确公开 API。

## 5 空白

- 赋值/比较/二元运算两侧各一空格；紧跟 `,`/`;`/`:` 不加。
- 切片 `a[1:5:2]` 不加空格；默认参数 `def f(a, b=None)` 等号两侧不加。
- 多重赋值可省略：`x = y = z`；函数实参 `f(a, *, b=1)` 中 `*` 后不加空格。

## 6 空行

- 顶层函数/类之间 2 空行；类内方法之间 1 空行。
- 用空行在函数中分隔逻辑块，但不过度；`# fmt: off/on` 慎用。

## 7 注释与 docstring

- 块注释 `# 文本`（# 后一空格）；与代码同缩进；完整句 + 句号。
- 注释说 why：解释约束、权衡、性能、不变量；不做代码逐行翻译。
- 行内注释：语句后至少 2 空格，再 `#`。
- docstring 用 `"""..."""`；模块/函数/类各写一行为主的摘要，随后：
    - 功能/异常/返回的语义；必要时 `Args:`/`Returns:`/`Raises:` 区块（Google/Numpy 风格需按仓库）。

## 8 推荐写法（对应 PEP 8 Programming Recommendations）

```python
# Good：真值判断
if items:            # 而非 if len(items) >
if not items:
if value is None:    # 不用 == None
```

```python
# Good：可变默认参数
def f(items=None):
    items = [] if items is None else items
    return items
```

```python
# Good：捕获具体异常
try:
    value = mapping[key]
except KeyError:
    value = default
```

```python
# Good：类型注解（PEP 484 / 585）
def total(seq: list[float]) -> float:
    return sum(seq)
```

- 对比用 `isinstance(obj, int)` 而非 `type(x) is int`。
- 前缀/后缀判断用 `s.startswith('x')` / `s.endswith('y')`，不用切片。
- 字符串格式化：优先 f-string；`%`/`.format` 仅在复杂情形；绝不 `+` 拼串。
- 避免 `lambda` 赋值给变量；简单时用 comprehension 替代 `map`/`filter`+lambda。

## 9 常见工具（实际运行验证）

- `black` / `ruff format`：自动化排版；`ruff check`：lint，`ruff check --fix` 自动修。
- `flake8 --max-line-length=88`：按仓库限长；`mypy`/`pyright`：类型检查。
- `.editorconfig` 提供空格/UTF-8/限长；提交前跑测试 + lint 全绿。

## 10 反例（Good / Bad）

```python
# Bad
if foo == True:
if len(a) > 0:
if result == None:
def f(x): return x * 2

# Good
if foo:
if a:
if result is None:
def f(x):
    return x * 2
```

> 未注明极端情况以官方 PEP 8 及项目工具配置为准；PEP 8 强调"规则可被知情打破"——当 lint/风格与可读性冲突时给出注释理由即可。
