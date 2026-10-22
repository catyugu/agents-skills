# Verification guidelines

Define acceptance criteria while designing the model, before a batch run.

## Verification tiers

Cases are split into two tiers by verification strictness, mirrored by the
`examples/analytic` and `examples/physical` directories and by the lab's
`src/` / `scripts/verifications/` layout:

- **analytic** — the reference solution is an analytical one (closed-form
  temperature/potential/stress field, Bessel/Fourier series, variable
  transform, Fabry-Pérot S-parameters, ...). This is the preferred tier: it
  gives the strongest independent check. Place a new case here whenever an
  analytical solution is available.
- **physical** — no analytical solution is available (complex geometry such as
  the busbar, or a pure build baseline). Acceptance then rests on flow
  runnability plus physical-reasonableness checks: imposed boundary values,
  field ranges, symmetry, conservation, constitutive identities, and mesh
  convergence. Place the case here only when an analytical check is not
  feasible.

## Prefer independent checks

1. Compare with an analytical solution when the geometry and boundary conditions allow it.
2. Check imposed boundary values and physically valid ranges.
3. Check symmetry, conservation, and constitutive identities.
4. For discretization-sensitive results, perform a mesh-convergence comparison.

The analytic tier is not limited to uniform fields. It also covers, with documented
approximations and a downgrade trigger:

- **clean-zone pointwise comparison** against an analytic profile, with boundary layers
  excluded (Saint-Venant end effects, hole-edge singularities, load-edge corners) —
  validated on `SmPlateHoleStationary`.
- **shape-ratio comparison** where the FE base value is fitted rather than hardcoded,
  so only the analytic profile is tested — validated on `TFinArrayStationary`
  (isolated-fin cosh profile; θ_base fitted from the root plane). Explicit downgrade
  trigger: if the shape mismatch exceeds ~15% even after low-Bi design, the case moves
  to the physical tier (imposed-boundary + monotonicity + range checks only).
- **degenerate-pair eigenvalue checks** with mode-shape identification (not mode-index
  mapping): assert the two lowest eigenfrequencies equal the analytic fundamental
  (square-section beam → orthogonal degenerate bending pair), and classify each mode's
  displacement axis from the exported field rather than trusting solver ordering —
  validated on `SmCantileverEigenfrequency` (Euler–Bernoulli f1/f2).

## Export deliberately

- Export values valid for the selected dataset and entity dimension.
- Do not use global evaluation for a domain-only expression.
- Save enough coordinates, time values, domain identifiers, and fields to reproduce the check.

## Batch success criteria

Require all of the following:

- compilation succeeds and the expected class exists;
- `comsolbatch` exits successfully;
- expected MPH and exported data files exist;
- batch log has no meaningful exception, license, failure, divergence, or out-of-memory signal;
- model-specific numerical checks pass.

A solver can exit with code zero while returning a trivial field caused by a wrong selection or terminal definition. Detect this using independent numerical checks.
