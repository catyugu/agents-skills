# Runtime dependency (DLL/so) deployment for embedded libraries

## What upstream projects do

| project | approach | cost |
| --- | --- | --- |
| abseil-cpp | unconditional `set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)` with the comment that a DLL must sit next to its exe; install rules gated on top-level; on ELF `CMAKE_INSTALL_RPATH "$ORIGIN"` + `CMAKE_BUILD_RPATH_USE_ORIGIN` | takes over the parent's artifact layout |
| protobuf | same global-dir trick, but only when building shared + MSVC; its else-branch forces `BUILD_SHARED_LIBS OFF` because "a DLL built under `_deps/` can't be found at runtime" | takes over layout; forces static |
| oneTBB | sets all four `CMAKE_*_OUTPUT_DIRECTORY` to a compiler/arch/CRT-encoded dir; tests point at the DLL dir instead of copying; install rules not top-level gated | takes over layout; installs into the parent's prefix |
| LLVM | never touches `CMAKE_*_OUTPUT_DIRECTORY` (comment: it confuses msbuild); per-target `set_output_directory(tgt BINARY_DIR ... LIBRARY_DIR ...)`, DLLs to `bin/` on Windows | per-target, no global state |
| Qt | ships a helper the *app author* calls: `qt_generate_deploy_app_script()` + `install(TARGETS)` + `install(SCRIPT deploy_script)`; `qt_deploy_runtime_dependencies()` for custom cases | needs docs and a caller |
| vcpkg toolchain | wraps `add_executable`/`add_library` and adds a POST_BUILD applocal step per target (`VCPKG_APPLOCAL_DEPS`, default ON) | problem disappears if the parent uses the toolchain |
| CMake docs | official recipe: `add_custom_command(TARGET exe POST_BUILD COMMAND ${CMAKE_COMMAND} -E copy -t $<TARGET_FILE_DIR:exe> $<TARGET_RUNTIME_DLLS:exe> COMMAND_EXPAND_LISTS)` | consumer-side, no pollution |

`$<TARGET_RUNTIME_DLLS:tgt>` (CMake >= 3.21) lists the DLLs from SHARED targets in
the target's transitive dependencies; valid only on executables, SHARED and
MODULE targets; empty on non-DLL platforms; imported SHARED targets need
`IMPORTED_LOCATION` pointing at the `.dll`.

## Recipes, cheapest first

1. Consumer-side POST_BUILD copy (default choice; multi-config safe because
   `$<TARGET_FILE_DIR:>` resolves per config):

   ```cmake
   add_custom_command(TARGET <consumer> POST_BUILD
       COMMAND ${CMAKE_COMMAND} -E copy -t $<TARGET_FILE_DIR:<consumer>> $<TARGET_RUNTIME_DLLS:<consumer>>
       COMMAND_EXPAND_LISTS)
   ```

2. No copy, tests only — plain `add_test` plus a PATH entry:

   ```cmake
   set_tests_properties(<test> PROPERTIES
       ENVIRONMENT_MODIFICATION "PATH=path_list_prepend:$<TARGET_FILE_DIR:<lib>>")
   ```

   Does not help CTest discovery of `gtest_discover_tests` (see SKILL.md pitfall).

3. Install side: `install(TARGETS)` puts the DLL in `bin/`; use
   `install(RUNTIME_DEPENDENCY_SET ...)` / `install(IMPORTED_RUNTIME_ARTIFACTS ...)`
   (CMake >= 3.21) only when the library ships third-party DLLs of its own.

4. Library side, only if it must be runnable in-tree: per-target
   `RUNTIME_OUTPUT_DIRECTORY` (never the `CMAKE_*` variable), and on ELF
   `$ORIGIN` rpath for both build and install trees.

## Choosing
- A library that does not want to own the parent's layout: keep an internal
  POST_BUILD copy for its own tests, expose `$<TARGET_FILE_DIR:lib>` /
  `$<TARGET_RUNTIME_DLLS:>` through the target, and document recipe 1 or 2 for the
  parent. This is the shape to prefer.
- Publish recipe 1 as a callable helper rather than as prose: a
  `function(<name>_stage_runtime target)` in the top-level file that runs the
  POST_BUILD copy for whatever target is passed to it. CMake `function()`
  definitions are global, so the parent can call it on *its own* executable right
  after linking your target, and the DLLs (yours and your dependencies') land next
  to that executable — verified end to end: the parent executable runs under a
  minimal `PATH` when the helper is called and exits 127 without it. Keep the
  helper opt-in, so the parent's binaries are touched only when the parent names
  them; the library's own tests and examples are just its first caller.
- Document that a vcpkg-based parent needs nothing at all.
- Never write the opposite claim into the README ("runtime deployment of the
  shared library is handled by CMake"): the parent then chases a link error that is
  really a runtime 127. Say instead that the library does not deploy, and give the
  helper or the `PATH`/rpath requirement.
- Do not adopt the global-output-dir trick just because large projects use it: it
  has a creation-order dependency and moves the problem onto the parent.
