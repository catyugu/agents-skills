# Post-update re-application and verification

Depth for step 6: what to port, how to prove it on the real system, and how to
attribute a failure you did not cause.

## Port or drop each hunk

| Hunk shape | Verdict | Action |
|---|---|---|
| Upstream now implements the same intent with different code | Obsolete | Drop it; note the upstream path that serves it |
| Upstream generalizes a check the local hunk special-cased (e.g. a bare-value filter became a routability helper, and the local hunk added another value to the special-case list) | Obsolete, porting is harmful | Read the upstream guard's SEMANTICS first — the value the local hunk "fixed" is often vacuously allowed by design upstream |
| Same intent, still missing upstream, anchor moved | Port by hand | Re-anchor at the new location; preserve the comment's WHY, drop the WHAT |
| Catalog/table hunks (model lists, provider tables, enum data) | Almost always obsolete | Those moved into curated/generated data files; diff the current file before porting |
| Whitespace, EOL, trailing-newline edits | Noise | Drop; never carry them forward |

A whole hunk can be obsolete while its sibling in the same file survives —
decide per hunk, per call path (the same intent is often read on several call
paths: a CLI resume path, a TUI/desktop RPC path, a gateway path).

## Prove it on the real system

A patch's own tests prove self-consistency only. The strong evidence is the
production chain over real state:

1. **Read the real persisted state read-only.** Open the app's store in
   read-only mode (sqlite: `file:<path>?mode=ro`, `uri=True`) and select the
   rows the fix is about. Confirm the premise: the row carries only the field the
   fix reads, and the pre-fix code path had nothing usable to work with.
2. **Drive the production chain, not a mock.** Import the real modules from the
   checkout, feed a real row, print what the resolver returns (route, provider
   identity, endpoint). Diff it against what the ambient default would have
   produced — that difference is the bug, and its absence is the proof.
3. **State the before/after.** For each row, say what the pre-fix branch returned
   (derivable from the diff you hold). "Row R resolved to X" plus "before, this
   returned nothing and fell back to Y" is evidence; a green unit test is not.
4. Keep the probe as a small script in the durable directory OUTSIDE the
   checkout and re-run it after the next update — it is the regression check for
   the whole port.

Coverage rule for the test run: compute the real blast radius by grepping the
test tree for the symbols you touched (`grep -rl <symbol> tests/`) and run those
files; a touched helper usually has one owning file plus a handful of adjacent
ones.

## Attribute failures before reporting them

- Run the touched-symbol files first, so any red test is already narrowed.
- A failure in a path you never touched can still be inherited: look for the
  module's own documented limitation (e.g. a helper docstring stating the
  platform cannot distinguish two states), then reproduce the MECHANISM in a
  ~5-line standalone experiment (call that helper on a same-shape input and show
  the values are identical, so the assertion cannot pass here regardless of your
  patch).
- Cross-check that the failing test does not call any changed function before
  claiming it is unrelated.
- Never hand the user a bare "N failed" for a patch you just applied — they will
  read it as your regression. Report the inherited failure with its evidence and
  what it would take to fix (an upstream change), separately from your result.

## Restart map

Source edits in a live checkout are inert in processes that already imported
them. Enumerate which surfaces execute the changed code path and which must
restart:

- newly launched CLI processes pick the change up on their own;
- long-lived backends (TUI/desktop backend, gateway, dashboard) keep the old
  module in memory until restarted — a service restart is a user-visible action,
  so name the command instead of silently restarting a live service.
