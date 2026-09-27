# Validated physics API recipes

Use these as local COMSOL 6.2 evidence. Read the linked full Java case before applying a recipe.

| Scope | Interface / coupling | Important evidence | Full reference |
| --- | --- | --- | --- |
| Electrostatics | `Electrostatics` | Explicit `TerminalType="Voltage"` is required for a voltage terminal. | `examples/analytic/EcSquareStationary.java` |
| Electric currents | `ConductiveMedia` | `ElectricCurrents` was not registered locally; use `electricconductivity`, and use `relpermittivity` where required. | `examples/analytic/EcSquareStationary.java` |
| Solid heat transfer | `HeatTransfer` | Boundary features use `TemperatureBoundary` and `HeatFluxBoundary`; material properties include `thermalconductivity`, `density`, `heatcapacity`. | `examples/analytic/TCylinderStationary.java` |
| Convective heat flux (Robin) | `HeatFluxBoundary` + `HeatFluxType="ConvectiveHeatFlux"` | The ambient temperature is the `Text` property (`Text_src` accepts only `userdef`); coefficient is `HeatTransferCoefficientType="UserDef"` + `h`. `minput_temperature`/`minput_temperature_src` are accepted and listed by `properties()` but do not enter the flux. | `examples/analytic/TFinArrayStationary.java` |
| Solid mechanics | `SolidMechanics` | Use `Fixed`, `Roller`, and `BoundaryLoad`; configure `lemm1` elastic properties deliberately. | `examples/analytic/SmPlateHoleStationary.java` |
| Electric to thermal | `multiphysics().create(..., "ElectromagneticHeating")` | Set `EMHeat_physics` and `Heat_physics`; use `multiphysics()`, not `coupling()`. | `examples/analytic/EcTCylinderStationary.java` |
| Thermal to structural | `multiphysics().create(..., "ThermalExpansion")` | Select all intended domains explicitly; thermal expansion is `thermalexpansioncoefficient`. | `examples/analytic/EcTSmCubeTransient.java` |
| EM waves, freq domain | `ElectromagneticWaves` (emw) | Periodic `Port` (set `PortType=Periodic`, `Eampl`, `n`, `alpha1_inc`; `SlitType`/`PortOrientation`/`InputType` already default to `PECBacked`/`ForwardPort`/`E`) + `PeriodicCondition` Floquet/FromPeriodicPort; `WaveEquationElectric` wee1 auto-created, DisplacementFieldModel=RefractiveIndex; **IdenticalMesh group1/group2** for periodic face pairs (required for Floquet); Freq study step plist+punit; S 参数 emw.S11/S21 (emw.S11dB/S21dB). | `examples/analytic/EmwSlabSweepFrequency.java` |
| Revolve (rotated solid) | `Revolve` on a WorkPlane 2D sketch | `geom("geom1").create("rev1","Revolve")`; set `revolvefrom="workplane"`, `workplane="wp1"`, `angtype="full"`; `selection("input").set({"wp1"})`. Rotates a 2D cross-section into a 3D solid (e.g. hollow cylinder shells). | `examples/analytic/TRevolveStationary.java` |
| Nonlinear material (k(T)) | Material expression directly on `thermalconductivity` | `propertyGroup("def").set("thermalconductivity", "k0*(1+beta*(T-Tref))")` — a single expression string, not a String[][]; material property can reference the dependent variable `T`. Verify with a variable-transform analytical solution. | `examples/analytic/TmSlabNonlinear.java` |
| Geometry `Array` (linear pattern) | `geom("geom1").create("arr1","Array")` | `selection("input").set({"blk1"})`, `set("size",String[]{"n1","n2","n3"})`, `set("displ",String[]{"dx","dy","dz"})`. **Must `Union` the arrayed copies** (they are disjoint bodies) before solving a connected-physics model. | `examples/analytic/TFinArrayStationary.java` |
| 3D boolean `Difference` | `geom("geom1").create("diff1","Difference")` | `selection("input").set({"blk1"})`, `selection("input2").set({"cyl1"})` — 3D subtract of a solid from another (2D already validated). Faces/domains after the boolean are probed, never assumed. | `examples/analytic/SmPlateHoleStationary.java` |
| Eigenfrequency study | `study("std1").create("eig","Eigenfrequency")` | `set("neigsactive","on")` + `set("neigs","4")`. Read mode frequencies via a `PlotGroup1D` `"Global"` plot of `freq` (no xdataexpr → default mode index x-axis) + `"Plot"` export. | `examples/analytic/SmCantileverEigenfrequency.java` |
| Element order (p-refinement) | `physics().prop("ShapeProperty").set("order_<field>", "<code>")` — `Physics.prop(String)` returns a `PhysicsProp` (no reflection needed). The key is `order_` + the interface's own field name (`physics().field(tag).field()`, e.g. `displacement`, `electricfield`); component names are not keys. `getAllowedPropertyValues(key)` enumerates the accepted codes, and they differ per interface family. | A vector field's components are declared by the interface (`displacement` → `[u,v,w]`, `electricfield` → `[Ex,Ey,Ez]`), never set per component. Lagrange families (SolidMechanics, HeatTransfer, PressureAcoustics) take `1,2,2s,3,3s,4,4s,5`; Electrostatics/ConductiveMedia take `1,2,3,4,5` (no `s`); emw takes curl-element codes `1,1t2,2,2t2,…,7,7t2`; Shell only `1,2`; LaminarFlow exposes a single `order_fluid`. `s` = serendipity (equal to Lagrange on a simplex, fewer dofs on a quad/hex); `t2` = curl type 2 (different on a simplex too: 3D tet emw dofs 894 at `2` vs 1341 at `2t2`). The solver log prints "Quadratic Lagrange" for both `2` and `2t2` — compare dofs, not the log line. Higher p converges as O(h^(p+1)); quadratic Lagrange on a coarse mesh already reaches machine precision for bending stresses. | `examples/demonstration/FieldDiscretizationDemonstration.java` (API contract), `examples/analytic/SmCantileverBendingStationary.java` (numerical effect) |
| PlotGroup3D surface plot + Image export | `result().create("pg3","PlotGroup3D")` + `export().create("img1","Image")` | Surface plot on a 3D model must use `PlotGroup3D` (a 2D plot group needs a 2D dataset). Image export needs an **absolute** filename path; do not set `size`/`width`/`height` (they reject). | `examples/analytic/TFinArrayStationary.java` |
| Geometry shape order (P2 mesh export) | `component().sorder("automatic"/"linear"/"quadratic"/"cubic"/"quartic")` + `result().export().create(tag,"Mesh")` | Default is `automatic`. Curved geometry then exports **second-order geometry elements** (`edg2`/`tri2`/`tet2`, 3/6/10 nodes per element, mid nodes after the corners); `sorder("linear")` exports `edg`/`tri`/`tet`. Purely planar geometry stays linear even with `sorder("quadratic")`, and `cubic`/`quartic` still export P2 — the Mesh export caps at second order. | `examples/analytic/EcHollowCylinderStationary.java` |

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
- A boolean splits each original surface into several faces (a `Difference` of two cylinders leaves 4 patches per cylinder face). A boundary feature that selects only one patch covers a fraction of the intended boundary and the field is wrong by O(0.5) with **no error from COMSOL** — collect every face at the target radius (`facesAtRadius` returning `int[]`), never a single first match.

