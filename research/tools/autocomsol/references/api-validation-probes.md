# 新 API 字符串验证协议（探针纪律）

任何未在参考库 / case-naming.md 映射速查中记录过的 COMSOL API 字符串（feature 类型、
属性名、研究步骤、导出类型、派生值类型），**必须先验证再进正式案例**，禁止凭记忆
猜测。COMSOL 的 feature 类型字符串是运行时注册表键，不是类名——`unzip -l` 看到的
`OpArray.class` 只能佐证存在性，真正的 `op="Array"` 字样在序列化模型里。

本机没有 COMSOL Java 参考手册 / 示例模型（machine-profile.md 证据），因此权威来源是
安装目录下的 **jar 类清单** 与 **applications/ 下已序列化的 .mph 模型**。

## 验证方法（按成本从低到高）

### 1. jar 类清单法（最快，确定"存在性"）

```bash
# 几何 feature 类型 → geommesh jar（Op* 类即 feature 类型算子）
unzip -l "D:/Program Files/COMSOL/COMSOL62/Multiphysics/plugins/com.comsol.geommesh_1.0.0.jar" \
  | grep -iE 'geom/Op(Array|Mirror|Move|Rotate|Pattern|Fillet|Chamfer)'
# → com/comsol/geommesh/geom/OpArray.class 等，佐证存在

# 导出/派生值/绘图 → api jar（接口类）
unzip -l .../plugins/com.comsol.api_1.0.0.jar | grep -iE 'ImageExport|NumericalFeature|SurfacePlot|PlotGroup'

# 研究步骤 / 物理接口 → model 实现 jar
unzip -l .../plugins/com.comsol.model_1.0.0.jar | grep -iE 'StudyStep'
```

jar 佐证后仍需第 2 或第 3 步拿**精确字符串**（`"Array"` 而非 `"Pattern"`）。

### 2. 挖掘已序列化的 .mph（拿权威字符串，推荐）

applications/ 下官方模型是 COMSOL 自己生成的序列化模型，字符串 100% 准确：

```python
import zipfile, re
z = zipfile.ZipFile(r"D:/Program Files/COMSOL/COMSOL62/Multiphysics/applications/Heat_Transfer_Module/Applications/forced_air_cooling_with_heat_sink.mph")
d = z.read("dmodel.xml").decode("utf-8", errors="replace")
re.findall(r'<GeomFeature op="([A-Za-z]+)"', d)   # → 全部几何 op（Array/Extrude/Union/...）
re.findall(r'<StudyFeature op="([A-Za-z]+)"', d)  # → 研究步骤（Stationary/Eigenfrequency/...）
re.findall(r'op="(Av[A-Za-z]+)"', d)              # → 派生值（AvSurface/Average/...）
re.findall(r'op="(PlotGroup[A-Za-z0-9]*)"', d)    # → 绘图组（PlotGroup1D/2D/3D）
```

先例：EmwSlabSweepFrequency 的周期 Port / PeriodicCondition 字符串就是从
`applications/RF_Module/.../fresnel_equations.mph` 挖出来的。

已挖到（2026-08-16，证据见 local-evidence-index.md）：

| 字符串 | 证据 |
| --- | --- |
| 几何 `op="Array"`（`p:type="linear"`, `p:size`） | `forced_air_cooling_with_heat_sink.mph`（散热片官方模型） |
| 几何 `OpArray` / `OpMirror` / `OpMove` / `OpRotate` 类 | `com.comsol.geommesh_1.0.0.jar`（无 `OpPattern` → 用 `Array` 不用 `Pattern`） |
| 研究 `op="Eigenfrequency"`（`s("eig") s("Eigenfrequency")`） | `ladder_frame.mph`（梁模态官方模型） |
| 派生值 `op="Average"` / `AvSurface` | `concentric_tube_heat_exchanger.mph`、`forced_air_cooling...mph` |
| 绘图组 `PlotGroup1D/2D/3D` | 多个官方模型 |
| 导出 `ImageExport` 接口类 | `com.comsol.api_1.0.0.jar` |

### 2b. 挖掘离散阶次键（physics `ShapeProperty`）

物理场的离散阶次不写在几何/网格里，而是物理接口节点上的 `PhysicsProp tag="ShapeProperty"`：

```python
import zipfile, re
z = zipfile.ZipFile(r".../applications/RF_Module/Antennas/conical_antenna.mph")
d = z.read("dmodel.xml").decode("utf-8", errors="replace")
# 键名 + 合法值（官方模型只会出现合法值）: order_<场标识>
re.findall(r'param="(order_[A-Za-z_0-9]+)" value="1\|1,\'([^\']*)\'"', d)
# 不带 order_ 前缀的接口（BEM / Beam Envelopes）用 shapeorder
re.findall(r'param="(shapeorder[a-zA-Z_0-9]*)" value="1\|1,\'([^\']*)\'"', d)
# 接口 op 与 order 键的归属: 先定位 <Physics op="..."> 再取其后的 ShapeProperty
```

全库扫一遍（`applications/**/*.mph`，1839 个）即可得到各接口族的键名与合法码分布；
但**默认值与拒绝行为必须靠探针**（第 3 步）拿：`prop(key)` 的当前值、
`getAllowedPropertyValues(key)` 的完整清单、以及非法码的报错文本。

### 3. 临时探针法（最终权威，拿"行为 + 精确字符串 + 属性名"）

jar / 挖掘只能证明"字符串存在于某个模型"，Java 调用是否成立、属性名是否对，
只有**真正编译 + 运行**才说了算。写一个临时 `ApiProbes` scratch 类，多探针各打
`XXX_OK` 或异常到 stdout，编译运行一次：

