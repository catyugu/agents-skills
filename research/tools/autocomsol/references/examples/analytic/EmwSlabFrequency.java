import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EmwSlabFrequency: 3D 介质平板 频域电磁波 (emw) — 最简单的 emw 入门案例.
 *
 * <p>物理: 电磁波, 频域 (ElectromagneticWaves, emw)。一个平面波 (TE, y 偏振) 垂直入射到 一个无损介质平板 (n_slab=2)。周期性单元: 空气盒
 * + 中间介质板 (Form Union → 3 域)。 顶/底面为周期性端口 (Periodic Port): port1 激励 (底部), port2 接收 (顶部); 四侧面为
 * Floquet 周期性条件 (PeriodicCondition)。单频 2.45 GHz, 提取 S11/S21。
 *
 * <p>几何: blk_air (period×period×H) + blk_slab (period×period×t_slab, 居中), Union intbnd=on → 3 域
 * (上空气, 介质板, 下空气)。
 *
 * <p>材料: Air (n=n_air=1) + Slab (n=n_slab=2), 均用 RefractiveIndex 材料模型 (wee1
 * DisplacementFieldModel=RefractiveIndex 从材料读 n)。
 *
 * <p>边界: port1/port2 在 z 最小/最大外表面 (Periodic 端口, Fresnel 标准配置); pc1/pc2 Floquet 周期在 x/y 方向成对面组。
 *
 * <p>验证 (解析法): 垂直入射无损平板 = Fabry-Pérot, 解析 S11/S21: r = (n1-n2)/(n1+n2), δ = 2π·n_slab·t_slab/λ0,
 * S11 = r(1-e^{-2iδ})/(1-r²e^{-2iδ}), S21 = (1-r²)e^{-iδ}/(1-r²e^{-2iδ})。 无损 → |S11|²+|S21|²=1
 * (功率守恒)。比较导出的 emw.S11dB / emw.S21dB。
 *
 * <p>本机证据: 官方 RF_Module/Verification_Examples/fresnel_equations.mph (dmodel.xml) — emw 接口
 * op="ElectromagneticWaves"; wee1 WaveEquationElectric; Port Periodic (SlitType=PECBacked,
 * PortOrientation=ForwardPort, InputType=E, Eampl={0,1,0}, n=n_air, alpha1_inc); PeriodicCondition
 * Floquet/FromPeriodicPort; Freq 研究步 plist=f0 + punit=GHz; S 参数全局变量 emw.S11/emw.S21
 * (emw.S11dB/emw.S21dB)。
 *
 * <p>模块需求: RF Module。 运行: python scripts/run.py all EmwSlabFrequency <run-dir> args[0]=mph,
 * args[1]=CSV
 */
public class EmwSlabFrequency {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";

        // ---- 全局参数 ----
        model.param().set("period", "10[mm]", "周期单元边长 (亚波长)");
        model.param().set("t_slab", "6[mm]", "介质板厚度");
        model.param().set("h_air", "15[mm]", "每侧空气层高度");
        model.param().set("f0", "2.45[GHz]", "工作频率");
        model.param().set("alpha", "0[deg]", "入射角 (0=垂直入射)");
        model.param().set("n_air", "1", "空气折射率");
        model.param().set("n_slab", "2", "介质板折射率 (εr=4)");
        model.param().set("lda0", "c_const/f0", "自由空间波长");
        model.param().set("mh", "2.5[mm]", "最大网格尺寸");
        model.param().set("mh_slab", "1.25[mm]", "介质板内最大网格尺寸");

        model.component().create(comp, true);
        com.comsol.model.GeomSequence g3 = model.component(comp).geom().create("geom1", 3);

