# Model-refresh cost-table reference

Use this pattern when a model update changes measured performance values in a Markdown report and an existing DOCX.

1. Run the relevant benchmark independently for every mesh or configuration represented in the old table. Do not infer new timings, cell counts, or basis orders from the previous report.
2. Preserve the benchmark contract: duration, step size/output count, solver/preconditioner, tolerance, and timing scope. Distinguish full-solve time, ROM online time, and offline extraction time.
3. Store or read the machine-readable result for each case. Compute amortization from measured per-step values: `N_break_even_steps = t_extract / (t_full_per_step - t_rom_per_step)` and `N_break_even_runs = N_break_even_steps / step_count`.
4. Update the Markdown table and explanatory paragraph from the same result set. For DOCX, inspect table indices/shapes and nearby paragraphs first; edit only the relevant table and prose, preserving institutional content.
5. Verify by reading both artifacts back. For DOCX, run a package validator and check every updated cell plus the explanatory paragraph. Reconcile displayed rounding against the raw JSON values.

Do not report a one-run speedup when extraction dominates. State that the break-even workload amortizes offline extraction. A missing optional document dependency is a setup issue: use the project environment or an ephemeral `uv run --with python-docx` invocation rather than changing the document workflow.