Silently inert properties:

- A convective `HeatFluxBoundary` takes its ambient temperature from `Text`, not from `minput_temperature`. Both are legal properties — `properties()` lists both, `set()` accepts both, and `minput_temperature_src` accepts `root.<comp>.<field>`, `userdef` and `fromCommonDef` — but only `Text` enters the flux: setting `minput_temperature` alone leaves the ambient at the 293.15 K default with a clean solve and no warning. `Text_src` accepts only `userdef`, so `Text` is always the user expression. GUI-serialized models carry both keys, with `minput_temperature` left at `293.15[K]` while `Text` holds the physical ambient expression — read `Text` when mining a model for its ambient temperature. Validated on 6.2 with a 1D conduction slab (probe, see `api-validation-probes.md`).

Writing a value that is already the default:

- Read the current value before writing it: `getString(key)` on a freshly created feature returns the interface default, so a `set(key, <that value>)` is noise and should be dropped. Verified defaults on 6.2: `HeatFluxBoundary.HeatTransferCoefficientType` = `UserDef`; `TemperatureBoundary.T0_src` = `userdef`; `ThermalExpansion.alpha_mat` = `from_mat`; `Union.intbnd` = `on`; emw `Port` `SlitType`/`PortOrientation`/`InputType` = `PECBacked`/`ForwardPort`/`E` (these three survive `PortType=Periodic` unchanged).
- Properties whose default differs and therefore must be written: `HeatFluxBoundary.HeatFluxType` (`GeneralInwardHeatFlux`), `h` (0), `Text` (293.15 K); `TemperatureBoundary.T0` (293.15 K); `Terminal.TerminalType` (charge type, not `Voltage`); `ThermalExpansion.minput_strainreferencetemperature_src` (`fromCommonDef`).
- Removing such a call is not a refactor to reason about: the contract layer shows the default, and a case whose analytic check still passes at the same tolerance confirms it. Do both — the contract read alone cannot tell an inert property from an effective one.

Runtime behavior:

