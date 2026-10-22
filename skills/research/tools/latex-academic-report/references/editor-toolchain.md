# Editor and formatter toolchain

How to make a LaTeX report build and auto-format correctly in an editor, and how to prove the
formatter is not silently rewriting hand-laid-out source.

## 1. Pick the engine from the preamble, then pin it

`fontspec`, `xeCJK`, `unicode-math`, `\setmainfont`, and any `ctex` document that sets a Latin font
require **XeLaTeX or LuaLaTeX**. Under pdfLaTeX the compile dies inside `fontspec.sty`:

```
Fatal Package fontspec Error: The fontspec package requires either XeTeX or LuaTeX.
```

LaTeX extensions default to a `latexmk` recipe that runs `-pdf` -> pdfLaTeX, so the document fails in
the editor while compiling fine from a manual `xelatex` call. Grep the preamble for the trigger, then
pin the engine in the editor settings (section 5):

```jsonc
// %APPDATA%\<editor>\User\settings.json
{
    // preamble uses fontspec -> must not build with pdflatex
    "latex-workshop.latex.recipe.default": "latexmk (xelatex)"
}
```

`latexmk -xelatex final.tex` is the shell equivalent of that recipe; use it to reproduce or rule out
an editor-only build failure.

## 2. Getting the rules to latexindent at all

latexindent falls back to its built-in defaults — silently, no error, no warning, output that looks
exactly like a config which was read and honoured — whenever the settings it is pointed at do not
resolve. Two ways to land there:

- `-l <path>` naming a file that does not exist.
- The extension's own default args, which pass only `-y=defaultIndent: '%INDENT%'` and never name a
  project config, so a project `.latexindent.yaml` is simply not consulted.

The editor does **not** defeat latexindent's own search, though: it writes its scratch file into the
document directory (`__latexindent_temp_<name>.tex`), runs with the document directory as cwd, and
deletes both that file and `indent.log` afterwards. So `-c %DIR%/` already keeps cruft out of the
project, and a project config is reachable by the ordinary upward search. Point at it explicitly
anyway — it removes the guesswork about which directory the run started in:

```jsonc
{
    "latex-workshop.latexindent.args": [
        "-c", "%TMPDIR%",                    // scratch dir for indent.log and backups
        "-l", "%DIR%/.latexindent.yaml",     // %DIR% = document dir; the editor runs on %TMPFILE%
        "%TMPFILE%"
    ],
    "latex-workshop.formatting.latexindent.args": [ /* the same array */ ]
}
```

Both keys exist and both must be set: `latexindent.args` drives format-on-save / format-document,
`formatting.latexindent.args` drives range formatting.

### Rules inline, with no config file at all

Every rule can go on the command line as **one** `-y` argument, using latexindent's own separator
syntax: comma separates settings, colon chains a nested path, semicolon separates sibling keys.

```
-y="defaultIndent: '%INDENT%',specialBeginEnd: inlineMath: lookForThis: 0,verbatimEnvironments: algorithmic: 1,lookForAlignDelims: tabular: 0,noAdditionalIndentGlobal: mandatoryArguments: 1;namedGroupingBracesBrackets: 1;UnNamedGroupingBracesBrackets: 1"
```

Verified to produce the same output, and the same idempotency, as the config file in section 3. Use
this form when the rules are not supposed to live in a file at all.

Three traps make a correct-looking inline attempt silently do nothing:

- **Repeated `-y` flags do not merge — only the last one survives.** Splitting the rules across
  several `-y` arguments discards all but the final one.
- A single `-y` containing a real newline parses only its first line. Keep it on one line.
- The nested form is colon-chained (`specialBeginEnd: inlineMath: lookForThis: 0`); a YAML flow
  mapping (`specialBeginEnd: {inlineMath: {...}}`) is rejected outright.

## 3. Default latexindent rules that damage a hand-laid-out document

latexindent formats as if the source were machine-generated. On a document with hand-wrapped prose
lines and hand-indented listings, these defaults corrupt the layout. The third column names the
setting; spell it for the command line with the separator syntax in section 2:

