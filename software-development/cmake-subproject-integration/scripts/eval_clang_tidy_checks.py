"""Count clang-tidy diagnostics per check for one or more candidate configs.

Usage:
    python eval_clang_tidy_checks.py <build-dir> <repo-dir> <config> [<config> ...]

<build-dir> must contain compile_commands.json (configure with
-DCMAKE_EXPORT_COMPILE_COMMANDS=ON). Translation units are taken from that file:
the ones under <repo-dir> and outside any fetched-dependency cache, so the count
is about the project's own code.

Purpose: choose a .clang-tidy check set by measurement instead of by taste. Run
it once with a permissive candidate, read which checks actually fire and how
often, exclude the ones the project's conventions will always trip, then re-run
until the count is zero — while a control file of deliberately bad code still
produces diagnostics (that control file must compile: a TU with an error reports
only clang-diagnostic-error and proves nothing about the enabled checks).
"""

import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

DIAGNOSTIC = re.compile(
    r"^(?P<file>.+):(?P<line>\d+):(?P<col>\d+): "
    r"(?:warning|error): (?P<message>.*?) \[(?P<checks>[^\]]+)\]$"
)
EXCLUDE_PARTS = (".cache", "_deps", "CMakeFiles")


def own_translation_units(build_dir: Path, repo_dir: Path) -> list[str]:
    database = json.loads((build_dir / "compile_commands.json").read_text(encoding="utf-8"))
    repo = str(repo_dir).replace("\\", "/")
    units = []
    for entry in database:
        path = entry["file"].replace("\\", "/")
        if not path.startswith(repo) or any(part in path for part in EXCLUDE_PARTS):
            continue
        units.append(path[len(repo):].lstrip("/"))
    return sorted(set(units))


def diagnose(config: Path, unit: str, build_dir: Path, repo_dir: Path) -> list[tuple[str, str]]:
    result = subprocess.run(
        ["clang-tidy", "-p", str(build_dir), f"--config-file={config}", unit],
        cwd=repo_dir, capture_output=True, text=True, errors="replace",
    )
    hits = []
    for line in result.stdout.splitlines():
        match = DIAGNOSTIC.match(line.strip())
        if match:
            hits.append((match.group("checks"), f"{match.group('file')}:{match.group('line')}: {match.group('message')}"))
    return hits


def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    build_dir, repo_dir = Path(sys.argv[1]), Path(sys.argv[2])
    units = own_translation_units(build_dir, repo_dir)
    if not units:
        print("no translation units of this project found in compile_commands.json", file=sys.stderr)
        return 1
    for name in sys.argv[3:]:
        config = Path(name)
        per_check: Counter[str] = Counter()
        per_unit: Counter[str] = Counter()
        sample: dict[str, str] = {}
        for unit in units:
            for checks, detail in diagnose(config, unit, build_dir, repo_dir):
                per_unit[unit] += 1
                for check in checks.split(","):
                    per_check[check] += 1
                    sample.setdefault(check, detail)
        total = sum(per_check.values())
        print(f"#### {config.name}: {total} diagnostics on {len(units)} own TUs")
        for unit, count in per_unit.most_common():
            print(f"     {count:4d}  {unit}")
        for check, count in per_check.most_common():
            print(f"     {count:4d}  {check}\n             {sample[check][:160]}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
