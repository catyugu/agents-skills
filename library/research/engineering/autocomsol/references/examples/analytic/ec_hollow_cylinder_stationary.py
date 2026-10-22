#!/usr/bin/env python3
"""
ec_hollow_cylinder_stationary.py — 案例 EcHollowCylinderStationary 验证
(3D 空心圆柱导电稳态 + 二阶几何网格导出)

案例: EcHollowCylinderStationary (src/analytic/EcHollowCylinderStationary.java)。
物理: ConductiveMedia (ec), 内表面 Terminal V0=1V, 外表面 Ground, 端面默认绝缘。
      几何 外圆柱 r_out=0.03 减内圆柱 r_in=0.01, 高 hcyl=0.02; 网格 FreeTet hmax=3mm。
解析解: V(r) = V0·ln(r/r_out)/ln(r_in/r_out) (电流密度仅径向)。

本脚本同时验证**导出网格的几何阶次** (mesh.mphtxt 与 CSV 同目录):
  - 导出单元类型必须是二阶几何单元 edg2/tri2/tet2 (每单元 3/6/10 节点);
    线性 (edg/tri/tet) 说明几何形状阶次被降到 linear, 或几何没有曲面实体。
  - 曲面上的二阶单元**边中点**必须落在解析圆柱面上: |r-R|/R < 1e-6
    (实测 2.3e-16)。弦中点 (线性几何的代表) 偏离量为 R(1-cos(θ/2)) ≈ 1e-4,
    两者相差 1e4 倍以上, 因此该阈值可区分"真 P2 几何"与"仅补了中点"。
  - 同一组边的中点/弦中点偏离比 < 1e-2 (实测 4.1e-8), 即中点确实贴曲面而非弦中点。

检查:
  - mesh_quadratic_types: 单元类型集 = {vtx, edg2, tri2, tet2}
  - mesh_nodes_per_elem: 每单元节点数 1/3/6/10
  - mid_nodes_on_cylinder: 内/外圆柱面上 tri2 边中点 |r-R|/R < 1e-6
  - mid_not_chord: 中点偏离 / 弦中点偏离 < 1e-2
  - V_profile: 全部采样点 max|V-V_ana(r)| < 1e-3 V (实测 3.1e-4)
  - V_boundary: 内表面 V=V0, 外表面 V=0 (|dev| < 1e-6)
  - axisymmetry: 同半径桶内 V 散布 < 0.02 V (实测 7.4e-3)

用法:
    python ec_hollow_cylinder_stationary.py <csv-path> <json-out> <md-out>
"""
import math
import os
import re
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from common import CASE_KEY, check, emit_report, load_csv

CASE_KEY = "EcHollowCyl"

# ---- 物理/几何参数 (与 EcHollowCylinderStationary.java 一致) ----
R_IN, R_OUT, V0 = 0.01, 0.03, 1.0
LOG_RATIO = math.log(R_IN / R_OUT)

# ---- 二阶几何判定阈值 ----
MID_DEV_REL_MAX = 1e-6      # 边中点相对解析圆柱面的偏离 (实测 2.3e-16)
SAGITTA_RATIO_MAX = 1e-2    # 中点偏离 / 弦中点偏离 (实测 4.1e-8)
CHORD_MIN = 1e-9            # 弦中点偏离小于此值的边不参与比值检查 (退化边)


def v_analytic(r):
    """空心圆柱导电稳态解析电位 V(r)。"""
    return V0 * np.log(np.asarray(r, float) / R_OUT) / LOG_RATIO


def parse_mphtxt(path):
    """解析 COMSOL .mphtxt: 返回 (vertices, {type_name: (nodes_per_elem, elements)})。

    块格式: "<npe> <name> # type name" 之后是 "# number of vertices per element" /
    "# number of elements" / "# Elements" + 每行一个单元的节点索引 (二阶单元的
    中点节点排在角点之后)。
    """
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    nv = None
    for l in lines:
        m = re.match(r"^\s*(\d+)\s+#\s*number of mesh vertices", l)
        if m:
            nv = int(m.group(1))
            break
    if nv is None:
        raise ValueError("no vertex count in mphtxt")
    start = lines.index("# Mesh vertex coordinates") + 1
    verts = np.array(
        [[float(x) for x in lines[start + k].split()] for k in range(nv)], float
    )

    types = {}
    for i, l in enumerate(lines):
        m = re.match(r"^\s*(\d+)\s+(\w+)\s+#\s*type name\s*$", l)
        if not m:
            continue
        name = m.group(2)
        j = i + 1
        while "# number of vertices per element" not in lines[j]:
            j += 1
        npe = int(lines[j].split()[0])
        while "# number of elements" not in lines[j]:
            j += 1
        nel = int(lines[j].split()[0])
        while lines[j].strip() != "# Elements":
            j += 1
        types[name] = (npe, [[int(v) for v in lines[j + 1 + k].split()] for k in range(nel)])
    return verts, types


