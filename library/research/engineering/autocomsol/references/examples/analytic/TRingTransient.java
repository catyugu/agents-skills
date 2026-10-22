import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * TRingTransient.java — 实验 T2: 2D 瞬态环形传热（对流换热 BC, 有解析解）
 *
 * <p>物理: 传热 (HeatTransfer, ht) — 本机纯固体传热接口 几何: 2D 圆环 r_in=0.2m, r_out=0.5m, 中心在原点（外圆减内圆 → 有孔，几何非平凡）
 * 边界: 内环(xy 平面内 r≈0.2) TemperatureBoundary T=T1=473K（Dirichlet） 外环(r≈0.5) HeatFluxBoundary 对流换热
 * h=100 W/m²K, T∞=293K（Robin） 其余边界默认热绝缘 初值: T=293K（=T∞） 材料: k=50 W/mK, ρ=7850 kg/m³, Cp=500
 * J/kgK（钢） 研究: 瞬态 TimeDependent, t=0..3000s（保存 5 个时间点: 0,100,500,1500,3000s）
 *
 * <p>解析解（圆环稳态 + 贝塞尔级数，Carslaw & Jaeger §7.2 型）: 稳态（径向）: T_s(r) = T∞ + A·(ln(r/a) - B), 满足内壁
 * Dirichlet + 外壁 Robin。 瞬态: T(r,t) = T_s(r) + Σ_n c_n·e^{−α λ_n² t}·U_n(λ_n r) 其中 U_n 为 J0/Y0
 * 组合满足边值，λ_n 为超越方程根，c_n 由初条件正交投影。 本类仅导出 CSV，解析解计算在
 * scripts/verifications/analytic/t_ring_transient.py。
 *
 * <p>本机证据（COMSOL 6.2 官方示例 heating_circuit.mph 解包）: - 对流边界特征: HeatFluxBoundary, 参数
 * HeatFluxType='ConvectiveHeatFlux', Text (环境温度; Text_src 仅允许 'userdef'),
 * h (HeatTransferCoefficientType 默认 UserDef) - 瞬态研究: study("std1").create("time", "TimeDependent");
 * study("std1").feature("time").set("tlist", "range(...)") - 初值: ht.init 或
 * physics("ht").feature("init1").set("T", ...)
 *
 * <p>模块需求: Heat Transfer 模块 运行: python scripts/run.py all TRingTransient <run-dir> 参数: args[0]=mph
 * 保存路径, args[1]=CSV 导出路径
 */
public class TRingTransient {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("r_in", "0.2[m]", "内半径");
        model.param().set("r_out", "0.5[m]", "外半径");
        model.param().set("T1", "473[K]", "内壁温度");
        model.param().set("Tinf", "293[K]", "环境温度");
        model.param().set("h_conv", "100[W/(m^2*K)]", "对流换热系数");
        model.param().set("k", "50[W/(m*K)]", "热导率");
        model.param().set("rho_val", "7850[kg/m^3]", "密度(钢)");
        model.param().set("Cp_val", "500[J/(kg*K)]", "比热容");
        model.param().set("tmax", "3000[s]", "终止时间");

        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 2D 圆环（外圆减内圆）
        model.component(comp).geom().create("geom1", 2);
        model.component(comp).geom("geom1").create("c_out", "Circle");
        model.component(comp).geom("geom1").feature("c_out").set("r", "r_out");
        model.component(comp).geom("geom1").feature("c_out").set("pos", new double[] {0, 0});
        model.component(comp).geom("geom1").create("c_in", "Circle");
        model.component(comp).geom("geom1").feature("c_in").set("r", "r_in");
        model.component(comp).geom("geom1").feature("c_in").set("pos", new double[] {0, 0});
        model.component(comp).geom("geom1").create("diff1", "Difference");
        model.component(comp).geom("geom1").feature("diff1").selection("input").set(new String[] {"c_out"});
        model.component(comp).geom("geom1").feature("diff1").selection("input2").set(new String[] {"c_in"});
        model.component(comp).geom("geom1").run();

