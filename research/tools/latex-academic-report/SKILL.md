---
name: latex-academic-report
description: Use when writing a LaTeX academic report to a template.
category: productivity
---

# LaTeX Academic Report (template-driven)

Use this skill when producing or revising a formal academic report as LaTeX source compiled to PDF
against an institutional template with a page ceiling and a word floor — PRP/结题报告, thesis
chapter, course paper. Covers the compile-and-measure loop, content-only compression to a page
budget, bibliography ordering, and template compliance. For Word/.docx deliverables use
`evidence-grounded-report-authoring` instead.

## Non-negotiable rules

1. **Never buy pages with layout.** Do not touch geometry, `\linespread`, `\abovedisplayskip`,
   float spacing, `\algomargin`, or font size to meet a page limit. Over budget means delete content.
   A report that meets the limit by tightening spacing is rejected outright.
2. **Cut prose, never substance.** When asked to shorten, delete redundant explanatory sentences,
   merge subsections that describe the same thing, and drop argumentation (design intent, why a
   working condition was chosen, restated background). Keep every equation, pseudocode listing,
   measured number, table and citation.
3. **Formulas in LaTeX math mode only.** No hand-written Unicode math symbols anywhere in the source.
4. **Describe only what is implemented.** No "not yet implemented", "limitations", or other
   self-negating sections unless the user explicitly asks for them.
5. **Own results carry no citation.** Cite external literature only; the project's own measurements
   are stated directly.
6. **Abstracts state the work, not its results or its lineage.** Do not name the third-party project the
   implementation was modelled on, and do not print measured values in it (error magnitudes, speedup
   factors, iteration, step or peak counts). Write that the accuracy meets the stated acceptance
   criteria and that the coupled cases were solved more efficiently than the reference; the figures
   belong to the results section, and the Chinese and English abstracts must agree with no number.
7. **A weak-form section states the math.** Give the weak form, its boundary terms and the coupling
   terms; leave assembly, serialization and storage mechanics to the architecture section.
8. **Do not re-run experiments to refresh numbers.** Use the result files, logs and comparison
   tables already in the repository.
9. **Commit each stable version.** A page-budget rewrite is a destructive edit; commit every version
   that compiles and reads correctly so a later cut cannot lose an earlier good state. The user edits
   the same repository while you work: if they reset one of your commits or say not to commit, stop
   committing, leave the edits in the working tree, report exactly what is modified, staged and
   untracked, and commit again only when asked. Recompile from the current sources immediately before
   the commit: the user edits the repository in parallel, so a build verified earlier no longer
   describes what is being committed — a reverted preamble line leaves no trace in the log or the page
   count, and only the fresh compile shows that the artifact you were about to vouch for is stale.
10. **Editor and formatter configuration belongs in the user-level editor settings, not in the report
    repository.** Do not commit `.vscode/`, `.latexindent.yaml`, or `.gitignore` negations for them —
    the repository holds the document, not the machine's build config. Reverting such a config is
    expected work, not a setback: revert the commit, delete the files, and confirm
    `git ls-files | grep -iE 'vscode|latexindent'` prints nothing.
11. **A missing figure gets a labelled placeholder, not silence.** When the report needs an asset that
    does not exist yet (a model or geometry screenshot), insert a visible placeholder box carrying the
    caption and a one-line statement of what the figure must show, keep the `\label`/`\ref` wiring
    live, and list every placeholder in the handover notes so the user can supply the files.
12. **A cover inserted with `\includepdf` is not the report's page 1.** `pdfpages` increments the page
    counter, so the body starts at 2 and every reference to "page N" is off by one; reset it with
    `\setcounter{page}{1}` immediately after the `\includepdf` line. The reset then makes the cover and
    the body's first page carry the *same* page number, so `hyperref` writes the destination `page.1`
    twice and xdvipdfmx reports `Object @page.1 already defined` (surfaces as a build diagnostic in the
    editor). Silence it by suppressing page anchors on the cover only:

    ```latex
    \hypersetup{pageanchor=false}
    \includepdf[pages=1]{cover.pdf}
    \hypersetup{pageanchor=true}
    \setcounter{page}{1}
    ```

    Whether the cover counts toward the page ceiling is a requirement detail — establish it explicitly
    (it is usually excluded) and report the ceiling against **body** pages only, stating which reading
    you used.
    Including a cover whose PDF version is newer than the driver's default (e.g. a 1.7 cover against
    output version 1.5) also warns; it is harmless, and `hyperref`'s `pdfversion` option is **not** a
    fix — under XeLaTeX it aborts the compile. Leave the warning or ask before rewriting the asset.