def check_mesh_order(verts, types, checks):
    """二阶几何单元类型 + 边中点贴曲面检查。"""
    names = set(types)
    want = {"vtx", "edg2", "tri2", "tet2"}
    checks.append(
        check("mesh_quadratic_types", names == want, sorted(names),
              "types == {vtx, edg2, tri2, tet2}", "")
    )

    npe_want = {"vtx": 1, "edg2": 3, "tri2": 6, "tet2": 10}
    npe_ok = all(types[n][0] == npe_want[n] for n in names & want) and names == want
    checks.append(
        check("mesh_nodes_per_elem", npe_ok,
              {n: types[n][0] for n in sorted(names)},
              "vtx/edg2/tri2/tet2 = 1/3/6/10 nodes", "")
    )
    if "tri2" not in types:
        return

    tris = types["tri2"][1]
    for R, label in ((R_IN, "inner"), (R_OUT, "outer")):
        devs, ratios = [], []
        for e in tris:
            r_corner = np.hypot(verts[e[:3], 0], verts[e[:3], 1])
            if not np.all(np.abs(r_corner - R) < 1e-9):
                continue
            for k in range(3):
                a, b, mid = e[k], e[(k + 1) % 3], e[3 + k]
                r_mid = math.hypot(verts[mid][0], verts[mid][1])
                chord = (verts[a] + verts[b]) / 2
                r_chord = math.hypot(chord[0], chord[1])
                devs.append(abs(r_mid - R) / R)
                if abs(r_chord - R) > CHORD_MIN:
                    ratios.append(abs(r_mid - R) / abs(r_chord - R))
        if not devs:
            checks.append(check(f"mid_nodes_on_{label}", False, None,
                                f"tri2 faces at r={R} with corners on the cylinder", ""))
            continue
        checks.append(
            check(f"mid_nodes_on_{label}", max(devs) < MID_DEV_REL_MAX,
                  max(devs), f"max |r(mid)-R|/R < {MID_DEV_REL_MAX:g} at R={R}", "")
        )
        checks.append(
            check(f"mid_not_chord_{label}", max(ratios) < SAGITTA_RATIO_MAX,
                  max(ratios),
                  f"max |r(mid)-R|/|r(chord)-R| < {SAGITTA_RATIO_MAX:g} at R={R}", "")
        )


def check_field(rows, checks):
    """解析电位剖面 / 边界值 / 轴对称性检查。"""
    arr = np.array(rows, float)
    r = np.hypot(arr[:, 0], arr[:, 1])
    v = arr[:, 3]

    dev = np.abs(v - v_analytic(r))
    checks.append(
        check("V_profile", dev.max() < 1e-3, float(dev.max()),
              "max|V - V0*ln(r/r_out)/ln(r_in/r_out)| < 1e-3 V", "V")
    )

    for R, target, label in ((R_IN, V0, "V_inner_wall"), (R_OUT, 0.0, "V_outer_wall")):
        sel = np.abs(r - R) < 1e-9
        wdev = float(np.abs(v[sel] - target).max()) if sel.any() else float("inf")
        checks.append(
            check(label, wdev < 1e-6, wdev, f"max|V({R}) - {target}| < 1e-6 V", "V")
        )

    buckets = {}
    for rr, vv in zip(np.round(r, 4), v):
        buckets.setdefault(rr, []).append(vv)
    spreads = [max(x) - min(x) for x in buckets.values() if len(x) > 1]
    checks.append(
        check("axisymmetry", max(spreads) < 0.02, max(spreads),
              "max V spread per radius bucket < 0.02 V", "V")
    )


def main():
    if len(sys.argv) < 4:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    csv_path, json_out, md_out = sys.argv[1], sys.argv[2], sys.argv[3]
    mesh_path = os.path.join(os.path.dirname(csv_path), "mesh.mphtxt")

    headers, rows = load_csv(csv_path)
    checks = []
    if len(rows) == 0:
        checks.append(check("data_present", False, None, "non-empty CSV", "rows"))
        return emit_report(CASE_KEY, csv_path, checks, json_out, md_out)

    check_field(rows, checks)

    mesh_counts = None
    if not os.path.exists(mesh_path):
        checks.append(check("mesh_exported", False, mesh_path, "mesh.mphtxt exists", ""))
    else:
        verts, types = parse_mphtxt(mesh_path)
        check_mesh_order(verts, types, checks)
        mesh_counts = {n: len(types[n][1]) for n in sorted(types)}

    evidence = {
        "analytic": "V(r) = V0*ln(r/r_out)/ln(r_in/r_out), coaxial cylinder "
                    "with insulated end faces",
        "mesh": mesh_counts,
    }
    return emit_report(CASE_KEY, csv_path, checks, json_out, md_out, evidence=evidence)


if __name__ == "__main__":
    sys.exit(main())
