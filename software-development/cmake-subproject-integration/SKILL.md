---
name: cmake-subproject-integration
description: Use when embedding a CMake library in a superproject.
---

Use when a CMake project is (or will be) consumed by another project via
`add_subdirectory` / `FetchContent` / CPM, when a superproject's build breaks
after such an embed, or when an embedded shared library's DLL/so is not found at
runtime. Also use when hardening a library or template so it embeds cleanly, or
when judging whether a template is fit to be a standard distributable library
(the non-CMake packaging surface is in references/library-packaging-hygiene.md).
Reply in the user's language (Chinese by default), compact, `path:line`
evidence, plan first, code only after the user picks items.

## The design contract (state it before proposing changes)
- A library describes **targets**, not the build: never set
  `CMAKE_*_OUTPUT_DIRECTORY`, `CMAKE_BUILD_TYPE`, `CMAKE_MSVC_RUNTIME_LIBRARY`,
  `CMAKE_EXPORT_COMPILE_COMMANDS` or `enable_testing()` unconditionally — guard
  them with `PROJECT_IS_TOP_LEVEL` (or `CMAKE_SOURCE_DIR STREQUAL PROJECT_SOURCE_DIR`).
  The C++ standard is the exception: `set(CMAKE_CXX_STANDARD ...)` is
  directory-scoped, so inside the subproject it never reaches the parent's own
  targets (measured: the parent's TU stays `-std=gnu++17` while the subproject's
  are `-std=c++20`). It is a local convenience, not the mechanism — publish the
  requirement as `target_compile_features(tgt PUBLIC cxx_std_20)`, which lifts a
  parent TU that links the target to `-std=gnu++20` while leaving the parent's
extension policy intact. Know the one place the variable does reach: third-party
  sources the subproject fetches are compiled inside that directory scope, so its
  standard and extension settings decide how they build.
- `BUILD_SHARED_LIBS` decides static vs shared; never force it, and never let the
  library decide where the consumer's executable goes.
- The library does not deploy DLLs. It exposes DLL location through target
  dependencies (`$<TARGET_FILE_DIR:tgt>`, `$<TARGET_RUNTIME_DLLS:tgt>`) and
  documents the consumer-side recipe (see references/dll-deployment.md). Its
  own staging helper serves its own tests and examples only — say so in the
  README instead of implying the build system deploys the DLL for consumers.
- Build-only needs (warnings, dependencies, test frameworks) go into
  `$<BUILD_INTERFACE:...>` INTERFACE targets linked PRIVATE, so `install(EXPORT)`
  never has to export them. That hides them for real only while they are
  header-only (or consumed as headers): a *compiled* PRIVATE dependency leaves
  undefined symbols at the consumer's link time with nothing in the export to
  explain it, so either promote it to PUBLIC plus a `find_dependency` in the
  package config file, or ship the library as a shared library that carries it.
  See references/install-export-surface.md for the installed-artifact checklist.
- A compiled PRIVATE dependency must be resolved one of exactly three ways; pick by
  what the consumer should have to install:
  1. `find_package(<dep> REQUIRED)` + `find_dependency(<dep>)` in the package config
     template (conditional on the static build) — the consumer must have the
     dependency installed, and every new dependency adds a line to the config
     template, which is the cohesion cost of this route.
  2. Compile the dependency's implementation *into your own target* — fetch its
     sources with CPM `DOWNLOAD_ONLY YES`, then attach them to an interface
     aggregate target (`target_sources(<agg> INTERFACE <dep sources>)`). The
     installed package is then self-contained, but the dependency's build
     configuration (source list, which sources implement what, per-compiler flags)
     becomes your maintenance burden — reject this when the requirement is "the
     dependency's build is not ours to manage".
  3. Install the dependency target as part of your own export set. This works even
     when the dependency came from CPM/FetchContent (a local, non-imported target):
     `install(TARGETS <mylib> <dep> EXPORT myTargets ...)` — the export then contains
     `<ns>::<dep>` and its `$<LINK_ONLY:...>` reference resolves. Register the
     dependency's *real* target name (aliases cannot be installed) in one variable in
     the dependency file and reference that variable from the main file, so the main
     file still names no dependency. Install the dependency's library/DLL, never its
     headers when your public headers do not expose it — the only residue is a
     harmless `INTERFACE_INCLUDE_DIRECTORIES` on the exported dependency target
     pointing at your prefix's include dir.
     Installing the dependency's headers is pure cost: no consumer includes them.
