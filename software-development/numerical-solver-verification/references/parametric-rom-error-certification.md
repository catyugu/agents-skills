# Certifying a parametric reduced model over a continuous parameter box

Depth for the SKILL.md sections on a parametric certificate and on exact versus
loose error evaluation. Applies to a family `A(p)` whose parameters enter
through local or low-rank structure (a boundary heat transfer coefficient, a
material field over a subregion), reduced by a sampling-and-SVD or a greedy
extraction, and certified over the whole box rather than at the sampled points.

## What the guarantee has to cover

A certificate bounds the error *everywhere in the box*. None of the following is
one: a necessary condition; a counterfactual node count; a residual checked only
at the parameters that were solved; a random-probe stopping rule, where more
probes only lower the chance of a false accept; or a bound valid at the sampled
points that a closing truncation (an SVD cutoff at the same tolerance) can
reopen. A denser deterministic tensor of the same construction is not a proof of
continuity either — it is the same unproved statement at more points.

## Where the looseness comes from

For a residual-based bound the error is `norm(r, A(p)^-1)` in the natural
(parameter-dependent) energy norm, and in that norm the statement is an
*identity* with no constant. A bound that is orders of magnitude loose has
usually paid for a substitution on the way to the norm it is stated in — most
often replacing the parameter-dependent Riesz map by a fixed one at an anchor
parameter. Diagnose that before touching the greedy: measure the effectivity,
and check whether the loss is in the estimator or in the map between norms.

## The structured-operator construction

When `A(p) = A_ref + B diag(delta(p)) B^T` with `B` of rank much smaller than the
operator (support-localized parameters), the family is evaluated from one
factorization of `A_ref`:

- `A(p)^-1` follows from the Woodbury identity, so no re-factorization per
  parameter is needed;
- the reduced-output error is an entrywise formula in the reduced quantity
  `Z^T A(p)^-1 Z`, evaluated for the whole box at the cost of one factorization
  plus `m_b + n_src` back-substitutions — cheaper than a partition sweep by a
  wide margin;
- the poles of the family are the reciprocals of the eigenvalues of the boundary
  Schur complement, which is what makes the parameter dimension finite and small.

Limits worth stating: the construction needs the parameter dependence to be
local or low-rank. A parameter that changes the whole operator does not admit it,
and the classical estimator is then the only option. A rank large enough to make
`m_b` comparable to the number of sampled parameters is not worth it — the exact
table is then more expensive than the sampling it would replace.

## Certifying the box, not the samples

- Bound the *variation* of the reduced quantity across a cell of the partition
  with a Bernstein (or equivalent polynomial-envelope) argument, and normalize by
the cell's own lower bound of the output, taken from the same table.
- Refine the partition rather than raising the trial order when the bound stalls:
  a bound dominated by the Riesz variation across a cell does not improve with a
  better polynomial, and the stall reads as "the certificate has no resolution"
  when the partition is the lever.
- A frequency axis behaves differently from a real parameter axis. A resolvent
  family in the frequency is Cauchy–Stieltjes, so a one-parameter theorem
  (the optimal-shift rate for a Stieltjes function on a spectral interval) does
  exist and justifies an elliptic shift plan. An attempt to carry a real-parameter
  box certificate onto the imaginary axis needs the anchor map to control several
  decades of the parameter at once, which is exactly the regime where it is
  useless; treat that as a known gap, not a tuning problem.

## What the literature does and does not provide

Consult these before claiming novelty; each was checked against the source.

- **No multivariate Zolotarev theory.** The two-parameter (bivariate) analogue
  of the Zolotarev problem is not established. Do not build an argument on it.
- **No convergence theorem for multivariate rational Krylov on Stieltjes matrix
  functions with two or more parameters.** Multivariate AAA is an explicitly open
  problem; the parametric AAA variants assume a tensor-product grid and provide
  interpolation, not convergence.
- **Kolmogorov width for localized parameter support.** Where the parameter
  matrices have disjoint or localized supports, the width decays exponentially in
  the number of terms *without* the dimension-dependent exponent — the curse of
  dimensionality is absent from the parameter direction, which is why the
  measured tail of a boundary-parameter family falls so fast. This is the
  theorem that justifies sampling the parameter direction at all.
- **Greedy is optimal only up to a constant.** The weak-greedy is quasi-optimal
  with a factor involving the greedy's stability constant; and no subspace spanned
  by elements of the family itself can in general reach the width rate. So
  `error <= C * width` is not available in general — say which of the two a claim
  rests on.
