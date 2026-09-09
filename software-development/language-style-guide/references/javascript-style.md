# JavaScript / TypeScript 风格参考

## 规范层级

JavaScript 的语言层面没有唯一官方格式标准。ECMAScript 规范定义语义，不规定缩进、引号或换行；TypeScript Handbook 主要规定类型系统用法。格式部分应以仓库的 Prettier/ESLint 配置为准。Google JavaScript Style Guide、Airbnb JavaScript Style Guide 属于社区规范，不能当作 ECMAScript 强制要求。

## 格式与文件组织

- 默认 2 个空格缩进，禁止 Tab；一行尽量不超过 80–100 列，具体上限由 formatter 决定。
- 使用分号或不使用分号必须全项目一致；若无明确配置，优先让 Prettier 决定。字符串统一单引号或双引号，模板字符串仅用于插值或多行文本。
- 一个模块先写导入，再写类型/常量、实现、导出；导入按外部包、内部绝对路径、相对路径分组，并由 linter 排序。
- 文件名通常使用 `kebab-case`（如 `user-profile.ts`）；组件类项目可按框架约定使用 `PascalCase`。一个文件应有清晰职责，避免导出几十个无关符号。

## 命名与语言惯用法

变量、函数和属性用 `lowerCamelCase`；类、接口、类型和枚举用 `UpperCamelCase`；常量是否 `UPPER_SNAKE_CASE` 取决于项目，通常仅对真正不变的模块级值使用。布尔值使用 `is/has/can/should` 前缀。优先 `const`，需要重新绑定时用 `let`，新代码避免 `var`。使用 `===`/`!==`，不要依赖隐式类型转换；明确区分 `null`、`undefined` 和缺失属性。

TypeScript 中开启并保持 `strict`；公共函数显式写参数和返回类型，局部变量可依赖推断。避免 `any`，无法立即建模时用 `unknown` 并在边界收窄。优先联合类型、判别字段、泛型和 `readonly`，不要用类型断言掩盖设计问题。接口命名不强制 `I` 前缀；类型别名适合联合类型，`interface` 适合可扩展对象契约。

## 错误处理与并发

使用 `async`/`await` 管理 Promise，必须处理拒绝路径；不要在循环中无意间串行等待，也不要用 `Promise.all` 吞掉单项失败。需要保留部分成功时显式收集结果。只捕获能处理的错误，捕获后补充上下文或转换为领域错误；不要空 `catch`。导出 API 的文档应说明异常、拒绝、取消和副作用。避免共享可变模块状态；浏览器/Node 环境差异应在边界处说明。

## 注释、工具与示例

注释解释原因、协议约束、竞态和兼容性，不复述代码。公共 JS API 使用 JSDoc，TS 公共 API 可用 TSDoc。推荐 `prettier` 格式化、`eslint`/`typescript-eslint` 检查、`tsc --noEmit` 类型检查，并在 CI 固定版本。

```ts
// good: 未知输入先收窄，失败语义明确
async function loadUser(id: string): Promise<User> {
  const response = await fetch(`/users/${encodeURIComponent(id)}`);
  if (!response.ok) throw new UserLoadError(id, response.status);
  const value: unknown = await response.json();
  return parseUser(value);
}
```

```ts
// bad: any、隐式转换、吞错
async function load(id: any) {
  try { return await fetch('/users/' + id).then(r => r.json()); }
  catch (_) { return null; }
}
```

来源：ECMAScript（<https://tc39.es/ecma262/）；TypeScript> Handbook（<https://www.typescriptlang.org/docs/）；Google> Style（<https://google.github.io/styleguide/tsguide.html）；Prettier（https://prettier.io/docs/）；ESLint（https://eslint.org/docs/latest/）。>
