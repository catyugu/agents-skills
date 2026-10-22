# Shallow checkout: detecting and repairing a broken fast-forward

A shallow clone/install can carry a depth cut that truncates the graph, and
self-update tooling often makes it worse: a read-only "is an update available?"
check typically runs `git fetch --depth 1` when it detects a shallow checkout
(fast, and it only needs presence), which marks the NEW tip as a shallow root.

## Symptoms

- `git merge-base HEAD origin/<branch>` prints nothing; `git merge-base
  --is-ancestor HEAD origin/<branch>` exits non-zero.
- `git rev-list --left-right --count origin/<branch>...HEAD` reports an absurd
  "ahead" count (tens of thousands) plus a small behind count, contradicting an
  apparently clean tree.
- The updater reaches its diverged-history path: "local history shares no common
  ancestor / orphan divergence", writes a rescue ref, and hard-resets to the
  remote — a hard reset where a fast-forward was expected.

## Detect

```bash
cat .git/shallow                       # list of boundary commits
git rev-list --count HEAD              # truncated if a boundary cuts your lineage
git rev-list --count origin/<branch>
git cat-file -t $(git rev-parse origin/<branch>^)   # is the parent object present?
```

A boundary entry equal to the remote tip is the smoking gun: the tip is treated
as a root, so everything you already have looks unreachable from it.

## Repair, cheapest first

1. **Remove only the offending tip boundary** when the parent object is present
   locally (the fetch that added the boundary may well have downloaded the
   history anyway). Back the file up first, keep any older boundaries:

   ```bash
   cp .git/shallow "$TMP/shallow.bak"
   grep -v '^<tip-sha>$' .git/shallow > "$TMP/shallow.new" && cp "$TMP/shallow.new" .git/shallow
   git merge-base --is-ancestor HEAD origin/<branch> && echo FF-OK
   ```

   Revert by restoring the backup if ancestry still fails. Edits to `.git/shallow`
   are a graph assertion, not a data change — objects are untouched.
2. **Deepen for real**: `git fetch --unshallow origin` (or `git fetch
   --depth=<large>`) when the missing objects are genuinely absent. Slower and
   heavier, but always legitimate.

## Then

- Re-assert fast-forwardability immediately before the update, and again after
  running any read-only check command, because the next `--depth 1` check re-adds
  the boundary. If the update still lands via the reset path, inspect the rescue
  ref the updater created and confirm the local work is on the parked branch or
  in your patch files before dropping it.
