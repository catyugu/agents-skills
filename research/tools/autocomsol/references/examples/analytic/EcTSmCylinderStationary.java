import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EcTSmCylinderStationary: 3D 同轴双材料稳态电→热→力耦合（含解析解）
 *
 * <p>物理: 电流 (ConductiveMedia, ec) + 传热 (HeatTransfer, ht) + 固体力学 (SolidMechanics, solid)， 焦耳热经
 * ElectromagneticHeating 耦合 (ec→ht)，温度场经 ThermalExpansion 域特征 (ht→solid)。
 *
 * <p>几何: 3D 同轴圆柱（内芯 r1=0.15 + 外壳 r1&lt;r&lt;r2=0.3, L=1, 中心原点）→ 2 个独立域。
 *
 * <p>电: 顶面 Terminal V0=0.3V, 底面 Ground; 电流只流经内芯(外壳 σ≈0 绝缘) → 内芯轴向 V=V0·(z+L/2)/L 线性, J=σ1·V0/L 均匀,
 * 焦耳热 Q1=σ1·(V0/L)² 均匀（唯一热源）。
 *
 * <p>热: 侧面+顶面+底面 全部对流 h=200 → T∞=293K（无绝热）。稳态径向温度: 内芯
 * T_c(r)=T∞+Q1·r1²/(2r2h)+Q1·r1²/(2k2)·ln(r2/r1)+Q1·(r1²-r²)/(4k1) （含源抛物线） 外壳
 * T_o(r)=T∞+Q1·r1²/(2r2h)+Q1·r1²/(2k2)·ln(r2/r) （无源对数）
 *
 * <p>力: 广义平面应变 (ε_z=0): 下端面 z=-0.5 Roller (uz=0), 上端面 z=+0.5 Roller (uz=0)， 径向自由;
 * RigidMotionSuppression 抑制径向刚体模态。 内芯/外壳 E,ν 相同、仅 α 不同 → 界面位移/径向应力自动连续, 解析解单常数。
 *
 * <p>热应力解析解 (ε_z=0 轴对称, E1=E2, ν1=ν2, α 分片): 定义 ψ(r)=α(r)·(T(r)-Tref), Q(r)=∫₀ʳ ψ(r')·r'
 * dr'（分片数值积分）, m=(1+ν)/(1-ν), G=E/(2(1+ν)), λ=Eν/((1+ν)(1-2ν)) C1 = G·m·Q(r2) / ((λ+G)·r2²) σ_r(r)
 * = -2G·m·Q(r)/r² + 2(λ+G)C1 σ_θ(r) = -2G·m·ψ(r) + 2G·m·Q(r)/r² + 2(λ+G)C1 σ_z(r) = 2λ·C1 -
 * 2G·m·ψ(r) 验证在 scripts/verifications/analytic/ec_tsm_cylinder_stationary.py（y=0 半轴: σ_r=sx, σ_θ=sy,
 * σ_z=sz）。
 *
 * <p>本机证据: - ThermalExpansion 域特征: physics("solid").create("te1","ThermalExpansion",3),
 * alpha_mat='from_mat' 用材料 thermalexpansioncoefficient, minput_strainreferencetemperature=Tref - 材料
 * def: E/nu/thermalexpansioncoefficient（9分量）; lemm1 E_mat=from_mat 默认 - 约束: Roller (uz 法向),
 * RigidMotionSuppression (contributingPoints=automatic) - 变量: solid.sx/sy/sz (正应力), solid.disp
 *
 * <p>模块需求: ACDC + Heat Transfer + Structural Mechanics 运行: python scripts/run.py all
 * EcTSmCylinderStationary <run-dir> args[0]=mph, args[1]=CSV
 */
public class EcTSmCylinderStationary {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("r1", "0.15[m]", "内芯半径");
        model.param().set("r2", "0.3[m]", "外壳外半径");
        model.param().set("L", "1[m]", "圆柱高度");
        model.param().set("V0", "0.3[V]", "施加电压");
        model.param().set("sigma1", "1e6[S/m]", "内芯电导率");
        model.param().set("sigma2", "1e-8[S/m]", "外壳电导率(绝缘)");
        model.param().set("k1", "15[W/(m*K)]", "内芯热导率");
        model.param().set("k2", "30[W/(m*K)]", "外壳热导率");
        model.param().set("Tinf", "293[K]", "环境温度");
        model.param().set("h_conv", "200[W/(m^2*K)]", "对流换热系数");
        model.param().set("rho1", "8500[kg/m^3]", "内芯密度");
        model.param().set("Cp1", "450[J/(kg*K)]", "内芯比热");
        model.param().set("rho2", "3900[kg/m^3]", "外壳密度");
        model.param().set("Cp2", "880[J/(kg*K)]", "外壳比热");
        // 力学: E,ν 两相相同（保证解析解界面连续）, α 分片不同
        model.param().set("E", "200e9[Pa]", "杨氏模量(两相相同)");
        model.param().set("nu", "0.3", "泊松比(两相相同)");
        model.param().set("alpha1", "1.2e-5[1/K]", "内芯热膨胀系数");
        model.param().set("alpha2", "0.6e-5[1/K]", "外壳热膨胀系数");
        model.param().set("Tref", "293[K]", "热应变参考温度");
        model.param().set("Q1", "sigma1*(V0/L)^2", "内芯体积焦耳热");

        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 同轴圆柱（外壳环形 diff + 内芯独立圆柱）
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("cyl_out", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_out").set("r", "r2");
        model.component(comp).geom("geom1").feature("cyl_out").set("h", "L");
        model.component(comp)
                .geom("geom1")
                .feature("cyl_out")
                .set("pos", new double[] {0, 0, -0.5});
        model.component(comp).geom("geom1").create("cyl_rm", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_rm").set("r", "r1");
        model.component(comp).geom("geom1").feature("cyl_rm").set("h", "L");
        model.component(comp).geom("geom1").feature("cyl_rm").set("pos", new double[] {0, 0, -0.5});
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
                .set(new String[] {"cyl_rm"});
        model.component(comp).geom("geom1").create("cyl_core", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_core").set("r", "r1");
        model.component(comp).geom("geom1").feature("cyl_core").set("h", "L");
        model.component(comp)
                .geom("geom1")
                .feature("cyl_core")
                .set("pos", new double[] {0, 0, -0.5});
        model.component(comp).geom("geom1").run();

        // 域识别（确定性）: 最大半径最小的域=内芯, 最大的=外壳
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] coreShell = detectCoreShellDomains(gi);
        int coreDom = coreShell[0], shellDom = coreShell[1];
        System.out.println("CORE_DOM=" + coreDom + " SHELL_DOM=" + shellDom);

        // 材料: 内芯(导电+α1) 与 外壳(绝缘+α2); 两相 E,ν 相同 (E,ν 在 lemm1 userdef
        // 设, M1 实证)
        model.component(comp).material().create("mat_core", "Common");
        model.component(comp).material("mat_core").selection().set(new int[] {coreDom});
        setMat(model.component(comp).material("mat_core"), "sigma1", "k1", "rho1", "Cp1", "alpha1");
        model.component(comp).material().create("mat_shell", "Common");
        model.component(comp).material("mat_shell").selection().set(new int[] {shellDom});
        setMat(
                model.component(comp).material("mat_shell"),
                "sigma2",
                "k2",
                "rho2",
                "Cp2",
                "alpha2");

        // 物理场
        model.component(comp).physics().create("ec", "ConductiveMedia", "geom1");
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");
        model.component(comp).physics().create("solid", "SolidMechanics", "geom1");
        // E,ν 在 lemm1 上 userdef 设 (M1 实证), α 从材料 from_mat
        model.component(comp).physics("solid").feature("lemm1").set("E_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("E", "E");
        model.component(comp).physics("solid").feature("lemm1").set("nu_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("nu", "nu");

        // 按坐标选面
        int[] top = facesAtZ(gi, 0.5);
        int[] bottom = facesAtZ(gi, -0.5);
        int[] side = facesAtRadius(gi, 0.3);
        requireFaces("top", top);
        requireFaces("bottom", bottom);
        requireFaces("side", side);
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

        // 热边界: 全对流（侧面+顶面+底面）
        addConvective(comp, model, "hf_side", side);
        addConvective(comp, model, "hf_top", top);
        addConvective(comp, model, "hf_bottom", bottom);

        // 多物理场: 焦耳热 → 体积热源
        model.component(comp).multiphysics().create("emh1", "ElectromagneticHeating");
        model.component(comp).multiphysics("emh1").set("EMHeat_physics", "ec");
        model.component(comp).multiphysics("emh1").set("Heat_physics", "ht");

        // 多物理场耦合: 热膨胀 (ht→solid), alpha 从材料 from_mat, 覆盖全部域
        model.component(comp).multiphysics().create("te1", "ThermalExpansion");
        model.component(comp).multiphysics("te1").selection().set(new int[] {coreDom, shellDom});
        model.component(comp).multiphysics("te1").set("Heat_physics", "ht");
        model.component(comp).multiphysics("te1").set("Solid_physics", "solid");
        model.component(comp).multiphysics("te1").set("alpha_mat", "from_mat");
        model.component(comp)
                .multiphysics("te1")
                .set("minput_strainreferencetemperature_src", "userdef");
        model.component(comp).multiphysics("te1").set("minput_strainreferencetemperature", "Tref");

        // 约束: ε_z=0 广义平面应变 (两端 Roller uz=0, 径向自由) + 刚体抑制
        model.component(comp).physics("solid").create("roll_bottom", "Roller", 2);
        model.component(comp).physics("solid").feature("roll_bottom").selection().set(bottom);
        model.component(comp).physics("solid").create("roll_top", "Roller", 2);
        model.component(comp).physics("solid").feature("roll_top").selection().set(top);
        model.component(comp).physics("solid").create("rms1", "RigidMotionSuppression", 3);
        model.component(comp)
                .physics("solid")
                .feature("rms1")
                .selection()
                .set(new int[] {coreDom, shellDom});

        // 网格 + 研究
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(2);
        model.component(comp).mesh("mesh1").run();
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 导出 CSV（V, T, 正应力分量, 位移）
        String csvOut = args.length > 1 ? args[1] : "EcTSmCylinderStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result()
                .export("data1")
                .set(
                        "expr",
                        new String[] {"V", "T", "solid.sx", "solid.sy", "solid.sz", "solid.disp"});
        model.result().export("data1").run();

        String outPath = args.length > 0 ? args[0] : "EcTSmCylinderStationary.mph";
        model.save(outPath);
        System.out.println("EcTSmCyl_OK");
        System.out.println("TAG_ec=" + (model.component(comp).physics("ec") != null));
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
        System.out.println("TAG_solid=" + (model.component(comp).physics("solid") != null));
        System.out.println("TAG_te1=" + (model.component(comp).multiphysics("te1") != null));
    }

    /** 写材料 def 组各属性（电/热/热膨胀系数）。E,ν 在 lemm1 userdef 设。 */
    private static void setMat(
            com.comsol.model.Material mat,
            String sigma,
            String k,
            String rho,
            String cp,
            String alpha) {
        mat.propertyGroup("def").set("electricconductivity", new String[][] {{sigma}});
        mat.propertyGroup("def").set("relpermittivity", new String[][] {{"1"}});
        mat.propertyGroup("def").set("thermalconductivity", new String[][] {{k}});
        mat.propertyGroup("def").set("density", new String[][] {{rho}});
        mat.propertyGroup("def").set("heatcapacity", new String[][] {{cp}});
        // thermalexpansioncoefficient: 3x3 对称张量扁平为 9 个值
        // (xx,xy,xz,yx,yy,yz,zx,zy,zz)
        mat.propertyGroup("def")
                .set(
                        "thermalexpansioncoefficient",
                        new String[] {alpha, "0", "0", "0", alpha, "0", "0", "0", alpha});
    }

    /** 加对流换热边界。 */
    private static void addConvective(String comp, Model model, String tag, int[] faces) {
        model.component(comp).physics("ht").create(tag, "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature(tag).selection().set(faces);
        model.component(comp).physics("ht").feature(tag).set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp).physics("ht").feature(tag).set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature(tag).set("minput_temperature", "Tinf");
        model.component(comp)
                .physics("ht")
                .feature(tag)
                .set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature(tag).set("h", "h_conv");
    }

    /** 确定性识别内芯/外壳域（最大半径最小=内芯）。 */
    private static int[] detectCoreShellDomains(GeomInfo gi) {
        int nDom = gi.getNDomains(), nFace = gi.getNFaces();
        int[] upDom = gi.getUpDown()[1];
        double[] domMaxR = new double[nDom + 1];
        java.util.Arrays.fill(domMaxR, -1.0);
        for (int f = 1; f <= nFace; f++) {
            int d = upDom[f - 1];
            if (d < 1 || d > nDom) continue;
            double[] pr = gi.faceParamRange(f);
            double maxRf = -1.0;
            for (int iu = 0; iu <= 4; iu++) {
                double t = iu / 4.0;
                double pu = pr[0] + t * (pr[1] - pr[0]);
                for (int iv = 0; iv <= 4; iv++) {
                    double s = (pr.length >= 4) ? iv / 4.0 : 0.5;
                    double pv = (pr.length >= 4) ? pr[2] + s * (pr[3] - pr[2]) : 0.5;
                    double[][] pts = gi.faceX(f, new double[][] {{pu, pv}});
                    if (pts != null && pts.length > 0) {
                        double r = Math.sqrt(pts[0][0] * pts[0][0] + pts[0][1] * pts[0][1]);
                        if (r > maxRf) maxRf = r;
                    }
                }
            }
            if (maxRf > domMaxR[d]) domMaxR[d] = maxRf;
        }
        int core = 1, shell = 1;
        for (int d = 2; d <= nDom; d++) {
            if (domMaxR[d] < domMaxR[core]) core = d;
            if (domMaxR[d] > domMaxR[shell]) shell = d;
        }
        if (core == shell && nDom > 1) shell = (core == 1) ? 2 : 1;
        System.out.println("DETECT domMaxR=" + java.util.Arrays.toString(domMaxR));
        return new int[] {core, shell};
    }

    private static int[] facesAtZ(GeomInfo gi, double zt) {
        int n = gi.getNFaces(), c = 0;
        int[] tmp = new int[n];
        for (int f = 1; f <= n; f++) {
            double[] ctr = sampleFaceCenter(gi, f, gi.faceParamRange(f));
            if (ctr != null && Math.abs(ctr[2] - zt) < 1e-3) tmp[c++] = f;
        }
        int[] r = new int[c];
        System.arraycopy(tmp, 0, r, 0, c);
        return r;
    }

    private static int[] facesAtRadius(GeomInfo gi, double rTarget) {
        int n = gi.getNFaces(), c = 0;
        int[] tmp = new int[n];
        for (int f = 1; f <= n; f++) {
            double[] ctr = sampleFaceCenter(gi, f, gi.faceParamRange(f));
            if (ctr == null) continue;
            double r = Math.sqrt(ctr[0] * ctr[0] + ctr[1] * ctr[1]);
            if (Math.abs(r - rTarget) < 1e-3) tmp[c++] = f;
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