```java
// ApiProbes.java（临时，不入库，不三处同步）
public class ApiProbes {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";
        model.component().create(comp, true);
        model.component(comp).geom().create("geom1", 3);
        // 探针 1: Array 几何
        try {
            model.component(comp).geom("geom1").create("arr1", "Array");
            model.component(comp).geom("geom1").feature("arr1")
                .selection("input").set(new String[]{"blk1"});
            model.component(comp).geom("geom1").feature("arr1")
                .set("size", new String[]{"4", "1", "1"});
            System.out.println("ARRAY_OK");
        } catch (Throwable t) { System.out.println("ARRAY_FAIL: " + t); }
        // 探针 2: ... 每个新字符串一个探针
    }
}
```

运行：`python scripts/run.py all ApiProbes <runs-dir>`（复用既有闭环，探针类放
src 下编译会进 build/classes，跑完即删）。

结果记入 `logs/YYYY-MM-lab-notebook.md` + `local-evidence-index.md`；探针类从 `src/` 删除，
**不入库**（不污染正式案例清单，无需三处同步）。

### 3b. 属性死活判定（契约层只能证存在，行为层才能证有效）

契约层（`com.comsol.model.ParameterEntity`）可以一次拿全"属性是否合法、默认值、合法取值"：

```java
f.hasProperty("minput_temperature")         // 该键在 feature 上是否存在
f.properties()                              // 该 feature 当前类型的全部合法键
f.getAllowedPropertyValues("HeatFluxType")  // 合法取值清单
f.getValueType("Text")                      // "String" / "Double" / ...
f.getString("Text")                         // 当前值（未设过即默认值）
```

但**合法 ≠ 生效**。先例（COMSOL 6.2，2026-09-19 探针）：`HeatFluxBoundary` +
`HeatFluxType='ConvectiveHeatFlux'` 上，`minput_temperature` 在契约层完全合法
（`hasProperty=true`、`properties()` 列出、`set()` 接受、读回正常），却**不进方程**；
对流边界的实际环境温度由 `Text` 驱动（`Text_src` 只接受 `userdef`）。只看契约层会得出
"两个键都能用"的错误结论。

行为层是唯一判据：把两个候选键设成**互相远离**的值（差值远大于网格误差与容差），
跑一个**有解析解**的最小模型，用解反推哪个键生效。

- 本例最小模型: 2D 方板、1D 沿 x 导热 —— x=0 定温 `T_hot`，x=L 对流（`h·L/k = 1`）→
  端面温度 = `(T_hot + T_amb)/2`；两个候选环境温度相差 100 K，解出的端面温度唯一确定活键。
- 每个配置导出场 CSV 后离线比对解析值（batch 中派生值 `getReal()` 不可用），
  解析值与解出的端面温度四位小数一致，判定无歧义。
- 挖掘只能提出假设：官方 .mph 全库（1839 个）里 `HeatFluxBoundary` 同时携带 `Text` 与
  `minput_temperature`，后者几乎恒为未改动的默认 `293.15[K]`，前者承载物理环境温度表达式 ——
  与行为层结论一致，但判定本身必须来自行为层。

结论写入 `references/`（recipes + pitfalls），探针类删除。

### 3c. 默认值判定（写等于默认值的 set 是噪声）

同一套契约层读取还能回答"这条 `set(...)` 是否必要"：**新建 feature 后立刻读**
`getString(key)` 得到的就是该键的默认值，`set(key, 该值)` 不改变任何行为。

```java
f = physics("ht").create("hf1", "HeatFluxBoundary", 1);   // 不设任何属性
f.getString("HeatTransferCoefficientType");               // → 默认值本身
f.getString("HeatFluxType");                              // → 需要显式改写的键
```

先例（COMSOL 6.2，2026-09-19 探针）：`HeatFluxBoundary.HeatTransferCoefficientType`、`TemperatureBoundary.T0_src`、
`ThermalExpansion.alpha_mat`、`Union.intbnd`、emw `Port` 的 `SlitType`/`PortOrientation`/`InputType`
读回即目标值 —— 这些 `set` 全部可删；`HeatFluxType`、`h`、`Text`、`T0`、`TerminalType`、
`minput_strainreferencetemperature_src` 的默认值不是案例想要的，必须写。

两点纪律：

- **切换枚举键后要重读**：某些键的默认值随另一个键的选择变化（`PortType=Periodic` 时需重新读 `SlitType` 等；
  本例实测这三个键在切换后仍是默认值，但不能默认如此）。
- 删掉一条 `set` 之后必须跑**行为层**验证（有解析解的案例 + 全回归），不能只凭契约层断言"无影响"。

## 降级规则

新字符串验证失败 → **降级到已验证 API 的等价实现**，案例交付不阻塞。已建立的兜底：

- `Array` 失败 → N 个复制体 + `Union`（已验证）
- `Image` 导出失败 → 本案例先不产图，派生值载荷保留
- `result().numerical()` 派生值失败 → 用已验证的 `PlotGroup1D`+"Global"+`"Plot"` 导出
  （EmwSlabSweepFrequency 范式）读标量
- `Eigenfrequency` 研究失败（几乎不可能）→ 退 `"Eigenvalue"` 并重新验证

## 触发条件

在以下任一情形**必须**先跑探针/挖掘，不得直接写进正式案例：

1. 该字符串未出现在 references/ 任何案例或映射速查中
2. 该字符串只出现在 case-naming.md 但无 "validated on \<Case\>" 标记
3. 跨版本 / 跨模块可用性存疑（COMSOL 字符串随版本与许可证模块变化）
4. 同概念多个候选名（如 `Array` vs `Pattern`、`Eigenfrequency` vs `Eigenvalue`、
   `HeatTransfer` vs `HeatTransferInSolids`）——已证伪的候选一并记入 pitfalls
