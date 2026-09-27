---
name: numerical-solver-verification
description: Use when verifying a numerical solver change.
---

Applies whenever a change touches a discretization, an iteration, a time
integration, a coupling, a reduced-order extraction or a material model. The
deliverable is evidence that the new number is right, not a build that compiles.
Layer the evidence from cheapest to most conclusive, and keep straight which
layer a failure belongs to.

## A parametric certificate is an upper bound, not a count

See also `references/inexact-solve-gram-bracket.md` (bounding a Riesz/energy Gram
from an inexact solve, and what its tolerance buys) and
`references/comparing-selection-rules.md` (how to compare two parameter-selection
rules so the claim survives review).

When a method claims a guarantee over a continuous parameter box (an HTC range,
a frequency interval), the certificate has to bound the error everywhere in the
box. A necessary condition, a counterfactual node count, a residual checked only
at the nodes that were solved, or "k consecutive random probes passed" is not
one: extra probes only change the chance of a false accept, and a closing
truncation (an SVD cutoff at the same tolerance) can reopen a residual that
already passed at those nodes. Do not replace a cheap adaptive loop with a
denser deterministic tensor and call the density a proof. Judge the method on
the cost the user named (full-order solves, not wall time or ROM order) against
the baseline that already pays that cost, and retire it when it loses — a
smaller error at a higher solve count is not a win. State the verdict the
measurement gives: a certified method that wins on the named cost is the
deliverable, a method that loses is retired, and a losing method is never
promoted into a new algorithm.

Raw result files (comparison JSON, logs, downloaded CI artifacts) stay out of
the repository. Record the conclusion and the command that reproduces it; leave
the numbers in the ignored output or the CI artifact.

### An exact evaluation beats a tighter bound

Measure a certificate's effectivity (bound over true error) before using it for
anything, and record it. A bound that is orders of magnitude loose is not merely
conservative, it is uninformative for ranking: two designs whose true errors
differ by a large factor are reported as nearly equal, so the bound cannot
support the comparison the user actually asked for, and conclusions drawn from
it are artefacts of the bound.

When the family's operator is a low-rank perturbation of a fixed operator, the
reduced error need not be estimated at all — a Woodbury identity yields the
parameter-dependent Riesz map from one factorization of the reference operator
plus a small number of back-substitutions, for every parameter of the box at
once. The looseness of the classical bound is the substitution of a fixed Riesz
map for the parameter-dependent one, not a property of the greedy, so the fix is
to remove the substitution rather than to tighten the bound.

- **Cross-check the exact evaluation against the loose one.** The exact value
  must lie below the bound everywhere and the gap must close where the two agree;
  an independent upper bound the exact map never violates is the cheapest
  evidence that the map is right. Compare against a direct full-order solve at a
  few parameters as well, and expect agreement at the level of the linear solve.
- **A cell-local normalization beats a global one.** Taking the enclosing map the
  table already holds, per cell, instead of one anchor's map for the whole box,
  is what makes a strict box bound nearly tight — at the cost of refining the
  partition, not of a higher polynomial order.
- **Name the norm the guarantee is in.** The residual identity is exact in the
  parameter-dependent energy norm and carries no constant there; transferring it
  to another norm pays the Riesz mismatch. The two are different statements and
  must not be quoted as one.

### Count what the code does, not what it reports

- Instrument the solve and factorization caches and compare counted against
  performed. A count taken from the code's intent rather than from the counters
  is routinely short, and the shortfall is invisible in the result — only the
  cost claim is wrong.
- Keep the units apart: right-hand-side solves, distinct operator factorizations
  and preconditioner rebuilds are three different numbers, and a family with
  several sources separates them by the source count. Quote both and name the
  unit the baseline pays.
- **Nominally identical operators can be distinguished by floating-point
  jitter.** A plan generated per member from per-member estimates that agree to
  nearly every digit yields many distinct operators where one was intended. Where
  the union of the members' intervals holds for every member, generate the plan
  once for the whole family, then verify the delivered basis is bit-identical —
  the sharing must not move the answer, only the cost.
- **Measure the headroom before re-architecting.** The achievable error at the
  best possible parameters (a width, or an oracle-exact evaluation) says whether
  the sampling is the lever at all. Where the delivered error is already near it,
  the lever is the cost of the factorization, not the choice of samples.
- **Profile the stage before re-architecting around a reported cost.** A stage's
  seconds are routinely spent somewhere other than the term the redesign targets:
  in a certified greedy the candidate scoring is dense algebra whose cost does not
  move with the mesh, while the stage is dominated by an eigensolve for seed
  placement and by the snapshot factorizations. Separate the terms that scale with
  the mesh from those that scale with the candidate count, and quote each one's
  share of the total, before concluding which term the redesign buys.
