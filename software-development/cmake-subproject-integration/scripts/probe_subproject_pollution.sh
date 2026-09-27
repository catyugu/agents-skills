#!/usr/bin/env bash
# Audit how a CMake project pollutes a superproject when added with add_subdirectory.
#
#   probe_subproject_pollution.sh <subproject-source-dir> [probe-dir]
#
# Env:
#   PROBE_CMAKE_ARGS="-DMYPROJECT_BUILD_TESTS=ON -DBUILD_SHARED_LIBS=ON"
#                                           applied to EVERY configure; keep a
#                                           compiler out of it (export CXX
#                                           instead) or the two trees differ and
#                                           the cache diff is toolchain noise
#   PROBE_FIND_PACKAGES="GTest;cxxopts"     names checked with find_package() after the add
#   PROBE_LINK_TARGET="myproject::myproject" parent target the consumer exe links
#   PROBE_CONSUMER_SOURCE=<file.cpp>        consumer that CALLS into the library
#                                           (a consumer that references no symbol may record
#                                           no DLL import, hiding the missing-DLL failure)
#   PROBE_TARGET_NAME=myproject             target name grepped in cmake_install.cmake
#   PROBE_PARENT_CXX_FLAGS=-Wall            parent's warning flags for the inheritance probe,
#                                           applied with add_compile_options (appending to
#                                           CMAKE_CXX_FLAGS drops /EHsc and breaks the probe)
#   PROBE_BOGUS_C_COMPILER=C:/no/such/cc.exe  bogus compiler for the language-claim probe
#   PROBE_WERROR_DEV=1                      also configure both trees with -Werror=dev
#                                           (0 disables): author warnings become hard
#                                           configure failures there
#   PROBE_GENERATOR=Ninja
#
# Paths are POSIX (git-bash / MSYS); native tools get forward-slash native paths.
# Each command below was run by hand on this machine; the script only sequences them.
set -u

SUB_SRC="${1:?usage: probe_subproject_pollution.sh <subproject-source-dir> [probe-dir]}"
PROBE="${2:-$PWD/subprobe}"
GEN="${PROBE_GENERATOR:-Ninja}"
mkdir -p "$PROBE/src"

cat > "$PROBE/src/probe.cpp" <<'EOF'
int probe_answer() { return 42; }
int main() { return probe_answer() == 42 ? 0 : 1; }
EOF

if [ -n "${PROBE_CONSUMER_SOURCE:-}" ]; then
    cp "$PROBE_CONSUMER_SOURCE" "$PROBE/src/consumer.cpp"
else
    cat > "$PROBE/src/consumer.cpp" <<'EOF'
int main() { return 0; }
EOF
fi

