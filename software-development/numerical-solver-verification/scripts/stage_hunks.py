#!/usr/bin/env python3
"""Stage hunks of the working-tree diff by index, non-interactively.

A tree that holds several concerns (the groups of a refactor) is committed one
concern at a time by staging only the hunks that belong to it. `git add -p`
drives that interactively; this drives the same mechanism from a script.

    stage_hunks.py list                     print every file and hunk
    stage_hunks.py apply f1:0,2 f2:1        stage exactly those hunks

Hunk indices are relative to the CURRENT unstaged diff: hunks leave the diff as
they are staged, so re-run `list` after every commit instead of reusing the
previous index list. Exit code 2 means nothing was selected.
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path


def diff_text() -> str:
    return subprocess.run(
        ["git", "diff", "--no-color", "--no-ext-diff", "-U3"],
        capture_output=True, text=True, check=True,
    ).stdout


def split(text: str):
    """[(path, file header lines, [hunk, ...])] in file order."""
    files, path, header, hunks = [], None, [], []
    for line in text.splitlines(keepends=True):
        if line.startswith("diff --git "):
            if path:
                files.append((path, header, hunks))
            path, header, hunks = line.split(" b/")[-1].strip(), [], []
        elif line.startswith("@@"):
            hunks.append(line)
        elif hunks:
            hunks[-1] += line
        else:
            header.append(line)
    if path:
        files.append((path, header, hunks))
    return files


def summarise(hunk: str) -> str:
    lines = hunk.splitlines()
    removed = [ln[1:] for ln in lines[1:] if ln.startswith("-")]
    added = [ln[1:] for ln in lines[1:] if ln.startswith("+")]
    return (f"- {removed[0][:60] if removed else ''!r}"
            f"  + {added[0][:60] if added else ''!r}")


def main() -> int:
    files = split(diff_text())
    if len(sys.argv) < 2 or sys.argv[1] == "list":
        for path, _header, hunks in files:
            print(f"== {path} ({len(hunks)} hunks)")
            for i, hunk in enumerate(hunks):
                print(f"   [{i}] {summarise(hunk)}")
        return 0

    want: dict[str, set[int]] = {}
    for spec in sys.argv[2:]:
        path, idx = spec.rsplit(":", 1)
        want.setdefault(path, set()).update(int(i) for i in idx.split(","))

    selected = []
    for path, header, hunks in files:
        if path not in want:
            continue
        selected.extend(header)
        selected.extend(h for i, h in enumerate(hunks) if i in want[path])
    if not selected:
        print("nothing selected", file=sys.stderr)
        return 2

    # newline="" is load-bearing: text-mode writing translates every \n to
    # \r\n, so on a CRLF checkout (core.autocrlf) each patch line ends up with
    # a doubled CR and git apply rejects the whole patch as "does not apply".
    with tempfile.NamedTemporaryFile("w", suffix=".patch", delete=False,
                                     newline="") as fh:
        fh.write("".join(selected))
        patch = fh.name
    r = subprocess.run(["git", "apply", "--cached", "--verbose", patch],
                       capture_output=True, text=True)
    Path(patch).unlink(missing_ok=True)
    print(r.stdout.strip())
    if r.returncode != 0:
        print(r.stderr.strip(), file=sys.stderr)
    return r.returncode


if __name__ == "__main__":
    raise SystemExit(main())
