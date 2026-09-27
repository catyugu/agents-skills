---
name: customized-checkout-updates
description: "Use when updating a git checkout with local patches."
version: 1.0.0
author: Hermes Agent
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [git, update, self-update, local-patches, stash, shallow-checkout]
    related_skills: [hermes-agent, agent-merge-conflict-arbiter]
---

# Updating a Customized Git Checkout

For any in-place git install that self-updates by pulling (a patched vendor
checkout, a fork, or Hermes's own source checkout after the user has hand-edited
it). Three things must hold at the end: the local work still exists somewhere
recoverable, the update landed as a pure fast-forward, and nothing was left
half-applied.

## Hard Rules

1. **Never update with a dirty tree and trust the updater's auto-stash.** The
   re-apply is a 3-way merge against code that moved; when upstream refactored
   (facades split into siblings, test files relocated), it conflicts or silently
   drops hunks. Park the change set first, then update a clean tree.
2. **Preserve local work in two independent places** — patch files on disk plus
   a named git branch. A stash alone is not a preservation strategy: stashes are
   unnamed, unlisted in normal workflow, and are exactly how work goes missing.
3. **Assert fast-forwardability immediately before updating** (`git merge-base
   --is-ancestor HEAD origin/<branch>`). If it fails, stop and repair the graph
   — do not let the updater reach its diverged-history fallback.
4. **Back up user state before touching git.** Config/sessions/skills live
   outside the checkout and are the part that cannot be re-cloned.
5. **Re-apply each local change deliberately after the update, by hand, at the
   current upstream shape** — and re-verify its premise on the running system,
   not just with the test the patch itself added.

## Procedure

### 1. Triage the checkout

- Identify install kind, version, and running services (`hermes --version`,
  `hermes status`, `hermes update --plan` where available — the plan is
  read-only and lists every live service the update will restart).
- Read the real state: `git status -sb`, `git diff --stat`, `git stash list`
  (with dates), `git branch -vv`, `git log --oneline -5`, remotes.
- Note every leftover from previous updates: stashes, parked/merge branches,
  scratch clones. They are clues that a previous update lost or duplicated work.
- Done when you can name every local modification, where it came from, and
  everything that will be restarted.

### 2. Judge each local change

- For each modified file ask: is this a real fix, test-only, or noise (a stray
  trailing-newline edit is noise — revert it, do not preserve it)?
- Check whether upstream already fixed it: `git show origin/<branch>:<path>` and
  `git grep <symbol> origin/<branch>` on the fresh fetch. Fixes that upstream
  absorbed should not be re-applied.
- **Run the local tests before the update.** New tests with no implementation
  landing with them (red tests in a tree that is otherwise clean) is the
  signature of a botched stash restore — the implementation hunks are still
  parked somewhere and must be recovered and parked alongside their tests.
- Done when every change has a verdict: keep / drop / already upstream.

### 3. Park the change set

- Write patches to a durable directory OUTSIDE the checkout (e.g.
  `$LOCALAPPDATA/hermes/local-patches/`, `~/local-patches/`): one patch per
  source of local work (worktree diff, and each stash you are preserving).
- Record it as a named branch too, so it is discoverable by name later.
- The running app's live checkout is guarded against worktree-rewriting git
  commands, so build the commit with plumbing that does not touch the worktree —
  recipe and pitfalls: `references/parking-local-changes.md`.
- Done when a patch file and a branch both contain the change set, and the
  patch still `git apply --check`s cleanly.

### 4. Verify the update can fast-forward

- `git fetch origin <branch>`, then `git merge-base --is-ancestor HEAD
  origin/<branch> && echo FF-OK` and `git rev-list --left-right --count
  origin/<branch>...HEAD` (expect `<N>\t0`).
- Impossible counts (e.g. `git status` showing no divergence while a count says
  thousands behind, or a huge "ahead") mean a shallow boundary is truncating the
  graph. Detect and repair: `references/shallow-checkout-repair.md`.
