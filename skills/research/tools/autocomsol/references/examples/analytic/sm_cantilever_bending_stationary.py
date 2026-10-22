#!/usr/bin/env python3
"""
sm_cantilever_bending_stationary.py — SmCantileverBendingStationary 验证脚本

案例: 2D 悬臂梁纯弯曲 (SolidMechanics, p=2 二次 Lagrange)
几何: 2D 矩形梁 L=10, h=2, 中心 (0,0); 左端 Fixed, 右端 σ_xx(y)=M·y/I 分布载荷
解析解: σ_xx(y) = M·y/I = 1.5·y (线性, 二次 Lagrange → 机器精度级 ~3e-10)
       σ_yy=0, σ_xy=0 (无横向载荷); u_y ≠ 0 (plane stress 自由 ε_yy, ν=0 时也存在)
"""
import os
import sys
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from common import check, emit_report, load_csv


def main():
    if len(sys.argv) < 4:
        print("usage: sm_cantilever_bending_stationary.py <csv> <json-out> <md-out>", file=sys.stderr)
        sys.exit(2)
    csv_path = sys.argv[1]
    json_out = sys.argv[2]
    md_out = sys.argv[3]

    headers, rows = load_csv(csv_path)
    if not rows:
        print(f"ERROR: empty CSV {csv_path}", file=sys.stderr)
        sys.exit(1)
    arr = np.array(rows)

    # 列约定: x,y,u,v,mises,sx,sy,sxy (与 Java export expr 一致)
    x, y, u, v = arr[:, 0], arr[:, 1], arr[:, 2], arr[:, 3]
    sx, sy, sxy = arr[:, 5], arr[:, 6], arr[:, 7]

    # 关键判据 1: σ_xx 全场 max|σ_xx - 1.5y| ≤ 1e-6 (二次 Lagrange 机器精度 ~3e-10)
    err_sx = np.abs(sx - 1.5 * y)
    sx_uniform = check("sx_uniform_max_err", err_sx.max() < 1e-6,
                       value=float(err_sx.max()), threshold=1e-6,
                       unit="Pa (vs analytic 1.5y)")

    # 关键判据 2: 中段纯弯曲 (σ_xx 不依赖 x, 在 y=+1 上多 x 位置应一致)
    mid = arr[(x >= -3) & (x <= 3)]
    sx_top_mid = mid[np.abs(mid[:, 1] - 1.0) < 0.01, 5]
    sx_x_std = float(sx_top_mid.std()) if len(sx_top_mid) > 1 else 0.0
    sx_pure_bending = check("sx_pure_bending_x_std_mid", sx_x_std < 0.01,
                            value=sx_x_std, threshold=0.01, unit="Pa")

    # 关键判据 3: u_x 沿 y 在 x=4 处线性 (Euler-Bernoulli 极限, p=2 应机器精度)
    mask = (np.abs(x - 4.0) < 0.01) & (np.abs(y) > 0.05)
    if mask.any():
        sub = arr[mask]
        ys_, us_ = sub[:, 1], sub[:, 2]
        A = np.vstack([ys_, np.ones_like(ys_)]).T
        coef, *_ = np.linalg.lstsq(A, us_, rcond=None)
        resid = us_ - (coef[0] * ys_ + coef[1])
        ux_resid = float(np.abs(resid).max())
    else:
        ux_resid = float("inf")
    ux_linear = check("ux_linear_resid_x4", ux_resid < 1e-6,
                      value=ux_resid, threshold=1e-6, unit="m (linear fit residual)")

    # 关键判据 4: 中段 (y=0 附近) σ_yy, σ_xy 应近似为 0
    mid_y0 = mid[np.abs(mid[:, 1]) < 0.01]
    syy_mid = float(np.abs(mid_y0[:, 6]).max()) if len(mid_y0) else float("inf")
    sxy_mid = float(np.abs(mid_y0[:, 7]).max()) if len(mid_y0) else float("inf")
    syy_small = check("syy_small_midline", syy_mid < 1e-6,
                      value=syy_mid, threshold=1e-6, unit="Pa")
    sxy_small = check("sxy_small_midline", sxy_mid < 1e-6,
                      value=sxy_mid, threshold=1e-6, unit="Pa")

    checks_list = [sx_uniform, sx_pure_bending, ux_linear, syy_small, sxy_small]
    emit_report(
        case_key="SmCantileverBendingStationary",
        csv_path=csv_path,
        checks=checks_list,
        json_out=json_out,
        md_out=md_out,
        evidence={
            "physics": "SolidMechanics (2D plane stress, ν=0)",
            "p_order": "2 (Quadratic Lagrange; ShapeProperty.order_displacement)",
            "geometry": "2D Rectangle L=10, h=2, 中心 (0,0)",
            "loads": "Fixed at x=-L/2; BoundaryLoad ForceArea FperArea=[M·y/I,0,0] at x=+L/2 (端部弯矩精确等效)",
            "analytic": {
                "sigma_xx": "M·y/I = 1.5·y  (M=1, I=h³/12=2/3)",
                "sigma_yy": "0  (no transverse load)",
                "sigma_xy": "0  (no shear)",
                "u_y": "non-zero (plane stress 自由 ε_yy, ν=0 仍有)",
            },
            "n_points": int(len(arr)),
            "metric": {
                "sigma_xx max_err": f"{err_sx.max():.3e} (机器精度 ~3e-10, p=2 直接体现)",
                "sigma_xx mid x_std": f"{sx_x_std:.3e} (纯弯曲判据)",
                "u_x resid at x=4": f"{ux_resid:.3e} (Euler-Bernoulli 线性)",
                "sigma_yy midline max": f"{syy_mid:.3e}",
                "sigma_xy midline max": f"{sxy_mid:.3e}",
            },
        },
    )


if __name__ == "__main__":
    main()
