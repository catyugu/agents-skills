# Bounding a Gram of an inexact solve

Use when a certificate needs `B^* W^-1 B` (a Riesz/energy Gram) and the direct
factorisation is the bottleneck.

## The bracket

For any approximate `Y` with residual `R = B - W Y` the identity

```text
B^* W^-1 B = (B^* Y + Y^* B - Y^* W Y) + R^* W^-1 R
```

holds exactly, and with any `0 < lam0 <= lam_min(W)` the correction is bounded
by `lam0^-1 R^* R`, so

```text
lower = B^*Y + Y^*B - Y^*W Y  <=  B^* W^-1 B  <=  lower + lam0^-1 R^*R
```

Rules that are easy to get wrong:

- The solver need not converge and need not be certified: a worse solve only
  loosens `upper`. Never gate correctness on a solver tolerance or on a set of
  norm-wise a posteriori estimates; gate it on the explicit residual.
- `lower` is valid for *every* solve quality, including `Y = 0`. Test that
  degeneration explicitly (`0 <= B^*W^-1 B <= lam0^-1 B^*B`).
- The Gram usually enters the certificate only through a non-negative quadratic
  form, so substituting `upper` where the exact Gram was used keeps the
  enclosure valid. Verify that condition before swapping, and re-run the
  enclosure check afterwards rather than assuming it.
- The iterative tolerance must be re-tuned per operator size. A tolerance that
  is negligible on a small operator can let the correction dominate on a fine
  one; sweep it instead of inheriting a production default.

## Sourcing `lam0`

Prefer one global enclosure of the smallest eigenvalue at the reference
parameter, propagated by monotonicity (`A(p) >= A(p^-)`, `W = A + wC`), over any
per-cell eigenvalue computation. Never put `eigsh(which='SA')` on the hot path:
on large sparse operators it costs far more than the solve it is meant to bound.

The admissible slack in `lam0` is worth *measuring*: the correction is quadratic
in the residual, so orders of magnitude of slack in `lam0` cost little once the
residual is small, which is what makes a cheap verified enclosure sufficient.

## Measuring the win honestly

- Time complete paths: preconditioner setup plus one solve per right-hand side,
  against a direct factorisation plus the same right-hand sides. A setup that is
  orders of magnitude cheaper than a factorisation is still paid once per
  operator.
- Report the loosening of the quantity that actually enters the certificate —
  often the square root of the Gram's spectral ratio, not the Gram itself.
- At small operator sizes the direct path wins on wall time; the crossover has
  to be located, not assumed.

## Tooling pitfall

`scipy.sparse.linalg.cg` returns `(x, info)` where `info` is a convergence code,
not an iteration count. Count iterations with `callback=`; an iteration count
read off `info` prints zeros and hides what the preconditioner is doing.
