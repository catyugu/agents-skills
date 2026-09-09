# Validated physics API recipes

Use these as local COMSOL 6.2 evidence. Read the linked full Java case before applying a recipe.

| Scope | Interface / coupling | Important evidence | Full reference |
| --- | --- | --- | --- |
| Electrostatics | `Electrostatics` | Explicit `TerminalType="Voltage"` is required for a voltage terminal. | `examples/analytic/EcSquareStationary.java` |
| Electric currents | `ConductiveMedia` | `ElectricCurrents` was not registered locally; use `electricconductivity`, and use `relpermittivity` where required. | `examples/analytic/EcSquareStationary.java` |
| Solid heat transfer | `HeatTransfer` | Boundary features use `TemperatureBoundary` and `HeatFluxBoundary`; material properties include `thermalconductivity`, `density`, `heatcapacity`. | `examples/analytic/TCylinderStationary.java` |
| Solid mechanics | `SolidMechanics` | Use `Fixed`, `Roller`, and `BoundaryLoad`; configure `lemm1` elastic properties deliberately. | `examples/analytic/SmCylinderAxialStationary.java` |
| Electric to thermal | `multiphysics().create(..., "ElectromagneticHeating")` | Set `EMHeat_physics` and `Heat_physics`; use `multiphysics()`, not `coupling()`. | `examples/analytic/EcTCylinderTransient.java` |
| Thermal to structural | `multiphysics().create(..., "ThermalExpansion")` | Select all intended domains explicitly; thermal expansion is `thermalexpansioncoefficient`. | `examples/analytic/EcTSmCylinderStationary.java` |
| EM waves, freq domain | `ElectromagneticWaves` (emw) | Periodic `Port` (PortType=Periodic, SlitType=PECBacked, PortOrientation=ForwardPort, InputType=E, Eampl, n, alpha1_inc) + `PeriodicCondition` Floquet/FromPeriodicPort; `WaveEquationElectric` wee1 auto-created, DisplacementFieldModel=RefractiveIndex; **IdenticalMesh group1/group2** for periodic face pairs (required for Floquet); Freq study step plist+punit; S 参数 emw.S11/S21 (emw.S11dB/S21dB). | `examples/analytic/EmwSlabFrequency.java` |
| Revolve (rotated solid) | `Revolve` on a WorkPlane 2D sketch | `geom("geom1").create("rev1","Revolve")`; set `revolvefrom="workplane"`, `workplane="wp1"`, `angtype="full"`; `selection("input").set({"wp1"})`. Rotates a 2D cross-section into a 3D solid (e.g. hollow cylinder shells). | `examples/analytic/TRevolveStationary.java` |
| Nonlinear material (k(T)) | Material expression directly on `thermalconductivity` | `propertyGroup("def").set("thermalconductivity", "k0*(1+beta*(T-Tref))")` — a single expression string, not a String[][]; material property can reference the dependent variable `T`. Verify with a variable-transform analytical solution. | `examples/analytic/TmSlabNonlinear.java` |
| Geometry `Array` (linear pattern) | `geom("geom1").create("arr1","Array")` | `selection("input").set({"blk1"})`, `set("size",String[]{"n1","n2","n3"})`, `set("displ",String[]{"dx","dy","dz"})`. **Must `Union` the arrayed copies** (they are disjoint bodies) before solving a connected-physics model. | `examples/analytic/TFinArrayStationary.java` |
| 3D boolean `Difference` | `geom("geom1").create("diff1","Difference")` | `selection("input").set({"blk1"})`, `selection("input2").set({"cyl1"})` — 3D subtract of a solid from another (2D already validated). Faces/domains after the boolean are probed, never assumed. | `examples/analytic/SmPlateHoleStationary.java` |
| Eigenfrequency study | `study("std1").create("eig","Eigenfrequency")` | `set("neigsactive","on")` + `set("neigs","4")`. Read mode frequencies via a `PlotGroup1D` `"Global"` plot of `freq` (no xdataexpr → default mode index x-axis) + `"Plot"` export. | `examples/analytic/SmCantileverEigenfrequency.java` |
| PlotGroup3D surface plot + Image export | `result().create("pg3","PlotGroup3D")` + `export().create("img1","Image")` | Surface plot on a 3D model must use `PlotGroup3D` (a 2D plot group needs a 2D dataset). Image export needs an **absolute** filename path; do not set `size`/`width`/`height` (they reject). | `examples/analytic/TFinArrayStationary.java` |

