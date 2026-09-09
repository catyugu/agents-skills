---
name: autocomsol
description: Design, write, and validate new COMSOL Multiphysics Java models intended for comsolcompile and comsolbatch. Use when creating or modifying a COMSOL Java simulation, selecting COMSOL physics APIs (electric currents, electrostatics, heat transfer, solid mechanics, electric-thermal-structural couplings, frequency-domain electromagnetic waves), geometry entities, materials, studies, multiphysics couplings, exports, or numerical acceptance checks. Consult the bundled locally validated Java cases and evidence documents before using nontrivial COMSOL APIs.
---

# AutoCOMSOL

Create a new COMSOL Java simulation from a user requirement. Treat the bundled cases as API evidence and reusable patterns, not as fixed scenarios to rerun.

## Workflow

1. Translate the request into geometry, materials, physics, boundary conditions, study type, outputs, and acceptance criteria.
2. Read `references/case-naming.md` and choose a compliant new case name.
3. Select the closest complete Java reference from `references/examples/analytic/` (verified API patterns) or `references/examples/physical/` (flow / geometry patterns); read the whole file before reusing its nontrivial API patterns.
4. Read the applicable reference below before implementing unfamiliar API calls.
5. Create a new Java model. Preserve proven patterns, but adapt parameters, geometry, selections, materials, studies, exports, and checks to the requested problem.
6. Specify an output contract: Java `main` receives an MPH path as its first argument and a CSV path as its second argument. Save and export to those paths.
7. Compile with `comsolcompile` and run with `comsolbatch` in the caller's project environment. Follow `references/command-usage.md`; this skill does not provide or install a runtime.
8. Assess success from the output files, batch log, and a model-specific numerical check. Do not treat a zero batch exit code alone as validation.
9. When a new API pattern is confirmed, retain the full Java source and add concise evidence documentation alongside the reference library.

## Reference routing

- Command syntax and batch behavior: read `references/command-usage.md`.
- Naming, tier placement, interfaces, material properties, feature tags, and result variables: read `references/case-naming.md`.
- Deterministic 2D/3D boundary, face, and domain selection: read `references/geometry-selection.md`.
- API recipes by physics and coupling, plus validated pitfalls and advanced geometry/material/mesh recipes: read `references/physics-api-recipes.md`.
- Acceptance-check strategy, including the distinction between analytic and physical-reasonableness verification: read `references/verification-guidelines.md`.
- Validating a new or unverified API string before committing it to a case: read `references/api-validation-probes.md`.

## Selection rules

Never assume that a geometry entity number is stable. After geometry finalization, derive selections from geometry information:

- In 2D, use `edgeX` samples to classify edges by their coordinates.
- On 3D faces, query `faceParamRange` before sampling with `faceX`; curved faces do not necessarily accept a `0.5` parameter.
- For multiple domains, use `getUpDown()` and/or `getAdj()` together with sampled geometry features.
- Keep the detection logic private and local to the Java model unless a shared utility is explicitly needed.

## Validation rules

Prefer an analytical solution. If none is suitable, combine independent checks such as imposed-boundary values, field ranges, symmetry, conservation, constitutive identities, and mesh convergence. Export only variables that are valid on the selected dataset dimension.

## Boundaries

- Do not invent COMSOL interface names, feature IDs, property names, or coupling tags. Find local evidence or run a minimal probe first.
- Do not alter a reference case merely to satisfy a new request; create a distinct case.
- Place a new case with an analytical-solution check in `references/examples/analytic/`; place a case without an analytical solution (flow-runnability or physical-reasonableness only) in `references/examples/physical/`.
- Do not embed executable wrappers, installation paths, or runtime dependencies in this skill.
- Keep full Java references intact. Their value is that they are known-good, complete API examples.
