---
name: python-native-boundary
description: Use when refactoring ctypes-backed Python bindings.
category: software-development
---

# Python Native Boundary

Use this skill when a Python package wraps a C or C++ library through ctypes
(or a similar low-level ABI). The goal is a small, inspectable boundary between
native handles and high-level domain objects.

## Object graph first

Model the runtime graph before editing:

```text
Definition → CompiledModel → Operators → Solution
```

For every object, record its ownership, mutability, storage, and operations.
Handles and ctypes structs are ownership and transport mechanisms; they are not
additional domain objects.

## Adapter structure

Group low-level functions by the native object they operate on:

```text
_native_model.py
_native_compiled.py
_native_solution.py
```

Keep ctypes pointer construction, string encoding, buffer allocation, status
checking, and native destruction in these adapters. High-level classes should
not know ABI field order or pointer types.

Prefer explicit adapter functions:

```python
add_material(dll, handle, name, kx, ky, kz, rho, specific_heat, viscosity)
```

Avoid dynamic calls such as `getattr(dll, name)`, `*args` dispatch, and generic
stringly-typed wrappers when they hide the native function and argument order.
A few repeated explicit calls are better documentation than a broad dispatcher.

Once all callers have moved, remove a facade that only re-exports adapter
functions. Keeping it preserves the old conceptual boundary and allows stale
imports to survive.

## Copy-out contracts

Native copy-out functions should return explicit data objects rather than
unstructured dictionaries. Use frozen dataclasses for metadata, material
values, probe snapshots, and solution snapshots. Keep the native defaults as
the single source of truth; Python option objects provide overrides only.

At the high-level layer, maintain one authoritative data object per immutable
result. Derive views such as `temperature = state[:fvm_count]` and
`temperature_history = state_history[:, :fvm_count]` instead of caching duplicate
arrays that can become inconsistent.

## Testing and verification

Before or alongside a boundary refactor, add a minimal real end-to-end smoke
test. Exercise the smallest model through construction, compilation, operator
copy-out, and solution snapshotting when relevant. Assert public behavior,
array shapes, physical units, and ownership/view semantics—not implementation
layout.

For NumPy views, use `numpy.shares_memory`; `ndarray.base` need not directly
refer to the apparent source array after multiple view operations.

Run the focused test first, then the complete Python test suite, compile checks,
`git diff --check`, and the native build. Preserve exact command output when
reporting results.

## Pitfalls

- Do not split files before responsibilities are understood.
- Do not add broad defensive validation for impossible states.
- Do not copy numerical defaults into Python.
- Do not turn every dictionary in a scientific application into a dataclass;
  this skill applies to ABI copy-outs and high-level native result contracts.
- Do not change solver mathematics while cleaning the binding boundary.
- Do not treat a passing import test as proof that ctypes argument wiring works;
  call the real native library with a minimal model.

## Reference

See `references/boundary-refactor-checklist.md` for a compact reusable checklist.
