# Where a multigrid hierarchy beats a direct factorization, and where it loses

Method for locating the size at which an AMG-preconditioned Krylov solve stops
beating a sparse direct factorization on the operator a case actually
assembles, and for tuning the hierarchy when it loses below that size.

## Measure a fresh solve, not a kept one

A solver object kept across calls amortizes its setup, and a transient run's
operator barely moves — so the kept number hides exactly the cost a nonlinear
loop pays. Measure it the way the loop pays it: a new solver per repetition, so
every measurement carries its own setup. Report setup, the sweep loop and the
Krylov count separately, or the crossover cannot be attributed to either half.

## The threshold, and the regime underneath it

amgcl's `coarse_enough` is `3000 / static_rows<value_type>()` — its own
skyline-LU's report about itself, not a statement about the operator: 3000 rows
for a scalar double, 1000 blocks for a 3x3 block value (the same 3000 scalar
rows). Three regimes follow, and only the third is what AMG is for.

- **At or below it, no coarsening happens at all** — the coarsest level is the
  matrix and its factorization is the whole preconditioner, an exact inverse.
  The Krylov count collapses to 1, and the "setup" is a direct factorization
  done worse than a real direct solver does it, which is why a small enough
  operator loses to PARDISO by 2 to 4 times.
- **A little above it, one or two steps leave a coarse level of a few thousand
  rows** that a skyline factorization pays for by *profile width* rather than by
  row count. The setup is then **not monotone in size** — 135 ms at 7749
  unknowns against 40 ms at 17019 on one operator family — so a crossover
  measured on such a configuration is a band with holes in it. Report the band,
  not a single size.
- **Well above it the hierarchy dominates** and the iterative solve wins by a
  factor that keeps growing (1.2x at 43k unknowns, 1.5x at 155k).

## Coarsening is value dependent, so measure the run's own operator

Ruge-Stueben's strength test is relative to a row's largest negative
off-diagonal, so the F/C splitting — and with it the setup cost — moves with the
mass/stiffness balance and not only with the sparsity. One mesh and one
sparsity measured 29.9 ms of setup at one time-step weight and 74.2 ms at
another. A micro-benchmark that pins a single weighting can therefore disagree
in sign with the application it stands for: the micro-benchmark explains and
tunes, the application's own interleaved end-to-end A/B decides. Sweep the
weighting as well (`a0 M + b0 K` at the scheme's smallest and largest step) when
reporting — the same case can be won by the hierarchy at a small step and lost
to the factorization at a large one.

## The operator family to sweep

- Refine a mesh of the real shape for the sweep, and measure the real mesh as
  its own point. The crossover moves with nonzeros per row: a tetrahedral mesh
  carries roughly half of what a hexahedral one does at the same element order,
  and the factorization is correspondingly cheaper, so a hexahedral sweep alone
  reads too favourably for the iterative solve.
- Element order belongs to the operator and not to the mesh, and raising it
  costs far more than the dof count suggests.
- Watch the machine's own drift while sweeping: the same case's factorization
  moved from 72 ms to 110 ms between sessions here. Only ratios taken inside one
  session, interleaved, carry.

## Landing a threshold change

- Baseline the full suite BEFORE the edit and record the commands, so a test
  that moves after it is a finding rather than a surprise.
- Expect a patch test whose bound was pinning the old threshold to fail; run the
  size and tolerance attribution before touching the bound ("A round-off bound
  that pins the preconditioner, not the discretization" in the parent skill).
- Quantify accuracy the way a timing is quantified: the old binary against
  itself is the comparison's resolution, and the old against the new is the
  number to report. Measured here, the change moved exported columns by at most
  4e-8 relative over eight cases, against comparison tolerances of 1e-3 and
  1e-2 — a difference at the old-vs-old spread would have been no difference.
