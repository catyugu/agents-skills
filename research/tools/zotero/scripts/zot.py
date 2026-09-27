#!/usr/bin/env python3
"""Zotero 本地自动化 CLI（仅标准库，零依赖）。

读/导出:  Zotero Local API   http://127.0.0.1:23119/api
新增条目: Zotero Connector   http://127.0.0.1:23119/connector  (无需授权)
写入:     建分类/挂附件/改条目/删除 (需写授权，首次弹窗)
"""

import argparse
import hashlib
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = os.environ.get("ZOTERO_HTTP", "http://127.0.0.1:23119")
USER = os.environ.get("ZOTERO_USER", "0")
API = f"{BASE}/api/users/{USER}"
STATE = os.path.expanduser(os.environ.get("ZOTERO_STATE", "~/.zotero-local.json"))


def die(msg):
    print(f"错误: {msg}", file=sys.stderr)
    raise SystemExit(1)


def hget(headers, name, default=None):
    for k, v in headers.items():
        if k.lower() == name.lower():
            return v
    return default


def http(url, method="GET", body=None, headers=None, timeout=60):
    """返回 (body_bytes, headers)；401 时返回 (None, headers)。"""
    req = urllib.request.Request(url, data=body, headers=headers or {}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read(), dict(r.headers)
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace").strip()[:300]
        if e.code in (401, 412):
            return None, {"status": e.code, "detail": detail}
        if e.code == 403 and "not enabled" in detail:
            die("Local API 未启用。Zotero → 设置 → 高级 → 勾选 "
                "“Allow other applications on this computer to communicate with Zotero”，重启 Zotero。")
        die(f"HTTP {e.code} {e.reason} {detail}")
    except urllib.error.URLError as e:
        die(f"连不上 {url}：{e.reason}（Zotero 桌面端在运行吗？）")


def load_state():
    try:
        with open(STATE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def save_state(**kw):
    d = load_state()
    d.update(kw)
    with open(STATE, "w", encoding="utf-8") as f:
        json.dump(d, f, indent=2)


def server_id():
    sid = load_state().get("server_id")
    if not sid:
        _, h = http(BASE + "/api/", headers={"Zotero-API-Version": "3"})
        sid = hget(h, "Zotero-Server-ID")
        save_state(server_id=sid)
    return sid


def library_version():
    _, h = http(f"{API}/items?limit=1", headers={"Zotero-API-Version": "3"})
    return hget(h, "Last-Modified-Version")


def authorize(app="Hermes Agent"):
    print(f"→ Zotero 正在弹出授权窗口（{app}），请点 “Always Allow”…", file=sys.stderr)
    body = json.dumps({"appName": app}).encode()
    out, _ = http(BASE + "/api/local/authorize", "POST", body,
                  {"Content-Type": "application/json", "Zotero-Server-ID": server_id()},
                  timeout=300)
    res = json.loads(out)
    if "key" not in res:
        die(f"授权被拒绝或被忽略：{res}")
    if res.get("remember"):
        save_state(key=res["key"])
    return res["key"]


def write_headers(extra=None):
    """写请求的公共头：授权 key、实例标识、并发前置条件。"""
    h = {"Zotero-API-Version": "3",
         "Zotero-API-Key": load_state().get("key") or authorize(),
         "Zotero-Server-ID": server_id(),
         "If-Unmodified-Since-Version": str(library_version())}
    h.update(extra or {})
    return h


def write_http(url, data, extra=None, method="POST", timeout=300, tries=4):
    """写请求：401 重新授权，412（库版本在读取后被改动）重取版本重试。

    Zotero 的 Last-Modified-Version 偶尔会滞后一步，导致紧随其后的写被判 412，
    因此每次重试都重新读取版本，而不是复用第一次的值。
    """
    for attempt in range(tries):
        out, hdrs = http(url, method, data, write_headers(extra), timeout)
        if out is not None:
            return out, hdrs
        status = hdrs.get("status")
        if status == 401:
            save_state(key=authorize())
        elif status == 412 and attempt < tries - 1:
            library_version()      # 再探一次版本，让下一次 write_headers 拿到最新值
        else:
            die(f"HTTP {status} {hdrs.get('detail', '')}")
    die("写请求重试次数用尽")


def api(path, params=None, method="GET", body=None, write=False, raw=False):
    """Local API 请求。write=True 时自动带授权头并处理并发重试。"""
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    headers = {"Zotero-API-Version": "3"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if write:
        out, hdrs = write_http(url, data, headers, method=method)
    else:
        out, hdrs = http(url, method, data, headers)
        if out is None:
            die("该操作需要 Zotero 写授权")
    if raw:
        return out.decode("utf-8", "replace"), hdrs
    return (json.loads(out) if out else None), hdrs  # DELETE 返回 204 空体


def post_import(text):
    """把 BibTeX/RIS/CSL JSON 文本交给 Zotero 导入翻译器入库（无需授权）。

    connector 会话按 session 参数去重：复用同一个（或省略）session 会返回 409 SESSION_EXISTS，
    所以每次导入都带一个唯一 session。
    """
    session = os.urandom(8).hex()
    out, _ = http(f"{BASE}/connector/import?session={session}", "POST", text.encode("utf-8"),
                  {"Content-Type": "text/plain",
                   "X-Zotero-Connector-API-Version": "3"}, timeout=180)
    return json.loads(out)


def fetch_doi_bibtex(doi):
    url = "https://doi.org/" + urllib.parse.quote(doi)
    out, _ = http(url, headers={"Accept": "application/x-bibtex; charset=utf-8",
                                "User-Agent": "hermes-zotero/1.0"}, timeout=45)
    return out.decode("utf-8", "replace")


def collection_add(name, parent=None):
    body = {"name": name}
    if parent:
        body["parentCollection"] = parent
    res, _ = api("/collections", method="POST", write=True, body=[body])
    return res["successful"]["0"]["key"]


def set_collections(item_key, collection_keys):
    api(f"/items/{item_key}", method="PATCH", write=True,
        body={"collections": list(collection_keys)})


def attach_file(parent_key, path, title=None):
    """把本地文件作为 stored attachment 挂到条目上（三阶段上传，Zotero 10+ 本地流程）。"""
    name = os.path.basename(path)
    data = open(path, "rb").read()
    res, _ = api("/items", method="POST", write=True, body=[{
        "itemType": "attachment", "linkMode": "imported_file",
        "parentItem": parent_key, "title": title or name,
        "contentType": mimetypes.guess_type(name)[0] or "application/octet-stream",
        "filename": name, "charset": "", "tags": [], "relations": {},
    }])
    key = res["successful"]["0"]["key"]

    # Zotero 的 form 解析用 decodeURIComponent，不会把 "+" 还原成空格，因此空格须编码为 %20
    form = urllib.parse.urlencode({
        "md5": hashlib.md5(data).hexdigest(), "filename": name,
        "filesize": len(data), "mtime": int(os.path.getmtime(path) * 1000),
    }, quote_via=urllib.parse.quote).encode()
    endpoint = f"{API}/items/{key}/file"
    form_headers = {"Content-Type": "application/x-www-form-urlencoded", "If-None-Match": "*"}
    out, _ = write_http(endpoint, form, form_headers)
    auth = json.loads(out)
    if not auth.get("exists"):
        payload = auth.get("prefix", "").encode() + data + auth.get("suffix", "").encode()
        http(auth["url"], "POST", payload,
             {"Content-Type": auth.get("contentType", "application/octet-stream")})
        write_http(endpoint, urllib.parse.urlencode({"upload": auth["uploadKey"]}).encode(),
                   form_headers)
    return key


def one_line(item):
    d = item.get("data", item)  # Local API 包一层 "data"，connector/import 返回扁平对象
    year = (d.get("date") or "")[:4]
    authors = "; ".join(
        c.get("lastName") or c.get("name", "") for c in d.get("creators", [])[:3])
    return f'{item["key"]}  {d.get("itemType",""):<16} {year:<6} {d.get("title","")}' \
           f'{"  [" + authors + "]" if authors else ""}'


# ---------------------------------------------------------------- commands

def cmd_status(args):
    _, h = http(BASE + "/api/", headers={"Zotero-API-Version": "3"})
    items, ih = api("/items", {"limit": 1})
    cols, _ = api("/collections")
    print(f"Zotero           {hget(h, 'X-Zotero-Version')}")
    print(f"API / schema     {hget(h, 'Zotero-Api-Version')} / {hget(h, 'Zotero-Schema-Version')}")
    print(f"Server-ID        {hget(h, 'Zotero-Server-Id')}")
    print(f"条目             {hget(ih, 'Total-Results')}")
    print(f"分类             {len(cols)}")
    print(f"写授权           {'已缓存' if load_state().get('key') else '无（首次写操作时弹窗）'}")


def cmd_list(args):
    if args.collection:
        path, params = f"/collections/{args.collection}/items", {}
    else:
        path, params = "/items", {}
    if args.type:
        params["itemType"] = args.type
    if args.tag:
        params["tag"] = args.tag
    if args.q:
        params["q"] = args.q
    if args.limit:
        params["limit"] = args.limit
    items, h = api(path, params)
    for it in items:
        print(one_line(it))
    print(f"-- {len(items)} 条（库内匹配 {hget(h, 'Total-Results')} 条）", file=sys.stderr)


def cmd_get(args):
    item, _ = api(f"/items/{args.key}")
    print(json.dumps(item["data"], ensure_ascii=False, indent=2))


def cmd_export(args):
    params = {"format": args.format}
    path = f"/collections/{args.collection}/items" if args.collection else "/items"
    if args.q:
        params["q"] = args.q
    text, _ = api(path, params, raw=True)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"已写入 {args.output} （{len(text)} 字节）", file=sys.stderr)
    else:
        print(text)


def cmd_collections(args):
    cols, _ = api("/collections")
    by_parent = {}
    for c in cols:
        by_parent.setdefault(c["data"].get("parentCollection") or "", []).append(c)

    def walk(parent, depth):
        for c in sorted(by_parent.get(parent, []), key=lambda x: x["data"]["name"].lower()):
            print("  " * depth + f'{c["key"]}  {c["data"]["name"]}'
                  f'  ({c["meta"].get("numItems", 0)} 条)')
            walk(c["key"], depth + 1)

    walk("", 0)
    print(f"-- 共 {len(cols)} 个分类", file=sys.stderr)


def cmd_collection_add(args):
    print(collection_add(args.name, args.parent))


def cmd_set_collections(args):
    set_collections(args.key, args.collections)


def cmd_attach(args):
    print(attach_file(args.key, args.path, args.title))


def cmd_tags(args):
    tags, _ = api("/tags")
    for t in sorted(tags, key=lambda x: -x["meta"].get("numItems", 0))[:args.limit]:
        print(f'{t["meta"].get("numItems", 0):<5} {t["tag"]}')


def cmd_text(args):
    data, _ = api(f"/items/{args.key}/fulltext")
    text = data.get("content", "")
    if not text:
        die("该附件没有已索引的正文（可能是扫描版 PDF 或未索引）")
    print(text[:args.max_chars] if args.max_chars else text)


def cmd_file(args):
    url, _ = api(f"/items/{args.key}/file/view/url", raw=True)
    print(urllib.request.url2pathname(url.strip().replace("file://", "")))


def cmd_add(args):
    if args.doi:
        text, label = fetch_doi_bibtex(args.doi), f"DOI {args.doi}"
    elif args.bibtex:
        text = open(args.bibtex, encoding="utf-8").read()
        label = args.bibtex
    elif args.ris:
        text = open(args.ris, encoding="utf-8").read()
        label = args.ris
    else:
        text, label = sys.stdin.read(), "stdin"
    items = post_import(text)
    if not items:
        die(f"未能从 {label} 解析出条目")
    for it in items:
        if args.collection:
            set_collections(it["key"], [args.collection])
    print(f"已导入 {len(items)} 条：")
    for it in items:
        print("  " + one_line(it))
    if not args.collection:
        print("注意：条目进入 Zotero 当前选中的分类。", file=sys.stderr)


def cmd_delete(args):
    for key in args.keys:
        api(f"/items/{key}", method="DELETE", write=True)
        print(f"已删除 {key}")


def main():
    p = argparse.ArgumentParser(description="Zotero 本地自动化（Local API + Connector）")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("status", help="服务与库概况").set_defaults(func=cmd_status)

    s = sub.add_parser("list", help="列出/筛选条目")
    s.add_argument("--type"), s.add_argument("--tag"), s.add_argument("--q")
    s.add_argument("--collection"), s.add_argument("--limit", type=int)
    s.set_defaults(func=cmd_list)

    s = sub.add_parser("get", help="单条完整元数据")
    s.add_argument("key"); s.set_defaults(func=cmd_get)

    s = sub.add_parser("export", help="导出 BibTeX / RIS / CSL JSON")
    s.add_argument("format", choices=["bibtex", "ris", "csljson"])
    s.add_argument("-o", "--output"), s.add_argument("--collection"), s.add_argument("--q")
    s.set_defaults(func=cmd_export)

    sub.add_parser("collections", help="分类树").set_defaults(func=cmd_collections)

    s = sub.add_parser("collection-add", help="新建分类，输出分类 key")
    s.add_argument("name"); s.add_argument("--parent")
    s.set_defaults(func=cmd_collection_add)

    s = sub.add_parser("set-collections", help="把条目移入指定分类（覆盖）")
    s.add_argument("key"); s.add_argument("collections", nargs="+")
    s.set_defaults(func=cmd_set_collections)

    s = sub.add_parser("attach", help="把本地文件作为附件挂到条目，输出附件 key")
    s.add_argument("key"); s.add_argument("path"); s.add_argument("--title")
    s.set_defaults(func=cmd_attach)

    s = sub.add_parser("tags", help="标签（按使用次数）")
    s.add_argument("--limit", type=int, default=50); s.set_defaults(func=cmd_tags)

    s = sub.add_parser("text", help="附件已索引全文")
    s.add_argument("key"); s.add_argument("--max-chars", type=int, default=0)
    s.set_defaults(func=cmd_text)

    s = sub.add_parser("file", help="附件本地路径")
    s.add_argument("key"); s.set_defaults(func=cmd_file)

    s = sub.add_parser("add", help="新增条目（无需授权；默认从 stdin 读 BibTeX/RIS）")
    g = s.add_mutually_exclusive_group()
    g.add_argument("--doi"), g.add_argument("--bibtex"), g.add_argument("--ris")
    s.add_argument("--collection", help="导入后放入该分类")
    s.set_defaults(func=cmd_add)

    s = sub.add_parser("delete", help="删除条目（需授权）")
    s.add_argument("keys", nargs="+"); s.set_defaults(func=cmd_delete)

    sub.add_parser("auth", help="预先取得写授权").set_defaults(
        func=lambda a: print("授权已缓存" if load_state().get("key") else
                             (authorize() and "授权已缓存")))

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
