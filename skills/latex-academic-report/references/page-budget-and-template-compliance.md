# Page budget and template compliance

## 1. Extract the template spec before writing

Read the template `.doc`/`.docx` and record every item in a checklist. Everything downstream is
encoded once in the preamble from this checklist.

| Item | What to extract |
|---|---|
| Page setup | paper size; top / bottom / left / right margins; header and footer distances |
| Header / footer | exact header text, font and size; page-number placement |
| Title block | title font and size; author-line layout; blank line after each block |
| Abstracts | heading font and size; body font and size; first-line indent; keyword label and size; English font |
| Body | font size; first-line indent; line spacing; space before / after paragraphs |
| Headings | per-level font and size; indent; numbering scheme (`1` / `1.1` / `1.1.1`, dot separators, no trailing punctuation below level 1) |
| Figures / tables | caption placement and size; table font size |
| Bibliography | font; numbering style; entry format; author name order |
| Section order | the exact ordered list the template requires |

## 2. The measure -> decide -> cut loop

1. Compile three passes; get the page count and the CJK character count.
2. Read the trailing gap of the final page (`scripts/tex_stats.py`).
   - Final page more than about half empty: the report is only slightly over budget, and cutting
     roughly that much text moves the whole document up one page.
   - Final page nearly full: the next page break costs a full page of text. Decide whether one page
     is worth that much content before cutting into substance.
3. Cut, recompile, re-measure. Never estimate the page saving of an edit — a paragraph that reads
   long may reflow without changing the page count at all.

## 3. Cut order (earliest cut first)

1. Argumentation that carries no information: the design intent of a case, why a working condition
   was chosen, "this also provides a consistency check", restated motivation.
2. Duplication across abstract, body and conclusion — state each result once in full and refer to it
   elsewhere.
3. Merged subsections: two subsections describing the same layer or the same step belong in one
   paragraph; splitting them forces each to restate the other.
4. Enumeration scaffolding: "其一…其二…其三…" collapses to a single sentence when the points are
   short.
5. English expansions of well-known acronyms and spelled-out names of standard methods, after their
   first use.
6. Parenthetical implementation detail inside a mathematics section (serialization, storage layout,
   cache behaviour).
7. Worked detail of a standard algorithm: state the methodology, not the derivation, for anything a
   reader in the field treats as common knowledge.

## 4. Never cut

Equations and their labels; pseudocode listings; measured numbers with units; tables; figure
captions; citations; the bibliography.

## 5. Bibliography correctness

- `thebibliography` numbers in bibitem order. A list grouped by topic prints wrong `[n]` values.
- After any change to which sources are cited, re-run `scripts/bib_renumber.py`.
- Delete entries that are never cited; they still consume a number and still print.

### Entry format — GB/T 7714-2015 (the style a Chinese template names)

- Every entry carries its document-type marker: `[M]` book or manual, `[J]` journal article,
  `[M]//` a chapter or paper inside a book (never `In: … ed.`), `[EB/OL]` anything online. A list
  without markers reads as unformatted to a Chinese reviewer even when every fact in it is right.
- Journals: 作者. 题名[J]. 刊名, 年, 卷(期): 起止页码. Books: 作者. 书名[M]. 版本. 出版地: 出版者,
  出版年. Chapters append the host book after `[M]//` and the page range at the end.
- More than three authors: keep the first three and write 等 — `et al.` in the Latin alphabet does not
  belong in a Chinese list. Names stay surname-first, small caps or all caps as the template shows.
- Online entries need the access date and the path: `题名[EB/OL]. 出版地: 出版者, 出版年[YYYY-MM-DD].
  <URL>`. Cite a DOI or a link you have actually opened; a deep documentation URL you have not
  resolved is a guess, and a vendor manual without a stable URL is better cited as `[M]`.
- Page ranges use the half-width hyphen (1-155, 475-487), not a LaTeX en dash.
- Wrap long URLs in `\url{...}`. A bare URL is one unbreakable word, so LaTeX stretches that line's
  inter-word spaces across the whole column — visible in the PDF, invisible in the log.
- Verify each entry against the item's own record (author list, year, volume, pages, DOI) before it is
  written, and note the verification in the handover file. The failure mode is a confidently wrong
  author list reconstructed from memory, not a typo.

## 6. Verification before delivery

- Zero `LaTeX Warning: Citation` and `LaTeX Warning: Reference` in the final pass log.
- No text block past the right text margin.
- Rendered heading order matches the template's required section order.
- Page count and word count reported against the stated limits, with the exact file path.
