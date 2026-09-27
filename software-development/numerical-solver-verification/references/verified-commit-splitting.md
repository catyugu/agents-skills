# Splitting a verified change set into small commits

For the end of a task: the working tree holds one large, verified change set (a
refactor, a fix plus its refactor, several unrelated cleanups) and the commits
have to be small, each building and green on its own. `scripts/stage_hunks.py`
next to this file drives the staging.

## Shape of the split

- **One concern per commit.** A commit that mixes "hold the shared defaults in
  one place" with "resolve a cell's expression once" cannot be reverted or
  bisected on its own. Two files can serve one concern, one file can serve two:
  the unit is the concern, not the file.
- **Order the commits so every intermediate tree compiles.** A signature change
  goes in the same commit as its definition and its callers; a caller-only or a
  definition-only commit does not build. Commit the change that others depend on
  first (a new header before the code that includes it).
- **Prefix by kind.** Conventional Commits, imperative: `refactor(app):`,
  `fix(app):`, `perf(la):`, `chore(app):`. A behaviour fix hides inside a big
  refactor — split it out so it can be reverted alone.
- **The message carries that commit's own evidence**: the exact build and test
  commands with their outcomes at that tree (assertion and case counts, the
  reference comparison passing), and for a fix the red/green observation. One
  body per commit beats one body describing the whole series.

## Procedure

1. `scripts/stage_hunks.py list` — every file with its hunks and, per hunk, the
   first removed and first added line, which is what tells the concerns apart
   ("which hunk is the new defaults header" vs "which is the factory lambda").
2. Decide the mapping: for each commit, the `path:indices` set whose hunks
   belong to its concern. Do not try to map hunks per file alone; expect to mix
   files into one commit and one file into two.
3. Stage and commit:
   `scripts/stage_hunks.py apply app/src/a.cpp:0,2 app/src/b.cpp:1` then
   `git add <new files>` for a file the split introduces, then
   `git commit -F <message file>` (a message file beats `-m` with a multi-line
   body and avoids shell quoting of backticks).
4. Verify that commit on its own tree:
   `git stash push -u -m rest && cmake --build build && <unit test> && <suite> && git stash pop`.
   - Build in the SAME build directory as the working tree: the untouched
     objects stay cached, so each intermediate build costs seconds rather than
     a full rebuild.
   - `-u` is needed whenever the change set holds a file that is not committed
     yet; without it the tree still holds that file and, when a later commit
     adds it, the earlier tree does not represent the commit.
   - `git stash pop` leaves the index at HEAD and everything else unstaged,
     which is exactly the state the next staging step expects.
5. Repeat 1-4 (re-run `list` each time — the indices shift), then check the last
   tree and the leftovers:
   `git status --short` and `git diff` must both be EMPTY.
   That emptiness is the proof the split lost no hunk; a non-empty diff means a
   hunk was never staged, and the commits do not add up to the change set that
   was verified.

## Pitfalls

- **A patch file written by hand in text mode never applies.** Python's
  `Path.write_text()` (and any `open(..., 'w')` without `newline=''`) turns each
  `\n` into `\r\n`; a diff taken from a CRLF checkout already ends its lines
  with `\r\n`, so every line gains a second CR and `git apply` reports the whole
  patch as "does not apply" — on every file at once, which looks like a problem
  with the diff rather than with the writer. Write bytes, or pass `newline=''`.
- **A multi-line anchor does not match in a CRLF working copy.** Where the
  checkout is CRLF (`core.autocrlf=true`) and the file is stored as LF, the
  search text and the file's bytes disagree on every line break, so an edit tool
  that anchors on a multi-line block reports no match while a single-line anchor
  still works. Do not keep re-trying variations of the block. Splice the file
  directly instead: write a small script that reads with
  `open(path, 'r', encoding='utf-8', newline='')`, splits and rejoins on the
  file's *actual* newline, replaces one unique anchor, writes with the same
  `newline=''`, and takes a backup first. Then `diff` the backup against the
  result to confirm that only the intended region changed, and check the newline
  counts before committing.
- **A splice script that assumes the wrong newline silently does nothing.** A
  script written with `\r\n` run against an LF file (or the reverse) finds no
  anchor and reports success. Detect the ending from the bytes, or split with
  `splitlines(keepends=True)` and rejoin, and assert that the number of replaced
  occurrences is the number intended.
- **Never pass file content through an inline shell string.** Backticks inside a
  double-quoted `python -c "..."` are command substitution: the shell runs the
  contents, substitutes the (empty) result, and the file is written with the
  quoted material silently missing — a document full of holes that still parses.
  Write the edit script to a file and run it, and keep backticked values out of
  commit messages the same way (a message file beats `-m` for this reason).
- **Hunk indices are per-current-diff.** They renumber after each commit and
  after any edit to the tree; re-list instead of reusing the list you built
  before the previous commit.
- **A partial revert of a fix can fail the build and leave a stale binary.**
  Reverting only the fix (for a red test run) while a parameter stays unused
  trips `/WX` or `-Werror`; the build stops, the binary is the previous one, and
  its passing run reads as "the test does not catch the bug". Read the build
  result before reading the test result.
- **A performance claim needs both binaries before the split.** If the change
  set carries a timing claim, build the pre-change tree first and copy that
  executable aside (stash, build, copy, pop) — after the split the old tree is
  only reachable through `git stash`/`git show`, and the interleaved A/B cannot
  be rerun.
- **Do not tidy line endings into the split.** A file whose endings differ from
  the rest of the checkout (after a `sed -i` or a text-mode rewrite) shows a
  whole-file diff under `git status` in some configurations; normalize it back
  before staging so the commit shows the concern and not the churn.
