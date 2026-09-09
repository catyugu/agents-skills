# Case 命名规则与物理场映射

## 命名规则

`<物理场缩略名><模型概述><Study类型>`

- 物理场缩略名: 见下表
- 模型概述: 几何/现象描述, 首字母大写 (如 Square, Cylinder, Cube, ThickCylinder)
- Study类型: Stationary / Transient / Eigenfrequency / Frequency 等, 首字母大写

## Tier 分层

案例按验证严谨度分两层，对应 `references/examples/` 下的子目录与 lab 的 `src/`、`scripts/verifications/` 同构:

- **analytic/** — 有解析解验证的案例（验证脚本含显式解析解）。新案例尽量进入此层。
- **physical/** — 无解析解、仅验证流程可运行性或大体物理合理性的案例（如母线板 `EcTSmBusbarStationary`、编译基线 `BaselineModel`）。

示例:

- `EcSquareStationary` — 2D 方板导电稳态
- `TCylinderStationary` — 3D 空心圆柱传热稳态
- `SmCylinderAxialStationary` — 3D 空心圆柱轴向拉伸稳态
- `EcTCubeTransient` — 3D 立方体电热耦合瞬态
- `TRingTransient` — 2D 圆环传热瞬态（对流 BC）
- `EcTCylinderTransient` — 3D 圆柱电热耦合瞬态
- `EcTCylinderStationary` — 3D 同轴双材料电热耦合稳态（焦耳热源 + 全对流）
- `EcTSmCylinderStationary` — 3D 同轴双材料稳态电→热→结构耦合（广义平面应变热应力）
- `EcTSmCubeTransient` — 3D 立方体瞬态电→热→结构耦合（1D slab 傅里叶级数 + 夹紧杆）
- `EcTSmBusbarStationary` — 3D L形铜母线+钛螺栓稳态电→热→结构耦合（贯穿螺栓仅外侧伸出, 三场, 7域）【physical tier】
- `EmwSlabFrequency` — 3D 介质平板频域电磁波 (emw) 垂直入射单频（Periodic 端口 + Floquet 周期, S11/S21）
- `EmwSlabSweepFrequency` — 3D 介质平板频域电磁波 (emw) 垂直入射扫频（2-3 GHz, S 参数随频率）
- `TRevolveStationary` — 3D 双层圆环稳态传热（Revolve 旋转体 + 双材料, 内 Dirichlet 外 Robin, 对数解析解）
- `TmSlabNonlinear` — 3D 平板非线性导热稳态（k(T) 温度相关, 变量变换解析解）
- `SmPlateHoleStationary` — 3D 中心圆孔张力板（3D Difference 布尔 + 对称 Roller, Kirsch Kt=3 应力集中）
- `TFinArrayStationary` — 3D 基板+翅片散热片稳态传热（Array 阵列 + Union, 孤立翅 1D cosh 解析解; 附带 PlotGroup3D+Image PNG 导出与派生值演示）
- `SmCantileverEigenfrequency` — 3D 方形截面悬臂梁特征频率（Eigenfrequency 研究, 欧拉-伯努利 f1/f2 退化对, 模态形状识别）

## 接口 feature / 材料 / 变量 映射速查

| 物理场 | 域特征 tag                      | 材料属性                            | 关键变量                       |
| ------ | ------------------------------- | ----------------------------------- | ------------------------------ |
| Es     | ccn1 (ChargeConservation)       | epsilonr                            | V, es.normE                    |
| Ec     | cucn1 (CurrentConservation)     | electricconductivity                | V, ec.normJ                    |
| T      | solid1 (SolidHeatTransferModel) | thermalconductivity                 | T                              |
| Sm     | lemm1 (Linear Elastic Material) | E, nu (lemm1 直接设)                | solid.mises, solid.disp        |
| EcT    | emh1 (ElectromagneticHeating)   | —                                   | V, T                           |
| TSm    | te1 (ThermalExpansion)          | thermalexpansioncoefficient (9分量) | solid.sx/sy/sz, solid.T/Tref   |
| EcTSm  | emh1 (Ec→T) + te1 (T→Sm)        | materialModel("Enu") E/nu           | V, T, ec.Qrh, solid.disp/mises |
| Emw    | wee1 (WaveEquationElectric)     | RefractiveIndex (n)                 | emw.S11/S21, emw.S11dB/S21dB   |
