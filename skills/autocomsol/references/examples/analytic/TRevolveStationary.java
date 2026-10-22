import com.comsol.model.GeomInfo;
import com.comsol.model.GeomSequence;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * TRevolveStationary.java — 实验 TRevolve: 3D 双层圆环稳态传热（Revolve 旋转体几何, 双材料）
 *
 * <p>几何 (Revolve 旋转体): 两个 xz 工作平面各含一个矩形截面 (wp1: r∈[r_in,r_mid], wp2:
 * r∈[r_mid,r_out], 厚度均为 t), 各自 Revolve 360° 成中空圆柱壳, Union (intbnd 默认 on) 合并 → 内环壳(A) +
 * 外环壳(B) 两个独立域。
 *
 * <p>物理: 纯固体传热 HeatTransfer (ht), 稳态 Stationary。
 *   - 内壁 (r=r_in): TemperatureBoundary T=T0 (Dirichlet)
 *   - 外壁 (r=r_out): HeatFluxBoundary 对流换热 ConvectiveHeatFlux h=h_conv → T∞=Tinf (Robin)
 *   - 环壳间界面 (r=r_mid): 默认连续性 (热流连续 + 温度连续)
 *
 * <p>解析解 (双层圆环对数分布, 每层 T=A·ln(r)+B):
 *   内层 A 区 (r_in≤r≤r_mid): T_A(r) = T0 + (T_m - T0)·ln(r/r_in)/ln(r_mid/r_in)
 *   外层 B 区 (r_mid≤r≤r_out): T_B(r) = Tinf + (T_m - Tinf)·ln(r/r_out)·(r_out·h/kB)/[1+(r_out·h/kB)·ln(r_out/r_mid)]
 *   界面温度 T_m = T_A(r_mid) = T_B(r_mid) 由内层/外层界面热流连续 + 外壁对流平衡解得。
 *
 * <p>本机证据: - 旋转体几何: GeomSequence.create("rev1","Revolve") + revolvefrom=workplane +
 *   selection("input").set({"wp1"}) + angtype=full - WorkPlane 嵌套 2D: create("wp1","WorkPlane") +
 *   quickplane=xz, feature("wp1").geom() 取内嵌 2D 序列 - 双材料分配: material("matA").selection().set({domA})
 *   + propertyGroup("def").set("thermalconductivity",{{"kA"}}) - 对流 BC / Dirichlet BC: 同 TRingTransient
 *
 * <p>验证在 scripts/verifications/analytic/t_revolve_stationary.py。
 * 运行: python scripts/run.py all TRevolveStationary <run-dir>
 * 参数: args[0]=mph 保存路径, args[1]=CSV 导出路径
 */
public class TRevolveStationary {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("r_in", "0.1[m]", "内半径");
        model.param().set("r_mid", "0.25[m]", "内/外壳分界半径");
        model.param().set("r_out", "0.4[m]", "外半径");
        model.param().set("t", "0.05[m]", "圆环厚度");
        model.param().set("T0", "500[K]", "内壁温度");
        model.param().set("Tinf", "293[K]", "环境温度");
        model.param().set("h_conv", "20[W/(m^2*K)]", "外壁对流换热系数");
        model.param().set("kA", "60[W/(m*K)]", "内环热导率");
        model.param().set("kB", "30[W/(m*K)]", "外环热导率");

        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 双层圆环（Revolve 旋转体）
        //  wp1: xz 平面矩形 [r_in..r_mid]×[t/2..t/2] → rev1 绕轴旋转 360° → 内环壳
        //  wp2: xz 平面矩形 [r_mid..r_out]×[t/2..t/2] → rev2 绕轴旋转 360° → 外环壳
        //  Union (intbnd 默认 on) → 内环 + 外环 两个独立域
        model.component(comp).geom().create("geom1", 3);

        // wp1: 内环截面
        model.component(comp).geom("geom1").create("wp1", "WorkPlane");
        model.component(comp).geom("geom1").feature("wp1").set("quickplane", "xz");
        GeomSequence g2a = model.component(comp).geom("geom1").feature("wp1").geom();
        g2a.create("rect1", "Rectangle");
        g2a.feature("rect1").set("size", new String[] {"r_mid-r_in", "t"});
        g2a.feature("rect1").set("pos", new String[] {"r_in", "-t/2"});
        g2a.run();

        // wp2: 外环截面
        model.component(comp).geom("geom1").create("wp2", "WorkPlane");
        model.component(comp).geom("geom1").feature("wp2").set("quickplane", "xz");
        GeomSequence g2b = model.component(comp).geom("geom1").feature("wp2").geom();
        g2b.create("rect2", "Rectangle");
        g2b.feature("rect2").set("size", new String[] {"r_out-r_mid", "t"});
        g2b.feature("rect2").set("pos", new String[] {"r_mid", "-t/2"});
        g2b.run();

        // rev1: 内环旋转体
        model.component(comp).geom("geom1").create("rev1", "Revolve");
        model.component(comp).geom("geom1").feature("rev1").set("revolvefrom", "workplane");
        model.component(comp).geom("geom1").feature("rev1").set("workplane", "wp1");
        model.component(comp).geom("geom1").feature("rev1").selection("input").set(new String[] {"wp1"});
        model.component(comp).geom("geom1").feature("rev1").set("angtype", "full");

