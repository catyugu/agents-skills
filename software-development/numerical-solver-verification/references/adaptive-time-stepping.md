# Adaptive time stepping: variable-order BDF with step-size control

Recipe for reproducing a reference's BDF time stepping (COMSOL's Time-Dependent
Solver, which drives SUNDIALS IDA; the same scheme is in CVODE and DASPK). Paths
named below are the mpfem app; the method is general to a first-order-in-time
field equation `M u' + K u = f`.

**Reproduce the reference's mechanism, not an equivalent one.** A time
integration has enough free choices — the error estimate, the weight, the
controller's clamp, how fields are combined — that a scheme which is correct in
the textbook sense still steps differently and reports different values at the
output times. Work from the reference's own source or manual, and verify against
its own solver log (below) rather than against a manufactured solution alone.

## Interface: keep the axes apart

Discretization, step placement and storage are three settings, as they are in
the reference:

- **method and order range** — the BDF with a minimum and a maximum order;
- **steps mode** — free (the solver's own steps), intermediate (at least one
  step in every subinterval of the output times), strict (a step ends at every
  output time), manual (a step of the model's own, with the time-step error test
  off);
- **store mode** — the output times by interpolation, the closest solver step,
  or the solver's own steps.

Do not collapse them into one "scheme" value, and do not offer a CLI flag for
any of them unless the reference's own model states it: a run must not be
configurable into a different discretization from the one the reference used,
or the comparison measures two time discretizations against each other.

A scheme the reference does not have is not a feature. Implementing a second
family (Crank-Nicolson, say) because the code had one costs a second code path,
a second error coefficient and a second controller branch, all of it unverified
against any reference — delete it and keep the method the reference runs.

## Variable-step weights

A constant-step weight table silently drops to first order when the step changes.
Write the weights from the steps, `h` the new one and `h'` the one before it:

- order 1: `a = {1/h, -1/h}`, `b = {1, 0}`
- order 2: `a = {(2h + h') / (h (h + h')), -(h + h') / (h h'), h / (h' (h + h'))}`,
  `b = {1, 0, 0}`, load at the new level alone

The two steps being equal reproduces `(3/2, -2, 1/2) / h`. Test the property
rather than the numbers: at an uneven pair of steps the weights must
differentiate the polynomial through the levels exactly (a quadratic for order
2), and satisfy `Σa = 0`, `Σb = 1` at any ratio — a constant state has no time
derivative.

A fully implicit family needs no load history: keep the solution levels alone,
and write the load of the new level straight into the right-hand side. A stored
load per level is dead weight the moment the second family that read it goes.

## The error estimate is the reference's own, not the textbook LTE

This is the single most expensive mistake available here. The textbook local
truncation error of BDF-q is `C_q dt^(q+1) u^(q+1)` with `C_1 = 1/2`,
`C_2 = 2/9`, which in divided differences is `c_q d_(q+1)` with `c_1 = 1`,
`c_2 = 4/3`. An implementation that estimates the correction its step is
built from does NOT measure that quantity: the correction carries the
predictor polynomial's extrapolation remainder as well as the corrector's own
truncation error, and the two leading terms are of comparable size. At order 2
they differ by a factor of 2.5, which is enough to select a different order,
which changes the step sequence, which changes the interpolated value at every
output time.

Read the estimate out of the reference's source instead of deriving it. In IDA
the step's correction `ee` is kept, `sigma[k] = k! · prod(alpha[1..k])` with
`alpha[i] = h / (psi[i-1] + h)` from the past steps, and the estimate is
`err_k = sigma[k] · ||ee||_WRMS`. At a constant step `sigma[k] = 1/(k+1)` and `ee`
is the `(k+1)`-th backward difference of the computed levels, so
`err_k = k! |dt^(k+1) DD_(k+1) u|`. The estimates at the orders around the one
in use come from the same `phi` table: `err_{k-1}` from the difference already
there, `err_{k+1}` from the change of `ee` in one step, each with its own
`sigma`.

Check the derivation before believing it: the ratio between the two forms is a
closed-form constant of the order, so it is measurable — perturb one and watch
the order selection flip. A step sequence that does not reproduce the
reference's log is the signal that the estimate is still wrong.