        // ---- 几何: 空气盒 + 居中介质板 ----
        g3.create("blk_air", "Block");
        g3.feature("blk_air").set("pos", new double[] {0, 0, 0});
        g3.feature("blk_air").set("base", "center");
        g3.feature("blk_air").set("size", new String[] {"period", "period", "2*h_air+t_slab"});
        g3.create("blk_slab", "Block");
        g3.feature("blk_slab").set("pos", new double[] {0, 0, 0});
        g3.feature("blk_slab").set("base", "center");
        g3.feature("blk_slab").set("size", new String[] {"period", "period", "t_slab"});
        g3.create("uni1", "Union");
        g3.feature("uni1").selection("input").set(new String[] {"blk_air", "blk_slab"});
        g3.feature("uni1").set("intbnd", "on");
        g3.run();
        System.out.println("GEOM built");

        // ---- 域/面识别 (确定性, 基于几何探测, 不硬编码编号) ----
        GeomInfo gi = model.component(comp).geom("geom1");
        int nDom = gi.getNDomains();
        int nFace = gi.getNFaces();
        int[] upDom = gi.getUpDown()[1];
        System.out.println("NDOM=" + nDom + " NFACE=" + nFace);

        // 域 bbox (由面采样点累积) → 高度≈t_slab 的是介质板
        double[][] bmin = new double[nDom + 1][3];
        double[][] bmax = new double[nDom + 1][3];
        for (int d = 1; d <= nDom; d++) {
            for (int k = 0; k < 3; k++) {
                bmin[d][k] = Double.MAX_VALUE;
                bmax[d][k] = -Double.MAX_VALUE;
            }
        }
        double[][] faceC = new double[nFace + 1][3];
        for (int f = 1; f <= nFace; f++) {
            int d = upDom[f - 1];
            if (d >= 1 && d <= nDom) {
                double[][] pts = sampleFaceGrid(gi, f, 3);
                if (pts == null) continue;
                for (double[] p : pts)
                    for (int k = 0; k < 3; k++) {
                        bmin[d][k] = Math.min(bmin[d][k], p[k]);
                        bmax[d][k] = Math.max(bmax[d][k], p[k]);
                    }
            }
            double[] c = faceCenter(gi, f);
            if (c != null) faceC[f] = c;
        }
        double[] hgt = new double[nDom + 1];
        for (int d = 1; d <= nDom; d++) hgt[d] = bmax[d][2] - bmin[d][2];
        for (int d = 1; d <= nDom; d++) {
            System.out.println(
                    "DOM "
                            + d
                            + " h="
                            + String.format("%.4f", hgt[d])
                            + " bbox z=["
                            + String.format("%.5f", bmin[d][2])
                            + ","
                            + String.format("%.5f", bmax[d][2])
                            + "] x=["
                            + String.format("%.5f", bmin[d][0])
                            + ","
                            + String.format("%.5f", bmax[d][0])
                            + "] y=["
                            + String.format("%.5f", bmin[d][1])
                            + ","
                            + String.format("%.5f", bmax[d][1])
                            + "]");
        }

        int slabDom = -1;
        java.util.List<Integer> airDoms = new java.util.ArrayList<>();
        for (int d = 1; d <= nDom; d++) {
            if (Math.abs(hgt[d] - 0.006) < 1e-4) slabDom = d; // t_slab=6mm
            else airDoms.add(d);
        }
        System.out.println("SLAB_DOM=" + slabDom + " AIR_DOMS=" + airDoms);
        if (slabDom < 0 || airDoms.size() != 2) {
            throw new IllegalStateException(
                    "domain detection failed: slab=" + slabDom + " air=" + airDoms.size());
        }

