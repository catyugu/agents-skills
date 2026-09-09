# comsolcompile / comsolbatch usage

Compile a COMSOL Model Java file with `comsolcompile` and run it with `comsolbatch`.
Quote every path that may contain spaces.

## Compile

```bash
comsolcompile -classpathadd <extra-classpath> <source.java>
```

- Adds extra classpath entries when the source needs them; the COMSOL OSGi class path
  (public API in `com.comsol.api`) is resolved automatically.
- Outputs a `.class` next to the source file. Use `-outputdir <path>` to redirect.
- Compile with the COMSOL Java toolchain so the resulting class-file version is loadable
  by the COMSOL bundled JRE; a class compiled by a newer JDK will be rejected at run time
  with a "class file version" error. Pass `-encoding UTF-8` for non-ASCII source text.

## Run

```bash
comsolbatch -inputfile <compiled-class-or-mph> -outputfile <output.mph> \
  -batchlog <log-path> -study <study-name> [-np <cores>] [-stoptime <seconds>]
```

- `-inputfile` accepts `.mph` or `.class`.
- A batch class must be `public` with `public static void main(String[] args)`;
  the model's Java `main` receives the MPH path and the CSV export path as `args[0]`
  and `args[1]`.
- Key options: `-study <name>`, `-job <name>`, `-methodcall <tag>`, parametric sweep
  via `-paramfile`/`-plist`/`-pname`/`-pindex`, `-stoptime <seconds>` max wall time,
  `-np <cores>`, `-tmpdir`, `-recoverydir`, `-continue`/`-recover`, and the test-only
  `-nosave`/`-norun`.
- `-outputfile <X>.mph` also writes `<X>_Model.mph` with the model tag appended.
- `comsolbatch` runs in the foreground; output goes to stdout and the `-batchlog` file.

## Full regression sweep (lab)

The lab's `scripts/run.py` wraps compile+run+verify and can sweep every registered case:

```bash
python scripts/run.py sweep [--keys K1,K2] [--skip-pass] [--runs-root DIR]
```

- Compiles all `src/` sources once, runs each case to `runs/<key>/`, invokes the
  case verification script, and aggregates the health reports into
  `runs/aggregate-summary.json` + `aggregate-summary.md`.
- The case list is the single `REGISTRY` in `scripts/health_check.py` — do not keep a
  second copy elsewhere.
- `--skip-pass` re-runs only keys whose last health report was not PASS.
- Exit code 0 iff every case PASSes; a single failing case does not abort the sweep.

## Judging success

A zero batch exit code alone is not validation. Require all of:

- the expected class was produced and `comsolbatch` exited successfully;
- the expected MPH and exported data files exist;
- the batch log shows no meaningful exception, license, failure, divergence,
  or out-of-memory signal;
- the model-specific numerical checks pass.

A solver can exit cleanly while returning a trivial field caused by a wrong selection
or terminal definition — catch this with independent numerical checks, not the exit code.