| Default rule | Damage | Setting that fixes it |
| --- | --- | --- |
| `specialBeginEnd.inlineMath.lookForThis: 1` | Indents the continuation line of any `$...$` spanning a line break — hits hand-wrapped prose throughout the file | `specialBeginEnd: {inlineMath: {lookForThis: 0}}` |
| environment indentation inside `algorithmic` | `\While` / `\If` / `\For` come from `algdef`, not from an environment, so latexindent does not nest them and flattens the listing | `verbatimEnvironments: {algorithmic: 1}` |
| `lookForAlignDelims.tabular: 1` | Realigns `tabular` by character count; a table hand-aligned for CJK display width comes back visibly ragged | `lookForAlignDelims: {tabular: 0}` |
| `noAdditionalIndentGlobal.*: 0` | Re-indents continuation lines inside command arguments and braces, reflowing wrapped prose | set `mandatoryArguments`, `namedGroupingBracesBrackets`, `UnNamedGroupingBracesBrackets` to `1` |

Working file — keep it minimal and let each rule name the thing it protects:

```yaml
# .latexindent.yaml
defaultIndent: '    '

# a line-broken inline $...$ must not have its continuation line indented
specialBeginEnd:
  inlineMath:
    lookForThis: 0

# algorithmic is hand-nested via \While/\If; latexindent would flatten it
verbatimEnvironments:
  algorithmic: 1

# tabular is aligned by CJK display width, not by character count
lookForAlignDelims:
  tabular: 0

# keep command-argument and brace continuation lines as written
noAdditionalIndentGlobal:
  mandatoryArguments: 1
  namedGroupingBracesBrackets: 1
  UnNamedGroupingBracesBrackets: 1
```

## 4. Verify the formatter config instead of trusting it

Run the formatter on a **copy**, from a directory unrelated to the project, so the test also proves
the config path works. With a config file:

```bash
cp body.tex "$TMPDIR/tmpfile.tex"
cd "$TMPDIR"
latexindent -c "$TMPDIR" -l "$PROJECT/.latexindent.yaml" tmpfile.tex > out.tex

diff --strip-trailing-cr "$PROJECT/body.tex" out.tex      # every hunk must be whitespace-only
wc -l < <(diff --strip-trailing-cr -w "$PROJECT/body.tex" out.tex)   # 0 == only whitespace changed
```

When the rules are passed inline instead, swap `-l "$PROJECT/.latexindent.yaml"` for the single `-y`
argument from section 2; the diff and idempotency checks are identical.

Then require idempotency: format the output again and demand a byte-identical result. A formatter
that is not idempotent churns the file on every save.

Then **pre-normalize**: run the formatter over the sources once, confirm the diff is whitespace-only,
and commit. With the sources already in canonical form, format-on-save is a no-op and an accidental
save cannot perturb the document.

Finally, sanity-check that the config is live rather than over-restrictive: format a deliberately
mis-indented sample (a bare `equation`, an `itemize`) and confirm it still gets indented. A config
that suppresses everything would also "pass" the no-op test.

## 5. Where the config lives: the user-level editor settings

Default to the **user-level editor settings**, not files inside the report repository. The toolchain
belongs to the machine, not to the document, and a report repository carrying editor dotfiles mixes
build configuration into a deliverable that is read by people who will never open it in an editor.

- Windows: `%APPDATA%\<editor>\User\settings.json` — `%APPDATA%\VSCodium\User\settings.json` for
  VSCodium. Merge into the existing object; never replace the file.
- A global recipe and global formatter rules apply to **every** LaTeX project on the machine. State
  that when you set them: a project needing pdfLaTeX (pstricks) or default formatting must then
  override them locally.
- If the config was already committed, revert it and delete the files rather than leaving them in
  place, then confirm the repository is clean of them
  (`git ls-files | grep -iE 'vscode|latexindent'` prints nothing). Whitespace changes made while
  normalizing the document sources are a separate concern — keep them in their own commit.

Record *why* each setting exists, next to it (a comment in the JSON, or a line in the project's
handover notes). Every setting above exists to fix one specific failure, and a later reader who does
not know the failure will delete the setting.

Committing the config is the fallback for a repository that genuinely must be self-contained (a
shared checkout, CI): an allowlist `.gitignore` (`*.*` plus explicit `!` negations) ignores a new
dotfile by default, so add the negations and confirm with `git check-ignore -v <path>` (no output
means it is track-eligible).