13. **Claims must match the evidence.** Never upgrade a measurement into a stronger statement in the
    abstract, discussion or conclusion — see `references/results-prose-discipline.md` for the checks
    (machine precision, speedup direction, scalability from two points, unexercised code paths, error
    attribution, physical naming of a field).
 14. **A measured number enters the report without the engineering that produced it.** State the result
 (solve time, ratio, error); the internal optimisations, caches and refactors that got it there stay
 out unless the user asks for them. A report is not a changelog of its own implementation.
 15. **More detail must not mean more length.** Asked to add a model's concrete values without growing
 the section, keep the relation inline in math mode and fold the constants into the sentences that
 already exist. A display equation plus a loose coefficient list costs roughly a page and displaces
 the figure that belongs with that text — which the user reads as a defect, not as depth.
 16. **Cite the source the data actually came from.** A model or case adapted from a vendor's tutorial
 takes its properties, geometry and boundary values from that tutorial's own documentation — cite
 that page, not the canonical materials handbook it resembles. State the report's own operating
 point where it differs from the source's, and mark a coefficient the report sets itself as its own
 choice rather than the source's. Name a vendor example by its model-library entry — title plus
 application ID, both looked up rather than recalled — and read the case's own model file for the
 example it came from instead of inferring it from the geometry. A case that is the project's own
 design has no source to cite, so give it its own property table (generated from that case's model
 file, with the remaining loads and boundary values stated in the sentence that introduces it) and
 label the values as that case's settings rather than literature data.
