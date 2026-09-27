---
name: zotero
description: "Use when automating Zotero: read, import, export, cite."
---

# Zotero 文献管理自动化

Zotero 桌面端在 `127.0.0.1:23119` 暴露两个 HTTP 服务，配合起来可以完全脚本化：

| 需求 | 通道 | 是否需要用户授权 |
|---|---|---|
| 读条目/分类/标签/全文、导出 BibTeX/RIS/CSL JSON、取 PDF 路径 | Local API `/api/users/0/...` | 否 |
| 新增条目（BibTeX/RIS/CSL JSON 文本 → 库） | Connector `/connector/import` | 否 |
| 修改/删除已有条目 | Local API 写请求 `POST/PUT/PATCH/DELETE` | 是，运行时弹窗授权 |
| 跨机同步、群组库、无需桌面端在线 | Web API + API key | 需 zotero.org 上的 key |

## 一次性开关（Local API 默认关闭）

必须在 Zotero → 设置 → 高级 → 勾选 “Allow other applications on this computer to communicate with Zotero”。
未开启时 `/api/...` 返回 403 且 body 为 `Local API is not enabled`。
脚本化启用方式：向 **Roaming** profile 目录写 `user.js`，内容 `user_pref("extensions.zotero.httpServer.localAPI.enabled", true);`，然后重启 Zotero。
注意 `user.js` 每次启动都会覆盖该 pref（GUI 里关掉也会被重新打开），不需要时删除文件即可。

profile 目录不一定是 `%LOCALAPPDATA%`：从 `%APPDATA%/Zotero/Zotero/profiles.ini` 的 `Path=` 定位；实际数据目录看 profile `prefs.js` 里的 `extensions.zotero.dataDir`。

## 关键契约

- 只支持 API version 3，请求带 `Zotero-API-Version: 3`；响应头 `Zotero-Server-ID` 标识当前库实例。
- 用户 ID 固定传 `0` 即代表本地登录用户。
- 读请求无认证；写请求需 `POST /api/local/authorize` 拿 key（`Zotero-API-Key` 头），且必须同时带 `Zotero-Server-ID` 头，否则 428。
- 授权弹窗返回 `remember: true`（用户点 Always Allow）时 key 可长期重用，否则为一次性，401 后需重新授权。
- 响应默认不分页，一次返回全部匹配对象；`Total-Results` 头给出总数。
- `/connector/*` 端点要求 `X-Zotero-Connector-API-Version: 3` 头，否则来自浏览器 UA 的请求会被静默拒绝。
- 写请求（含 DELETE）还必须带 `If-Unmodified-Since-Version: <库版本>`，否则返回 428；库版本取任一同期读响应的 `Last-Modified-Version`。

## 脚本

`scripts/zot.py`，纯标准库、零依赖（PEP 668 环境下不要为此装包）：

```
python zot.py status
python zot.py list --type journalArticle --tag 机器学习 --limit 20
python zot.py get <itemKey>
python zot.py export bibtex -o refs.bib
python zot.py collections | tags
python zot.py text <itemKey>        # 附件全文（已索引的 PDF）
python zot.py file <itemKey>        # 附件本地路径
python zot.py add --doi 10.1145/3292500.3330701
python zot.py add --bibtex refs.bib
python zot.py delete <itemKey>      # 首次会弹授权窗
```

`add` 走 `/connector/import`，由 Zotero 自带的 BibTeX/RIS 翻译器解析，元数据质量与桌面端一致，且**不需要授权**。DOI 先经 doi.org content negotiation 换成 BibTeX 再导入。

## 元数据核对

批量核对（例如清理“待核对”标签）按 `references/metadata-audit.md` 走：先落盘条目 JSON 做结构化体检，再对 DOI 走 CrossRef、对图书读附件版权页（扫描件渲染读图），最后只 PATCH 有证据的字段并读回验证。

## 陷阱

- `/connector/import` 每次请求都要带唯一的 `session` 查询参数；复用同一或缺省 session 会返回 409 `SESSION_EXISTS`（Zotero 端的 session 会存活约 10 分钟）。
- 新增条目会进入 Zotero 界面**当前选中**的分类（`getSaveTarget`），不是根目录；需要指定位置时先在 UI 选好分类。
- Zotero 运行中不要直接改 `zotero.sqlite`；`zotero.sqlite-wal` 可能远大于主库，主文件是旧快照。
- 附件正文只有 Zotero 已索引的条目才有 `fulltext`，扫描版 PDF 返回空。
- 中文文献的知网/中文元数据建议配合 jasminum（茉莉花）插件，`translators_CN.json` 在数据目录下。
