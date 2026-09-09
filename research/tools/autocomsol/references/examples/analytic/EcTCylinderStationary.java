import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EcTCylinderStationary.java — 实验 ET2: 3D 同轴双材料电热耦合稳态（含热源, 全对流）
 *
 * <p>物理: 电流 (ConductiveMedia, ec) + 传热 (HeatTransfer, ht) + ElectromagneticHeating 耦合（焦耳热体积热源） 几何:
 * 3D 同轴圆柱（内芯 r1=0.15 + 外壳 r1<r<r2=0.3, L=1, 中心原点） → 两个独立域: 内芯域(导电) + 外壳域(绝缘) 电: 顶面 Terminal
 * V0=0.3V, 底面 Ground; 电流只流经内芯(外壳不导电) → 内芯轴向电场 E=V0/L 均匀, J=σ1·E 均匀, 焦耳热 Q1=σ1·(V0/L)² 均匀 热:
 * 侧面(r=r2) 与 顶/底面(z=±0.5) 全部对流换热 h=200 → T∞=293K（无绝热） 材料: 内芯 σ1=1e6 S/m, k1=15 W/mK; 外壳
 * σ2=1e-8(绝缘), k2=30 W/mK 密度/比热仅传热需; 研究: Stationary
 *
 * <p>解析解（中平面 z=0 是严格对称面, ∂T/∂z=0 精确成立, 端面散热不影响中平面）: 电流: V(z)=V0·(z+L/2)/L, E=V0/L, J=σ1·E,
 * Q1=σ1·(V0/L)²=9e4 W/m³ (内芯均匀热源) 稳态热径向 (含源内芯抛物线 + 无源外壳对数): T_c(r) = T∞ + Q1·r1²/(2·r2·h) +
 * Q1·r1²/(2·k2)·ln(r2/r1) + Q1·(r1²-r²)/(4·k1) T_o(r) = T∞ + Q1·r1²/(2·r2·h) +
 * Q1·r1²/(2·k2)·ln(r2/r) 界面 r1: 温度连续 + 热流连续; 外壁 r2: 对流平衡 验证在
 * scripts/verifications/analytic/ec_t_cylinder_stationary.py。
 *
 * <p>本机证据: - 电热耦合: multiphysics().create(..,"ElectromagneticHeating"), EMHeat_physics/Heat_physics
 * - 对流 BC: HeatFluxBoundary + ConvectiveHeatFlux + minput_temperature + h - 多材料:
 * material("matN").selection().set(domains) 分配域 - 域编号: GeomInfo.getVertexDomain()/getVertexCoord()
 * 由顶点坐标推断内芯/外壳 - 材料 def: electricconductivity/relpermittivity/thermalconductivity
 *
 * <p>模块需求: ACDC + Heat Transfer 模块 运行: python scripts/run.py all EcTCylinderStationary <run-dir>
 * 参数: args[0]=mph 保存路径, args[1]=CSV 导出路径
 */
public class EcTCylinderStationary {

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
        model.param().set("rho1", "8500[kg/m^3]", "内芯密度(镍铬)");
        model.param().set("Cp1", "450[J/(kg*K)]", "内芯比热");
        model.param().set("rho2", "3900[kg/m^3]", "外壳密度(氧化铝)");
        model.param().set("Cp2", "880[J/(kg*K)]", "外壳比热");
        model.param().set("Q1", "sigma1*(V0/L)^2", "内芯体积焦耳热");

        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 同轴圆柱（内芯 + 外壳, 两个独立域）
        // 注意: cyl_in 作为 Difference 的 input2 会被消耗掉, 因此外壳用
        //       cyl_out 减 cyl_in 生成环形, 内芯另建 cyl_core 保留为独立对象。
        //       r1 面接触的两个对象在 Form Union 后保留 2 个域。
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
        // 外壳 = cyl_out - cyl_rm (环形)
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
        // 内芯 = 独立圆柱 cyl_core (r1)
        model.component(comp).geom("geom1").create("cyl_core", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_core").set("r", "r1");
        model.component(comp).geom("geom1").feature("cyl_core").set("h", "L");
        model.component(comp)
                .geom("geom1")
                .feature("cyl_core")
                .set("pos", new double[] {0, 0, -0.5});
        model.component(comp).geom("geom1").run();

        // 域识别（确定性, 不依赖试错）:
        //   getUpDown()[1] = 每个面的所属域; 配合 faceX 多点采样求各域最大半径,
        //   最大半径最小的域是内芯(≤r1), 最大的域是外壳(≤r2)。
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] coreShell = detectCoreShellDomains(gi);
        int coreDom = coreShell[0];
        int shellDom = coreShell[1];
        System.out.println("CORE_DOM=" + coreDom + " SHELL_DOM=" + shellDom);

