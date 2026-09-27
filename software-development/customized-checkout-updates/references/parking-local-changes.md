# Parking local changes without touching the live worktree

Use when the checkout you must prepare is the source tree of the app you are
running from. Worktree-rewriting commands (`git checkout`, `switch`, `reset`,
`restore`, `stash push`) are refused by the runtime guard, and are genuinely
risky mid-session: modules already imported stay in memory while the files under
them change.

## 1. Save the patches (always do this first)

```bash
git diff > "$OUT/worktree.patch"           # unstaged
git diff --cached >> "$OUT/worktree.patch" # staged, if any
git stash show -p stash@{N} > "$OUT/stash-N.patch"
```

`$OUT` must be outside the checkout (user-state dir or a scratch dir on real
disk, not a RAM-backed `/tmp` that a dependency install can fill).

## 2. Build a named branch with plumbing only

None of these touch HEAD or the worktree:

```bash
export GIT_INDEX_FILE="$TMP/park-idx"
rm -f "$GIT_INDEX_FILE"
git read-tree HEAD                           # seed the index from HEAD
git apply --cached "$OUT/worktree.patch"     # patch the INDEX, not the tree
TREE=$(git write-tree)
COMMIT=$(git commit-tree "$TREE" -p HEAD -m "local: <what and why>")
git branch local/fixes-<topic> "$COMMIT"
unset GIT_INDEX_FILE; rm -f "$TMP/park-idx"
```

Verify: `git diff --stat main local/fixes-<topic>` must list exactly the files
you changed, and `git status` must still show the worktree untouched.

**Do not substitute `git add -A` for `git apply --cached`.** Staging the worktree
into an alternate index forces a full re-hash of every file; with line-ending
conversion (`core.autocrlf`) differing between config scopes (system true,
repo-local false, or vice versa) the re-hash produces blobs that differ from the
committed ones, and the "parked" commit then changes hundreds of unrelated files
(LICENSES, generated headers, docs) relative to HEAD. Applying the patch text
into the index preserves the original bytes.

If the plumbing-produced branch is wrong, `git branch -D` it and redo — deleting
a branch never touches the worktree, so it is safe where `checkout` is not.

## 3. Hand the worktree cleanup to an external shell

An external shell with the app stopped can rewrite files. Give the user the
exact block, with explicit paths rather than `git checkout -- .`:

```bash
cd <checkout>
git restore --source=HEAD --staged --worktree <path1> <path2> ...
git status                # must be empty
git fetch origin <branch> # then assert fast-forwardability
<update command>
```

Explicit paths keep the blast radius visible, and `git status` in between proves
the precondition before the update runs.

## 4. Triage an unclear checkout

When a checkout carries leftovers and you cannot tell what is applied:

```bash
git status -sb; git diff --stat; git diff --cached --stat
git stash list; git log -g --date=iso --pretty='%gd %cd %s' refs/stash
git branch -vv; git worktree list; git reflog -10 --date=iso
```

`git stash show --stat stash@{N}` printing nothing means an empty stash — dead
weight to drop. `git worktree list` plus branches named for past merge campaigns
reveal shared/scratch clones that also need cleanup.
