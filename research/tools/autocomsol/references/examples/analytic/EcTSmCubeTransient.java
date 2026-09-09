import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EcTSmCubeTransient: 3D 立方体瞬态电→热→力耦合（含解析解, 几何不同于 EcTSmCylinderStationary 的圆柱）
 *
 * <p>物理: 电流 (ConductiveMedia, ec) + 传热 (HeatTransfer, ht) + 固体力学 (SolidMechanics, solid)， 焦耳热经
 * ElectromagneticHeating 耦合 (ec→ht)，温度场经 ThermalExpansion 多物理场耦合 (ht→solid)。
 *
 * <p>几何: 3D 立方体 L=0.2m, 中心原点 (x,y,z ∈ [-0.1,0.1])。
 *
 * <p>电: x=+0.1 面 Terminal V0=0.02V, x=-0.1 面 Ground → 轴向电场 E_x=V0/L=0.1 V/m 均匀, J=σ·E 均匀, 焦耳热
 * Q=σ·(V0/L)²=1e4 W/m³ 均匀。
 *
 * <p>热: x=±0.1 两面对流 h=200 → T∞=293K, 其余面绝热(默认), 初温 T∞, 瞬态 0..6000s。 温度仅依赖 x (1D slab), 关于 x=0 对称,
 * 轴向均匀。傅里叶余弦级数瞬态解: T_s(x)=T∞+Q·a/h+Q·(a²-x²)/(2k), a=L/2 （稳态） T(x,t)=T_s(x)+Σ
 * c_n·e^{-αλ_n²t}·cos(λ_n x) λ_n 满足 Robin: k·λ·tan(λ·a)=h, c_n=∫(T∞-T_s)cos(λx)dx/∫cos²(λx)dx
 *
 * <p>力: 两端 Roller (x=±0.1 面 u_x=0, 径向自由) + RigidMotionSuppression 抑制刚体模态。 温度仅依赖 x 且自平衡 → 精确解:
 * σ_x(t)=-E·α·(⟨T⟩(t)-Tref) 均匀, ⟨T⟩=体平均温度 σ_y=σ_z=0 (侧向自由) u_x(x,t)=α·∫(ΔT-⟨ΔT⟩)dx, u_x(±a)=0 自动满足
 * 验证在 scripts/verifications/analytic/ec_tsm_cube_transient.py。
 *
 * <p>本机证据: - 热膨胀多物理场耦合: multiphysics("te1","ThermalExpansion"), Heat_physics/Solid_physics - alpha
 * 从材料 from_mat (thermalexpansioncoefficient 9 分量), minput_strainreferencetemperature=Tref - 约束:
 * Roller (法向 u 固定), RigidMotionSuppression (contributingPoints=automatic) - 瞬态研究: "Transient",
 * tlist 多段 range
 *
 * <p>模块需求: ACDC + Heat Transfer + Structural Mechanics 运行: python scripts/run.py all
 * EcTSmCubeTransient <run-dir> args[0]=mph, args[1]=CSV
 */
public class EcTSmCubeTransient {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("L", "0.2[m]", "立方体边长");
        model.param().set("V0", "0.02[V]", "施加电压");
        model.param().set("sigma", "1e6[S/m]", "电导率");
        model.param().set("k", "50[W/(m*K)]", "热导率");
        model.param().set("Tinf", "293[K]", "环境温度");
        model.param().set("h_conv", "200[W/(m^2*K)]", "对流换热系数");
        model.param().set("rho_val", "7850[kg/m^3]", "密度");
        model.param().set("Cp_val", "500[J/(kg*K)]", "比热容");
        model.param().set("E", "200e9[Pa]", "杨氏模量");
        model.param().set("nu", "0.3", "泊松比");
        model.param().set("alpha", "1.2e-5[1/K]", "热膨胀系数");
        model.param().set("Tref", "293[K]", "热应变参考温度");
        model.param().set("Q0", "sigma*(V0/L)^2", "体积焦耳热");

        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 3D 立方体 (中心原点)
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("blk1", "Block");
        model.component(comp)
                .geom("geom1")
                .feature("blk1")
                .set("size", new String[] {"L", "L", "L"});
        model.component(comp)
                .geom("geom1")
                .feature("blk1")
                .set("pos", new String[] {"-L/2", "-L/2", "-L/2"});
        model.component(comp).geom("geom1").run();

        GeomInfo gi = model.component(comp).geom("geom1");

        // 材料: 单材料 (σ/k/ρ/Cp/α; E,ν 在 lemm1 userdef)
        model.component(comp).material().create("mat1", "Common");
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("electricconductivity", new String[][] {{"sigma"}});
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
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set(
                        "thermalexpansioncoefficient",
                        new String[] {"alpha", "0", "0", "0", "alpha", "0", "0", "0", "alpha"});