        // 面分类: 纯按几何面心坐标 (getAdj 在此几何上顺序与面编号不一致, 不依赖它)
        // 外部面位于盒体坐标面: z=±H/2 端口, x=±period/2 与 y=±period/2 周期面
        double hp = 0.010 / 2, hz = (2 * 0.015 + 0.006) / 2; // period/2, H/2
        java.util.List<Integer> port1F = new java.util.ArrayList<>(),
                port2F = new java.util.ArrayList<>();
        java.util.List<Integer> xMinF = new java.util.ArrayList<>(),
                xMaxF = new java.util.ArrayList<>();
        java.util.List<Integer> yMinF = new java.util.ArrayList<>(),
                yMaxF = new java.util.ArrayList<>();
        for (int f = 1; f <= nFace; f++) {
            double[] c = faceC[f];
            if (c == null) continue;
            if (Math.abs(c[2] + hz) < 1e-3) port1F.add(f); // 底部外表面
            else if (Math.abs(c[2] - hz) < 1e-3) port2F.add(f); // 顶部外表面
            else if (Math.abs(Math.abs(c[0]) - hp) < 1e-3) (c[0] < 0 ? xMinF : xMaxF).add(f);
            else if (Math.abs(Math.abs(c[1]) - hp) < 1e-3) (c[1] < 0 ? yMinF : yMaxF).add(f);
        }
        System.out.println(
                "PORT1=" + port1F + " PORT2=" + port2F + " XMIN=" + xMinF + " XMAX=" + xMaxF
                        + " YMIN=" + yMinF + " YMAX=" + yMaxF);
        if (port1F.size() != 1
                || port2F.size() != 1
                || xMinF.size() != 3
                || xMaxF.size() != 3
                || yMinF.size() != 3
                || yMaxF.size() != 3) {
            throw new IllegalStateException(
                    "face classification failed: p1="
                            + port1F.size()
                            + " p2="
                            + port2F.size()
                            + " xm="
                            + xMinF.size()
                            + " xM="
                            + xMaxF.size()
                            + " ym="
                            + yMinF.size()
                            + " yM="
                            + yMaxF.size());
        }

        int[] airArr = new int[airDoms.size()];
        for (int i = 0; i < airDoms.size(); i++) airArr[i] = airDoms.get(i);
        int[] xMinArr = new int[xMinF.size()], xMaxArr = new int[xMaxF.size()];
        int[] yMinArr = new int[yMinF.size()], yMaxArr = new int[yMaxF.size()];
        for (int i = 0; i < xMinF.size(); i++) xMinArr[i] = xMinF.get(i);
        for (int i = 0; i < xMaxF.size(); i++) xMaxArr[i] = xMaxF.get(i);
        for (int i = 0; i < yMinF.size(); i++) yMinArr[i] = yMinF.get(i);
        for (int i = 0; i < yMaxF.size(); i++) yMaxArr[i] = yMaxF.get(i);

        // ---- 材料 (RefractiveIndex) ----
        setIndexMaterial(model, comp, "mat_air", "Air", airArr, "n_air");
        setIndexMaterial(model, comp, "mat_slab", "Slab", new int[] {slabDom}, "n_slab");
        System.out.println("MATERIALS set");

        // ---- 物理场: emw (电磁波, 频域) ----
        model.component(comp).physics().create("emw", "ElectromagneticWaves", "geom1");
        // wee1 (Wave Equation, Electric) 由 emw 接口自动创建, 只需配置
        model.component(comp)
                .physics("emw")
                .feature("wee1")
                .set("DisplacementFieldModel", "RefractiveIndex");

        // 端口: 底部激励 port1, 顶部接收 port2 (Fresnel 标准 Periodic 端口配置)
        setPeriodicPort(model, comp, "port1", 1, port1F.get(0), true);
        setPeriodicPort(model, comp, "port2", 2, port2F.get(0), false);

