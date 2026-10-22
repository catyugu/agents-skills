---
name: interface-audit-refactor-plan
description: Use when auditing a module's interfaces before a refactor.
---

Use when the user asks to find redundant, dead or misuse-prone interfaces in a
module, library or app layer and propose what to delete and reshape — typically
with "先不写代码" / "just the plan". The deliverable is a written plan with
`path:line` evidence, not edits. Answer in the user's language (Chinese by
default), compact, no preamble.

## Order of work
1. **Baseline first.** Build and run the module's own test target; record the
exact command and the pass count (assertions and cases). Every later claim of
"behaviour unchanged" is relative to that number, and it is also what tells you
whether a deletion is provably safe. If the module has an end-to-end suite, run
it too and note it — a plan that changes numerics must name that suite.
2. **Read the headers of the whole layer first** (they are the interface
surface), then each implementation. Never judge an interface from its call
sites alone: the comment that explains a parameter is often the only record of
why it exists.
3. **Census every suspicious symbol.** Grep the whole tree for it and split the
hits into production and test-only callers. A symbol whose only callers are
tests is dead in production — this is the single most productive
discriminator, and it is what separates real API from test scaffolding wearing
a public signature.
4. **Record evidence as you go:** `path:line` for the definition *and* for
every caller, plus the mechanism in one clause. A recommendation without a
caller census is a guess.
5. **Group, then rank.** Write the groups before the details so the user can
stop reading early.

## What to hunt, in priority order
- **Unreachable knobs:** an options/settings field no production path ever
  sets. Follow it out — it usually drags branches, enums, validation and a
  second code path with it. Find the value the production constructor
  actually passes; that value is the real interface.
- **Ownership mismatch:** a component holding a reference to something its
  caller copies or shares. The tell is a test constructing the owner from a
  temporary; it works only while nothing reads the member after construction.
- **One name, two jobs:** a setter that both declares a binding and sets
  state. Split it when omitting either use fails silently rather than loudly.
- **Unenforced contract pairs:** "call A then B" (refresh-then-assemble,
  prepare-then-solve) where both call sites hand-write the pair. Fold it into
  one entry point so a caller cannot get it wrong.
- **Defaults that belong elsewhere:** a domain-specific constant used as the
  module's general default, so tests and production run different values.
- **Sentinel as value:** `0`/`false` meaning "unavailable" where it also means
  a legitimate value the consumer reads as "very good".
- **Sibling overloads with same-typed, different-meaning parameters** (a cell
  region id and a facet region id both as bare integer sets) — the compiler
  cannot catch a swap. Name the functions or introduce distinct types.
- **Lifetime leaks:** a returned `span`, `const T*` or `string_view` outliving
  the container that owns it, and a returned pointer into a vector the owner
  can reallocate.
- **Duplicated machinery that has already drifted:** a feature added to one
  sibling and not the other is the proof that the duplication is a defect,
  not just verbosity. Without drift, do not propose the abstraction.
- **Dead includes and fields only a log line reads** — especially a field
  whose name invites the wrong use.

## Output shape the user wants
- Groups in this order: (A) delete outright, (B) rework, (C) duplication and
  layering, (D) **do NOT do** — name the tempting over-engineering and why it
  loses, (E) an ordered commit plan.
- Every claim carries `path:line` and a mechanism. "Redundant" alone is not a
  finding; "no production caller, and it forces these three branches at X" is.
- State the cost of each deletion: which tests die or must be rewritten, and
  roughly how many lines of test go with it. A test that must die is a cost
  line, never a reason to keep the interface — but name the coverage lost.
- **No code, no patches.** If the user later says go, the commit order is the
  plan.
- Lead with the two highest-leverage items ("if you only do one thing"): the
  largest branch-count reduction, and the one real latent bug.
- Order the commits so pure deletions come first (provably no-ops), then
  ownership/type fixes, then contract folding, then behaviour-changing defaults
  last, each with its verification command. Flag every step whose behaviour
  changes as needing the full regression re-run with its numbers re-measured.

## Pitfalls
- A test-only caller proves a symbol is unused *in production*, not that it is
  outside the published surface — check what the module exports or installs
  before recommending deletion of a public one.
- A knob can be live in a path you have not read: confirm what the driver or
  main entry actually sets rather than trusting a struct's defaults.
- Do not propose an abstraction to remove duplication unless it has drifted or
  the next change is certain to touch both sides; this user reads a
  speculative helper as added cognitive load.
- Re-check the tree before writing the plan: a branch may already have removed
  the thing you are about to recommend deleting.