- **The axis that has a theorem and the axis that does not.** A one-parameter
  resolvent family is Cauchy–Stieltjes, so the optimal-shift rate is a theorem
  and an elliptic shift plan is its implementation. The multi-parameter analogue
  has no such theorem; do not present a multi-parameter construction as if it
  did, and state the missing guarantee as missing. The citation map is in
  `references/parametric-rom-error-certification.md`.

## Order of evidence

1. **Isolated order test.** A manufactured solution with a known analytic
   answer, run at two or three refinements; assert the *observed order* (log2 of
   successive error ratios), not merely that the error is small. Keep the
   manufactured solution smooth, and for a time scheme make it spatially
   uniform: a uniform solution makes the spatial discretization exact, so the
   measured error is the scheme's alone. Measure it over every level, not only
   the final one: a problem that settles has forgotten its transient error by
   the end, and a comparison on the last level alone ranks a scheme that
   resolved the transient below one that did not.
2. **Degenerate-law consistency.** With the new term switched off (a zero
   nonlinearity, a zero coupling, a zero source), the new code path must
   reproduce the old result to solver tolerance. Catches assembly and iteration
   errors an order test averages away.
3. **Reference comparison end to end.** A real case against a trusted external
   solution, judged per output column with a relative tolerance. This is a
   gate, not the last nicety: run it for every change that touches the
   reporting or sampling path or a cross-field coupling, even when the unit
   suite is fully green. A unit suite that builds each field directly cannot
   see a change in how two fields read each other — the reference comparison
   is the only layer that can, and a coupled case failing there while every
   scalar case passes is the signature of that class, not of a physics bug.
4. **Contract test for the iteration.** Assert exactly what you claim: the
   previous solution really is the initial guess (a converged state needs the
   fewest possible iterations, a cold start needs more), the iteration
   converges, a warm start does not drift.

## A solve that reports failure by return value

A Krylov solver that stops at its iteration cap returns the cap and leaves an
intermediate iterate in `x`. Every caller must read that: a field whose solve
never converged is exported as if it had, and the run reports success with a
result file of the iterate. Judge a solver change on the iteration count AND
on whether the count reached the cap.

- A recurrence that divides by an inner product needs a guard before its
  first step: a zero right-hand side with a zero initial guess is already
  solved, and `rho / pq` is `0 / 0`. Check the initial residual against the
  tolerance and return without iterating. When one solver in a family has the
  guard and another does not, the missing one is the bug — read the siblings.
- A test can pass on NaN. Comparisons with NaN are false, so a magnitude
  accumulated as `std::max(0.0, NaN)` stays at its initial zero and an
  assertion of "the solution is zero" holds while the solution is NaN. Assert
  on a value derived from the solution, and check for NaN when a solver is
  allowed to fail.

## A fixed-point iteration whose inner tolerance sets the floor

An outer convergence test and an inner solver tolerance are usually written in
different norms: the outer one on the infinity norm of the residual relative to
the largest right-hand-side entry, the inner one on the 2-norm relative to the
whole right-hand side. Setting both to the same relative value hides the gap,
because the two norms differ by about the square root of the dof count while
the two references differ by the vector's shape. When the inner tolerance is
the looser of the two, the outer residual has a floor it can never get under:
the iterate stops moving, every remaining iteration recomputes the same
residual, and the run ends at the iteration cap with a log full of identical
numbers. Compare the residual the inner solve actually achieved against the
outer threshold and fail at once naming both. Accepting the floor silently is
not an option: it reports convergence at an accuracy the caller did not ask
for.

## An uncaught exception reads as a crash

A driver that lets an exception escape `main` ends the process through the
terminate path, which on Windows reports a stack-corruption exit code and
prints nothing at all. The log then stops at the last iteration before the
throw, so a solver's own complaint is indistinguishable from a segfault and the
reason is lost. Catch at the top of the driver and log the message.

## Measuring a change under a shared binary

- Never rebuild while a run is executing the binary: the link fails with
  "permission denied" and the run keeps the OLD binary, so the numbers that
  follow are from the previous build. Kill stale instances before rebuilding,
  and confirm the executable's timestamp moved.
- A leftover process from a killed run holds memory and CPU and inflates the
  next measurement by multiples. Check for it before timing, and re-measure
  rather than explaining an outlier. A foreign process does the same — the
  user's own applications can hold half the CPU while a run proceeds — and it
  inflates every absolute by two to three times while the *shares* the run's
  own phase timers report stay valid: quote the shares, re-measure the
  absolutes on an idle machine.
- Before attributing a slowdown to the algorithm, split the solve: log the
  preconditioner setup and the iteration loop separately, and print the
  hierarchy amgcl builds (`operator<<`) — number of levels and operator
  complexity tell a healthy coarsening from a degenerate one.

## A preconditioner whose iteration count grows with the problem