        // 周期性条件: x/y 方向 Floquet (自动配对面)
        int[] xAllArr = new int[xMinF.size() + xMaxF.size()];
        for (int i = 0; i < xMinF.size(); i++) xAllArr[i] = xMinF.get(i);
        for (int i = 0; i < xMaxF.size(); i++) xAllArr[xMinF.size() + i] = xMaxF.get(i);
        int[] yAllArr = new int[yMinF.size() + yMaxF.size()];
        for (int i = 0; i < yMinF.size(); i++) yAllArr[i] = yMinF.get(i);
        for (int i = 0; i < yMaxF.size(); i++) yAllArr[yMinF.size() + i] = yMaxF.get(i);
        setFloquetPeriodic(model, comp, "pc1", xAllArr);
        setFloquetPeriodic(model, comp, "pc2", yAllArr);
        System.out.println("PHYSICS set");

        // ---- 网格: FreeTet + 局部细化 (介质板) + IdenticalMesh (周期对) ----
        model.component(comp).mesh().create("mesh1");
        com.comsol.model.MeshSequence m = model.component(comp).mesh("mesh1");
        // FreeTet + 局部细化 (介质板) + IdenticalMesh (Floquet 周期对需一致 mesh)
        com.comsol.model.MeshFeature id1 = m.create("id1", "IdenticalMesh");
        id1.selection("group1").geom("geom1", 2).set(xMinArr);
        id1.selection("group2").geom("geom1", 2).set(xMaxArr);
        com.comsol.model.MeshFeature id2 = m.create("id2", "IdenticalMesh");
        id2.selection("group1").geom("geom1", 2).set(yMinArr);
        id2.selection("group2").geom("geom1", 2).set(yMaxArr);
        com.comsol.model.MeshFeature ftet = m.create("ftet1", "FreeTet");
        com.comsol.model.MeshFeature s1 = ftet.create("size1", "Size");
        s1.set("custom", "on");
        s1.set("hmax", "mh");
        s1.set("hmin", "0.2[mm]");
        s1.set("hcurve", 0.6);
        s1.set("hgrad", 1.5);
        com.comsol.model.MeshFeature s2 = ftet.create("size2", "Size");
        s2.selection().geom("geom1", 3).set(new int[] {slabDom});
        s2.set("custom", "on");
        s2.set("hmax", "mh_slab");
        m.run();
        System.out.println("MESH built");

        // ---- 研究: 频域, 单频 f0 ----
        model.study().create("std1");
        model.study("std1").create("freq", "Frequency");
        model.study("std1").feature("freq").set("plist", "f0");
        model.study("std1").feature("freq").set("punit", "GHz");
        model.study("std1").createAutoSequences("freq");
        model.study("std1").run();
        System.out.println("SOLVE done");

        // ---- 结果: S 参数全局曲线 + CSV 导出 (1 行/频率) ----
        model.result().create("pg1", "PlotGroup1D");
        model.result("pg1").set("data", "dset1");
        model.result("pg1").create("glob1", "Global");
        model.result("pg1").feature("glob1").set("expr", new String[] {"emw.S11dB", "emw.S21dB"});
        model.result("pg1").feature("glob1").set("xdataexpr", "freq");
        model.result("pg1").feature("glob1").set("xdataunit", "GHz");
        model.result("pg1").run();

        // S 参数曲线 CSV 导出 (1D Global 图数据: 每行 xdata + 各表达式值)
        String csv = args.length > 1 ? args[1] : "EmwSlabFrequency.csv";
        model.result().export().create("data1", "Plot");
        model.result().export("data1").set("plotgroup", "pg1");
        model.result().export("data1").set("filename", csv);
        model.result().export("data1").run();