- Hiding a compiled PRIVATE dependency behind `$<BUILD_INTERFACE:...>` compiles and
  installs cleanly and then fails at the consumer's *link*: `undefined symbol:
  fmt::v12::vformat` (probed). Whether the dependency's headers appear in your
  public headers is irrelevant — a static archive does not carry its dependency's
  object code, so the link requirement, not header visibility, is what decides.
- A compiled dependency that must appear in the published interface can **not** be
  obtained by CPM/FetchContent: the fetched target is local to the build tree, and
  `install(EXPORT)` then fails outright with `requires target "X" that is not in
  any export set` — for both PRIVATE (static libraries record it as
  `$<LINK_ONLY:X>`) and PUBLIC links, and wrapping the name in
  `$<BUILD_INTERFACE:>`/`$<INSTALL_INTERFACE:>` does not dodge the validation
  (probed). Only two things work: the dependency is an IMPORTED target
  (`find_package`, i.e. the consumer/producer must have it installed) or it is
  installed and exported as part of your own package (protobuf-style bundling,
  which also drags its artifacts into an embedding parent's install tree). So
  split dependencies by whether they reach the install surface: find_package for
  those, CPM/FetchContent for test-only or build-only ones. Make the package
  config's `find_dependency(<dep>)` conditional on the build being static
  (configure-time `if(BUILD_SHARED_LIBS)` + `if(@VAR@)` in the config template),
  otherwise a shared-install consumer is asked for a dependency it never links.
- Guard the *content* of the install rules on top level, never their existence.
  A superproject that runs `install(TARGETS itslib EXPORT itsTargets)` where
  itslib links your target fails to configure with `requires target "<lib>" that
  is not in any export set` as soon as your install rules are off — and an
  install option defaulting to `PROJECT_IS_TOP_LEVEL` is off in exactly that
  embedding case. Default it ON (`option(<NAME>_INSTALL ... ON)`, as fmt, spdlog
  and Catch2 do), let the parent opt out, and state the consequence in the README
  (the parent's `cmake --install` then also installs your headers, library and
  package files). See references/install-export-surface.md.
- Warnings are build-only needs, including their severity: gate `-Werror`/`/WX`
  on `PROJECT_IS_TOP_LEVEL` (or an option defaulting to it). Directory-scoped
  flags inherit downward, so the parent's global `CMAKE_CXX_FLAGS` compiles the
  library's sources too — the parent's `-Wall` plus your unconditional `-Werror`
  fails the parent's build inside your own headers.
- Per-config *optimization* flags belong in the same bucket: an options file that
  appends `-O2`/`-DNDEBUG` per `$<CONFIG:Release>` lands after the parent's
  `CMAKE_CXX_FLAGS_<CONFIG>` on the library's own TUs and wins (probed: the parent's
  `-O3 -DNDEBUG` followed by the library's `-O2`), while a fetched dependency's TU
  keeps the parent's flags — so the library alone ignores the parent's optimization
  policy. Gate them on top level rather than deleting them: this user's standing
  choice is to keep the conveniences (`-g3 -ggdb`, `-fno-omit-frame-pointer`, a
  top-level-only `-rdynamic`) for a top-level build and drop them only for an
  embedded one. Trim flags by evidence, not by taste — present a per-flag table
  (what CMake's `CMAKE_CXX_FLAGS_<CONFIG>` already sets, what the flag actually
  adds, what it costs) and let the user choose which defensive flags stay; a
  wholesale deletion is the one change here that silently removes debugging
  ability the user wanted.
- Version: `project(<name> VERSION x.y.z)` writes the tree-global
  `CMAKE_PROJECT_VERSION{,_MAJOR,_MINOR,_PATCH,_TWEAK}` cache entries, and the
  first versioned `project()` call wins — so a superproject that declares no
  version of its own reports the subproject's version in its own scope. Keep the
  version in project-scoped variables instead (`set(${PROJECT_NAME}_VERSION
  x.y.z)` plus derived `_MAJOR/_MINOR/_PATCH`) and feed them to
  `write_basic_package_version_file` and any configured header. A guarded
  `set(CMAKE_PROJECT_VERSION "")` before a dependency does *not* fix this: it
  suppresses only the main variable and still leaves the four component cache
  entries, so report the residual third-party leak instead of hacking it.
- Declare only the languages you actually compile: `LANGUAGES CXX` unless a `.c`
  source exists. `project()` enables a language tree-wide, so a spurious `C`
  forces every consumer to find a C compiler it never needed. A *fetched*
  dependency can do this for you: googletest's `project()` declares no
  `LANGUAGES`, so enabling your tests inside a CXX-only parent tree demands a C
  compiler that tree never needed — another reason not to fetch the test
  framework when not top-level.

## Audit procedure — build a probe superproject, never audit by reading alone
1. Generate a throwaway superproject in a temp dir: its own C++ standard and
   build type, its own `enable_testing()`, no dependency manager, its own install
   prefix. `scripts/probe_subproject_pollution.sh <subproject-src>` configures it
   twice (with and without the subject as a subproject), prints the diffs, and
   adds the global-flags and language-claim probes from step 2. Export the
   compiler (`CXX=clang++`) rather than passing it through `PROBE_CMAKE_ARGS`:
   that variable reaches every configure, but a compiler set differently between
   the two trees turns the cache diff into toolchain noise (`CMAKE_CXX_FLAGS`,
   every linker flag, `AR`/`RANLIB`) and buries the real findings.
2. Read these surfaces, strongest signal first. When the configure dies on a
   finding (a language the parent lacks, a versioned `project()`), the later legs
   — the flags build, the `-Werror=dev` pair, the consumer run — fail at that same
   line and tell you nothing about themselves; fix the first finding and re-run
   before reading them.
   - `diff` of the two `CMakeCache.txt` (normalize the build-dir path first): new
     `CMAKE_*` entries, `CPM_*`/`FETCHCONTENT_*` state, unprefixed options,
     `*_DIR` values — that is what got written into the parent's cache.
   - `CMAKE_FIND_PACKAGE_REDIRECTS_DIR`: CPM/FetchContent writes
     `<name>-config.cmake` there, so the parent's later `find_package(X)` silently
     gets the subproject's pinned X. Probe with a `find_package(X)` *after* the
     `add_subdirectory`.
   - `compile_commands.json`: compare `-std=`/`/std:` and `/MD` vs `/MT` per
     file. Target-level requirements (PUBLIC compile features, definitions) show
     up only on files that link the target.
   - Flag inheritance, the opposite direction: the parent's global flags land on
     the subproject's own TUs. Re-configure the parent with `-Wall` **added**
     (`add_compile_options`, never `CMAKE_CXX_FLAGS`: clobbering that variable also
     drops the toolchain defaults such as `/EHsc`, and the build then fails for an
     unrelated reason) and build — an unconditional `-Werror` in the subproject
     fails the *parent's* build, with the errors pointing at the subproject's
     headers. Read which files the errors name: a failure inside a fetched
     dependency (googletest compiles itself with `-WX`) is that dependency's
     interaction with the parent's flags, not your headers'.
     Pass the parent's flags as a CMake **list** (`-DSUBPROBE_PARENT_FLAGS=-Wall;-Wextra;-Werror`):
     a space-separated string reaches `add_compile_options` as a single argument,
     clang answers only `unknown warning option '-Wall -Wextra -Werror'`, and the
     probe reports a false clean. Re-check by hand on each frontend variant of the
     same compiler — `-Wall` means something different to the GNU and the MSVC
     frontend: clang-cl maps `-Wall` to `/Wall`, so a tree clean under
     `clang++ -std=c++20 -Wall -Wextra -Werror` can fail under
     `clang-cl /std:c++20 -Wall -Werror` (probed on a header that does nothing but
     use a nested namespace and a default member initializer; `/W4` does not turn
     those compat warnings on). That is the frontend's semantics, not the
     library's code — report it as such instead of "fixing" the library. Get the exact
     command for one object with `ninja -t commands <path/to/obj.obj>` rather than
     reconstructing it from `compile_commands.json`.
   - `-Werror=dev`: configure the parent once more with it, base tree and
     with-subproject tree, and diff the exit codes. CMake >= 4.4 deprecates that
     spelling in favour of `-Werror=author`, which does not exist before 4.4 (an
     unknown warning category is a hard `CMake Error`), so a script that must run
     on both sides of that boundary needs a version test — never blind-replace the
     flag. Author warnings raised by
     your install rules or your vendored modules become hard failures there (a
     dependency target's `PUBLIC_HEADER`, a vendored CPM without
     `EXTRACTED_CPM_VERSION`), so it is the cheapest way to find the findings a
     parent maintainer will actually hit.
   - Language claim: configure a CXX-only parent with a bogus
     `-DCMAKE_C_COMPILER=<nonexistent>`; it passes alone and fails with the
     subproject added iff the subproject declares a language it never uses.
   - Version leak: configure a parent that declares no `VERSION` of its own and
     grep its `CMakeCache.txt` for `CMAKE_PROJECT_VERSION*`; a subproject with a
     versioned `project()` fills all five entries and the parent then reports the
     subproject's version in its own scope.
   - Duplicate embed: `add_subdirectory` the same source twice under two names
     and check that the parent still configures, builds and links — the guard for
     this is cheaper than the failure it prevents.
   - `ctest -N` at the parent root, plus artifacts in the parent build root
     (`_deps/`, `cpm-package-lock.cmake`, `lib/`, `bin/`) and a grep of the
     top-level `cmake_install.cmake` for the subproject's target name.
   - The project's own README, read as a list of assertions to verify rather than
     as prose: "the build system deploys the DLL", "the export publishes the
     dependency", "consumers need nothing installed". Each claim a probe
     contradicts is a finding, and the fix is the documented recipe (or the
     corrected sentence), never silence.
   - Runtime: run the parent executable that links the subproject and check the
     exit code. A build that links is not a build that runs. Repeat it against a
     consumer built on the *installed shared* library, once with the library's
     `bin/` off the loader path (expect the 127/`0xc0000135` failure) and once
     with it on (expect 0); set `PATH` to a minimal valid value rather than
     unsetting it, since an unset `PATH` can make the run succeed misleadingly.
     From git-bash the DLL directory must be on `PATH` in MSYS form (`/c/...`):
     a native `C:/...` entry in that colon-separated list is not honoured, so the
     run keeps failing with 127 even though the directory is correct. A PATH cut
     down to `/c/Windows/system32` is too minimal as well: a clang-cl/VS-built
     binary needs the UCRT (`api-ms-win-crt-*.dll`) from another system directory,
     so exit 127 names that DLL instead of yours and the probe reads as a different
     failure — keep the full PATH and build the negative control by removing only
     the library's directory.
3. Classify each finding as (a) breaks the parent, (b) global/cache pollution,
   (c) published requirement the parent inherits, (d) clean, (e) endemic to the
   ecosystem. Rank (a) first and name the mechanism per item — "pollutes the
   cache" is not a finding. Bucket (e) is one short group at the end, marked as
   common to most libraries using CPM/FetchContent and not worth fixing: the
   `CMAKE_INSTALL_*` set and `*_NOTFOUND` entries a dependency leaves in the
   parent's cache, `_deps/`, absent `.pc` files. This user's standing instruction
   is to ignore those instead of padding the report — a finding earns a line only
   if it is specific to this project.
4. Deliver the ordered change list, each item independently verifiable, plus the
   zero-pollution alternative when one exists (usually install + `find_package`,
   or a documented consumer-side recipe). Unless the user asked for the
   implementation, prove each fix on a *throwaway copy* of the worktree — tar the
   worktree (minus `.git` and build dirs) into a temp dir, patch there, re-run the
   probes — and state in the report that the working tree is untouched: this user
   edits the same tree in parallel and resets commits. When implementing, deliver
   the change set in one pass and let the user set commit granularity: a set that
   was verified together is ONE commit with the per-item rationale in its body — do
   not propose a per-concern split or write a commit-splitting script unasked.
   Delete the compat path rather than keeping the old API.

## CI on hosted runners
- Run the checks from a script in the repository (`ci/run_checks.sh <library|consumer|superproject>`) and have the workflow only set the environment, so the same steps are reproducible by hand; the CI log then shows the generator, and a failure is debuggable locally.
- The script must run under the bash the runner ships: macOS `/bin/bash` is 3.2, where expanding an *empty* array under `set -u` is an `unbound variable` error (`"${args[@]}": unbound variable`) — pass an optional flag through an `if`/`else` instead of an argument array, and never use `mapfile`/`readarray` or `${@:3}`. Under `set -o pipefail` also avoid `find … | head -n 1` (a SIGPIPE'd `find` fails the pipeline): use `find … -print -quit`.
- `-Werror=dev` became `-Werror=author` in CMake 4.4 (the old name is deprecated, not removed). Before 4.4 an *unknown* warning category is silently ignored, so a hard-coded choice either loses the check or fails the configure — pick it from `cmake --version` in the script.
- A format check needs a pinned clang-format: distro/LLVM builds of the same major still differ, so install the official PyPI wheel (`pip install clang-format==<version>`) on every runner, and treat a local mismatch as expected. The same holds for clang-tidy, and there the version pin matters more: a newer tool adds checks, so validate the config under both the pinned wheel and whatever the dev machine has (probed: the same explicit check set reports zero diagnostics under both 22.1.x and 23.1.x, which is what makes the pin safe).
- A CI matrix cannot cover "two configurations installed into one prefix": each job gets a clean runner and one config, so the promise that Debug and Release coexist in a prefix (and that each config's consumer links its own postfixed artifact) is never exercised. Add it as a step inside one job — build+install Debug, then build+install Release into the same prefix, then configure/build/run one consumer per config — instead of adding matrix entries.
- Do not name a Visual Studio version in `-G` on a runner: the image ships whatever VS it ships (probed: `Visual Studio 17 2022` failed with `could not find any instance of Visual Studio` while the image had VS 18). Resolve the platform default instead — `cmake --help | awk '/^\*/ {sub(/^\* */,""); sub(/ *=.*/,""); print; exit}'` — and treat it as multi-config.
- In a workflow matrix, quote `"OFF"`/`"ON"`: YAML reads unquoted OFF/ON as booleans, so the job env gets `false`/`true` and the build silently takes the wrong path. Lint the workflow with actionlint (single static binary, download from the release page) — it catches this class and unknown keys before a run is spent.
- A script that calls native tools (cmake, ctest) must convert its own directory: under MSYS/Git Bash `pwd` yields `/d/a/...`, which native cmake rejects (`The source directory ... does not exist`). `cygpath -m` when `cygpath` exists; that also covers `$GITHUB_WORKSPACE` on Windows runners. Convert every path variable the script reads from the environment, not just its own directory: a caller passing `BUILD_ROOT=/c/...` makes the configure die with `The source directory ... does not exist` although the same path works fine in the shell — loop over the path variables and `cygpath -m` each one that starts with `/`.
- Prefer the default generator with a documented fallback (`Ninja` → `Unix Makefiles` when ninja is absent) so the script runs on a bare distro as well as on a runner.
- Set the exec bit on the script (`git update-index --chmod=+x`) or invoke it as `bash <script>`: a workflow step calling `ci/x.sh` directly fails with permission denied when the bit was lost on a Windows checkout.
- Ship a `.gitattributes` (`*.sh text eol=lf`, `*.yml text eol=lf`) instead of trusting the checkout's `core.autocrlf`: a Windows author with `autocrlf=true` commits CRLF blobs for the shell scripts, git-bash runs those happily, and the Linux/macOS runners do not — so the defect never shows up locally. Fix it in the repository; re-saving the files does not survive the next checkout.

## Pitfalls
- `$<PLATFORM_ID:...>` compares against the *exact* string (`Linux`, `Darwin`, `Windows`), so `$<PLATFORM_ID:linux>` is always false and the flag it guards silently never reaches a link line. Probe it with `file(GENERATE OUTPUT x CONTENT "$<PLATFORM_ID:linux>")` instead of trusting the spelling. The same applies to any platform-scoped flag whose absence is invisible: a `-rdynamic`-style link option can sit in an options file for years without ever being applied.
- A `.clang-format` written against an older clang-format silently loses every key that was removed or renamed since (older spellings of `SpacesInParentheses`, `BinPackArguments`, `Standard: Cpp11`, `AlwaysBreakTemplateDeclarations`…): the file still parses, so the tree formats with unintended defaults. Compare `clang-format -dump-config` against the file to find dead keys, then check the whole tree with `--dry-run -Werror` — the enforced style is whatever the effective config says, not what the file lists.
- A `.clang-tidy` gets the same treatment, with one extra trap: a check set built as
  `'*'` minus a list (what most templates ship) inherits every check a future
  clang-tidy adds, so an upgrade turns CI red with checks nobody chose. Name the
  categories explicitly (`-*, bugprone-*, clang-analyzer-*, misc-*, modernize-*,
  performance-*, portability-*, readability-*` plus per-check exclusions) and
  exclude what the project's own conventions would flag forever (trailing-return
  type, `#pragma once`, magic numbers, identifier length, `misc-include-cleaner`
  when a bundled dependency's headers are not installed). Prove the set is not
  vacuous before trusting a clean run: run it over a control file of deliberately
  bad code and require diagnostics — and make that control file *compile*, because
  a TU with an error reports only `clang-diagnostic-error` and hides whether any
  check was enabled at all. `scripts/eval_clang_tidy_checks.py` counts diagnostics
  per check for several candidate configs at once, which is how the exclusions are
  chosen instead of guessed.
