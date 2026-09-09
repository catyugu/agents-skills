import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * TCylinderStationary.java — 实验 T1: 3D 稳态传热基准（空心圆柱, HeatTransfer）
 *
 * <p>物理: 传热 (HeatTransfer, ht) — 本机纯固体传热接口 几何: 空心圆柱(圆筒) r_in=0.3m, r_out=0.5m, h=0.2m, 中心在原点 外圆柱
 * Cylinder(r=0.5) 减 内圆柱 Cylinder(r=0.3) → Difference 边界(按坐标提取面): 内壁(曲面 r≈0.3) TemperatureBoundary
 * T=T1=100K 外壁(曲面 r≈0.5) TemperatureBoundary T=T2=300K 上下端面(z=±0.1) ThermalInsulation(默认 ins1) 材料:
 * Common 材料, thermalconductivity = 50 W/(m*K) 研究: Stationary
 *
 * <p>解析解（圆柱坐标稳态径向传热, 无轴向梯度）: T(r) = T1 + (T2-T1) * ln(r/r_in) / ln(r_out/r_in) r_in=0.3, r_out=0.5
 * → T(r) = 100 + 200*ln(r/0.3)/ln(0.5/0.3) 检查点: r=0.4 → T = 100 + 200*ln(0.4/0.3)/ln(0.5/0.3) ≈
 * 100+200*0.2877/0.5108 ≈ 212.7 K
 *
 * <p>本机证据: - HeatTransferInSolids 报 Unknown physics interface（不可用） - HeatTransfer
 * 是本机纯固体传热接口（HeatProbe: tags solid1/init1/ins1/...） - 边界特征名
 * TemperatureBoundary（inline_induction_heater.mph 证据） - TemperatureBoundary 参数: T0_src=userdef,
 * T0（本机 XML 证据） - 材料属性 thermalconductivity（finned_pipe.mph 证据）
 *
 * <p>模块需求: Heat Transfer 模块 运行: comsolcompile TCylinderStationary.java; comsolbatch -inputfile ...
 * -outputfile ... <savepath.mph> <csv-out> 参数: args[0]=mph 保存路径, args[1]=CSV 导出路径
 */
public class TCylinderStationary {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("r_in", "0.3[m]", "内半径");
        model.param().set("r_out", "0.5[m]", "外半径");
        model.param().set("H", "0.2[m]", "圆筒高度");
        model.param().set("T1", "100[K]", "内壁温度");
        model.param().set("T2", "300[K]", "外壁温度");
        model.param().set("k", "50[W/(m*K)]", "热导率");

        // 组件
        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 空心圆柱（外柱减内柱）
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("cyl_out", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_out").set("r", "r_out");
        model.component(comp).geom("geom1").feature("cyl_out").set("h", "H");
        model.component(comp)
                .geom("geom1")
                .feature("cyl_out")
                .set("pos", new double[] {0, 0, -0.1});

        model.component(comp).geom("geom1").create("cyl_in", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_in").set("r", "r_in");
        model.component(comp).geom("geom1").feature("cyl_in").set("h", "H");
        model.component(comp).geom("geom1").feature("cyl_in").set("pos", new double[] {0, 0, -0.1});

        model.component(comp).geom("geom1").create("diff1", "Difference");
        model.component(comp)
                .geom("geom1")
                .feature("diff1")
                .selection("input")
                .set(new String[] {"cyl_out"});
        model.component(comp)
                .geom("geom1")
                .feature("diff1")
                .selection("input2")
                .set(new String[] {"cyl_in"});
        model.component(comp).geom("geom1").run();

        // 材料: 热导率（Common 材料 def 属性组）
        model.component(comp).material().create("mat1", "Common");
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("thermalconductivity", new String[][] {{"k"}});

        // 物理场: HeatTransfer（本机纯固体传热接口）
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");

        // 按坐标提取面: 内壁(r≈0.3), 外壁(r≈0.5)
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] inner = facesAtRadius(gi, 0.3);
        int[] outer = facesAtRadius(gi, 0.5);
        requireFaces("inner(r=0.3)", inner);
        requireFaces("outer(r=0.5)", outer);
        System.out.println("INNER_FACES=" + java.util.Arrays.toString(inner));
        System.out.println("OUTER_FACES=" + java.util.Arrays.toString(outer));

        // 内壁 TemperatureBoundary T=T1
        model.component(comp).physics("ht").create("temp_in", "TemperatureBoundary", 2);
        model.component(comp).physics("ht").feature("temp_in").selection().set(inner);
        model.component(comp).physics("ht").feature("temp_in").set("T0_src", "userdef");
        model.component(comp).physics("ht").feature("temp_in").set("T0", "T1");

        // 外壁 TemperatureBoundary T=T2
        model.component(comp).physics("ht").create("temp_out", "TemperatureBoundary", 2);
        model.component(comp).physics("ht").feature("temp_out").selection().set(outer);
        model.component(comp).physics("ht").feature("temp_out").set("T0_src", "userdef");
        model.component(comp).physics("ht").feature("temp_out").set("T0", "T2");

        // 网格
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(2);
        model.component(comp).mesh("mesh1").run();

        // 研究
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 导出 CSV: 温度 T（域量）。法向热通量 ht.ntflux 是边界量，域上未定义，
        // 热量守恒检查在健康检查中通过解析梯度 q=-k*dT/dr 完成。
        String csvOut = args.length > 1 ? args[1] : "TCylinderStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"T"});
        model.result().export("data1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "TCylinderStationary.mph";
        model.save(outPath);

        System.out.println("T1_OK");
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
    }

    /** 按半径选面（圆柱坐标: r = sqrt(x^2+y^2)）。匹配所有该半径的曲面段。 */
    private static int[] facesAtRadius(GeomInfo gi, double rTarget) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int count = 0;
        double tol = 1e-3;
        for (int f = 1; f <= n; f++) {
            double[] pr = gi.faceParamRange(f);
            double[] ctr = sampleFaceCenter(gi, f, pr);
            if (ctr == null) continue;
            double r = Math.sqrt(ctr[0] * ctr[0] + ctr[1] * ctr[1]);
            if (Math.abs(r - rTarget) < tol) {
                tmp[count++] = f;
            }
        }
        int[] result = new int[count];
        System.arraycopy(tmp, 0, result, 0, count);
        return result;
    }

    /** 采样面中心坐标（用参数范围中点）。 */
    private static double[] sampleFaceCenter(GeomInfo gi, int f, double[] pr) {
        try {
            if (pr == null || pr.length < 2) return null;
            double p0 = (pr[0] + pr[1]) / 2;
            double p1 = (pr.length >= 4) ? (pr[2] + pr[3]) / 2 : 0.5;
            double[][] pts = gi.faceX(f, new double[][] {{p0, p1}});
            if (pts != null && pts.length > 0) return pts[0];
        } catch (Exception e) {
            try {
                double[][] pts = gi.faceX(f, new double[][] {{0.5, 0.5}});
                if (pts != null && pts.length > 0) return pts[0];
            } catch (Exception e2) {
            }
        }
        return null;
    }

    /** 断言面存在且非空。 */
    private static void requireFaces(String label, int[] faces) {
        if (faces == null || faces.length == 0) {
            throw new IllegalStateException("facesAtRadius: no face matched for '" + label + "'");
        }
    }
}
