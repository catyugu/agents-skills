# Zotero 元数据逐条核对流程

**核心原则**：以原始出处为最高证据，不用二手聚合站。证据等级：

1. 条目自带附件的书名页/版权页/CIP 页（最可信，可离线复现）
2. 出版社官网页（ISBN、页数、版次、出版日期）、CrossRef（DOI 注册数据）、arXiv API、IEEE 事件名
3. 图书馆书目（K10plus SRU、OpenLibrary）、豆瓣（中文书的出版年/页数）

## 流程

1. **落盘**：`/items/top` 或 `/items?tag=<标签>` 全量拉 JSON 存文件，后续比对都在本地做，避免重复请求。
2. **结构化体检**：按 itemType 列表检查必填与常用字段（DOI / publicationTitle / volume / pages / proceedingsTitle / conferenceName / repository / ISBN / numPages / creators 是否有 lastName 或单字段 name）。逐项打印缺字段，先定位可疑条目。
3. **DOI 类条目**：`https://api.crossref.org/works/<doi>`（带 mailto 的 UA 进 polite pool）比对标题（比较时忽略大小写与标点）、作者顺序、container-title、volume/issue/page、event.name/location/start-end。
4. **arXiv 类条目**：`export.arxiv.org/api/query?id_list=<id>` 核对标题、作者、日期。
5. **图书**：用 pymupdf 读附件前几页与版权页，搜 `ISBN`、`edition`、`copyright` 定位；**扫描版无文字层**时把候选页 `get_pixmap(dpi=120~300)` 渲染成 PNG，拼 contact sheet 后用视觉模型逐页辨认，再对目标页高分辨率复核。
6. **中文图书**：版权页 CIP 数据是权威源；**页数 = 印张 × 开本分母**（如 787×960 1/16 印张 49.25 → 788 页）；ISBN 校验位单独验算。
7. **单项校验**：ISBN-13 用加权和（1,3 交替）mod 10 == 0；DOI 用 `HEAD https://doi.org/<doi>` 看是否解析成功。

## 写回

- `PATCH /items/<key>`，body 只含要改的字段；成功返回 204（脚本里表现为 `None` + 无 status），**必须读回同一条目逐字段确认**。
- 清空标签：`{"tags": []}`；标签是整体覆盖，不是增量。
- 只改有证据的字段；多源冲突且无权威值时**保留原值并在报告中说明**，不要凭猜改数。

## 批量清理的两类常见脏数据

- **arXiv 导入噪声标签**（DOI 为 `10.48550/*` 的条目最常见）共三类，都是 arXiv 元数据被逗号切碎的结果，可整条清空：FOS 分类片段（`FOS: Computer and information sciences`、以及被切碎的 `electronic engineering`/`information engineering`/`Finance`/`and Science (cs.CE)`/`Computational Engineering`）、arXiv 分类名（`Machine Learning (cs.LG)`、`Numerical Analysis (math.NA)`、`Systems and Control (eess.SY)` …）、MSC 码（`41A45`、`65N30` …）。清空后 `/tags` 里计数归零即彻底消失。
- **conferencePaper 缺 conferenceName**：用出版社登记的会议全名（CrossRef `event.name`，IEEE/ACM 都提供，含届次、年份与缩写），如 `2017 23rd International Workshop on Thermal Investigations of ICs and Systems (THERMINIC)`；同一会议族保持同一写法。ACM 的 `event.name` 常被截断（如 “the 2003 international symposium”），此时用 `event.acronym` + `container-title` 拼全名。

## 坑

- **CJK 作者姓名必须用单字段 `name`**（如 `{"creatorType":"author","name":"王勖成"}`）：拆成 lastName/firstName 会让 CSL 渲染成“王, 勖成”，也可能被误按西文姓名排序。
- **CrossRef 对 IEEE DOI 返回小写规范形式**（`10.1109/therminic.2017.8233810`），不是错误，不要“修正”成大写。
- **附件常有质量问题**：页数远小于全书（节选）、缺索引、正文/索引被截断、同一书挂两份重复 PDF。这些都表现为“条目元数据看着正常但数据不可用”，要单独列出。
- **Knovel 重排版 PDF**：页眉含 “reformatted by Knovel”，版式与纸质原版不同，页码仍可追溯，numPages 应以原版/书目为准。
- 在线连载式图书（作者自建站点）条目 date 对应的是**附件那一版的日期**，站点常有更新的 “Last updated”，两者不同不算元数据错误。
- 出版社官网常被 Cloudflare/404 挡住（Wiley、Elsevier shop、IEEE Xplore 尤甚）；此时优先用图书馆书目 SRU 与 CrossRef，而不是反复重试浏览器。
