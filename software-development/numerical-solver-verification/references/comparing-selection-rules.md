# Comparing two parameter-selection rules

Use when claiming that one sampling/selection rule beats another (greedy versus
random, grid versus adaptive) and the claim has to survive review.

## Control the expensive resource, not the loop count

The scarce resource is full-order operator work, so hold *factorisations and
right-hand sides* fixed across arms and let everything else differ. Equal
candidate-grid cost is not equal cost: a 41 x 41 sweep and a branch-and-bound
run spend their time in different places.

## Score with an independent exact quantity

Each arm's own stopping certificate is a different functional (a relative
entrywise residual on a candidate grid versus an absolute cell bound), so those
numbers cannot be put in one column. Score both arms with the same independent
measure — the exact error on a grid over the whole box, which must include the
box corners — and re-bracket both arms on one common metric when a bracket table
is wanted.

## Measure the crossover, not one point

"Arm A is better" is only meaningful together with the budget at which the other
arm catches up. Run the losing arm at one or two extra budget steps and report
where the crossing is, otherwise the claim is an artefact of the chosen budget.

## Decompose cost into count and unit price

Separate how many cell/point evaluations the method needed from what each one
costs. The two scale differently: the count is set by how tight the bound is,
the unit price by the discretisation. Reporting only the total hides which of
the two has to be improved, and a mesh-independent count with a
mesh-dependent unit price means the algorithm is fine and the solver is the
problem.

## Keep the losing arm runnable

Freeze the previous selection rule as a baseline arm in the same driver, with
the same basis construction and frequency plan, instead of deleting it. The
reported comparison then stays reproducible after the new rule changes.
