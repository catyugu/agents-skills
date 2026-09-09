import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EcTCylinderTransient.java — 实验 ET1: 3D 实心圆柱瞬态电热耦合（有解析解）
 *
 * <p>物理: 电流 (ConductiveMedia, ec) + 传热 (HeatTransfer, ht)，经 ElectromagneticHeating 多物理场耦合（焦耳热 →
 * 体积热源） 几何: 3D 实心圆柱 r=0.3m, h=1m, 中心在原点（几何非平凡: 3D 曲面+体积） 电: 顶面(z=+0.5) Terminal Voltage V0=1V,
 * 底面(z=-0.5) Ground → 轴向电场 E=V0/L 均匀, J=σE 均匀, 焦耳热 Q=J²/σ 均匀 热: 侧面对流 h=200 W/m²K 到 T∞=293K,
 * 顶/底面热绝缘, 初温 T=293K 材料: σ=2e4 S/m, k=50 W/mK, ρ=7850 kg/m³, Cp=500 J/kgK 研究: 瞬态 Transient,
 * t=0..3000s
 *
 * <p>解析解（中平面 z=0 处，轴向对称 → 纯径向，均匀热源+侧面对流）: 电流: V(z) = V0·(z+L/2)/L, J=σV0/L, Q=σ(V0/L)²=2e4 W/m³
 * 稳态热: T_s(r) = T∞ + Q·R/(2h) + Q·(R²-r²)/(4k) 瞬态热: T(r,t) = T_s(r) + Σ c_n e^{-αλ_n²t}·J0(λ_n r)
 * λ_n 满足 Robin: k·λ·J1(λR) = h·J0(λR) c_n = ∫r(Tinf-T_s)J0(λr)dr / ∫r J0²(λr)dr 验证在
 * scripts/verifications/analytic/ec_t_cylinder_transient.py。
 *
 * <p>本机证据: - 耦合接口: MultiphysicsCoupling op="ElectromagneticHeating" （thermoelectric_cooler.mph 解包）,
 * 参数 EMHeat_physics/Heat_physics - Java API: model.component(comp).coupling().create("emh1",
 * "ElectromagneticHeating") - 电流接口 ConductiveMedia: Terminal/Ground (cucn1/ein1) - 传热接口
 * HeatTransfer: HeatFluxBoundary 对流 (h_conv 避免 h 冲突) - 瞬态研究: "Transient" (tag=time),
 * tlist=range(...) - 材料 def: thermalconductivity/density/heatcapacity（内联数值防冲突）
 *
 * <p>模块需求: ACDC + Heat Transfer 模块 运行: python scripts/run.py all EcTCylinderTransient <run-dir> 参数:
 * args[0]=mph 保存路径, args[1]=CSV 导出路径
 */
public class EcTCylinderTransient {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("R", "0.3[m]", "圆柱半径");
        model.param().set("L", "1[m]", "圆柱高度");
        model.param().set("V0", "1[V]", "施加电压");
        model.param().set("sigma", "2e4[S/m]", "电导率");
        model.param().set("Tinf", "293[K]", "环境温度");
        model.param().set("h_conv", "200[W/(m^2*K)]", "对流换热系数");
        model.param().set("k", "50[W/(m*K)]", "热导率");
        model.param().set("rho_val", "7850[kg/m^3]", "密度");
        model.param().set("Cp_val", "500[J/(kg*K)]", "比热容");
        model.param().set("Q0", "sigma*(V0/L)^2", "体积焦耳热");
        model.param().set("tmax", "3000[s]", "终止时间");

        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 3D 实心圆柱
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("cyl1", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl1").set("r", "R");
        model.component(comp).geom("geom1").feature("cyl1").set("h", "L");
        model.component(comp).geom("geom1").feature("cyl1").set("pos", new double[] {0, 0, -0.5});
        model.component(comp).geom("geom1").run();

        // 材料: σ/k/ρ/Cp（Common def 组; 数值内联防与 physics 参数冲突）
        model.component(comp).material().create("mat1", "Common");
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("electricconductivity", new String[][] {{"sigma"}});
        // CurrentConservation 需要相对介电常数
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("relpermittivity", new String[][] {{"1"}});
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("thermalconductivity", new String[][] {{"k"}});
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("density", new String[][] {{"rho_val"}});
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("heatcapacity", new String[][] {{"Cp_val"}});

        // 电流: ConductiveMedia
        model.component(comp).physics().create("ec", "ConductiveMedia", "geom1");

        // 传热: HeatTransfer（本机纯固体传热接口）
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");
        model.component(comp).physics("ht").feature("init1").set("T", "Tinf");

        // 按坐标选面
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] top = facesAtZ(gi, 0.5);
        int[] bottom = facesAtZ(gi, -0.5);
        int[] side = facesAtRadius(gi, 0.3);
        requireFaces("top(z=0.5)", top);
        requireFaces("bottom(z=-0.5)", bottom);
        requireFaces("side(r=0.3)", side);
        System.out.println("TOP=" + java.util.Arrays.toString(top));
        System.out.println("BOTTOM=" + java.util.Arrays.toString(bottom));
        System.out.println("SIDE=" + java.util.Arrays.toString(side));