17. **Verify every bibliography entry against the item's own record before writing it.** Open the DOI
 or publisher page and check the author list, year, volume, pages and identifier; a reference
 assembled from memory invents authors (a plausible but wrong second author is the usual failure) and
 mistates page ranges, and the reader who knows the paper sees it first. Write the entries in the
 style the template names — GB/T 7714 for a Chinese report, with its document-type markers — and
 record in the handover notes what was checked against what.
 18. **A table carries one kind of fact.** Keep the case-scale table to scale — vertex and element
     counts, element order, degrees of freedom — and leave measured performance to the section that
     discusses it; a table that also prints solve times is sent back to be rewritten, and the numbers
     then have to be relocated into the prose rather than dropped (they are the report's evidence). Name
     every row exactly as the report names that case in its own section — a row named one way while the
     section names the same case another way is read as two different cases — and after deleting a
     column re-read the sentence that introduces the table: it still advertises the column that is gone,
     and grepping that one sentence is what finds it.

## Workflow

### 1. Read the constraints before writing

- The template `.doc`/`.docx` is the authority for margins, header/footer text, font and size per
  heading level, heading numbering, bibliography style and the required section order. Extract that
  spec into a checklist (see `references/page-budget-and-template-compliance.md`); do not invent
  formatting.
- The requirements notice gives the hard limits (page ceiling, word floor) and the deliverable
  format.
- Follow the template's stated order even when it contradicts habit — if it lists 参考文献 before
  致谢, write it that way.

### 2. Split the source

- `final.tex` — document class, fonts, geometry, `fancyhdr`, `ctexset` heading formats, float and
  algorithm styles, `\includepdf` cover, title block, Chinese and English abstracts, then
  `\input{body.tex}`.
- `body.tex` — all sections, then `\input{references.tex}`.
- `references.tex` — `\begin{thebibliography}`; the printed `[n]` is bibitem order.
- Chinese reports: `ctexart` with `zihao=5`, `\singlespacing`, and per-level `\ctexset` for heading
  size, indent and numbering. Set the algorithm environment's keywords to Chinese once in the
  preamble rather than per listing.

### 3. Compile and measure every iteration

```bash
for i in 1 2 3; do xelatex -interaction=nonstopmode -halt-on-error final.tex > .scratch/pass$i.log 2>&1 \\  || { echo "FAIL pass $i"; grep -nE "^!" .scratch/pass$i.log | head; break; }; done
pdfinfo final.pdf | grep Pages
```

Three passes: pass 1 writes the `.aux` needed to resolve cross-references and citation numbers.
`-halt-on-error` plus `grep -nE "^!"` turns a failed compile into one line of output; also check
`grep -c undefined .scratch/passN.log` on the **last** pass.

`Output written on final.pdf` is not proof the artifact is readable. Confirm it with
`pdfinfo final.pdf | grep Pages`, and re-run the loop once if a reader reports a damaged trailer
dictionary or contents — a PDF written while another job held the disk comes out corrupt while the
log looks clean, and a one-shot re-run fixes it.

Then run `scripts/tex_stats.py` for pages, CJK count per section, per-page trailing gap,
right-margin overflow and rendered heading order. Measure — never estimate the page effect of an
edit.

### 4. Compress content, not layout

Follow the cut order in `references/page-budget-and-template-compliance.md`, and use its last-page
diagnostic to judge whether the next cut actually saves a page.

### 5. Fix the bibliography last

`thebibliography` numbers by bibitem order, so any change to which sources are cited invalidates
the numbering. Run `scripts/bib_renumber.py body.tex references.tex`: it reorders entries to
first-citation order, drops uncited entries and rewrites the `\cite` keys.

A source inserted mid-list renumbers every entry after it, and the compile cannot see the damage:
grep the handover notes for the old numbers afterwards — a note that still quotes `[14]` for a source
now printed as `[15]` is wrong where no build will flag it.

### 6. Make the editor's build and format match the document

A report that compiles from the shell but fails in the editor, or that the editor silently rewrites
on save, is not finished. Pin the engine, pin the formatter rules, and prove both with a diff rather
than by inspection — see `references/editor-toolchain.md`. Put both in the editor's **user-level**
settings (rule 10), not in files inside the report repository.

## Pitfalls

- `algpseudocode` defines no `\algomargin` length; `\setlength{\algomargin}{...}` aborts the
  compile with `! Undefined control sequence.` Do not add it.
- **Write `.tex` edits through a script, never as an inline replacement string.** Save a Python
  script with the file tool that does exact-match replacements (one `assert` per pattern, so a stale
  pattern fails loudly instead of silently dropping the edit) and run it. The `patch` tool's JSON `old_string` and `new_string` interpret the two-character escape `\r` as a literal carriage return before it is sent to the file, so any pattern containing `\rho`, `\ref`, `\angle`, `\rangle`, `\rceil`, `\rfloor` or any other `\r`-prefixed LaTeX command is mangled: the search string loses its `r` and is followed by an embedded newline, the file's commands silently break into `\nho` / `\nef` / `\nangle`, and the edit either no-ops or writes corrupted text — the build still passes but the rendered math is wrong. Python on Windows uses CRLF line endings by default, so a `open(path).write(...)` round-trip preserves the original EOL. A shell heredoc *and* the patch tool both read a single backslash-escape sequence in the replacement text as the character it names, so a cross-reference or a math macro is silently corrupted. Patch the script itself with a fresh file write, never with another inline `python -c` whose replacement text nests quotes and backslashes: those edits match nothing and look like a stale pattern. A script that rewrites a markdown table has one more trap — a row split on `|` yields an empty first cell, so index the real columns from `1`, assert the cell count per row, and confirm the row count after writing; a loop that emits one record per old row silently drops a row that an earlier insert added.

- Overfull hbox warnings are harmless at a fraction of a point; read the magnitude in the log before
  chasing one. Fix a real overflow by rewording the sentence or splitting the inline math, never by
  changing layout.
- A `tabular` wider than `\linewidth` runs past the right margin **without erroring**. Detect it by
  comparing text-block right edges against `page width - right margin`; fix it by shortening column
  headers and moving the unit into the caption.
- Deleting a whole section by hand risks leaving a stray brace or a dangling `\ref`. Locate the
  `\section{...}` boundaries programmatically, delete the range, then re-grep the source for
  references to that section's labels.
- Mid-flight revision requests name a specific section. Apply each as a surgical edit to that section
  and recompile; do not rewrite the whole document in one pass, and do not batch several such
  requests into one unverified rewrite.
- Re-check float placement after any edit that adds or moves prose near a figure. A tight page budget
  pushes the float onto the next page while the text that refers to it stays behind, and a figure
  separated from its section reads as a layout defect even when the page count is still met. Render the
  pages (`pdftoppm -r 100 -png -f N -l N final.pdf out`) and look at them; the log carries no warning.
- **A figure too tall to share a page takes one for itself** and spends a page of the budget on a
  single asset. Size the panels so the figure plus a few lines of text fits (roughly half a text
  height), trim the images' own white margins before including them, force the float next to the
  paragraph that cites it with `[!ht]`, and prefer one 2x2 grid of related panels over a wide row plus
  a second float. Confirm by rendering the page: the page count alone cannot show that a figure sits
  alone, and the user reads that as a defect.
- **Several floats declared in one chapter queue up and land as a page of figures followed by pages of
  text**, which the reader reports as scrambled figure/text order even though every reference resolves.
  LaTeX defers a float it cannot place and flushes the queue when it grows, so give the queue room
  rather than rewrite prose, and escalate in this order:
  1. Shrink the asset so it can share a page: trim the image's own white margin, drop panel widths,
     prefer one 2x2 grid over a wide row plus a second float.
  2. Relax the fraction limits in the preamble (`\topfraction` 0.9, `\bottomfraction` 0.8,
     `\textfraction` 0.07, `\floatpagefraction` 0.66 — a float page then has to be nearly full, so
     floats prefer sharing a page), declare the floats `[!htbp]` so the limits are ignored, and declare
     each float *after* the paragraph that discusses it.
  3. If the order is still wrong — a figure belonging to one section printed below the next section's
     text, or a figures-only page — pin that chapter's figures and tables with `\usepackage{float}` and
     `[H]`. A pinned float sits exactly where it is declared, so the printed order equals the source
     order and text alternates with figures. The price is up to a few centimetres of blank space at a
     page bottom when a float does not fit; prefer that to scrambled order, and do not reach for
     `\clearpage` before the next section instead — it flushes the queue but spends a whole page, which
     then has to be clawed back elsewhere.
  Re-render the section's pages after any of these: the arrangement, not the page count, is what was wrong.
- A mass of `Citation ... undefined` in the final pass means the `.aux` was truncated or rewritten
  mid-run (a second build of the same job — the editor, or the user — racing yours): delete
  `final.aux` and re-run the loop. Do not start editing the bibliography or the `\cite` keys; the
  source is fine and one clean pass resolves every entry.
- Figures that show a computed field are the report's slowest content to get right and the most visible
  when wrong: see `references/field-figures.md` before plotting one.
- A preamble using `fontspec`, `xeCJK` or `\setmainfont` **requires XeLaTeX**; pdfLaTeX dies inside
  `fontspec.sty`. Editor LaTeX extensions default to a pdfLaTeX recipe, so the project fails to build
  in the editor while building fine from a manual `xelatex` call. Pin the recipe to the XeLaTeX one —
  never "fix" it by deleting the font settings.
- A formatter config can be **silently ignored**, and a silently-ignored config is indistinguishable
  from a working one until you diff. latexindent falls back to its built-in defaults — no error, no
  warning, output that looks exactly like an ignored config — whenever the settings it is pointed at
  do not resolve: a mistyped `-l` path, or an extension default that never names a project config at
  all. Confirm the result with a whitespace-aware diff on a copy — do not confirm it by eye.
- A formatter's settings must reach it as **one** argument. latexindent's repeated `-y` flags do not
  merge, so splitting the rules across several silently keeps only the last one; its separator syntax
  is comma for settings, colon for a nested path, semicolon for sibling keys.
- Do not let a formatter rewrite hand-laid-out source unreviewed. A general-purpose LaTeX formatter
  will re-indent wrapped prose and flatten listings it does not understand. Require that the diff is
  whitespace-only and that a second pass is byte-identical, then pre-normalize the sources and commit
  so format-on-save is a no-op.

## Support files

- `scripts/tex_stats.py` — page count, CJK count per section, per-page trailing gap, right-margin
  overflow, rendered heading order.
- `scripts/bib_renumber.py` — renumber `thebibliography` to first-citation order, drop uncited
  entries, rewrite `\cite` keys.
- `references/page-budget-and-template-compliance.md` — template spec extraction, the measure/decide/
  cut loop, cut order, bibliography correctness, pre-delivery verification.
- `references/field-figures.md` — plotting a computed field over a model (temperature, displacement,
  stress): why raw sample scatter is not a figure, what interpolation does and does not work, drawing
  the cut of the model's own mesh, choosing a section plane so the feature the figure is about is
  actually in it, matching an export's rows back to mesh nodes, the render/crop steps, rendering the
  several fields of one case as a consistent panel set, and sizing the panels so the figure shares a
  page with its text.
- `references/results-prose-discipline.md` — keeping the abstract, results and conclusion to what the
  measurements support: over-claiming patterns, terminology reviewers catch, compression that removes
  the AI-generated flavour.
- `references/editor-toolchain.md` — engine selection (XeLaTeX vs pdfLaTeX), where the toolchain
  config lives (user-level editor settings, not the repo), latexindent wiring (config file or inline
  `-y`), the formatter defaults that damage hand-laid-out source, and how to verify a formatter
  config with a diff.

## Related skills

- `grounded-citations` - claims, sources and the Sources block; use it before the
  bibliography step so every citation in the .tex has a resolved [n].
- `evidence-grounded-report-authoring` - the same evidence-first discipline for
  Word/.docx deliverables.
- `research-paper-writing` - paper-level structure; this skill covers the
  template-driven report loop.