        // 物理场
        model.component(comp).physics().create("ec", "ConductiveMedia", "geom1");
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");
        model.component(comp).physics("ht").feature("init1").set("T", "Tinf");
        model.component(comp).physics().create("solid", "SolidMechanics", "geom1");
        model.component(comp).physics("solid").feature("lemm1").set("E_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("E", "E");
        model.component(comp).physics("solid").feature("lemm1").set("nu_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("nu", "nu");

        // 按坐标选面
        int[] xp = facesAtX(gi, 0.1);
        int[] xm = facesAtX(gi, -0.1);
        requireFaces("x=+0.1", xp);
        requireFaces("x=-0.1", xm);
        System.out.println("X_PLUS=" + java.util.Arrays.toString(xp));
        System.out.println("X_MINUS=" + java.util.Arrays.toString(xm));

        // 电边界: x=+a Terminal V0, x=-a Ground
        model.component(comp).physics("ec").create("term1", "Terminal", 2);
        model.component(comp).physics("ec").feature("term1").selection().set(xp);
        model.component(comp).physics("ec").feature("term1").set("TerminalType", "Voltage");
        model.component(comp).physics("ec").feature("term1").set("V0", "V0");
        model.component(comp).physics("ec").create("gnd1", "Ground", 2);
        model.component(comp).physics("ec").feature("gnd1").selection().set(xm);

        // 热边界: x=±a 两端对流
        model.component(comp).physics("ht").create("hf_xp", "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature("hf_xp").selection().set(xp);
        model.component(comp)
                .physics("ht")
                .feature("hf_xp")
                .set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp)
                .physics("ht")
                .feature("hf_xp")
                .set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature("hf_xp").set("minput_temperature", "Tinf");
        model.component(comp)
                .physics("ht")
                .feature("hf_xp")
                .set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature("hf_xp").set("h", "h_conv");

        model.component(comp).physics("ht").create("hf_xm", "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature("hf_xm").selection().set(xm);
        model.component(comp)
                .physics("ht")
                .feature("hf_xm")
                .set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp)
                .physics("ht")
                .feature("hf_xm")
                .set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature("hf_xm").set("minput_temperature", "Tinf");
        model.component(comp)
                .physics("ht")
                .feature("hf_xm")
                .set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature("hf_xm").set("h", "h_conv");

        // 多物理场: 焦耳热 → 热; 热膨胀 → 力
        model.component(comp).multiphysics().create("emh1", "ElectromagneticHeating");
        model.component(comp).multiphysics("emh1").set("EMHeat_physics", "ec");
        model.component(comp).multiphysics("emh1").set("Heat_physics", "ht");

        model.component(comp).multiphysics().create("te1", "ThermalExpansion");
        model.component(comp).multiphysics("te1").selection().set(new int[] {1});
        model.component(comp).multiphysics("te1").set("Heat_physics", "ht");
        model.component(comp).multiphysics("te1").set("Solid_physics", "solid");
        model.component(comp).multiphysics("te1").set("alpha_mat", "from_mat");
        model.component(comp)
                .multiphysics("te1")
                .set("minput_strainreferencetemperature_src", "userdef");
        model.component(comp).multiphysics("te1").set("minput_strainreferencetemperature", "Tref");

        // 约束: 两端 Roller (u_x=0), 刚体抑制
        model.component(comp).physics("solid").create("roll_xp", "Roller", 2);
        model.component(comp).physics("solid").feature("roll_xp").selection().set(xp);
        model.component(comp).physics("solid").create("roll_xm", "Roller", 2);
        model.component(comp).physics("solid").feature("roll_xm").selection().set(xm);
        model.component(comp).physics("solid").create("rms1", "RigidMotionSuppression", 3);
        model.component(comp).physics("solid").feature("rms1").selection().set(new int[] {1});

        // 网格 + 研究 (瞬态)
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(4);
        model.component(comp).mesh("mesh1").run();
        model.study().create("std1");
        model.study("std1").create("time", "Transient");
        model.study("std1")
                .feature("time")
                .set(
                        "tlist",
                        "range(0,100[s],500[s]) range(500,500,1000[s]) "
                                + "range(1000,1000,2000[s]) range(2000,2000,4000[s]) "
                                + "range(4000,2000,6000[s])");
        model.study("std1").createAutoSequences("time");
        model.study("std1").run();

        // 导出 CSV（V, T, 应力分量, 位移, 全时间步）
        String csvOut = args.length > 1 ? args[1] : "EcTSmCubeTransient.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result()
                .export("data1")
                .set(
                        "expr",
                        new String[] {"V", "T", "solid.sx", "solid.sy", "solid.sz", "solid.disp"});
        model.result().export("data1").run();

        String outPath = args.length > 0 ? args[0] : "EcTSmCubeTransient.mph";
        model.save(outPath);
        System.out.println("EcTSmCube_OK");
        System.out.println("TAG_ec=" + (model.component(comp).physics("ec") != null));
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
        System.out.println("TAG_solid=" + (model.component(comp).physics("solid") != null));
        System.out.println("TAG_te1=" + (model.component(comp).multiphysics("te1") != null));
    }

    /** 按 x 坐标选面。 */
    private static int[] facesAtX(GeomInfo gi, double xt) {
        int n = gi.getNFaces(), c = 0;
        int[] tmp = new int[n];
        for (int f = 1; f <= n; f++) {
            double[] ctr = sampleFaceCenter(gi, f, gi.faceParamRange(f));
            if (ctr != null && Math.abs(ctr[0] - xt) < 1e-3) tmp[c++] = f;
        }
        int[] r = new int[c];
        System.arraycopy(tmp, 0, r, 0, c);
        return r;
    }

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

    private static void requireFaces(String label, int[] faces) {
        if (faces == null || faces.length == 0) {
            throw new IllegalStateException("no face matched for '" + label + "'");
        }
    }
}
