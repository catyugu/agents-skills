---
name: evidence-grounded-report-authoring
description: Use when expanding reports from verified project evidence.
category: productivity
---

# Evidence-Grounded Report Authoring

Use this skill when expanding or completing an academic, internship, engineering, or research report from project repositories, weekly reports, experiment logs, result files, and an existing Word template.

## Core principles

1. Produce the requested artifact, not only an outline.
2. Treat repositories, weekly reports, scripts, logs, and result files as the evidence base.
3. Separate verified results from interpretation and future work. Never invent values, experiments, or implementation ownership.
4. Preserve the template's cover data, approval tables, headers, footers, and institutional formatting unless redesign is requested.
5. Avoid a chronological diary. Organize around motivation → method → personal contribution → validation → limitations → conclusions.
6. Keep individual work distinct from group-level project descriptions.
7. Prefer quantitative tables generated from result files over screenshots or decorative figures.

## Workflow

### 1. Discover sources

- Resolve every attached file and repository path before drafting.
- If an alias such as `@file:...` is missing, search the working directory and referenced project path; do not conclude it is unavailable until the filesystem has been checked.
- Inspect the report structure and existing text first.
- Read weekly reports chronologically, then inspect README files, relevant scripts, experiment outputs, and JSON/CSV results.
- Build a compact evidence ledger: claim, source path, exact value or implementation detail, and suitability for the main text.

### 2. Plan causal structure

A robust engineering internship outline is:

1. Purpose and task definition
2. Background and project architecture
3. Core implementation and personal contribution
4. Experimental design and quantitative results
5. Error analysis, limitations, and improvement directions
6. Summary and learning outcomes

Include equations only when they explain an implemented method. Explain the comparison protocol before presenting numbers: same geometry, materials, sources, initial state, boundary conditions, time interval, and step count.

### 3. Draft with evidence discipline

- State what was implemented, reproduced, measured, or compared.
- For every numerical claim, preserve units, mesh size, cell/DOF count, solver, tolerance semantics, timing scope, and reference definition.
- Distinguish online ROM time from offline extraction time and report break-even workload where relevant.
- Explain surprising outcomes through model semantics before attributing them to algorithmic performance.
- For multi-domain thermal ROM, preserve the verified rule: reduce source-containing regions and keep passive source-free regions as full FVM unless independently validated otherwise.
- Do not paste large code blocks into the body; use compact equations or refer to scripts.

### 4. Edit DOCX safely

- Use `python-docx` or the document skill's scripts, not raw text replacement inside DOCX XML.
- Inspect the template's actual style catalog. Institutional templates may lack `Heading 1`, `Heading 2`, or `Table Grid`; fall back to an existing normal style and create report-specific heading styles when needed.
- Remove only the instructional placeholder region, not cover or evaluation content.
- Insert report paragraphs and quantitative tables before the approval/signature table.
- Apply Chinese fonts to run fonts and East Asian font mappings.
- If `Table Grid` is absent, leave the table style unset or define a safe custom style.
- Overwrite the input only when explicitly intended; otherwise create a sibling output and report its absolute path.

### 5. Verify before delivery

Run all checks against the result:

- Read structure: paragraph/heading count, table count, and table shapes.
- Read full text and verify expected sections, key numerical claims, and absence of template instructions.
- Run the DOCX package health validator and require an explicit healthy result with no issues.
- When a renderer is available, inspect page breaks, formulas, and table widths.
- Report the exact changed file and checks passed.

## Pitfalls

- A missing alias can refer to a nearby directory with a different name; search before drafting.
- `python-docx` may be absent from the active interpreter. Use the project's environment or an ephemeral `uv run --with python-docx ...`; do not treat this as a document limitation.
- Do not assume the default Word style catalog exists.
- Verify XML insertion order; otherwise content can land after the approval table.
- Reconcile every declared total and timing value against source files before finalizing.
- Do not claim all project work was personally implemented; attribute shared architecture carefully.

## Evidence ledger template

| Claim | Source | Exact evidence | Main-text treatment |
|---|---|---|---|
| Feature or method | weekly report / source file | implementation detail | explain method |
| Performance result | JSON/log | value + units + scope | quantitative table |
| Accuracy result | regression output | error metric + reference | validation paragraph |
| Limitation | experiment notes | observed condition | limitations section |

## Related skills

For general DOCX API and package checks, consult the protected `docx` skill. For the domain semantics of the target document, consult the project's own skill. This skill adds the evidence-first composition and template-preserving integration layer.

Verifiable claims and sources go through the `grounded-citations` skill (ledger +
Sources block); this skill owns composition and template preservation.
