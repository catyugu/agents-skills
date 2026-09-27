#!/usr/bin/env python3
"""Stats for a compiled LaTeX report: pages, CJK count per section, per-page trailing gap,
right-margin overflow, rendered heading order.

Usage:
    python tex_stats.py final.pdf body.tex [--right-cm 2.5] [--bottom-cm 2.54]

Requires pymupdf. Margin defaults match the common Chinese thesis template
(top 3.2cm / bottom 2.54cm / left 2.5cm / right 2.5cm on A4); pass the template's own values.

Reading the output:
  * trailing gap = empty space below the last content block on a page. A final page with a large
    gap means the report is only slightly over budget; a nearly full final page means the next
    page break costs a full page of text.
  * right-margin overflow lists text blocks whose right edge is past the text area. LaTeX does NOT
    warn about an over-wide tabular, so this check is the only signal.
"""

import argparse
import re

import pymupdf

CM = 28.3465
CJK = re.compile(r"[\u4e00-\u9fff]")
HEADING = re.compile(r"\\(section|subsection|subsubsection)\*?\{([^}]*)\}")
NUMBERED = re.compile(r"^\d+(\.\d+)*\s")


def source_stats(path):
    text = open(path, encoding="utf-8").read()
    heads = [(m.start(), m.group(1), m.group(2)) for m in HEADING.finditer(text)]
    bounds = [h[0] for h in heads] + [len(text)]
    rows = []
    for i, (pos, kind, title) in enumerate(heads):
        chunk = text[pos:bounds[i + 1]]
        rows.append((kind, title, len(CJK.findall(chunk))))
    return rows, len(CJK.findall(text))


def pdf_stats(path, right_cm, bottom_cm):
    doc = pymupdf.open(path)
    pages = []
    for page in doc:
        width, height = page.rect.width, page.rect.height
        right_limit = width - right_cm * CM
        text_bottom = height - bottom_cm * CM
        bottoms, wide = [], []
        for block in page.get_text("blocks"):
            x1, y1 = block[2], block[3]
            if y1 <= text_bottom + 5:  # skip the footer / page number
                bottoms.append(y1)
            if x1 > right_limit + 1:
                wide.append((round(x1, 1), " ".join(block[4].split())[:60]))
        for img in page.get_image_info():
            bottoms.append(img["bbox"][3])
        bottom = max(bottoms) if bottoms else 0.0
        pages.append((text_bottom - bottom, wide))
    return doc.page_count, pages


def rendered_headings(path):
    doc = pymupdf.open(path)
    found = []
    for i, page in enumerate(doc, 1):
        for block in page.get_text("blocks"):
            line = " ".join(block[4].split())
            if len(line) < 60 and NUMBERED.match(line):
                found.append((i, line))
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("source", help="body.tex")
    ap.add_argument("--right-cm", type=float, default=2.5)
    ap.add_argument("--bottom-cm", type=float, default=2.54)
    a = ap.parse_args()

    count, pages = pdf_stats(a.pdf, a.right_cm, a.bottom_cm)
    rows, total = source_stats(a.source)

    print("pages: %d" % count)
    print("source CJK characters: %d" % total)
    print()
    print("%-14s %7s  %s" % ("kind", "CJK", "title"))
    for kind, title, n in rows:
        print("%-14s %7d  %s" % (kind, n, title))

    print()
    print("page  trailing gap")
    for i, (gap, _) in enumerate(pages, 1):
        print("%4d  %8.0f pt  (%.1f cm)" % (i, gap, gap / CM))

    over = [(i, x, txt) for i, (_, wide) in enumerate(pages, 1) for x, txt in wide]
    print()
    if over:
        print("RIGHT-MARGIN OVERFLOW:")
        for i, x, txt in over:
            print("  page %d  x1=%.1f  %s" % (i, x, txt))
    else:
        print("no right-margin overflow")

    print()
    print("rendered headings:")
    for page, line in rendered_headings(a.pdf):
        print("  p%-3d %s" % (page, line))


if __name__ == "__main__":
    main()