        // 材料: 热导率/密度/比热容（Common 材料 def 属性组。
        // 属性名: thermalconductivity/density/heatcapacity（heating_circuit.mph
        // 证据） 注意: density/heatcapacity 若引用全局参数 rho/Cp 会与 physics
        // 特征同名 参数冲突解析成 0 → 直接内联数值）
        model.component(comp).material().create("mat1", "Common");
        model.component(comp).material("mat1").propertyGroup("def").set("thermalconductivity", new String[][] {{"k"}});
        model.component(comp).material("mat1").propertyGroup("def").set("density", new String[][] {{"rho_val"}});
        model.component(comp).material("mat1").propertyGroup("def").set("heatcapacity", new String[][] {{"Cp_val"}});

        // 物理场: HeatTransfer（本机纯固体传热接口）
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");

        // 初值: T=Tinf（环域初始均匀）
        model.component(comp).physics("ht").feature("init1").set("T", "Tinf");

        // 按坐标选边界: 内环(r≈0.2) TemperatureBoundary, 外环(r≈0.5)
        // HeatFluxBoundary(对流)
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] inner = edgesAtRadius(gi, 0.2);
        int[] outer = edgesAtRadius(gi, 0.5);
        requireEdges("inner(r=0.2)", inner);
        requireEdges("outer(r=0.5)", outer);
        System.out.println("INNER_EDGES=" + java.util.Arrays.toString(inner));
        System.out.println("OUTER_EDGES=" + java.util.Arrays.toString(outer));

        // 内环: TemperatureBoundary T=T1（Dirichlet）
        model.component(comp).physics("ht").create("temp_in", "TemperatureBoundary", 1);
        model.component(comp).physics("ht").feature("temp_in").selection().set(inner);
        model.component(comp).physics("ht").feature("temp_in").set("T0", "T1");

        // 外环: HeatFluxBoundary 对流换热（Robin）→ 含对流换热 BC
        model.component(comp).physics("ht").create("hf_out", "HeatFluxBoundary", 1);
        model.component(comp).physics("ht").feature("hf_out").selection().set(outer);
        model.component(comp).physics("ht").feature("hf_out").set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp).physics("ht").feature("hf_out").set("Text", "Tinf");
        model.component(comp).physics("ht").feature("hf_out").set("h", "h_conv");

        // 网格: 自由三角形，中等偏细（瞬态精度敏感）
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(2);
        model.component(comp).mesh("mesh1").run();

        // 研究: 瞬态 Transient（本机 feature 名: Transient，tag=time）。
        // 时间点: 0,100,500,1500,3000s
        model.study().create("std1");
        model.study("std1").create("time", "Transient");
        model.study("std1").feature("time").set("tlist",
                "range(0,100[s],500[s]) range(500,1000,1500[s]) "
                        + "range(1500,1500,3000[s])");
        model.study("std1").createAutoSequences("time");
        model.study("std1").run();

        // 结果导出: CSV（T 域量; 导出全部已保存时间步, COMSOL Data 导出含 t 列）
        String csvOut = args.length > 1 ? args[1] : "TRingTransient.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"T"});
        model.result().export("data1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "TRingTransient.mph";
        model.save(outPath);

        System.out.println("T2_OK");
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
    }

    /** 按半径选边界(2D 边): 圆环边界为圆, 采样中点坐标 r=sqrt(x²+y²) 匹配。 */
    private static int[] edgesAtRadius(GeomInfo gi, double rTarget) {
        double tol = 1e-3;
        int n = gi.getNEdges();
        int[] tmp = new int[n];
        int count = 0;
        for (int e = 1; e <= n; e++) {
            double[][] mid = gi.edgeX(e, new double[] {0.5});
            if (mid == null || mid.length == 0) continue;
            double[] pt = mid[0];
            if (pt == null || pt.length < 2) continue;
            double r = Math.sqrt(pt[0] * pt[0] + pt[1] * pt[1]);
            if (Math.abs(r - rTarget) < tol) {
                tmp[count++] = e;
            }
        }
        int[] result = new int[count];
        System.arraycopy(tmp, 0, result, 0, count);
        return result;
    }

    /** 断言边界存在且非空。 */
    private static void requireEdges(String label, int[] edges) {
        if (edges == null || edges.length == 0) {
            throw new IllegalStateException("edgesAtRadius: no edge matched for '" + label + "'");
        }
    }
}