- **A loose coercivity bound weakens the guarantee, provably.** The rate that an
  estimator-driven greedy achieves depends on the coercivity lower bound, so a
  pessimistic certificate degrades the theoretical rate as well as the practical
  ranking. This is the formal motive for an exact evaluation, not just a
  convenience.
- **The exact-map identity is folklore outside model reduction, and its closest
  published relative is a reduction, not a certification.** The static
  reanalysis identity predates model reduction (structural reanalysis
  literature); the nearest published MOR use splits a structured transfer
  function into non-parametric subsystems via the same identity and builds a
  sampling-free parametric model, demonstrated on a chip thermal-conduction
  benchmark. Cite that as the closest related work, and state the difference
  honestly: it *constructs* a model and requires the subsystem to be reducible,
  whereas an exact map *evaluates* the error of an already-delivered basis and
  requires no such condition.
- **In this application's own literature** the baseline extraction method has
  never published a sampling rule with a normalized error constant or a proved
  bound over a continuous parameter box; its stopping rule is a random-probe
  heuristic and its truncation is a column-normalized SVD at a fixed tolerance.
  The one a-priori bound over arbitrary parameter values in the wider literature
  comes from a different community and assumes a single input, a diagonal
  parameter map and symmetry — not transferable as stated. Same-structure
  deterministic MOR without sampling exists in the power-electronics literature.

## Surveying this literature

- Fan out disjoint sub-questions to parallel subagents (what theorems exist and
  under what conditions; what the baseline's own lineage published; what the
  adjacent community did on the same structure) rather than one broad query.
- Require each answer to give the theorem statement with its conditions, the
  venue, and the sample/pole counts — and to say which claims are unverified.
- **Demand that citations be checked for existence.** A plausible author list and
  subject are not a paper; a survey that conflates two titles or invents a
  follow-up will otherwise enter the document as prior art. Ask explicitly for
  the flag when a citation could not be located.
- Paywalled venues are unverified, not negative results. Record "could not be
  retrieved" and restrict the conclusion to what the abstract and metadata
  support; do not report an absence as a finding.

## Driving a refinement loop with the certificate

A branch-and-bound refinement is only as good as its choice of the cell to
refine, and the bound is not qualified to make that choice.

- **The bound's maximizer is not the error's maximizer.** On a coarse partition a
  bound built from an anchor map can be orders of magnitude loose, and its
  largest cell then sits in the wrong place: refining where the *bound* is worst
  walks away from the true maximum. Measure both arguments on the same box before
  wiring the loop. Let the upper bound do stop and prune only; take the
  enrichment parameter from a lower bound or from the exact evaluation on the
  cell; declare a cell resolved when its lower bound reaches its upper bound.
- **The same construction that is useless on a coarse partition is adequate on a
  fine one.** Quantify the bound against the exact box maximum across partition
  levels. That measurement is the motive for an adaptive partition; a cost
  argument (a candidate sweep growing like `m^d`) usually is not, because the
  sweep is dense algebra whose cost does not move with the mesh.
- **The per-cell cost of the rigorous bound sets the budget, not the candidate
  count.** A cell whose Riesz map comes from the shared Woodbury table still pays
  a dense solve of the boundary size per cell, so the bound costs `N_active`
  times a cubic in the boundary degrees of freedom; an anchor shared by many cells
  costs one sparse factorization per anchor plus dense algebra per cell. At a fine
  mesh only the shared-anchor form is affordable, and its anchors are then coarse
  relative to the cells — which is also what the rate theorem needs (a bounded
  per-cell parameter ratio), so tighten the bound by refining cells, not by giving
  every cell its own anchor.
- **Report the maximum over cells.** A sweep that records a secondary value of the
  bound (a differently normalized, or absolute, variant) at the cell that
  maximizes the *relative* one reports that cell's value, not a box guarantee, and
  tabulating it beside the exact box maximum invites an invalid comparison — the
  reported number can fall below the true maximum, which reads as a violated
  bound. Return the max over cells of every quantity separately.

## Gaps to state rather than paper over

Report what still has no certificate alongside what does — typically a transient
accumulated over many steps (certified at each step, not over the trajectory)
and any axis the box certificate does not accept. A method that is exact on one
axis and heuristic on another is described that way in the summary and the
commit message.