- Self-update tooling commonly does a `--depth 1` fetch for its read-only
  "update available?" check on a shallow install, which re-marks the new tip as
  a shallow root and destroys the merge base. Re-run this step after ANY check
  command, not just at the start.
- Done when FF-OK is printed against the freshly fetched ref.

### 5. Clean the tree, back up, update

- Get the worktree back to pristine HEAD. If the live checkout blocks it (it
  will, while the app is running), hand the user an exact copy-paste block for an
  external shell: explicit-path `git restore --source=HEAD --staged --worktree
  <paths>`, `git status` must be empty, the FF assertion, then the update command.
- Take the user-state backup first (full archive, not just the quick snapshot).
- Then update. With a clean tree on the tracked branch and FF-OK, the update is
  a pure fast-forward: no merge, no conflict, no stash restore.
- Done when the update reports success and the version/HEAD moved.

### 6. Re-apply and verify

- Re-apply surviving local fixes at the CURRENT upstream shape. Files move
  between releases (god files split into `<stem>_<topic>` siblings, tests
  relocated) — a stale patch will not apply; port it by hand to the new
  location and confirm the anchor still exists.
- **Port the INTENT, not the diff.** Before moving a hunk forward, search
  upstream for an equivalent mechanism that now covers it. A local relaxation of
  a check is usually obsolete once upstream generalizes that check, and moving it
  forward re-introduces a divergence maintainers resolved deliberately.
- Run the project's own test entry point, not bare `pytest`/`npm test` (repo
  conventions usually add env isolation that the bare runner lacks).
- Verify each fix's premise on the real system (a real call against the affected
  provider/endpoint, a real run) — a test the patch introduced only proves the
  patch is self-consistent. Drive the production path over real persisted state
  and print the resolved result: `references/post-update-reapplication.md`.
- Attribute every failure before reporting it: a red test touching none of the
  symbols you changed (or whose premise the platform cannot satisfy) is
  inherited — show that evidence instead of a bare "N failed" against a patch you
  just applied.
- Re-park the re-applied set against the NEW base (fresh patch + named branch),
  and name the surfaces that must restart before the edit is actually live.
- Then a health check of the app itself, and clean up the leftovers from step 1.

## Pitfalls

- **Stash-as-backup.** Unnamed stashes accumulate across updates and get carried
  forward silently; when one is eventually popped it lands on unrelated code.
  Name the branch, write the patch, then drop the stash.
- **Half-applied change sets** (tests landed, implementation hunks stuck in a
  stash) leave a tree that looks locally-modified but has red tests. Recover the
  missing hunks before updating, or the update bakes the breakage in.
- **Trusting `git status` for content.** With line-ending conversion configured
  differently across config scopes, the stat cache can report files clean while
  their bytes differ from the committed blobs. Any operation that forces a
  re-hash (alternate index `git add -A`, a fresh clone) then shows hundreds of
  unrelated files changed. Apply patches into an index instead of re-adding the
  worktree.
- **Shallow graphs break fast-forward.** See
  `references/shallow-checkout-repair.md`; the failure mode is the updater
  declaring "no common ancestor / orphan divergence" and hard-resetting.
- **Cleaning the live checkout in-process.** The app's own source tree is
  guarded while it runs; never fight the guard — do the rewrite externally, or
  use worktree-free plumbing.
- **A source edit is not a live fix.** A patch you re-applied and verified green
  is still inert in every process that already imported the module (long-lived
  backends keep the old copy until restarted; only newly launched processes pick
  it up). Say which surfaces need a restart instead of reporting the behaviour as
  fixed and leaving the user to wonder why it still misbehaves.

## Verification

- `git status` clean on the tracked branch; `git rev-list --left-right --count
  origin/<branch>...HEAD` ends in `0` (no local commits).
- Patch files exist outside the checkout and `git apply --check` cleanly against
  the pre-update base; a named branch holds the same content.
- A user-state backup archive exists and its restore command is known.
- Post-update: app health check passes, re-ported fixes are exercised on the
  real path, and stale stashes/branches/scratch clones are removed.
- The re-applied change set exists as a patch + branch for the NEW base, and
  every surface that must restart to load it has been named.