        String out = args.length > 0 ? args[0] : "EmwSlabFrequency.mph";
        model.save(out);
        System.out.println("EmwSlabFrequency_OK");
    }

    /** 折射率材料 (RefractiveIndex 模型), n 对角张量 */
    private static void setIndexMaterial(
            Model model, String comp, String tag, String name, int[] doms, String n) {
        model.component(comp).material().create(tag, "Common");
        model.component(comp).material(tag).label(name);
        model.component(comp).material(tag).selection().set(doms);
        com.comsol.model.Material mat = model.component(comp).material(tag);
        mat.materialModel().create("RefractiveIndex", "RefractiveIndex");
        mat.propertyGroup("RefractiveIndex")
                .set("n", new String[] {n, "0", "0", "0", n, "0", "0", "0", n});
    }

    /** Periodic 端口 (Fresnel 标准配置) */
    private static void setPeriodicPort(
            Model model, String comp, String tag, int name, int face, boolean excite) {
        model.component(comp).physics("emw").create(tag, "Port", 2);
        model.component(comp).physics("emw").feature(tag).selection().set(new int[] {face});
        model.component(comp).physics("emw").feature(tag).set("PortName", String.valueOf(name));
        model.component(comp)
                .physics("emw")
                .feature(tag)
                .set("PortExcitation", excite ? "on" : "off");
        model.component(comp).physics("emw").feature(tag).set("PortType", "Periodic");
        model.component(comp).physics("emw").feature(tag).set("SlitType", "PECBacked");
        model.component(comp).physics("emw").feature(tag).set("PortOrientation", "ForwardPort");
        model.component(comp).physics("emw").feature(tag).set("InputType", "E");
        model.component(comp)
                .physics("emw")
                .feature(tag)
                .set("Eampl", new String[][] {{"0"}, {"1"}, {"0"}});
        model.component(comp)
                .physics("emw")
                .feature(tag)
                .set("n", new String[] {"n_air", "0", "0", "0", "n_air", "0", "0", "0", "n_air"});
        model.component(comp).physics("emw").feature(tag).set("alpha1_inc", "alpha");
        model.component(comp).physics("emw").feature(tag).set("Pin", "1[W]");
    }

    /** Floquet 周期性条件 (从周期端口取 k) */
    private static void setFloquetPeriodic(Model model, String comp, String tag, int[] faces) {
        model.component(comp).physics("emw").create(tag, "PeriodicCondition", 2);
        model.component(comp).physics("emw").feature(tag).selection().set(faces);
        model.component(comp).physics("emw").feature(tag).set("PeriodicType", "Floquet");
        model.component(comp).physics("emw").feature(tag).set("Floquet_source", "FromPeriodicPort");
    }

    /** 面中心采样 (faceParamRange 中点, 平面足够) */
    private static double[] faceCenter(GeomInfo gi, int f) {
        double[] pr;
        try {
            pr = gi.faceParamRange(f);
        } catch (Exception e) {
            return null;
        }
        if (pr == null || pr.length < 2) return null;
        double u = (pr[0] + pr[1]) / 2, v = pr.length >= 4 ? (pr[2] + pr[3]) / 2 : 0.5;
        try {
            double[][] pts = gi.faceX(f, new double[][] {{u, v}});
            return (pts != null && pts.length > 0) ? pts[0] : null;
        } catch (Exception e) {
            try {
                double[][] pts = gi.faceX(f, new double[][] {{0.5, 0.5}});
                return (pts != null && pts.length > 0) ? pts[0] : null;
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /** 面内采样 N×N 网格点 (用于域 bbox 累积) */
    private static double[][] sampleFaceGrid(GeomInfo gi, int f, int n) {
        double[] pr;
        try {
            pr = gi.faceParamRange(f);
        } catch (Exception e) {
            return null;
        }
        if (pr == null || pr.length < 2) return null;
        java.util.List<double[]> out = new java.util.ArrayList<>();
        double u0 = pr[0], u1 = pr[1];
        double v0 = pr.length >= 4 ? pr[2] : 0, v1 = pr.length >= 4 ? pr[3] : 1;
        double du = (u1 - u0) / n, dv = (v1 - v0) / n;
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                try {
                    double[][] pts = gi.faceX(f, new double[][] {{u0 + i * du, v0 + j * dv}});
                    if (pts != null && pts.length > 0) out.add(pts[0]);
                } catch (Exception e) {
                }
            }
        }
        return out.toArray(new double[0][]);
    }
}
