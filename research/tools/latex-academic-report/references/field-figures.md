# Field-distribution figures in a report

For figures that show a computed field (temperature, displacement, stress, potential) over a model:
what to draw, how to place the cut, and how to get the values onto the mesh.

## Raw samples are not a figure

A scatter of the exported sample points is not acceptable: the density follows the mesh, so the hot
spot reads as a cluster of dots and the geometry is unreadable. Interpolate and draw filled contours.

- Fails: triangulating the sample cloud alone (its convex hull fills the gap around a concave model);
  masking triangles by a global edge length or area (the refined region — exactly where the field
  matters — gets masked away); a cubic interpolator (overshoots outside the sampled range, inventing
  temperatures at the boundary); grid occupancy with dilation (a cheat, not an interpolation).
- Works: cut the model's own mesh with a plane and interpolate *on the cut*, then draw that with the
  report's usual 2D style. The mesh already carries the discretization, so the field over a cut element
  is the solver's own interpolant — no new approximation is introduced by the figure.
  - pyvista reads the solver's mesh export and slices it with a plane, optionally keeping the cut's
    boundary so the cut face is a closed polygon; matplotlib then draws it with the values mapped on
    the vertices. Field values are attached as point data on the full mesh and interpolate correctly
    through the slice.
- Keep the style the rest of the report uses: the same colormap family, a colorbar labelled with the
  field's unit, and a title naming the plane (`T, xy slice at z = 0 mm`) so the caption need not.
- Print the field's min and max when writing the figure and check them against the report's own stated
  values; a plotting pipeline that silently drops or rescales points changes the colorbar range only.
- Draw the several fields of one case as one consistent set: the same camera, background and view for
  every panel, one perceptually uniform colormap per field (temperature, potential, displacement), and
  each colorbar labelled with its own quantity and unit. A row of panels rendered independently reads
  as unrelated images, and the reader cannot compare them.

## A section plane can miss the feature the figure is about

- Read the geometry's own parameters for where the features sit before choosing the plane. A bolt, boss
  or channel lying at a different depth than the plane does not appear at all, and the figure then shows
  a hole where the reader expects a part — a defect the reader notices before any number.
- No single plane can show features spread along the cut's normal direction. When the interesting parts
  are offset in that direction, switch to a 3D view of the whole mesh coloured by the field and pick the
  camera so every feature is visible — including the ones on the far side, which may need a low or
  below-model elevation. Verify the render before shipping it.
- The export's rows are the mesh's *corner* nodes: neither its P2 nodes, nor its file order. The corner
  set is the unique node ids in the cells' first `n` columns (four of the ten a quadratic tet block
  writes), and its size is what the export's own row total should equal.
- Read the mesh file by its comments, not by line position: an element block is announced by a commented
  line (`4 tet2 # type name`), so find it by substring, never by a line ending, and take each count off
  its own comment line (`<n> # number of mesh vertices`). Header lengths differ between exports, and a
  fixed line index reads a wrong count whose failure surfaces somewhere unrelated.
- Match values to mesh nodes by rounded coordinate, then repair whatever is left with a nearest-node
  lookup and assert on the maximum repair *distance* (the mesh's own node spacing), not on the repair
  count. A vendor export can carry a handful of rows (well under a percent) that no node sits on;
  failing the figure over those throws away an otherwise correct pipeline.
  - Round the coordinates to integer keys and join the two sets with `np.unique(..., axis=0,
    return_inverse=True)`, then read each node's value through the inverse map. Comparing coordinate
    tuples through a structured or void dtype (`np.allclose` on a `.view(...)`) raises instead of
    matching, and sorting the sets lexicographically shifts every pairing after the first mismatch.
  - Build the grid on the corner array and renumber the cells into it with `np.searchsorted(corners,
    cells)`; the cells still index the full node list, so an un-renumbered connectivity draws the wrong
    geometry without any error. The locator's own `find_closest_point` takes a single point, so keep
    the nearest-node repair as a brute-force pass over the unmatched coordinates only.

- Fit the camera instead of guessing a zoom: set the position, focal point and up vector, then
  `reset_camera()` and a couple of percent of zoom. A hand-picked zoom clips the model's own corner and
  the panel looks deliberate while it is wrong. Trim the render's white border (the bounding box of the
  image's difference from white) before including it, so the panel carries no margin the layout pays for.
  - Re-render and re-trim whenever the layout size changes; a figure regenerated from an older script
    overwrites the trimmed file with an untrimmed one, and only the page render shows it.
- One colorbar label per field: pass the field name only and let the unit come from the export's column
  header, or the label prints the unit twice.
- Size the panels so the figure shares a page with the text that cites it. Four panels (temperature,
  potential, displacement, a history plot) as a 2x2 grid at about 0.4-0.45 `\linewidth` each do that;
  the same four as a wide row plus a second float cost a page of their own, and a chapter already
  carrying several floats needs them smaller still (0.31-0.36 each), or the float queue has no room and
  the figure lands below the next section's text — the float pitfall in SKILL.md covers that fight.

## Provenance

- The figure's data come from the case's own artifacts (mesh plus reference export), never from a fresh
  run and never hand-copied. Keep the plotting script with the report's scratch work, state its two
  inputs in the handover notes, and regenerate the figure from the script rather than editing an image.
- A regenerated figure replaces the old file under the same name and the LaTeX needs no change; delete
  the superseded asset so the case directory holds only what the report includes.