cat > "$PROBE/CMakeLists.txt" <<'EOF'
cmake_minimum_required(VERSION 3.26)
project(ProbeParent VERSION 1.0.0 LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
add_executable(probeapp src/probe.cpp)
enable_testing()
add_test(NAME probe.smoke COMMAND probeapp)

if(SUBPROBE_PARENT_FLAGS)
  # The parent's warning flags are APPENDED. Never push them through
  # CMAKE_CXX_FLAGS: clobbering it also drops the toolchain defaults (CMake's
  # "/DWIN32 /D_WINDOWS /W3 /GR /EHsc" on MSVC-like compilers) and the probe then
  # fails for an unrelated reason: "cannot use 'try' with exceptions disabled".
  add_compile_options(${SUBPROBE_PARENT_FLAGS})
endif()

message(STATUS "PROBE|before|CXX_STANDARD=${CMAKE_CXX_STANDARD}")
message(STATUS "PROBE|before|PROJECT_NAME=${PROJECT_NAME}")
message(STATUS "PROBE|before|MODULE_PATH=${CMAKE_MODULE_PATH}")
message(STATUS "PROBE|before|INSTALL_LIBDIR=${CMAKE_INSTALL_LIBDIR}")
message(STATUS "PROBE|before|REDIRECTS=${CMAKE_FIND_PACKAGE_REDIRECTS_DIR}")

if(SUBPROBE_WITH_SUB)
  add_subdirectory("${SUBPROBE_SUB_SOURCE}" "${CMAKE_BINARY_DIR}/subprobe-build")
endif()

message(STATUS "PROBE|after|CXX_STANDARD=${CMAKE_CXX_STANDARD}")
message(STATUS "PROBE|after|PROJECT_NAME=${PROJECT_NAME}")
message(STATUS "PROBE|after|MODULE_PATH=${CMAKE_MODULE_PATH}")
message(STATUS "PROBE|after|INSTALL_LIBDIR=${CMAKE_INSTALL_LIBDIR}")
message(STATUS "PROBE|after|CPM_SOURCE_CACHE=${CPM_SOURCE_CACHE}")
message(STATUS "PROBE|after|BUILD_TYPE=${CMAKE_BUILD_TYPE}")
message(STATUS "PROBE|after|BUILD_SHARED_LIBS=${BUILD_SHARED_LIBS}")

foreach(_pkg IN LISTS SUBPROBE_FIND_PACKAGES)
  find_package(${_pkg} QUIET)
  message(STATUS "PROBE|after|find_package(${_pkg}) FOUND=${${_pkg}_FOUND} DIR=${${_pkg}_DIR}")
endforeach()

if(SUBPROBE_LINK_TARGET AND TARGET ${SUBPROBE_LINK_TARGET})
  add_executable(probeconsumer src/consumer.cpp)
  target_link_libraries(probeconsumer PRIVATE ${SUBPROBE_LINK_TARGET})
  add_test(NAME probe.consumer COMMAND probeconsumer)
endif()
EOF

configure() { # $1 = build dir, rest = extra -D args
    local dir="$1"; shift
    cmake -S "$PROBE" -B "$PROBE/$dir" -G "$GEN" "$@" > "$PROBE/$dir.log" 2>&1
    local rc=$?
    echo "--- configure $dir: exit=$rc ---"
    grep -E 'PROBE\||CMake (Warning|Error)|CPM:' "$PROBE/$dir.log" | head -40
}

echo "### A: parent alone"
# Same extra args as B on purpose: a compiler or option given only to the
# with-subproject configures makes the whole cache diff compare two toolchains
# (CMAKE_CXX_FLAGS, linker flags, AR/RANLIB, std/CRT column) instead of the
# subproject's effect.
# shellcheck disable=SC2086
configure build_base ${PROBE_CMAKE_ARGS:-}

echo
echo "### B: parent with the subproject"
# shellcheck disable=SC2086
configure build_sub -DSUBPROBE_WITH_SUB=ON -DSUBPROBE_SUB_SOURCE="$SUB_SRC" \
    -DSUBPROBE_FIND_PACKAGES="${PROBE_FIND_PACKAGES:-}" \
    -DSUBPROBE_LINK_TARGET="${PROBE_LINK_TARGET:-}" \
    ${PROBE_CMAKE_ARGS:-}

if [ ! -f "$PROBE/build_sub/build.ninja" ] && [ ! -f "$PROBE/build_sub/Makefile" ]; then
    echo "NOTE: the with-subproject configure produced no build files; read"
    echo "      $PROBE/build_sub.log in full (the later build log has its own name)."
fi

echo
echo "### B2: the parent's global flags compile the subproject's sources too"
# Directory scope inherits downward, so the parent's flags reach the subproject's
# own TUs; an unconditional -Werror there then fails the PARENT's build with the
# errors pointing at the subproject's headers.
# shellcheck disable=SC2086
configure build_flags -DSUBPROBE_WITH_SUB=ON -DSUBPROBE_SUB_SOURCE="$SUB_SRC" \
    -DSUBPROBE_FIND_PACKAGES="${PROBE_FIND_PACKAGES:-}" \
    -DSUBPROBE_LINK_TARGET="${PROBE_LINK_TARGET:-}" \
    -DSUBPROBE_PARENT_FLAGS="${PROBE_PARENT_CXX_FLAGS:--Wall}" \
    ${PROBE_CMAKE_ARGS:-}
cmake --build "$PROBE/build_flags" > "$PROBE/build_flags.build.log" 2>&1
echo "build with the parent's global flags: exit=$?"
grep -E 'error:' "$PROBE/build_flags.build.log" | head -5 || true

echo
echo "### B3: language claim — a compiler the parent never needed"
BOGUS_CC="${PROBE_BOGUS_C_COMPILER:-C:/no/such/cc.exe}"
# shellcheck disable=SC2086
cmake -S "$PROBE" -B "$PROBE/lang_base" -G "$GEN" -DCMAKE_C_COMPILER="$BOGUS_CC" ${PROBE_CMAKE_ARGS:-} > "$PROBE/lang_base.log" 2>&1
echo "CXX-only parent alone with a bogus C compiler: exit=$? (0 = C never needed)"
cmake -S "$PROBE" -B "$PROBE/lang_sub" -G "$GEN" -DSUBPROBE_WITH_SUB=ON -DSUBPROBE_SUB_SOURCE="$SUB_SRC" \
    -DCMAKE_C_COMPILER="$BOGUS_CC" ${PROBE_CMAKE_ARGS:-} > "$PROBE/lang_sub.log" 2>&1
echo "with the subproject added:                     exit=$? (non-zero = the subproject demands it)"
grep -E 'CMAKE_C_COMPILER|CMake Error' "$PROBE/lang_sub.log" | head -4 || true

echo
echo "### B4: -Werror=dev — author warnings become configure errors"
# A vendored module or an install rule that conflicts with a dependency target's
# properties raises an AUTHOR warning, which a parent that configures with
# -Werror=dev turns into a hard failure. Cheapest way to find what a parent
# maintainer will actually hit.
if [ "${PROBE_WERROR_DEV:-1}" != "0" ]; then
    # shellcheck disable=SC2086
    cmake -Werror=dev -S "$PROBE" -B "$PROBE/werr_base" -G "$GEN" ${PROBE_CMAKE_ARGS:-} > "$PROBE/werr_base.log" 2>&1
    echo "parent alone with -Werror=dev: exit=$? (0 = clean)"
    # shellcheck disable=SC2086
    cmake -Werror=dev -S "$PROBE" -B "$PROBE/werr_sub" -G "$GEN" -DSUBPROBE_WITH_SUB=ON -DSUBPROBE_SUB_SOURCE="$SUB_SRC" \
        -DSUBPROBE_FIND_PACKAGES="${PROBE_FIND_PACKAGES:-}" \
        -DSUBPROBE_LINK_TARGET="${PROBE_LINK_TARGET:-}" \
        ${PROBE_CMAKE_ARGS:-} > "$PROBE/werr_sub.log" 2>&1
    echo "with the subproject added:     exit=$? (non-zero = the subproject breaks the parent's configure)"
    grep -E 'CMake Error|Author' "$PROBE/werr_sub.log" | head -6 || true
else
    echo "(skipped: PROBE_WERROR_DEV=0)"
fi

echo
echo "### cache diff (base -> with subproject); BUILD@ is the normalized build dir"
diff <(grep -E '^[A-Za-z_]+:' "$PROBE/build_base/CMakeCache.txt" | sed "s|$PROBE/build_base|@BUILD@|g" | sort) \
     <(grep -E '^[A-Za-z_]+:' "$PROBE/build_sub/CMakeCache.txt"  | sed "s|$PROBE/build_sub|@BUILD@|g" | sort) || true

echo
echo "### new entries in the parent build root"
ls "$PROBE/build_sub" | grep -vE '^(\.ninja|CMakeCache.txt|CMakeFiles|build.ninja|rules.ninja|compile_commands.json|cmake_install.cmake|CTestTestfile.cmake)$' || true

echo
echo "### pkgRedirects (find_package hijack)"
ls "$PROBE/build_sub/CMakeFiles/pkgRedirects" 2>/dev/null || echo "(none)"

echo
echo "### ctest -N at the parent root"
(cd "$PROBE/build_sub" && ctest -N) 2>&1 | tail -15 || true

if [ -n "${PROBE_TARGET_NAME:-}" ]; then
    echo
    echo "### install rules naming ${PROBE_TARGET_NAME}"
    grep -c "${PROBE_TARGET_NAME}" "$PROBE/build_sub/cmake_install.cmake" 2>/dev/null || echo 0
fi

echo
echo "### build + run (a build that links is not a build that runs)"
# Distinct name from the configure log above: reusing $PROBE/build_sub.log here
# overwrites the configure output, and a failed configure is then invisible.
cmake --build "$PROBE/build_sub" > "$PROBE/build_sub.build.log" 2>&1
echo "build exit=$?"
(cd "$PROBE/build_sub" && ctest --output-on-failure) 2>&1 | tail -12
for exe in "$PROBE/build_sub/probeconsumer.exe" "$PROBE/build_sub/probeconsumer"; do
    if [ -x "$exe" ]; then "$exe"; echo "probeconsumer exit=$?"; break; fi
done

echo
echo "### compile flags per file (std / CRT)"
for f in "$PROBE/build_sub/compile_commands.json"; do
    [ -f "$f" ] && python - "$f" <<'PY'
import json, sys
db = json.load(open(sys.argv[1]))
for e in db:
    cmd = (e.get("command") or " ".join(e.get("arguments", []))).split()
    flags = [t for t in cmd if t.startswith(("-std", "/std")) or t in ("-MT", "-MTd", "/MT", "/MTd", "-MD", "-MDd", "/MD", "/MDd")]
    print(e["file"].replace("\\", "/").split("/")[-1], "->", flags)
PY
done
