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

先例：EmwSlabFrequency 的周期 Port / PeriodicCondition 字符串就是从
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

结果记入 `lab-notebook.md` + `local-evidence-index.md`；探针类从 `src/` 删除，
**不入库**（不污染 14 案例清单，无需三处同步）。

## 降级规则

新字符串验证失败 → **降级到已验证 API 的等价实现**，案例交付不阻塞。已建立的兜底：

- `Array` 失败 → N 个复制体 + `Union`（已验证）
- `Image` 导出失败 → 本案例先不产图，派生值载荷保留
- `result().numerical()` 派生值失败 → 用已验证的 `PlotGroup1D`+"Global"+`"Plot"` 导出
  （EmwSlabFrequency 范式）读标量
- `Eigenfrequency` 研究失败（几乎不可能）→ 退 `"Eigenvalue"` 并重新验证

## 触发条件

在以下任一情形**必须**先跑探针/挖掘，不得直接写进正式案例：

1. 该字符串未出现在 references/ 任何案例或映射速查中
2. 该字符串只出现在 case-naming.md 但无 "validated on <Case>" 标记
3. 跨版本 / 跨模块可用性存疑（COMSOL 字符串随版本与许可证模块变化）
4. 同概念多个候选名（如 `Array` vs `Pattern`、`Eigenfrequency` vs `Eigenvalue`、
   `HeatTransfer` vs `HeatTransferInSolids`）——已证伪的候选一并记入 pitfalls
