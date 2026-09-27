# Auditing the app layer for refactoring

The recurring defect shapes of an application layer over the solver library,
each with the mechanism that makes it silent. A finding is worth reporting when
a future caller could pass the wrong thing and still get an answer back.

## Finding them
- Before calling an interface dead, list its callers across the sources, the
  tests and the scripts: `grep -rn "<symbol>" app/`. An interface whose only
  callers are tests is a test-only API sitting in a production header — say so
  rather than quietly keeping it.
- A settings struct: list its fields, then grep each one for an assignment
  outside its own declaration. A field no production path writes makes every
  branch on it reachable from tests alone, and the honest fix is to delete it
  or to wire the producer — not to leave a knob that advertises a capability.
- Grep the header's doc comment against the call sites. Where they disagree,
  the translation unit and the driver are the truth.
- Count the names for one operation before proposing a rename or a merge: two
  entry points for one thing is the commonest redundancy in a layer like this.
- Cross-check a constant the code carries against the config it describes: a
  threshold quoted in a note, a script and a run has three chances to drift.

## The shapes

### A value type wider than the code can honour
A boundary-condition builder that takes a pointwise scalar expression — "a
value at (x,y,z,t)" — and evaluates it at `eval(0,0,0,t)` turns a value written
in terms of the coordinates into a constant, with no error. Narrow the
parameter to what is genuinely honoured (a value depending on `t` alone) so the
misuse cannot be written at all.

### A two-call protocol whose order lives only in a comment
A property that requires `bind_field` before `set_expression`: expressions parse
against the variable table as it stands, and the "reads a bound field" flags
are set by scanning the expression's variables at `set_expression` time.
Reversed, the symbol resolves to a model parameter of the same name (a wrong
value) or to nothing, and the property never re-evaluates — a nonlinear
iteration that converges to a wrong answer. Make the binding an argument of the
operation that needs it.

### A public accessor for an invalid state
An exposed "everywhere zero" coefficient, which physically means "no
coefficient at all", is an invitation to a degenerate solve. Keep it as the
internal starting point of the factories that need it.

### Configuration nothing sets
A time-stepping settings struct carrying step-placement mode, store mode,
manual step and an end-time flag while production code writes only the
tolerance, and the parser reads none of the model properties that would drive
the rest. A setting with no producer is a claim, not a feature.

### One operation, two names
A member solve and a free `solve_system`; a `record` that means "the fields are
already at `t`, do not bring them there" beside a `record_at`; a
`converged(iterations, max)` helper next to a driver that decides convergence
itself and throws. Keep the entry point the call sites use and move the other
into the translation unit.

### A name that collides across meanings
A mesh's polynomial order and a field's element order both called `order` in
one layer. They are independent (the field follows the reference's quadratic
default), so the shared word reads as a relationship that does not exist.

### A per-cell coefficient standing in for a per-facet value
A boundary property that writes its value into the cells around its facets:
two boundaries of one domain overwrite each other and the value leaks onto
cells that never touch the boundary, staying invisible while the solve keeps
the field near the boundary data. A guard against the value varying over the
boundary covers only part of the cases; the fix is a per-facet coefficient
space, not a better guard.

### A parser that drops what it does not know
An interpreter that returns silently when a tag is not found, and whose loops
stop at the first unrecognized method: a model statement landing on a missing
tag simply disappears, and a dropped boundary condition is invisible in any
comparison. When the rest of the layer refuses what it cannot bind, the parser
is the one lax place — and the first thing to check when a case solves but
disagrees.

### A contract that fails outside the error boundary
The driver's `try` covering the solver alone, while model parsing, mesh loading
and tolerance resolution run before it: their failures end the process without
their message, which is the opposite of what the comment above the `try`
claims. Extending a caught region is cheap; the rule is that every contract the
run depends on fails inside it.

### An error message that names the wrong cause
A guard that returns silently when its prerequisite is missing, so a later
step reports something else as absent. When a check is skipped, make the
message say what was actually missing.
