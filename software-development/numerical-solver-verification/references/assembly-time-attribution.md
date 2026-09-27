# Attributing FEM assembly time

Method for finding out where assembly time actually goes, before changing any
kernel. Works for a dolfinx-shaped assembler (per-cell kernels built by a
factory, driven by `assemble_matrix`).

## Measure in two places, not one

- **Library side, one thread**: temporary timers inside the kernel factory
  split each cell body into *geometry map* (gather, Jacobian, inverse,
  determinant, reference-to-physical basis mapping, coefficient values) and
  *weak form* (the user kernel). Key the counters by the weak-form function
  pointer so the per-form breakdown comes out of the same run. Revert the
  instrumentation before committing; never leave it in.
- **App side, default thread count**: wall clock per case, interleaved runs of
  the two revisions (A/B/A/B) because a workstation's absolute timing drifts
  by tens of percent between sessions; report medians.

Report **both** numbers. A kernel win can be invisible at the default thread
count (assembly stops being the critical path once it is spread over cores),
and only the one-thread number shows the work removed. Conversely, a flat
one-thread number with a moving wall clock means the change moved a serial
phase, not the kernel.

## Phase separation without instrumentation

When the library must stay clean, separate phases with no-op substitutes
instead of timers: assemble the same form with a scatter that keeps nothing
(kernel + map only), and with a no-op kernel (map + scatter only). The
difference against a fully no-op form gives each phase. Do not cite numbers
from an instrumented build and a clean build as if they were comparable.

## Pitfalls

- Scratch buffers inside a cell kernel must stay **per kernel copy**: the
  assembler hands every parallel body its own copy, so hoisting them into a
  shared struct silently corrupts results under parallelism. It shows up as
  test failures only, never as a compile error.
- `git stash` reverts *tracked* files a build depends on (a tools
  `CMakeLists.txt`, a target registration), which turns into "unknown target"
  and a stale binary that looks like a fresh measurement. To compare two
  revisions, copy the files aside and `git checkout` the originals, or build
  both binaries and keep them side by side — never trust a binary whose build
  step was skipped.
- A test that bounds an iterative solver's iteration count against another
  preconditioner's is a knife-edge: reordering floating-point sums in the
  assembly moves the AMG coarsening and with it the count. Before blaming a
  change, measure the bound's margin at the *previous* revision; if it is a
  few percent, recalibrate from measured values (and say so in the commit)
  rather than chasing rounding.
- Compiler instruction-set flags are a hypothesis like any other: measure
  them on the real kernel. Dense small-matrix loops often do not vectorize
  because of trip counts and aliasing, so `-march=native` can be worth nothing.
- Split a hot function into its sub-parts before rewriting any of them. On a
  point-evaluation path the *batched* basis tabulation plus the coefficient
  expansion was ~5 % of the call and the *per-point* pull-back loop (gather,
  Jacobian, inverse, determinant, mdspan construction) ~95 % — the per-point
  allocation that is obvious in the source was not the cost. Attribute inside
  the function; never pick the sub-part by reading it.
- Match the evaluator to what the consumer needs, and count the consumers. A
  cell-wise (DG0) property wants one value per cell, so the shape to write is a
  per-cell gather against the basis at the reference centroid — a constant per
  element type — not a faster general point evaluation. And a field two
  properties read is evaluated once per property unless the evaluation is
  shared per refresh: on the busbar that alone was half of the coefficient
  evaluation.
