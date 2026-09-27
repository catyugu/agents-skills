#!/usr/bin/env python3
"""Renumber a hand-written thebibliography to first-citation order.

Usage:
    python bib_renumber.py body.tex references.tex [--dry-run]

`thebibliography` numbers entries in bibitem order, not citation order, so a list grouped by topic
prints the wrong [n] values. This script collects the order of first citation from the body,
reorders and renumbers the entries, drops entries that are never cited (they still occupy a number
and print), and rewrites the \cite keys in a single pass so a remapped key can never be remapped a
second time.

The thebibliography wrapper is normalized to \begin{thebibliography}{99} + \zihao{5}; anything
before \begin{thebibliography} (header comments) is preserved verbatim.
"""

import argparse
import re
import sys

CITE = re.compile(r"\\cite\{([^}]*)\}")
ITEM = re.compile(r"\\bibitem\{([^}]+)\}(.*?)(?=\\bibitem|\\end\{thebibliography\})", re.S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("body")
    ap.add_argument("refs")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    body = open(a.body, encoding="utf-8").read()
    refs = open(a.refs, encoding="utf-8").read()

    order = []
    for m in CITE.finditer(body):
        for key in (k.strip() for k in m.group(1).split(",")):
            if key not in order:
                order.append(key)

    entries = {m.group(1): m.group(2) for m in ITEM.finditer(refs)}
    missing = [k for k in order if k not in entries]
    if missing:
        sys.exit("cited but not defined in %s: %s" % (a.refs, missing))
    dropped = [k for k in entries if k not in order]

    mapping = {old: "ref%d" % (i + 1) for i, old in enumerate(order)}
    print("entries: %d cited, %d defined" % (len(order), len(entries)))
    print("old -> new: %s" % mapping)
    if dropped:
        print("uncited, dropped: %s" % dropped)
    if a.dry_run:
        return

    def fix(m):
        keys = [k.strip() for k in m.group(1).split(",")]
        return "\\cite{" + ",".join(mapping[k] for k in keys) + "}"

    new_body = CITE.sub(fix, body)

    head = refs[: refs.index("\\begin{thebibliography}")]
    items = ["    \\bibitem{%s}%s" % (mapping[old], entries[old].rstrip()) for old in order]
    new_refs = (
        head
        + "\\begin{thebibliography}{99}\n    \\zihao{5}\n\n"
        + "\n\n".join(items)
        + "\n\n\\end{thebibliography}\n"
    )

    open(a.body, "w", encoding="utf-8", newline="").write(new_body)
    open(a.refs, "w", encoding="utf-8", newline="").write(new_refs)
    print("rewrote %s and %s" % (a.body, a.refs))
    print("now recompile three times and confirm zero Citation warnings")


if __name__ == "__main__":
    main()