For exact feature tags, properties, study creation, datasets, and exports, use `case-naming.md`, then inspect the full reference code.

## Common pitfalls (validated)

Interface and feature names deviate from the common-sense defaults:

- `ElectricCurrents` → `ConductiveMedia` (electric currents); `Electrostatics` for electrostatics.
- `HeatTransferInSolids` → `HeatTransfer` (pure solid heat transfer).
- Boundary features are `TemperatureBoundary` / `HeatFluxBoundary`, not `Temperature` / `HeatFlux`.
- The transient study step is `Transient`, not `TimeDependent`.
- Couplings are created via `multiphysics().create(...)`, not `coupling()`.

Selections, materials, and naming:

- A voltage terminal needs explicit `TerminalType="Voltage"`; otherwise the terminal defaults to charge type and the solve returns a trivial field.
- Global parameter names must not collide with a physics feature name (a colliding parameter resolves to 0).
- Material properties: `electricconductivity`, `thermalconductivity`, `density`, `heatcapacity`, `relpermittivity`, and `thermalexpansioncoefficient` (a 9-component vector).
- `ThermalExpansion` must be given an explicit selection of all intended domains; otherwise it selects none and stresses vanish.
- With `ConductiveMedia`, `CurrentConservation` needs `relpermittivity` as well as conductivity.
- Domain numbering after a `Difference` is not intuitive — derive entity numbers from geometry probing instead of assuming.

Runtime behavior:

- `model.save()` without a path fails ("No filename given.") — always save with `save(path)`.
- `Square.size` is a scalar (side length), not a vector.
- `EvalGlobal` evaluates global expressions only; a domain quantity must be exported as data.
- On a transient PARDISO timeout, reduce the mesh and the number of time steps rather than raising the limit.
- A global parameter must not be named `h` (a COMSOL built-in) — the solve fails with "Duplicate parameter/variable name. Variable: h". Use e.g. `h_conv` (validated on `TFinArrayStationary`).
- SolidMechanics displacement variables are `u`, `v`, `w` — without a `solid.` prefix. Exporting `solid.u` fails with "Undefined variable" (validated on `SmCylinderAxialStationary` and `SmCantileverEigenfrequency`).
- `neigsactive` on an Eigenfrequency study step takes `"on"`/`"off"`, not `"log"` (validated on `SmCantileverEigenfrequency`).

## Advanced recipes (geometry / material / mesh)

- **WorkPlane nested 2D**: `geom("geom1").create("wp1","WorkPlane")`, set `quickplane` (e.g. `"xz"`), then build the 2D sequence on `feature("wp1").geom()`. Fillet vertex selection targets objects with a `(1)` suffix (e.g. `selection("point").set("dif1(1)", new int[]{3})`). Extrude input: `selection("input").set(new String[]{"wp1"})`.
- **`Enu` material model**: `material.materialModel().create("Enu", "YoungsModulusAndPoissonsRatio")` then `propertyGroup("Enu").set("E", ...)`. With multiple materials, `lemm1` reads `E_mat=from_mat` from the material.
- **Local mesh refinement**: create `FreeTet` (`mesh("mesh1").create("ftet1","FreeTet")`), then a child `Size` on a domain selection with `size.selection().geom("geom1", 3).set(int[])` — the geometry and dimension are required (`selection().set(int[])` alone fails with "No entity dimension specified").
- **Revolve curved-face sampling**: on surfaces produced by `Revolve`, `faceX` at a parameter point outside the (possibly non-rectangular) parameter domain throws "Face parameter out of range". Wrap multi-point `faceX` sampling in try/catch and skip out-of-range points (validated on `TRevolveStationary`).
- **Nonlinear material expressions**: a material property such as `thermalconductivity` may be set to a single expression string referencing the dependent variable (`"k0*(1+beta*(T-Tref))"`). Match the analytical validation with a variable transform when the property is nonlinear in the dependent variable.
- **Derived values in batch**: `result().numerical().create("av1","AvVolume")` builds and serializes correctly, but `getReal()` returns an empty table `[[0.0]]` in a batch context (even after `computeResult()`/`getReal(true)`). Compute domain averages offline from the exported field CSV instead; a `"Global"` plot also cannot evaluate a spatial field variable (only global scalars such as S-parameters).

Search the reference examples before substituting an unverified interface or property name.
