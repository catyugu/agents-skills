#!/usr/bin/env python3
"""只读审计一个 markdown 笔记仓库，打印 linter 通常不查的可疑项。

用法: python vault_audit.py [vault_root]      # 默认当前目录

检查:
  - 目录缺少同名索引，或索引缺少 ## 概述 / ## 文件组织
  - 目录项（文件/子目录）未被同名索引链接（比较前对链接做 URL 解码）
  - 索引条目描述过短，疑似占位
  - attachments/ 中未被任何文档引用的文件

启发式脚本：不修改任何文件，可能有漏报，仓库自带 linter 才是权威。
"""

import re
import sys
import urllib.parse
from pathlib import Path

SKIP_DIRS = {"attachments", "__pycache__", ".git", ".venv", ".obsidian"}
LINK_RE = re.compile(r"\]\(([^)]+)\)")
FENCE_RE = re.compile(r"^```.*?^```|^~~~.*?^~~~", re.DOTALL | re.MULTILINE)
INLINE_RE = re.compile(r"`[^`\n]*`")
EXTERNAL = ("http://", "https://", "mailto:", "tel:")
MIN_DESC = 8


def strip_code(text: str) -> str:
    """把代码块与行内代码换成等长空白，避免其中的示例被当成真实链接。"""
    def blank(match):
        return re.sub(r"[^\n]", " ", match.group(0))

    return INLINE_RE.sub(blank, FENCE_RE.sub(blank, text))


def link_targets(text: str) -> set:
    targets = set()
    for raw in LINK_RE.findall(strip_code(text)):
        target = re.split(r"\s+[\"']", raw.strip())[0]
        if not target or target.startswith(EXTERNAL):
            continue
        targets.add(urllib.parse.unquote(target.split("#")[0]).rstrip("/"))
    return targets


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    referenced = set()
    for md in root.rglob("*.md"):
        if set(md.parts) & SKIP_DIRS:
            continue
        for target in link_targets(md.read_text(encoding="utf-8")):
            referenced.add(target)
            referenced.add(Path(target).name)

    problems = []
    dirs = {p.parent for p in root.rglob("*") if p.is_dir()} | {root}
    for directory in sorted(dirs):
        if set(directory.parts) & SKIP_DIRS:
            continue
        index = directory / f"{directory.name}.md"
        if not index.exists():
            problems.append(f"缺索引: {directory.relative_to(root)}")
            continue

        text = index.read_text(encoding="utf-8")
        for section in ("## 概述", "## 文件组织"):
            if section not in text:
                problems.append(f"索引缺 {section}: {index.relative_to(root)}")

        targets = link_targets(text)
        for item in directory.iterdir():
            if item == index or item.name.startswith(".") or item.name in SKIP_DIRS:
                continue
            names = {
                item.name,
                item.stem,
                str(item.relative_to(directory)).replace("\\", "/"),
            }
            if not names & targets:
                problems.append(f"未索引: {item.relative_to(root)}")

        for line in text.splitlines():
            if not line.startswith("- [") or "：" not in line:
                continue
            if len(line.split("：", 1)[1].strip()) < MIN_DESC:
                problems.append(f"索引描述过短: {index.relative_to(root)} -> {line[:60]}")

    for attachment in root.rglob("attachments/*"):
        if attachment.is_file() and attachment.name not in referenced:
            problems.append(f"未被引用: {attachment.relative_to(root)}")

    print("\n".join(problems) if problems else "[OK] 未发现可疑项")
    return 0


if __name__ == "__main__":
    sys.exit(main())