Weight it per dof by the reference's `A + R|U_i|` — the relative tolerance times
a fixed absolute factor (COMSOL's default 0.1, or a manual absolute value), NOT
a floor tied to the field's own current magnitude. A field that starts at zero
and grows then faces a purely relative criterion, which demands `h ∝ t` and an
unbounded number of steps. Take the RMS over the dofs and compare against 1.
The absolute factor is worth a direct measurement of its own: a manufactured
solution that decays to zero takes fewer steps under a larger factor, because
the factor is the floor under the weight of a dof whose value has vanished.

## Combining the fields is part of the method

A coupled case's estimate is over every dependent variable of the study, and
the reference's norm is a **weighted root mean square over them**: field `j`
enters as its own mean square divided by the number of the study's fields. It is
NOT the largest field's norm taken against the others'. The difference decides
steps: on a coupled transient the max form held the step where the reference
doubled and left exported columns outside the case's tolerance, while the
mean-square form reproduced the reference's step sequence and passed every
column.

Fields the study does not advance carry no truncation error of their own when
they are solved exactly at each level, so they contribute nothing to the mean.
Decide that explicitly; do not assume the count of fields that step.

## Controller

- **Startup.** Until the order reaches its maximum, or a step fails, or the
  order is lowered, take every step one order higher and at twice the size.
  There is no history to select from yet. The first step has none at all, so it
  is left at its size.
- **The deadbeat region.** A step that met the tolerance is *not* grown by the
  asymptotic formula. The factor is the clamp of `(2e + 1e-4)^(-1/(q+1))` to
  `[0.5, 0.9]` below one, one inside `(1, 2)`, and 2 above it. A step therefore
  doubles only while its estimate is below `2^-(q+2)` of the tolerance — the
  "16 times smaller" the reference's documentation calls the deadbeat region,
  at its order 2 — and is held at its size otherwise. A controller that grows
  the step by the asymptotic factor from inside the band takes a small multiple
  of the reference's steps.
- **A rejection** retries at `0.9 (2e + 1e-4)^(-1/(q+1))` clamped to
  `[0.25, 0.9]`, at the order the estimate favours; a second rejection takes a
  quarter of the step, a third one the order 1.
- **The order** is reconsidered only after `q + 2` steps at a constant order and
  step size, and not on the step after a change of order. Compare the truncation
  error norms `terr_j = (j + 1) err_j`: lower the order when the one below is at
  least twice as small, raise it when the one above is below half of it. The
  raise test differs at order 1 (the reference uses a different threshold there)
  — read it off the source rather than generalising from the higher orders.
- Clamp the factor and fail with a clear error when the step falls below a floor
  with the error still unmet, rather than spinning.
- Restore the pre-step state before retrying, or the retry assembles from a
  rejected level.

## Levels and output times

An output time a step stepped over is an *interpolation of the scheme's
polynomial*, so its accuracy depends on the step sequence and the order — not
only on the tolerance. Two solvers can agree at every step they take and still
report different values at the output times. That is why the step sequence has
to match before the output handling is worth touching: with the controller and
the order still mismatched, reproducing the reference's interpolation made
things worse; once the order matched, the same change was part of a full pass.

Fields that do not step in time are algebraic — solve them at every accepted
level, not only at the stored times, or a one-way coupling between them goes
stale.

## Verifying the controller

- **The reference's solver log is the specification.** Its step-size column,
  its `Order` column and its failure counters say whether the sequence matches.
  Print the app's own step sizes and orders and diff them against the log's —
  a sequence that doubles at every step up to a cap is the startup phase and
  the clamp being reached; one that grows by a fixed small factor is a
  different controller.
- Assert the rules as pure functions on a controller driven by hand: the
  startup sequence against the log's own numbers, the deadbeat band's edges,
  the rejection ladder, and the gate on the order selection.
- A manufactured solution that rises in a boundary layer and is flat afterwards
  (`t/(t+tau)` — a model expression language may have no `exp`) is the showcase
  for accuracy. Assert that the adaptive run beats a uniform one at the same
  step count, and that the cheapest uniform run matching its error costs more.
- Assert the achieved error is a small multiple of the tolerance — the
  tolerance bounds the error of a step, so the accumulated error is not a
  fraction of it.
- Do not assert that a rejection happened. Measured on such a problem with the
  reference's controller, the count is zero: the deadbeat region holds the step
  at the size the rise is resolved at, where the elementary controller grew
  past it and had to throw the overshoot away. Rejection counts are a property
  of the controller, not a universal sign that the error test is live.
- A case whose output spacing already resolves the dynamics can cost more steps
  adaptively than by stepping straight to the output times for about the same
  accuracy. That belongs in the report as expected: the value is the accuracy
  the tolerance holds, not a speedup.