- `model.save()` without a path fails ("No filename given.") — always save with `save(path)`.
- `Square.size` is a scalar (side length), not a vector.
- `EvalGlobal` evaluates global expressions only; a domain quantity must be exported as data.
- On a transient PARDISO timeout, reduce the mesh and the number of time steps rather than raising the limit.
- A global parameter must not be named `h` (a COMSOL built-in) — the solve fails with "Duplicate parameter/variable name. Variable: h". Use e.g. `h_conv` (validated on `TFinArrayStationary`).
- SolidMechanics displacement variables are `u`, `v`, `w` — without a `solid.` prefix. Exporting `solid.u` fails with "Undefined variable" (validated on `SmCantileverEigenfrequency`).
- `neigsactive` on an Eigenfrequency study step takes `"on"`/`"off"`, not `"log"` (validated on `SmCantileverEigenfrequency`).
- To set the Lagrange polynomial order on a physics (p-refinement), call `physics().prop("ShapeProperty").set("order_<field>", "<code>")`. The key suffix is the interface's own field name — `order_displacement` for SolidMechanics, `order_temperature`, `order_electricpotential`, `order_electricfield` (emw), `order_magneticvectorpotential` (ACDC magnetics), `order_fluid` (LaminarFlow) — and a component name is not a key (`order_u` → "Unknown parameter"). Two interfaces use a bare `shapeorder` instead: Beam Envelopes and the BEM interfaces (values `p11,p21,p22,p32,…`). Always read `getAllowedPropertyValues(key)` before setting: SolidMechanics accepts `"1"`, `"2"`, `"2s"`, `"3"`, `"3s"`, `"4"`, `"4s"`, `"5"` (`s` = serendipity), emw accepts the curl codes `1,1t2,2,2t2,…,7t2`, Electrostatics/ConductiveMedia accept only `1..5`, and Shell only `1,2` (validated on `FieldDiscretizationDemonstration`). `"order"` on `SolidMechanics` is rejected (validated on `SmCantileverBendingStationary`).
- Geometry shape order and physics order are two different knobs: `component().sorder(...)` controls the mesh geometry (hence the element order in an exported `.mphtxt`), while `physics().prop("ShapeProperty").set("order_<field>", ...)` controls the solution's degrees of freedom. Only the former changes the exported mesh. Numeric strings are rejected by `sorder` (`"2"` → "Invalid geometry shape function"); use `"linear"`/`"quadratic"`/`"cubic"`/`"quartic"`/`"automatic"` (validated on `EcHollowCylinderStationary`).
- The solver log does not distinguish the emw curl codes: `order_electricfield` `"2"` and `"2t2"` both print "Geometry shape function: Quadratic Lagrange". Judge which family was used from the degree-of-freedom count in `batch.log` instead (validated on `FieldDiscretizationDemonstration`).
- A `Mesh` export (`result().export().create(tag,"Mesh")`) **silently writes nothing** when the model has no solved dataset: `run()` raises no exception and no file appears. Run the study first (validated on `EcHollowCylinderStationary`).

## Advanced recipes (geometry / material / mesh)

- **WorkPlane nested 2D**: `geom("geom1").create("wp1","WorkPlane")`, set `quickplane` (e.g. `"xz"`), then build the 2D sequence on `feature("wp1").geom()`. Fillet vertex selection targets objects with a `(1)` suffix (e.g. `selection("point").set("dif1(1)", new int[]{3})`). Extrude input: `selection("input").set(new String[]{"wp1"})`.
- **`Enu` material model**: `material.materialModel().create("Enu", "YoungsModulusAndPoissonsRatio")` then `propertyGroup("Enu").set("E", ...)`. With multiple materials, `lemm1` reads `E_mat=from_mat` from the material.
- **Local mesh refinement**: create `FreeTet` (`mesh("mesh1").create("ftet1","FreeTet")`), then a child `Size` on a domain selection with `size.selection().geom("geom1", 3).set(int[])` — the geometry and dimension are required (`selection().set(int[])` alone fails with "No entity dimension specified").
- **Revolve curved-face sampling**: on surfaces produced by `Revolve`, `faceX` at a parameter point outside the (possibly non-rectangular) parameter domain throws "Face parameter out of range". Wrap multi-point `faceX` sampling in try/catch and skip out-of-range points (validated on `TRevolveStationary`).
- **Nonlinear material expressions**: a material property such as `thermalconductivity` may be set to a single expression string referencing the dependent variable (`"k0*(1+beta*(T-Tref))"`). Match the analytical validation with a variable transform when the property is nonlinear in the dependent variable.
- **Derived values in batch**: `result().numerical().create("av1","AvVolume")` builds and serializes correctly, but `getReal()` returns an empty table `[[0.0]]` in a batch context (even after `computeResult()`/`getReal(true)`). Compute domain averages offline from the exported field CSV instead; a `"Global"` plot also cannot evaluate a spatial field variable (only global scalars such as S-parameters).
- **Reading an exported `.mphtxt`**: the vertex table follows `<n> # number of mesh vertices` + `# Mesh vertex coordinates`; each element block is `<npe> <name> # type name`, then `# number of vertices per element`, `# number of elements`, `# Elements` and one node-index line per element. Second-order mid nodes follow the corner nodes (`tri2`: corners 0,1,2 then the mids of edges (0,1),(1,2),(2,0)). Verified offline in `scripts/verifications/analytic/ec_hollow_cylinder_stationary.py`.

Search the reference examples before substituting an unverified interface or property name.
