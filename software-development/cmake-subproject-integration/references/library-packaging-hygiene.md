# Packaging hygiene: the non-CMake surface of a distributable library template

Use when the ask is "is this good enough to be a standard, cross-platform,
distributable library" rather than "does it pollute a parent build". The probes
in SKILL.md cover build integration; the items here decide whether the repository
is consumable and maintainable as a package. Check them before proposing CMake
changes and report them in the same finding list.

## Line endings and file modes
- Ship a `.gitattributes` (`*.sh text eol=lf`, `*.yml text eol=lf`, optionally
  `* text=auto`) instead of relying on the author's `core.autocrlf`. With
  `autocrlf=true` a Windows checkout commits CRLF blobs for the shell scripts;
  git-bash runs those without complaint and the Linux/macOS runners do not, so the
  defect is invisible until CI.
- Keep the exec bit in the index for any script a workflow invokes directly
  (`git update-index --chmod=+x`), otherwise the step dies with permission denied.
- These are two different failures: a CRLF script is silent locally, a missing
  exec bit is immediate.

## Formatter and static-analysis configuration drift
- A `.clang-format` written for an older clang-format is partially ignored with no
  warning at all: an outdated `Standard:` value falls back (`Cpp11` → `Latest`) and
  keys removed from the tool are dropped silently, so the file no longer describes
  what the formatter does.
- Detect it by diffing `clang-format -dump-config` against the file, and by running
  the formatter over every tracked source and diffing the result. A repository that
  is not clean under its own config is the normal symptom; `--dry-run` alone
  reports nothing.
- If CI has no format step the config rots by construction. Add
  `clang-format --dry-run -Werror` over the tracked sources.
- The clang-tidy config drifts the same way but fails louder, so treat the check
  set as part of the repository's contract: name the categories explicitly rather
  than starting from `'*'` (a wildcard inherits every check a tool upgrade adds),
  exclude the checks the project's own conventions would flag forever, and prove
  the set still fires on a deliberately bad control file. Pin the tool version in
  CI the way the formatter is pinned, and check the same config under the pinned
  and the local version — "clean under both" is the property that makes the pin
  meaningful.

## Version file compatibility axis
- `write_basic_package_version_file`'s COMPATIBILITY decides which requested
  versions the installed package accepts, and the comparison is against the
  *requested* version: with SameMinorVersion the requested major and minor must
  both equal the current one, so an installed 0.2.0 rejects a
  `find_package(<lib> 0.1)` request and accepts 0.2.
- Probe both directions by installing two versions into two prefixes (or editing
  the installed `PACKAGE_VERSION`) and asking with a versioned request — a bare
  `find_package(<lib>)` accepts anything and proves nothing.
- 0.x needs SameMinorVersion, 1.0+ needs SameMajorVersion. Put the switch in the
  README's versioning section, or the first 1.0 release silently keeps 0.x
  semantics.

## CI coverage gaps worth naming
- No sanitizer (ASan/UBSan), no dependency cache (every job re-clones the pinned
  dependencies), no coverage job, and an embed job that covers one build flavor
  only (a static library in the superproject job while the shared combination is
  never exercised).
- No check that two configurations coexist in one install prefix. A matrix of
  clean runners cannot show it — each job installs one config — so it needs a step
  inside a single job (build+install Debug, then Release into the same prefix, one
  consumer per config). Name it as a coverage gap even when the mechanism itself
  was verified by hand: the README promise is what rots.
- These are gaps in the template rather than defects of the library: list them
  after the findings that actually break a consumer.

## Template versus library
- A template is not only a library: some things that read as defects are deliberate
  affordances for the projects generated from it (a second `LANGUAGES` entry so a
  future `.c` source compiles, an option that exists for the *consumer* to flip, a
  top-level-only debug flag). Check the comment or README line that explains the
  choice before ranking it as a finding, and say plainly when the user calls one
  out as a non-issue instead of re-arguing it.
- Hardening a template therefore means *gating*, not removing: keep the top-level
  conveniences, guard them with `PROJECT_IS_TOP_LEVEL`, and state in the README
  what an embedding parent gets instead.

## What the template must ship
- LICENSE naming the real copyright holder — the placeholder author is easy to
  leave behind — and a README whose claims are testable statements rather than
  aspirations (see the README-as-assertions step in SKILL.md).