        // 电边界: 顶面 Terminal V=V0, 底面 Ground
        model.component(comp).physics("ec").create("term1", "Terminal", 2);
        model.component(comp).physics("ec").feature("term1").selection().set(top);
        model.component(comp).physics("ec").feature("term1").set("TerminalType", "Voltage");
        model.component(comp).physics("ec").feature("term1").set("V0", "V0");
        model.component(comp).physics("ec").create("gnd1", "Ground", 2);
        model.component(comp).physics("ec").feature("gnd1").selection().set(bottom);

        // 热边界: 侧面对流 (Robin)
        model.component(comp).physics("ht").create("hf_side", "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature("hf_side").selection().set(side);
        model.component(comp)
                .physics("ht")
                .feature("hf_side")
                .set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp)
                .physics("ht")
                .feature("hf_side")
                .set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature("hf_side").set("minput_temperature", "Tinf");
        model.component(comp)
                .physics("ht")
                .feature("hf_side")
                .set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature("hf_side").set("h", "h_conv");

        // 多物理场耦合: 焦耳热 → 传热体积热源
        model.component(comp).multiphysics().create("emh1", "ElectromagneticHeating");
        model.component(comp).multiphysics("emh1").set("EMHeat_physics", "ec");
        model.component(comp).multiphysics("emh1").set("Heat_physics", "ht");

        // 网格: 自由四面体, 中等密度
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(2);
        model.component(comp).mesh("mesh1").run();

        // 研究: 瞬态 Transient。时间点: 0,100,500,1500,3000s
        model.study().create("std1");
        model.study("std1").create("time", "Transient");
        model.study("std1")
                .feature("time")
                .set(
                        "tlist",
                        "range(0,100[s],500[s]) range(500,1000,1500[s]) "
                                + "range(1500,1500,3000[s])");
        model.study("std1").createAutoSequences("time");
        model.study("std1").run();

        // 结果导出: CSV（V 与 T 域量, 全时间步）
        String csvOut = args.length > 1 ? args[1] : "EcTCylinderTransient.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"V", "T"});
        model.result().export("data1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "EcTCylinderTransient.mph";
        model.save(outPath);

        System.out.println("ET1_OK");
        System.out.println("TAG_ec=" + (model.component(comp).physics("ec") != null));
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
        System.out.println("TAG_emh=" + (model.component(comp).multiphysics("emh1") != null));
    }

    /** 按 z 坐标选面（端面）。 */
    private static int[] facesAtZ(GeomInfo gi, double zt) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int count = 0;
        for (int f = 1; f <= n; f++) {
            double[] pr = gi.faceParamRange(f);
            double[] ctr = sampleFaceCenter(gi, f, pr);
            if (ctr == null) continue;
            if (Math.abs(ctr[2] - zt) < 1e-3) tmp[count++] = f;
        }
        int[] result = new int[count];
        System.arraycopy(tmp, 0, result, 0, count);
        return result;
    }

    /** 按半径选面（侧面: r=sqrt(x²+y²)）。 */
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
            if (Math.abs(r - rTarget) < tol) tmp[count++] = f;
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
            throw new IllegalStateException("no face matched for '" + label + "'");
        }
    }
}
