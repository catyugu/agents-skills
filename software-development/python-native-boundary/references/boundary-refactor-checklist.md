# Boundary Refactor Checklist

1. Identify the object graph: definition, compiled runtime, operators, solution.
2. Record ownership and whether each returned array is native-owned or Python-owned.
3. Keep ctypes calls, pointer wiring, string encoding, and status checks in object-specific adapters.
4. Replace dynamic native dispatch with explicit functions that show argument order.
5. Remove re-export-only facades after all callers migrate.
6. Represent native copy-out results with explicit immutable data objects.
7. Keep one authoritative high-level snapshot; derive NumPy views instead of caching duplicates.
8. Add a smallest-real-model smoke test for construction, compile, copy-out, and solve paths touched.
9. Use `numpy.shares_memory` for view assertions.
10. Run focused tests, the full Python suite, compile checks, diff checks, and the native build.