A multigrid preconditioner is size-independent by construction: its Krylov
iteration count is set by the operator and the hierarchy, not by how many
unknowns the mesh carries. Test that directly — fix the element order, refine
the mesh, and require the count not to climb. "It converges" is not evidence
that the hierarchy works; a count that grows with the problem is the
hierarchy, and it goes unnoticed until the problem is large.

Separate the wrapper from the library before tuning anything: run the library's
own solver on the very same matrix, through the same assembled operator. The
same count on both sides means the configuration, not the plumbing — which
saves a rewrite of code that was never at fault.

Then find which part of the hierarchy is the limiter, in this order:

- **Depth.** Lower the coarse-level threshold so the hierarchy grows extra
  levels. If the count does not move at all, depth is not the limiter and the
  interpolation is: the coarse grid already resolves everything it can.
- **Interpolation.** Aggregation-based coarsening interpolates from a handful
  of vectors per aggregate. A strength threshold that is loose for the operator
  makes the aggregates large and the interpolation poor, and the threshold (or
  the aggregate size) is then the lever. Classical coarsening interpolates from
  the strongly coupled neighbours and is far less sensitive to it.
- **Smoother.** More sweeps buy a fraction, not a fix: if two or three sweeps
  per level only shave a quarter off the count, the smoother is not the
  limiter either.

The setup has the same lever, and it is the one a nonlinear loop pays: a
hierarchy rebuilt per iteration spends its time in the coarsest level, which
is solved by a direct factorization. An AMG library's coarse threshold
defaults to what its coarse *direct solver* reports about itself, not to the
dimension of the operator, and on a 3D operator one coarsening step can reach
that threshold — leaving a coarse system whose factorization costs more than
everything above it. Lower the threshold and re-measure the setup until it
stops falling, and read the Krylov count from the same run: the deeper
hierarchy can need a few more iterations, which is still a large win while the
setup dominated. Check the cases the change was not made for as well: a solve
whose time is mostly elsewhere can pay a few percent for the extra levels, and
that is a number to report, not to hide.

Below such a threshold the opposite regime hides the same lever: no coarsening
happens at all, so the coarsest level *is* the matrix and the "preconditioner"
is an exact inverse — a direct factorization wearing a V-cycle, which is why a
small enough operator loses to a real direct solver instead of beating it. The
tell is the Krylov count: a preconditioned solve that converges in one
iteration is not a good preconditioner, it is an exact one, and its setup *is*
the factorization. The threshold is a count of the library's own coarse
solver's rows, so a block hierarchy and a scalar one do not stop at the same
scalar size — lowering the scalar one to where the block one already sits is the
first thing to try. Where the crossover between the two sits, and how to
measure it, is in `references/amg-vs-direct-crossover.md`.

Two forms of the same work repeated, neither visible to a phase timer, both
found by profiling per integral: a load whose coefficient is identically zero
(the model defines no source, and the field still assembles its zero property
over every cell of every level), and the cell colouring that lets a parallel
scatter run lock-free — it depends on the dofmaps and the cell list alone, so
it is the same colouring at every assembly, and it is usually recomputed per
integral. Before reading such a profile, check that the instrumentation
registers into its registry from the timer's destructor: a timer stopped
explicitly first records nothing, and the registry's call count is what says
so.

For a system of equations, keep the block structure through the whole hierarchy,
and compare the alternatives on the real operator before switching anything: the
same coarsening applied to the scalar expansion of the matrix is usually much
worse, and a coarsening that cannot handle block-valued entries is not an option
at all. A library's near-nullspace interface may also be unimplemented for block
values — read its source for the note that says so before designing around it.

Two traps in this kind of measurement:

- **A geometry-dependent proxy is not the real case.** A thin box of hexahedra
  reproduced the slenderness of a tetrahedral model, and there an incomplete
  factorization was near-exact (a couple of iterations) while on the real
  tetrahedral mesh the same preconditioner needed an order of magnitude more
  than the multigrid it was meant to replace. Reproduce on the actual mesh.
- **A preconditioner must match the Krylov method's assumptions.** CG needs a
  symmetric preconditioner, and an ILU/ILUT factorization is not symmetric, so
  CG+ILU is an invalid pairing: it may converge for a while and then stall or
  diverge, which reads as a flaky preconditioner rather than a mismatched pair.