        // rev2: 外环旋转体
        model.component(comp).geom("geom1").create("rev2", "Revolve");
        model.component(comp).geom("geom1").feature("rev2").set("revolvefrom", "workplane");
        model.component(comp).geom("geom1").feature("rev2").set("workplane", "wp2");
        model.component(comp).geom("geom1").feature("rev2").selection("input").set(new String[] {"wp2"});
        model.component(comp).geom("geom1").feature("rev2").set("angtype", "full");

        // Union: 合并两个旋转体, 保留内部界面 (intbnd 默认 on → 两个独立域)
        model.component(comp).geom("geom1").create("uni1", "Union");
        model.component(comp).geom("geom1").feature("uni1").selection("input").set(new String[] {"rev1", "rev2"});
        model.component(comp).geom("geom1").run();

        // 域识别（确定性, 不依赖试错）: 与 EcTCylinderStationary 同理,
        // 用 getUpDown()[1] + faceX 多点采样求各域最大半径, 最大半径最小者=内环(A)。
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] coreShell = detectCoreShellDomains(gi);
        int domA = coreShell[0]; // 内环壳
        int domB = coreShell[1]; // 外环壳
        System.out.println("DOM_A=" + domA + " DOM_B=" + domB + " NDOM=" + gi.getNDomains());

        // 材料: 内环(A) 与外环(B) 不同热导率
        model.component(comp).material().create("matA", "Common");
        model.component(comp).material("matA").selection().set(new int[] {domA});
        model.component(comp).material("matA").propertyGroup("def").set("thermalconductivity", new String[][] {{"kA"}});

        model.component(comp).material().create("matB", "Common");
        model.component(comp).material("matB").selection().set(new int[] {domB});
        model.component(comp).material("matB").propertyGroup("def").set("thermalconductivity", new String[][] {{"kB"}});

        // 传热: HeatTransfer (纯固体传热)
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");

        // 按半径选面: 内壁 (r=r_in) + 外壁 (r=r_out)
        int[] inner = facesAtRadius(gi, 0.1);
        int[] outer = facesAtRadius(gi, 0.4);
        requireFaces("inner(r=r_in)", inner);
        requireFaces("outer(r=r_out)", outer);
        System.out.println("INNER_FACES=" + java.util.Arrays.toString(inner));
        System.out.println("OUTER_FACES=" + java.util.Arrays.toString(outer));

        // 内壁: Dirichlet T=T0
        model.component(comp).physics("ht").create("temp_in", "TemperatureBoundary", 2);
        model.component(comp).physics("ht").feature("temp_in").selection().set(inner);
        model.component(comp).physics("ht").feature("temp_in").set("T0", "T0");

        // 外壁: Robin 对流换热 h=h_conv → Tinf
        model.component(comp).physics("ht").create("hf_out", "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature("hf_out").selection().set(outer);
        model.component(comp).physics("ht").feature("hf_out").set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp).physics("ht").feature("hf_out").set("Text", "Tinf");
        model.component(comp).physics("ht").feature("hf_out").set("h", "h_conv");

        // 网格: 自由四面体
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(2);
        model.component(comp).mesh("mesh1").run();

        // 研究: 稳态 Stationary
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 结果导出: CSV（T）
        String csvOut = args.length > 1 ? args[1] : "TRevolveStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"T"});
        model.result().export("data1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "TRevolveStationary.mph";
        model.save(outPath);

        System.out.println("TRevolveStationary_OK");
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
    }

    /**
     * 确定性识别双层圆环的内/外壳域编号（不依赖求解试错）。
     *
     * <p>与 EcTCylinderStationary.detectCoreShellDomains 同理: getUpDown()[1] = face→domain 映射;
     * 对每个域用 faceX 多点采样求最大半径, 最大半径最小者=内环(≤r_mid), 最大者=外环(≤r_out)。
     *
     * @return int[]{domInner, domOuter}
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
                    try {
                        double[][] pts = gi.faceX(f, new double[][] {{pu, pv}});
                        if (pts != null && pts.length > 0) {
                            double r = Math.sqrt(pts[0][0] * pts[0][0] + pts[0][1] * pts[0][1]);
                            if (r > maxRf) maxRf = r;
                        }
                    } catch (Exception e) {
                        // 该采样点超出面参数域, 跳过 (Revolve 曲面参数域可能非规则)
                    }
                }
            }
            if (maxRf > domMaxR[d]) domMaxR[d] = maxRf;
        }
        int inner = 1, outer = 1;
        for (int d = 2; d <= nDom; d++) {
            if (domMaxR[d] < domMaxR[inner]) inner = d;
            if (domMaxR[d] > domMaxR[outer]) outer = d;
        }
        if (inner == outer && nDom > 1) outer = (inner == 1) ? 2 : 1;
        System.out.println("DETECT domMaxR=" + java.util.Arrays.toString(domMaxR));
        return new int[] {inner, outer};
    }

    /** 按半径选面（圆柱壳壁面: r=sqrt(x²+y²)）。 */
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
