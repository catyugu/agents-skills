# Deterministic geometry selection

Do not hard-code entity numbers. Create geometry, call `geom.run()`, then derive the entities needed by physics selections from `GeomInfo`.

## 2D edges

For each edge from `1` to `geom.getNEdges()`, call `edgeX(edge, new double[] {0.5})`. Classify the sampled midpoint by coordinate tolerance, then use the resulting edge IDs in `selection().set(...)`.

Validated square ordering happened to be left=1, bottom=2, top=3, right=4. This is evidence for `edgeX`, not a numbering convention to reuse.

## 3D faces

Call `faceParamRange(face)` before `faceX`. Use the midpoint of each returned parameter interval, and sample multiple points where a curved or segmented surface is possible.

- Planar faces: classify a constant x, y, or z coordinate.
- Cylindrical walls: classify with `x*x + y*y` near the target radius; a cylinder wall may be partitioned into multiple faces.
- Use all matching face IDs for a physical boundary such as an inner or outer cylindrical wall.

## Domains and exterior faces

- `geom.getUpDown()[1]` provides the face-to-domain relation used in the validated multi-domain cases.
- `geom.getAdj(2, 3)` identifies face/domain adjacency: one adjacent domain indicates an exterior face, two indicate an interior interface.
- Combine adjacency with face samples or domain-level geometric measures to classify materials and contacts.

Read `examples/analytic/EcTCylinderStationary.java` for core/shell identification, and `examples/physical/EcTSmBusbarStationary.java` for exterior-face and bolt-end identification.

## Ordering caveat

Face numbering is not consistently ordered across `getAdj(2, 3)`, `getUpDown()`, and
`faceX()` enumeration. On some geometries the adjacency array can disagree with the face
samples (validated on the emw slab). When the two conflict, classify faces by their
sampled face-center coordinates instead of by adjacency counts — do not trust either
ordering to match physics selection numbering.

## Curved / Revolve surfaces

Surfaces produced by `Revolve` may have a non-rectangular parameter domain: a sampled
`faceX` point can fall outside the domain and throw "Face parameter out of range"
(validated on `TRevolveStationary`). When sampling many points across a face, wrap
`faceX` in try/catch and skip out-of-range samples rather than letting one point abort
the whole classification pass.

## Arrays and booleans change entity numbering

- `Array` (and any boolean that partitions or replicates solids) creates **multiple
  disjoint bodies**; `Union` with `intbnd="on"` merges them into connected domains while
  keeping internal boundaries. Entity numbering after these steps is never intuitive —
  probe `GeomInfo` (faces by sampled center coordinate, domains by bounding-box volume)
  exactly as for any other geometry (validated on `TFinArrayStationary`,
  `SmPlateHoleStationary`).
- A symmetric load case is modeled with `Roller` on the symmetry faces; the roller
  selection comes from sampled face coordinates, and the stress singularity at the
  curved-hole/roller junction (and at the load-edge/symmetry junction) is confined to a
  small boundary layer — exclude it from the verification clean zone
  (validated on `SmPlateHoleStationary`).