        // 材料: 内芯(导电+高热导) 与 外壳(绝缘)
        // 材料 selection 必须保持 GEOMDIM 类型 (直接 set(int[]), 不要 .geom()
        // 整几何)。
        model.component(comp).material().create("mat_core", "Common");
        model.component(comp).material("mat_core").selection().set(new int[] {coreDom});
        model.component(comp)
                .material("mat_core")
                .propertyGroup("def")
                .set("electricconductivity", new String[][] {{"sigma1"}});
        model.component(comp)
                .material("mat_core")
                .propertyGroup("def")
                .set("relpermittivity", new String[][] {{"1"}});
        model.component(comp)
                .material("mat_core")
                .propertyGroup("def")
                .set("thermalconductivity", new String[][] {{"k1"}});
        model.component(comp)
                .material("mat_core")
                .propertyGroup("def")
                .set("density", new String[][] {{"rho1"}});
        model.component(comp)
                .material("mat_core")
                .propertyGroup("def")
                .set("heatcapacity", new String[][] {{"Cp1"}});

        model.component(comp).material().create("mat_shell", "Common");
        model.component(comp).material("mat_shell").selection().set(new int[] {shellDom});
        model.component(comp)
                .material("mat_shell")
                .propertyGroup("def")
                .set("electricconductivity", new String[][] {{"sigma2"}});
        model.component(comp)
                .material("mat_shell")
                .propertyGroup("def")
                .set("relpermittivity", new String[][] {{"1"}});
        model.component(comp)
                .material("mat_shell")
                .propertyGroup("def")
                .set("thermalconductivity", new String[][] {{"k2"}});
        model.component(comp)
                .material("mat_shell")
                .propertyGroup("def")
                .set("density", new String[][] {{"rho2"}});
        model.component(comp)
                .material("mat_shell")
                .propertyGroup("def")
                .set("heatcapacity", new String[][] {{"Cp2"}});

        // 电流: ConductiveMedia（默认所有域; 外壳 σ≈0 → 电流只流经内芯）
        model.component(comp).physics().create("ec", "ConductiveMedia", "geom1");

        // 传热: HeatTransfer
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");

        // 按坐标选面
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

        // 热边界: 侧面 + 顶面 + 底面 全部对流换热（无绝热）
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

        model.component(comp).physics("ht").create("hf_top", "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature("hf_top").selection().set(top);
        model.component(comp)
                .physics("ht")
                .feature("hf_top")
                .set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp)
                .physics("ht")
                .feature("hf_top")
                .set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature("hf_top").set("minput_temperature", "Tinf");
        model.component(comp)
                .physics("ht")
                .feature("hf_top")
                .set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature("hf_top").set("h", "h_conv");

        model.component(comp).physics("ht").create("hf_bottom", "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature("hf_bottom").selection().set(bottom);
        model.component(comp)
                .physics("ht")
                .feature("hf_bottom")
                .set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp)
                .physics("ht")
                .feature("hf_bottom")
                .set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature("hf_bottom").set("minput_temperature", "Tinf");
        model.component(comp)
                .physics("ht")
                .feature("hf_bottom")
                .set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature("hf_bottom").set("h", "h_conv");

        // 多物理场耦合: 焦耳热 → 传热体积热源
        model.component(comp).multiphysics().create("emh1", "ElectromagneticHeating");
        model.component(comp).multiphysics("emh1").set("EMHeat_physics", "ec");
        model.component(comp).multiphysics("emh1").set("Heat_physics", "ht");

        // 网格: 自由四面体, 中等密度
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(2);
        model.component(comp).mesh("mesh1").run();

        // 研究: 稳态 Stationary
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 结果导出: CSV（V, T, 电流密度范数）
        String csvOut = args.length > 1 ? args[1] : "EcTCylinderStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"V", "T", "ec.normJ"});
        model.result().export("data1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "EcTCylinderStationary.mph";
        model.save(outPath);

        System.out.println("ET2_OK");
        System.out.println("TAG_ec=" + (model.component(comp).physics("ec") != null));
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
    }

    /**
     * 确定性识别同轴圆柱的内芯/外壳域编号（不依赖求解试错）。
     *
     * <p>原理: GeomInfo.getUpDown()[1] 返回每个面的所属域 (face→domain 映射)。 对每个域, 用 faceX 多点采样其全部面,
     * 求该域能采到的最大半径。 同轴圆柱中, 内芯(圆柱 r1) 的最大半径 ≈ r1, 外壳(环形 r2) ≈ r2。 因此最大半径最小的域是内芯, 最大的域是外壳。
     *
     * <p>实测 (r1=0.15, r2=0.3, Difference+独立内芯圆柱): domain 1: max_r=0.429 → 外壳; domain 2: max_r=0.215
     * → 内芯 (注意 COMSOL 域编号不按直觉: Difference 的补集(外壳)先编号, 内芯后编号)
     *
     * @return int[]{coreDom, shellDom}
     */
    private static int[] detectCoreShellDomains(GeomInfo gi) {
        int nDom = gi.getNDomains();
        int nFace = gi.getNFaces();
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
        // 域最大半径排序: 最小者=内芯(最内部), 最大者=外壳
        int core = 1, shell = 1;
        for (int d = 2; d <= nDom; d++) {
            if (domMaxR[d] < domMaxR[core]) core = d;
            if (domMaxR[d] > domMaxR[shell]) shell = d;
        }
        if (core == shell && nDom > 1) shell = (core == 1) ? 2 : 1;
        System.out.println("DETECT domMaxR=" + java.util.Arrays.toString(domMaxR));
        return new int[] {core, shell};
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

    /** 按半径选面（外侧面: r=sqrt(x²+y²)）。 */
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