- An export macro that is empty off Windows (`#else #define API` — the shape most
  templates ship) plus no `CXX_VISIBILITY_PRESET` means the shared library exports
  everything on ELF/Mach-O, private implementation symbols included; `nm -D
  --defined-only` shows them and the consumer's namespace is polluted. Fix with the
  target properties `CXX_VISIBILITY_PRESET hidden` + `VISIBILITY_INLINES_HIDDEN ON`
  (target-scoped, so no cache pollution, and never a global `CMAKE_*` setting,
  which is the parent's to decide) plus a visibility clause in the macro
  (`#elif defined(SHARED) && (defined(__GNUC__) || defined(__clang__))`). Hiding is
  not free: every public type/function needs the macro, and an *exception class* in
  particular — a hidden one has no exported typeinfo, so a consumer's
  `catch`/`EXPECT_THROW` in another DSO fails to match at runtime while the build and
  the link stay green. Prove it with a shared build whose tests link the library as a
  separate DSO, not with the symbol count alone.
- Installing the same library for two configs into one prefix needs
  `DEBUG_POSTFIX` (fmt/spdlog ship `d`): without it the second install silently
  overwrites the first DLL/import lib, and the only symptom is a Debug consumer
  running Release code (on MSVC-family toolchains it surfaces as the
  `_ITERATOR_DEBUG_LEVEL` /failifmismatch link error instead). Verify per config:
  `IMPORTED_LOCATION_DEBUG` in the installed `*Targets-debug.cmake` must name the
  postfixed artifact, and a Debug-config consumer must link it.
- `write_basic_package_version_file(... COMPATIBILITY SameMajorVersion)` is wrong for
  a 0.x library: it accepts 0.2.0 for a `find_package(<lib> 0.1)` request while the
  breaking axis in 0.x is the minor version. Use `SameMinorVersion` until 1.0, and
  probe it by installing two versions into two prefixes (or editing the installed
  version file's `PACKAGE_VERSION`) and checking both directions of a *versioned*
  request (`find_package(<lib> 0.1)` — a bare request accepts anything): the
  comparison is against the requested version, so an installed 0.2.0 rejects a 0.1
  request and accepts 0.2 (probed). Switch to `SameMajorVersion` at 1.0 and say so
  in the README's versioning section, or the first 1.0 release silently keeps 0.x
  semantics.
- A vendored dependency file cannot be blamed for what it does not contain: read the
  file before attributing a cache entry to it (`grep -n GNUInstallDirs
  cmake/<vendored>.cmake`) — the entries usually come from the fetched dependency's
  own `include(GNUInstallDirs)`, and then no guard of yours removes them.
- On a Windows host the ELF probe runs under WSL, whose `/tmp` is wiped when the
  instance shuts down between separate `wsl.exe` invocations (the distro shows as
  `Stopped` in `wsl.exe -l -v`): build, inspect and run in ONE script invocation, and
  write the script's output to a path under `/mnt/<drive>/...` if you need it later.
  Give the WSL side the host's CPM source cache (`-DCPM_SOURCE_CACHE=/mnt/<drive>/...`)
  or it re-clones every dependency over a network the VM may not have.
- A fetched dependency's *own* build inherits the parent's directory-scoped flags,
  so a parent that adds `-Werror` globally can fail inside the dependency. Before
  blaming your own sources, read the failing target's name: a failure in
  `_deps/<dep>-build/...` is the dependency's code under the parent's flags. When
  the dependency builds extra targets you do not use, turn them off with its own
  documented options (fmt 12's `FMT_MODULE` builds a C++20 module target by default
  and that target is what fails under a parent's `-Werror`; `OPTIONS "FMT_MODULE OFF"`
  in `CPMAddPackage` removes both the failure and the build time).
- An install option that defaults ON (`option(<NAME>_INSTALL ... ON)`) makes the
  subproject's `include(GNUInstallDirs)` run inside the parent's tree: the parent's
  cache gains the whole `CMAKE_INSTALL_*` set, and the parent's `cmake --install`
  then also installs the subproject's headers, library and package files. Benign,
  but state it in the README next to the option instead of letting the parent
  discover it in its install prefix.
- Route 3 (installing the dependency target in your own export set) collides with
  a dependency that sets `PUBLIC_HEADER` on its target — fmt does it
  unconditionally, independent of its own install option: `install(TARGETS mylib
  <dep> ...)` installs no headers, so CMake emits `Target <dep> has PUBLIC_HEADER
  files but no PUBLIC_HEADER DESTINATION`, an author warning that a parent's
  `-Werror=dev` promotes to a configure failure. Clear the property right after
  naming the dependency (`set_target_properties(<dep> PROPERTIES PUBLIC_HEADER "")`);
  the installed export is unchanged. See references/install-export-surface.md.
- `$<BUILD_LOCAL_INTERFACE:...>` (CMake >= 3.25) hides a helper target's *name* from
  exports; it does not remove the empty `$<LINK_ONLY:>` entry that a
  `$<BUILD_INTERFACE:...>`-wrapped interface target leaves in the installed
  `INTERFACE_LINK_LIBRARIES`. That empty entry is harmless — do not chase it.
- On MSVC-family toolchains a single-config installed dependency (e.g. a
  hand-built Release-only fmt) breaks a Debug consumer at link time with
  `lld-link: error: /failifmismatch: mismatch detected for '_ITERATOR_DEBUG_LEVEL'`.
  Package managers install per-config; when you hand-install a dependency to probe
  with, install both configs into the same prefix (`fmt.lib` + `fmtd.lib`), or the
  probe fails for a reason that has nothing to do with the subproject.
- `CMAKE_*_OUTPUT_DIRECTORY` is read when a target is **created**, so a
  subproject setting it only affects targets added after it; the "all binaries in
  one bin/" trick (abseil, protobuf, oneTBB) is a convention that hijacks the
  parent's layout, not a mechanism. Prefer per-target output properties (LLVM's
  `set_output_directory`) or a consumer-side copy.
- CPM is a global singleton: `include(CPM)` resolves through `CMAKE_MODULE_PATH`,
  so the parent's vendored copy can win and a second copy elsewhere silently
  reuses the first. With `list(APPEND CMAKE_MODULE_PATH ...)` the parent's copy is
  found first and the subproject's pin becomes dead code with no warning. Read
  back `CPM_FILE`/`CPM_DIRECTORY` to see whose copy actually ran: an absolute path
  to your own copy does not change that (see the `CPM_INITIALIZED` bullet below).
  Keep `CPM_SOURCE_CACHE` out of the source tree (the default points into the
  subproject's checkout). A vendored CPM.cmake without `EXTRACTED_CPM_VERSION`
  reports `1.0.0-development-version`, which defeats CPM's own version guard and
  fires a false `CMake Warning (author) ... using a more recent CPM version
  (1.0.0-development-version) than the current project (<parent's release>)` in
  any parent that ships a released CPM. That comparison sits *before* CPM's
  `CPM_INITIALIZED` early return, so it fires even in a tree where the parent's
  own copy is the one that ran — and a parent configured with `-Werror=dev`
  turns it into a hard configure failure, so the parent cannot configure at all.
  The guard fires in reverse with a *released* vendored copy too: a parent that
  vendors an older CPM (probed at 0.38.7 and 0.40.0) warns under a plain configure
  and fails with `CMake Error (author) at <subproject>/cmake/CPM.cmake` under
  `-Werror=dev`, because the comparison runs before the `CPM_INITIALIZED` early
  return. Include the vendored copy only when no CPM has run yet —
  `if(NOT DEFINED CPM_VERSION)` … `include("${CMAKE_CURRENT_LIST_DIR}/CPM.cmake")`
  … `endif()`; `CPM_VERSION` is a `CACHE INTERNAL` set by whichever copy ran, so it
  is visible here (probed: the parent's 0.38.7 tree then configures clean with
  `-Werror=dev`, and a parent without CPM still picks up the vendored copy).
  Take the file from the release asset
  (`curl -sL https://github.com/cpm-cmake/CPM.cmake/releases/latest/download/CPM.cmake`),
  which injects the real version; the repo's `cmake/CPM.cmake` source copy does not
  (the asset also carries the `CPM_<name>_SOURCE` override the snapshot lacks).
  When swapping a vendored file in, restore the repo's line-ending convention and
  verify by bytes (`open(p, 'rb').read().count(b'\r\n')` vs bare `\n`) against
  upstream *after normalizing*: `curl` delivers LF into a CRLF repo, and a
  `git show HEAD:<file>` image used as the "before" side is LF as well, so a raw
  byte or diff comparison misleads in both directions.
- When not top-level, do not fetch the test framework. `CPMFindPackage(GTest ...)`
  writes `gtest-config.cmake` into the parent's `CMAKE_FIND_PACKAGE_REDIRECTS_DIR`,
  so the parent's later `find_package(GTest REQUIRED)` resolves inside the
  subproject's tree, and the subproject's pinned `BUILD_GMOCK OFF` leaves
  `GTest::gmock` undefined — the parent's `target_link_libraries(x PRIVATE
  GTest::gmock_main)` then fails at configure. CPM's redirect version file sets
  `PACKAGE_VERSION_COMPATIBLE`/`PACKAGE_VERSION_EXACT` TRUE unconditionally, so
  even an `EXACT` request is silently satisfied. Require the parent to provide
  `GTest::gtest_main`, and skip the fetch when that target already exists.
  Turning the redirect off removes the hijack but leaves the inverse symptom:
  the parent's later `find_package(GTest REQUIRED)` fails with `Could NOT find
  GTest` even though the subproject just built GTest into the same tree, because
  nothing wrote a find-module for it. No setting gives the parent both — pick one
  and document it: skip the fetch when not top-level and demand `GTest::gtest_main`
  from the parent, or keep the fetch and tell the parent to test
  `if(TARGET GTest::gtest_main)` instead of calling `find_package`.
- CMake `function()` definitions are global, so a subproject helper *is* callable
  by the parent after `add_subdirectory` (probe it before relying on it);
  `include()`d variables are not — they are directory-scoped.
- `cmake -E copy -t <dir> $<TARGET_RUNTIME_DLLS:tgt>` with an empty list exits 0,
  and the genex is empty on non-DLL platforms, so that copy recipe needs no
  platform or `BUILD_SHARED_LIBS` guard.
- `add_test` in a subdirectory is dropped silently unless testing is already
  enabled in the tree: no `CTestTestfile.cmake` is generated there and the
  parent's `ctest -N` lists nothing, so `-D<NAME>_BUILD_TESTS=ON` in an embedded
  build produces tests nobody runs. Gate `enable_testing()` on top level *and*
  register the tests only when testing is already on (`PROJECT_IS_TOP_LEVEL OR
  BUILD_TESTING`); a bare parent `enable_testing()` does not set `BUILD_TESTING`,
  so say the requirement in the README rather than relying on detection. Order
  matters, not just presence: a parent that calls `enable_testing()` *after* the
  `add_subdirectory` still ends up with zero tests at its root, so the contract to
  document is "call `enable_testing()` (or `include(CTest)`) **before**
  `add_subdirectory(<lib>)`. There is no cheap detection to add in its place:
  `get_property(DIRECTORY PROPERTY TESTS)` comes back empty both at the end of the
  subdirectory and from a `cmake_language(DEFER DIRECTORY ... CALL ...)` hook
  (probed) — `gtest_discover_tests` registers nothing through that property — so
  document the ordering contract instead of shipping a check that cannot fire. The
  subproject cannot fix it from its own side either: making its `enable_testing()`
  unconditional still leaves the parent's root with zero tests when the parent
  enables testing after the embed (probed), because `enable_testing()` is
  directory-scoped and only reaches downward.
- `gtest_discover_tests` with `DISCOVERY_MODE PRE_TEST` runs the test binary
  during CTest discovery, so test-only PATH injection (`ENVIRONMENT_MODIFICATION`)
  does not make the DLL findable — discovery needs it too. Keep a real POST_BUILD
  copy for the library's own tests.
- A consumer that links a shared library without calling into it may record no
  DLL import at all, so the missing-DLL failure hides; make the probe consumer
  actually reference the library.
- Installing a fetched dependency's target (`install(TARGETS <mylib> <dep> EXPORT ...)`) can
  trip an `AUTHOR_WARNING` you do not control: a dependency that unconditionally sets
  `PUBLIC_HEADER` on its target (fmt does, independent of its own install option) makes
  CMake report `Target <dep> has PUBLIC_HEADER files but no PUBLIC_HEADER DESTINATION`
  whenever your install rule does not install that dependency's headers. Benign under a
  plain configure, fatal under a parent's `-Werror=dev` (or `-DCMAKE_COMPILE_WARNING_AS_ERROR`
  style setups). Clear the property right after naming the target
  (`set_target_properties(<dep> PROPERTIES PUBLIC_HEADER "")`) — the exported target is
  unchanged, since you were not installing those headers anyway.
- A parent that configures with `-Werror=dev` turns every `AUTHOR_WARNING` raised anywhere
  in the tree into a hard failure, so the probe for "does the parent's build survive" must
  include one `-Werror=dev` configure: the plain configure passes while that one fails, and
  the failing location is inside the subproject's own files. Same class as the `-Werror`
  compile-flag probe, one level up at configure time.
- Third-party subprojects write absolute paths under `${CMAKE_BINARY_DIR}`
  (googletest puts archives in `${CMAKE_BINARY_DIR}/lib`), so the parent's build
  root gains `lib/`, `bin/` and `_deps/` it never asked for. A dependency that
  unconditionally `include(GNUInstallDirs)` also leaves the whole `CMAKE_INSTALL_*`
  set in the parent's cache, and a `CPMFindPackage` probe leaves `GTEST_*`/`GMOCK_*`
  `NOTFOUND` entries. The values are benign; the entries are still pollution the
  parent did not ask for — report them rather than hiding them.
- `include(GNUInstallDirs)` must precede the first use of
  `${CMAKE_INSTALL_INCLUDEDIR}`/`${CMAKE_INSTALL_LIBDIR}`. A project that uses
  them in `target_include_directories(... $<INSTALL_INTERFACE:...>)` before
  including GNUInstallDirs gets a non-empty value only by accident — because a
  dependency included it first — so the exported target silently loses its
  include directory the day that dependency changes. Variables expand when the
  command *runs*, not when the target is exported, so prove the ordering with
  `get_target_property(tgt INTERFACE_INCLUDE_DIRECTORIES)` before and after the
  `include()` (empty genex before, `.../include` after). `install(TARGETS ... INCLUDES
  DESTINATION ...)` is the belt-and-braces alternative.
- `set(CPM_DONT_UPDATE_MODULE_PATH ON)` *before* `include(CPM)` is the fix when
  the redirect above is not wanted: it keeps CPM from writing
  `<name>-config.cmake` into the shared `CMAKE_FIND_PACKAGE_REDIRECTS_DIR` for
  packages that are private implementation details.
- CPM also writes `cpm-package-lock.cmake` into `${CMAKE_BINARY_DIR}` — the
  parent's build root when embedded. Pre-setting `CPM_PACKAGE_LOCK_FILE` does not
  move it: CPM sets that variable as a `CACHE INTERNAL` entry, which implies
  FORCE, so your value is overwritten. `set(CPM_DONT_CREATE_PACKAGE_LOCK ON)`
  before `include(CPM)` is what actually keeps the file out, and only one lock
  file can exist per tree anyway — the parent's.
- Unprefixed options collide: any `option(ENABLE_* ...)`/`option(BUILD_* ...)` in
  the subproject can be forced ON by the parent or a sibling, and vice versa.
  Prefix every option with the project name.
- CPM is a global singleton for versions too: the first `CPMAddPackage(name)`
  anywhere in the tree wins, and a later caller asking for another version is
  silently given the first one (it only warns when the later request is
  *newer*). Version isolation is impossible while the same package name lives
  in one build tree — both copies would define the same targets. State this
  contract instead of promising isolation, and tell consumers to declare their
  own dependencies before `add_subdirectory`.
- `include("${CMAKE_CURRENT_LIST_DIR}/CPM.cmake")` does NOT make your copy win.
  CPM guards itself with a GLOBAL `CPM_INITIALIZED` property and returns before
  executing anything, so once any CPM has run in the tree your vendored copy is a
  silent no-op: no version warning, and `CPM_FILE`/`CPM_DIRECTORY`/`CPM_VERSION`
  still name the other copy. What the absolute path does guarantee is that your
  `CPMAddPackage` calls are handed to whichever copy actually ran — state that
  contract instead of promising your pin is what executes. Still prefer
  `CMAKE_CURRENT_LIST_DIR` over `CMAKE_CURRENT_SOURCE_DIR`: the latter triggers a
  cmake-intellisense false positive (`missing-file-path`) because the LSP resolves
  it against the including file's directory.
- To find where an included CMake module actually stops, copy it to a scratch dir
  with `message(STATUS "MARK-x")` at each early return and include the copy by
  absolute path: the last MARK that prints is the code that ran, and the branch
  right after it is the one that returned. A silent early return is invisible to
  plain reading of the module's source.
- A missing shared library surfaces as exit 127 / `0xc0000135` at runtime, not as
  a link error — never stop at "it builds".
- A template's rename/replace script that skips paths by substring match
  (`any(x in root for x in ["build", "out", ".git"])`) skips content replacement
  for the whole tree whenever the checkout path happens to contain one of those
  substrings — it still prints success and leaves the placeholder names in place,
  while the directory/file renames did happen, so the result is a half-renamed
  project. Match path components (`Path.parts`) instead of substrings, and verify on
  a throwaway copy at a path you did not choose — `git archive HEAD | tar -x -C
  <tmp>/<name-containing-a-skip-substring>` gives a clean copy with no `.git` or
  build dirs; run the script there, then assert zero leftover placeholders
  (`grep -c <placeholder> CMakeLists.txt` is 0) *and* that the namespace directory
  was renamed. A path whose name merely contains `out` is enough to trigger it.

## Verification checklist
- Static and shared builds both configure, build, and `ctest` clean.
- As a subproject: the parent's own tests plus the subproject's tests pass from
  the parent build dir, and the parent executable that links the subproject runs.
- Read the per-file `-std=`/`/std:` column of the parent's
  `compile_commands.json`: the parent's own TUs keep the parent's standard, the
  subproject's TUs use the subproject's, and only the TUs that link the target
  pick up the published feature (`-std=gnu++20` for a parent TU linking a
  `cxx_std_20` target, with the parent's extension policy preserved).
- Install mode: `cmake --install --prefix <tmp>`, then an independent
  `find_package(<lib> REQUIRED)` consumer configures, builds and runs — for both
  the static and the shared build; inspect the installed `*Targets.cmake` for
  leaked third-party dependencies and for a non-empty
  `INTERFACE_INCLUDE_DIRECTORIES`. For the shared build also read
  `IMPORTED_SONAME`/`IMPORTED_LOCATION` out of the installed
  `*Targets-<config>.cmake`: an ELF install should name a versioned
  `lib<name>.so.<soversion>`. A Windows-only tree cannot show that — the DLL name
  stays unversioned there — so run this one probe on an ELF toolchain (on a
  Windows host, WSL: `cmake -S /mnt/<drive>/<repo> -B /tmp/<b> -DBUILD_SHARED_LIBS=ON`).
  See references/install-export-surface.md.
- Shared-library surface, on ELF: `nm -D --defined-only` must list only the public
  API (no `detail::`/internal symbols), and the shared build's own tests must pass
  with the library linked as a separate DSO (that is what proves exception classes
  are exported). Install Debug and Release into one prefix and link one consumer per
  config, checking each resolves its own artifact.
- The same source added twice as two sibling subdirectories configures once and
  the parent's executable links and runs.
- Re-diff the probe cache after the fix and state which entries disappeared.