- **A nonlinear solve pays for its own assembly once per pass.** A fixed-point
  loop builds the frozen system on every pass, including the pass that only
  verifies the residual the previous solve left behind. Where the system does
  not read the iterate — every coefficient of a linear field, or a field whose
  material law reads another field — both passes build the same operator and
  one of them is pure waste (measured: half of a linear transient's assemblies).
  The knowledge has to come from the field, not from comparing matrices: a
  coefficient that reads nothing, or only another field's solution, makes the
  operator independent of the iterate.
- **Attribute a nonlinear run by phase before touching the preconditioner.**
  Timers around the frozen-system assembly, the preconditioner setup and the
  Krylov solve settle it in one run; on a 105 k-dof three-field case the
  assembly was 60 % (a third of that the evaluation of the temperature-
  dependent coefficients), the multigrid setup 16 %, the solves 12 %. A
  preconditioner kept stale across the iterations of one solve can also be
  refused outright: the inner solve then stops on a residual floor above the
  nonlinear threshold, which the loop's own guard reports.

## A round-off bound that pins the preconditioner, not the discretization

A patch test asserting a linear field is reproduced to `1e-10` may be measuring
the *linear solve*, not the space. The two regimes are separated by the AMG
coarse threshold: a system smaller than it gets the coarse direct solver as its
preconditioner — an exact inverse, so the solve lands at machine precision and
the test passes on a number that has nothing to do with the discretization.
Above it one V-cycle is a good but not exact preconditioner, and the residual it
leaves, amplified by the system's condition number, is what the error becomes.
Attribution before touching the bound, all cheap:

- Vary the *problem size* across the threshold with everything else fixed. Only
the cases above it move (orders 1-3 bit-identical at 50/243/676 unknowns, order
4 at 1445 changed) — that names the mechanism.
- Vary the *linear tolerance* by two orders. If the error barely moves, it is not
  the residual target and no tolerance setting will fix it; if it tracks, it is.
- Then read the solve back: the iteration count, and the residual the solver
  *believes* it reached against `b - A x`. A CG that reports convergence in one
  iteration with a machine-precision recurrence residual while `b - A x` is
  1e-10 is stopping on a recurrence that undershoots — a real weakness, and one
  that a preconditioner change can expose without touching the Krylov code.

Once the number is known to be the linear solve's, restate the bound as what the
test claims ("exact to the linear solve" rather than "exact to machine
precision"), record the measured values for both regimes in the test, and check
the new bound still has teeth by breaking the thing under test: a boundary value
perturbed by `1e-6` on a unit-scale solution must still fail it. Do not revert a
real preconditioner improvement to preserve a bound that was pinning an
accident.

Express the restated bound as a multiple of the defect the solve under test has
just left, rather than as a single looser constant: measure `max|b - A x|`
relative to `max|b|` in the test and require the field's error to be within a
small multiple of it. The bound then stays tight where the solve is exact, says
out loud which solve produced the number, and still fails on an assembly that
stopped reproducing the field. An error that does not move when the linear
tolerance moves by two orders of magnitude is not a tolerance anyone can set —
it is the attainable accuracy of the recurrence, and it is reported as the
delivered accuracy of the solve.

## Reporting a result from a derived state

Sampling a transient result at an output time the steps stepped over is
naturally done by writing the interpolated values somewhere. Writing them into
the field's own solution and restoring it afterwards makes correctness depend on
every path between the two calls, and any early return or throw leaves an
interpolated value in the state as if it had been solved. Give the sampler its
own state object instead, then:

- **Repoint every consumer of the field's state at it.** The result export and
every coupling of another physics must read the same object. Leave one of them
on the live solution and the coupled case is solved at two different times —
silently, on the coupled field alone, which reads as a physics bug rather than a
plumbing one. Publish the sampled state from the field itself so there is one
place that decides what the rest of the case reads.
- **Make the sample idempotent at a level's own time.** The polynomial through
the levels must reproduce the newest level exactly when asked for that level's
time, so one call serves both "store the level" and "store between levels"
without a branch, and the stored sequence is unchanged by the change.
- **Keep the algebraic fields in the same loop.** A field the study does not
advance is solved against the sampled state of the fields it couples to, so
sampling and those solves belong to one "bring every field to time t" step,
called from the first level, from every accepted step and from every stored
output time. Three call sites, one function — do not leave the algebraic solve
attached to only some of them.

## Localizing a failing reference comparison

Compare magnitudes before shapes: per output field and per level, the min/max of
the reference against ours. Matching ranges with a different shape is a
discretization problem; a range off by a factor is a driver, load or initial
state problem; an error that grows with the number of steps is a state that
accumulates instead of being replaced.

| Symptom | Cause |
| --- | --- |
| Relative error ~1 with a tiny absolute error | The reference column is noise on an identically zero field; judge it against that field's own magnitude. |
| Error proportional to the step or iteration count | A solve that adds its result into the state instead of replacing it. |
| Every column fails at once, including fields that cannot depend on the new term | The shared driver (vector reuse, initial state), not the new physics. |
| One level differs by exactly the initial value minus the boundary value | The initial state is not projected onto the constraints; algebraic fields must also be solved at the first level. |
| A scheme fails only the first levels, the error decaying afterwards | A non-L-stable scheme on a discontinuous initial condition; verify the order on a smooth problem instead of loosening the tolerance. |
| One coupled field fails while every other column passes, right after the reporting path changed | Two consumers of that field read different states: the export reads a sampled copy while a coupling still reads the live solution. |

## When the difference survives every check: refine the mesh

Before concluding a modelling error, refine the case's mesh and re-run BOTH
codes. A modelling error converges to a nonzero limit; a difference between two
legitimate discretizations of the same model halves with the element size. Put
the difference next to each code's own coarse-to-fine change: if the difference
is an order of magnitude below the discretization error of the mesh being
compared, the reference and our solve agree as far as that mesh can tell.

## Deciding which code is wrong: rebuild the discretization independently

A difference you cannot attribute to any single ingredient is not yet evidence
that either code is wrong. Assemble the discrete system yourself — one script,
the mesh file, the material map, the quadrature — solve it, and see which code
it agrees with. That is what turns "our answer differs" into "our answer is the
standard discretization's, and the reference's is not". Two things make this
cheap and decisive:

- **Form products, not matrices.** `(K v)_i = int B_i^T C (B v) dOmega` needs
  only one strain evaluation per quadrature point, so an operator check costs
  one pass over the elements instead of an assembly plus a solve.
- **Verify your own script against the code first.** Check a rigid translation
  gives `|K t| / |K|max ~ 1e-16`, and that your `K v` matches the code's on the
  FREE rows before comparing anything else.

Expect your own script to be the bug: every false lead in a long hunt came from
the probe, not the solver. Check it in this order.

- **Mask the way the code does.** A Dirichlet marker is per *physical* dof
  (`bs * node + component`), not per node; masking with the node index leaves
  the clamped rows in and reports a large "residual" that is just the raw
  equation residual on a row the code deliberately zeroes.
- **Join files by coordinate, never by index.** Dump files and mesh files
  number nodes differently, and a stale dump from a different mesh has the same
  shape as a fresh one.
- **Key points by distance, never by a rounded coordinate.** A quantized key
  breaks twice over. A mesh-built `-1e-18` formats as `-0.000000000` while an
  exact `0` formats as `0.000000000`; and a value landing exactly on a quantum's
  boundary rounds to either side, so two files stating one coordinate 2e-16 apart
  — a last-bit difference, `-1.2007812500000001` against `-1.20078125` — bucket
  the point differently. Either way a few percent of the nodes go unmatched, or a
  single one does, and the comparison fails on a point both files carry. Match
  within an absolute distance instead, and still refuse a point with no
  counterpart rather than pairing it with its nearest neighbour.
- **Set the stream precision** (`out.precision(17)`): the default is 6
  significant digits, which is ~5e-7 of error on a coordinate near 0.1.
- **Sum the right entries of a strain row.** The thermal stress is
  `(s, s, s, 0, 0, 0)`, so `B_i^T sigma_th` is the *diagonal* entry of the row;
  summing all six Voigt entries silently adds the shear terms.
- **A hand-written reference element needs the file's own node order.** For a
  quadratic tetrahedron mphtxt orders the edge functions `(0,1) (0,2) (1,2)
  (0,3) (1,3) (2,3)`, and the mesh may store the vertices in the opposite
  orientation to the reference cell — take `|detJ|`.
- **A vector test on block matrices must transpose the blocks.** Comparing `A`
  against its transpose without transposing each 3x3 interior reports a large
  asymmetry on a perfectly symmetric operator.

## The load a constant field cannot test

A uniform temperature makes the thermal load a *boundary* load: for a constant
`sigma_th`, `int grad phi_i = 0` at every interior node, so a uniform-expansion
test exercises the boundary nodes only and passes for any interior quadrature.
A load that varies is the one that tests the interior. Pin it with the identity
`b . v = int sigma_th div v` for a linear `v`, which is exact (both sides
polynomial, quadrature exact) and gives a closed-form right-hand side: for
`T = Tref + g x` on a box, `3 alpha g E/(1-2 nu) V xbar`.

## The reference's discretization is part of its answer

Matching a reference means taking the discretization it took, and the solver
log states it. The order matters more than the tolerance: on a coupled thermal
case, running order 1 reproduced the reference's temperature to 2.96e-05 where
order 2 sat at 2.33e-04 — eight times further out, and outside tolerance on 15
of 63 exported columns against order 1's none — because the reference's own
`Order` column read 1 for 14 of its 18 steps. A solver that selects its order
automatically (COMSOL's BDF, with a min and a max) will not announce it in the
model script; read it off the log's per-step `Order` column and configure the
code under test to the same order, or the comparison measures two time
discretizations against each other rather than two models.

The same goes for the step controller and the output handling: read the solver's
settings for "steps taken by solver" (free or constrained), how output times are
stored, the initial step fraction, the maximum step, and the error weighting —
then check each against what the log's step-size column shows.

The *mechanism* is part of the discretization too, and the tempting substitute
is a textbook-correct equivalent that is not the same function. Two that cost a
full hunt each, both invisible in a manufactured-solution test:

- **The error estimate.** A scheme that corrects a predictor measures the
  correction, not the local truncation error: the correction carries the
  predictor's extrapolation remainder as well as the corrector's truncation
  error, and at order 2 the two differ by a factor of 2.5 — enough to select a
  different order and change the step sequence. Derive the estimate from the
  reference's own source (its correction vector and its `sigma` table) rather
  than from the textbook coefficient.
- **Combining fields.** The reference's norm is a weighted root mean square
  over the study's dependent variables; taking the largest field's norm instead
  is a different function. On a coupled transient the max form held the step
  where the reference doubled and left exported columns outside tolerance,
  where the mean-square form reproduced the step sequence and passed all of
  them. Which fields enter the mean — and whether a field solved exactly at each
  level contributes at all — is a decision to make explicitly.

Both are cases where the code is *right* and the comparison is still wrong.
When a case resists every check of the physics, suspect the mechanism before the
model.

## Reading the reference solver's own log

When a code publishes a solver log, that log is the specification of its time
stepping, and it settles questions no amount of reasoning will. COMSOL's
`Time-Dependent Solver` block gives the step times, the step sizes, the order,
the iteration counts and the per-field scales. From it:

- **The step-size sequence tells you the controller.** A sequence that doubles
  at every step up to a cap is the clamp of 2 being reached each time, which
  only happens if the error estimate sits below `0.9^(order+1)` of the
  tolerance. A controller that grows a step only once its estimate is far
  inside the tolerance cannot produce that sequence — ours grew by 1.25x per
  step and took 4x the steps.
- **A row marked `out` with no step numbers is an output time the steps
  stepped over**, so the reference's value there is an interpolation of the
  scheme's polynomial, not a solution. Compare it against the local time
  constant: a 19.2 s step over a 17 s thermal time constant puts an O(1)
  difference into the reported value at the output time inside it.
- **`Scales for dependent variables` is the error weight.** The nonlinear and
  time error is measured as `W = max(|U|, S)` with that `S`, not relative to
  each dof's own value. A per-dof relative weight is meaningless for a field
  with a wide range (a displacement running from 1e-11 near a clamped face to
  7e-6), and its near-zero dofs then drive the step of the whole coupled case —
  measured, the displacement's round-off gave an error ratio of 27 against the
  heat field's 1e-4.
- **The `Order` column and the step-size column together are a diff target.**
  Print the code under test's own step sizes and orders and compare them
  against the log's, step for step. Agreement there is the strongest evidence
  the mechanism matches; a sequence that doubles at every step up to a cap is
  the startup phase and the clamp being reached, and one that grows by a fixed
  small factor is a different controller. Do this before chasing the output
  values: an output time a step stepped over is an interpolation of that step's
  polynomial, so a mismatched step sequence shows up as an output error with no
  error in the stepping itself.

## Reproducing a reference's output handling: it depends on the steps, not the approach

A reference that reports its output times by interpolating its own polynomial is
matched only if the two solvers' *step sequences* coincide; if they do not, the
interpolation errors do not cancel and both codes report a different
interpolation of a different step. So the same change measures both ways
depending on everything else being right:

- with the step controller and the order still mismatched, reproducing the
  reference's interpolation made things worse (columns outside tolerance 10 ->
  13), because the step sequences differed by a factor of two in count;
- once the order matched, the step sequence landed on the reference's own (18
  steps for 18) and the same change was part of a full pass (10 -> 0).

The lesson is the order of the work, not the answer: reproduce the controller,
the error weight, the step cap and the order first, then turn on the reference's
output handling and re-measure. Treat a "this made it worse" result as evidence
that something upstream is still mismatched, not as a settled verdict.

## Performance changes

A timing claim needs a comparison that can carry it; a build, or a day, apart is
not one.

- Attribute the cost before changing anything: count the solves, their
  iterations and their setups from the `spdlog::debug` lines of a run — and
  check first that the driver calls the library's logging initialiser, since a
  documented level variable is inert until it does and the debug lines then never
  appear. The dominant term is routinely not the assumed one — a loop that
  rebuilds a preconditioner per iteration spends its time in setup while every
  solve converges in a single Krylov iteration. The log's phase boundaries
  attribute a span as well: an iteration that already meets its residual
  threshold returns before its own solve, so the span it closes holds assembly
  and preconditioner setup with no Krylov solve in it, and the growth of the next
  span is the solve. Settle assembly against solve that way before optimizing
  either.
- Compare solve with solve, never wall clock with wall clock. A wall clock
  measures model parsing, mesh loading and export, which dominate a small case
  and say nothing about the solver, and a reference's total batch time includes
  its own model build and meshing. Take each code's own solve time — a reference
  that logs a `Solution time` for its solver node, the code under test from the
  span between its own phase lines — and say which is which. A reference whose
  log resolves only whole seconds cannot carry a ratio for a solve under about
  2 s, and a case that exists to demonstrate correctness is not remeshed to
  become a measurable benchmark: a sub-second solve is simply not a performance
  case.
- Toggle the change in the SAME binary pair (a compile-time switch, or a forced
  `if (false and ...)` at the call site — not by editing the predicate itself,
  which trips `/WX` on the unused parameters) and rebuild both. Take the median
  of at least three runs per configuration; the machine drifts a few percent
  between sessions, so cross-build or cross-session numbers are noise. A
  difference of a few percent with mixed signs is no difference.
- The first timing of a newly linked binary is not a measurement: it is inflated
  by multiples, by cold caches and by a scanner reading the new executable, and
  a suite's first run after a build inflates the same way. Discard it and
  re-measure both sides warm. A ratio that exists only between a build's first
  run and a steady-state run is warm-up, not the change — and it flatters
  whichever binary was built and run last, so it manufactures a speedup for the
  new one and a regression for the old one in the same breath.
- If the timings move in an unexpected direction — most cases faster, one much
  slower — hunt for a behaviour change instead of blaming noise. A performance
  edit that silently disables work (a preconditioner configured before the
  matrix it needs, and then never built) reads as a large regression on the
  cases that work mattered for and a small gain on the cases it did not. The
  mirror case is a change that removes a shared write and is measured against a
  run whose result is garbage: a run that diverged is faster, and its timing is
  not a measurement. Check that the run still produces the reference result
  before believing any number taken from it.
- A benchmark harness passes its arguments as a list with explicit native paths
  and asserts the exit code of every run. On Windows a shell string is executed
  by cmd.exe, so `$VAR` is not expanded, the app exits at once, and its
  "timing" reads as a plausible small number.
- **A profile is per integral, not per phase.** Key the timers by the integral
  being assembled (its weak form, its boundary, the coefficients it reads) and
  record beside the time how many *distinct coefficient-value snapshots* that
  site has seen. A site reached hundreds of times with one or two snapshots is
  not slow work, it is the same work repeated, and that is the waste to remove
  first. A phase-level timer cannot see it: it reports one number for the whole
  assembly, and the per-integral split routinely shows that most of it is
  recomputation rather than any expensive kernel.
- **A cache is only as good as its hit count.** After introducing a kept object,
  instrument the lookups and the rebuilds: a rebuild per lookup means the
  identity it keys on is wrong, or the values really do move; a hit count near
  the call count is the evidence that the profile's "constant" reading was
  right. Hold the cache to its contract with a test that changes the value,
  changes it back, and requires the first and the third result to be equal —
  then confirm the test fails when the predicate is replaced by one that never
  rebuilds. State the invariant the key rests on (what else the assembly reads
  and does not compare) in the cache's own documentation.
- **A before/after file comparison belongs to a timing claim, not to a
  refactor.** A change meant to be numerically identical is verified by the
  reference regression suite alone; rebuilding the previous binary to diff
  result files against it is ceremony this user does not want. Where the
  comparison *is* the evidence, it needs a control run of the OLD binary: a
  solver whose accumulation order is not fixed and whose nonlinear tolerance is
  loose does not reproduce itself run to run, so the spread of the old binary
  against itself is the resolution of the comparison, and a difference of that
  same order is no difference. Without the control, run-to-run noise reads as a
  regression, or as a speedup.
- "No measurable gain" is a legitimate result to report and to commit when the
  change is structurally justified (it removes per-solve work that only a larger
  problem pays for). Say which of the two it is; never dress an unmeasured
  optimisation up as a speedup.

Time integration has its own recipe — variable-step weights, the truncation
error estimate and the step/order controller — in
`references/adaptive-time-stepping.md`. Splitting an uncommitted change set into
independently verified commits is in `references/verified-commit-splitting.md`,
driven by `scripts/stage_hunks.py` (list the working-tree hunks, stage one
concern's subset, commit, verify that tree). Choosing and tuning the linear
solver — where a multigrid hierarchy stops coarsening, and where an iterative
solve should lose to a sparse direct factorization — is in
`references/amg-vs-direct-crossover.md`.

## Proving a fix (red to green)

- Write the failing test first, at the level where the bug lives (the library's
  API contract, not the caller that tripped over it).
- Prove it fails for the right reason: revert ONLY the fix, rebuild, watch it
  fail, restore, rebuild. Confirm the rebuild really happened before reading a
  result: `touch` the restored file first (a restored file keeps its old
  modification time, so the rebuild is a silent no-op), and treat a failed
  build as a stale binary — a revert that leaves a parameter unused stops the
  build under `/WX` or `-Werror`, and the previous binary's passing run then
  reads as "the test does not catch the bug".
- Emulate the pre-fix behaviour where the bug lives, not at the call site. When
  the fix moved a value into a signature (a hard-coded time or default became
  an argument), reverting the caller leaves the function's own behaviour
  correct and the test passes; the emulation has to go inside the function.
- Build and run the single target holding the test, not the whole suite; then
  re-run the full suite, because a fix in a shared numeric primitive perturbs
  every other case.
- Check that the test's OWN premise can separate the two behaviours, before
  reading a red/green result as evidence. A green run under the reverted code
  means the setup cannot distinguish them, not that the fix was unnecessary;
  and a test that cannot fail is worse than no test, because it is recorded as
  coverage. Two ways a setup goes degenerate, both invisible from the test
  passing on the fixed code: a unit-sized cell makes a normalized and an
  unnormalized quantity agree (the factor that was wrong is 1/h, and h is 1),
  and a quantity that is analytically zero under BOTH assemblies stays zero
  whatever the coefficients (a term parallel to the flux of a linear field is
  divergence-free in the face, so its conductance never enters). Print the
  difference between the two configurations and require it macroscopic before
  trusting the comparison; when it is not, break the symmetry they share —
  refine away from the degenerate size, or drive the field with a source so
  the term under test has something to redistribute. Prefer an identity that
  is exact under the correct code (a divergence theorem, a closed-form
  integral) over one that merely cancels, since cancellation is what a
  uniform error survives.

## Auditing a module before refactoring it

Read the whole module — every header AND every translation unit — plus the
driver and the pipeline script that invokes it, before proposing anything. A
header alone describes a program that may not exist: this codebase's headers
document a command-line flag the binary rejects, name classes the code calls
something else, and describe a scheme registry that a fixed method replaced.
The call sites, the driver's argument loop and the invoking script are the
truth; where a header disagrees with them, the header is itself a finding.

- Report in risk order, not file order: (1) interfaces that return a silently
  wrong result, (2) dead or duplicated interfaces, (3) one operation reachable
  several ways, (4) layering, (5) naming. The first tier is the work and the
  rest is optional; interleaving them buries the work.
- Lead with deletion, and name the deletion as the fix. The standing
  preference is to remove a test-only accessor, a settings field nothing
  assigns, or a backward-compatible path outright — not to deprecate it, and
  never to add a compatibility facade or an abstraction to hide it.
- Locate every finding as `path:line` and state what the wrong *result* looks
  like (a dropped boundary condition, a coefficient that never updates, a
  boundary value silently constant), not what the code looks like.
- The deliverable is the audit: no code in the reply, no edits to the tree.
- Order the findings into batches that verify one at a time — delete-only
  first, then interface convergence, then semantics, then layering — and state
  the gate each batch must pass. A delete-only or layering batch must leave
  the reference comparison where it was; a semantics batch needs a failing test
  first.
- The defect shapes to hunt for, and how to find them, are in
  `references/app-layer-audit.md`.

## Reporting

- Quote real numbers from real runs (per-column error, observed order,
  iteration counts) and name the exact commands. Never summarize a run you did
  not execute. Read every figure back out of the artifact the run produced
  rather than from memory of the run: a value recalled instead of re-read is
  wrong often enough to matter, and the artifact is one command away.
- A tolerance exceeded by a scheme's known numerical behaviour is reported as
  such, with the affected levels and the measured excess. Do not edit the
  reference or loosen the tolerance to make it green.
- A verified change set is ready to commit: Conventional Commits, with the
  verification commands and their outcomes in the message body.
- Land a refactor as small commits, one concern each. A commit that mixes
  "remove the duplicated defaults" with "resolve a cell's expression once"
  cannot be reverted or bisected on its own, and a concern usually spans
  several files while one file usually spans two concerns — so stage hunk
  subsets: filter the `git diff` and `git apply --cached` the hunks of one
  concern (list them first, each with its first removed and first added line).
  Generate the filtered patch without newline translation (write bytes, or a
  text file with `newline=''`): text-mode writing on a CRLF checkout doubles
  the CR on every line and the whole patch fails to apply.
- Verify each commit on its own tree, not only the last one. Stash the
  remaining changes, rebuild in the SAME build directory so the untouched
  objects stay cached (each intermediate build costs seconds, not a full
  rebuild), run the full suite, then pop. Put that outcome in the commit's own
  message, so the log carries the evidence per commit and a later bisect knows
  which commits were really green.
- Quote an accuracy figure together with the reference it was measured against.
  A reference regenerated at a different thread count is a different reference: a
  parallel reduction order moves an adaptive stepper's step sequence, so the same
  model re-solved shifts the comparison at ~1e-3 and every figure has to be
  recomputed. Say which thread count the quoted numbers came from.
- A quantity that fluctuates run to run — an error at the 1e-8 scale under
  parallel reduction — is quoted as an order or a range, never as a single figure
  with false precision.
- Put a test where the semantics live. A comparison or benchmark harness is
  scratch tooling: the cases it runs are what establish it, and unit tests for
  the harness itself are scope this user does not want.
