# PHP 风格参考

## 规范层级

PHP-FIG PSR-12 是社区维护的编码风格规范，不是 PHP 核心语言的官方强制标准；它扩展 PSR-1，项目可通过 PHP-CS-Fixer 或 PHP_CodeSniffer 执行。PHP 版本手册决定语法与标准库行为，框架（Laravel、Symfony 等）可能有更具体规则。先服从 `composer.json`、`.php-cs-fixer.php`、`phpcs.xml` 和 CI。

## 格式与文件/模块组织

- 使用 4 个空格缩进，禁止 Tab；每行目标不超过 120 列，PSR-12 对超过 120 列建议拆分但不是所有项目都硬性禁止。
- PHP-only 文件使用 `<?php`，UTF-8 无 BOM，文件末尾不写 `?>`。命名空间声明后是导入，再是声明；导入不使用无必要的通配式替代。
- 类、接口、trait、枚举使用 `StudlyCaps`；方法、属性和局部变量使用 `camelCase`；类常量通常 `UPPER_SNAKE_CASE`。布尔值使用 `is/has/can/should`。变量名表达单位、可空性和所有权。
- 通常一个文件放一个主要类/接口/trait；按领域或层组织目录，而不是建立无限大的 `Utils`。公共符号最小化可见性，默认使用 `private` 或 `final`（是否默认 final 属于项目政策）。

## 语言惯用法

启用 `declare(strict_types=1);` 是否强制取决于项目，但新库通常应统一采用并在边界明确类型。为参数、返回值、属性和集合元素提供类型；避免 `mixed` 和无理由的 `@var` 类型欺骗。使用 `===`/`!==`，不要依赖松散比较的类型转换。用值对象、枚举、不可变 DTO 表达领域约束；不要用数组承载结构稳定却无类型的对象。

## 错误处理与并发

对可预期失败抛出具体异常或返回明确的结果类型；不要用 warning、`@` 抑制符或空 catch 作为正常流程。只捕获能够恢复或添加上下文的异常，记录时避免密码、令牌和个人数据。资源应使用 `finally` 或库提供的作用域 API 清理。PHP-FPM、队列 worker 和长生命周期进程中的共享状态/静态缓存必须有生命周期说明；并发模型依运行时（进程、线程、Fiber、异步扩展）而定，不应假定全局变量安全。

## 注释、工具与示例

PHPDoc 用于泛型、数组形状、外部契约和公共 API；注释解释原因、兼容性和安全约束。推荐 PHP-CS-Fixer/PHPCS、PHPStan 或 Psalm、PHPUnit，并在 CI 固定 PHP 与依赖版本。

```php
<?php
declare(strict_types=1);

function parseLimit(string $raw): int
{
    $limit = filter_var($raw, FILTER_VALIDATE_INT);
    if ($limit === false || $limit < 1) {
        throw new InvalidArgumentException('limit must be positive');
    }
    return $limit;
}
```

```php
// bad: 松散比较、无类型、静默失败
function limit($value) { return $value == '' ? 0 : (int) $value; }
```

来源：PSR-12（<https://www.php-fig.org/psr/psr-12/）；PHP> Manual（<https://www.php.net/docs.php）；PHPStan（https://phpstan.org/user-guide/getting-started）；PHP_CodeSniffer（https://github.com/PHPCSStandards/PHP_CodeSniffer）。>
