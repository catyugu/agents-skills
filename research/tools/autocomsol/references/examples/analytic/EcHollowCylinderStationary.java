import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EcHollowCylinderStationary.java — 案例 EcHollowCylinderStationary: 3D 空心圆柱导电稳态,
 * 同时演示**二阶几何网格导出**。
 *
 * <p>几何: 外圆柱 r_out 减去内圆柱 r_in (高 hcyl)。内/外表面是曲面, 所以导出的
 * .mphtxt 里单元是二阶几何单元 (edg2/tri2/tet2, 等参 P2 几何), 边中点落在真实圆柱面上。
 *
 * <p>物理: 电流 ConductiveMedia (ec) — 内表面 Terminal V=V0, 外表面 Ground, 端面默认绝缘。
 * 网格: FreeTet + Size(hmax, hmin)。研究: Stationary。
 *
 * <p>解析解: 端面绝缘时电位只沿径向变化, V(r) = V0·ln(r/r_out)/ln(r_in/r_out)。
 * 二阶几何使曲面单元边中点严格落在 r=r_in / r=r_out 上, 因此本案例的网格检查可以
 * 用同一个解析圆柱面 (r=R) 同时判定"导出的是 P2 几何"与"边中点不是弦中点"。
 *
 * <p>本机关键证据 (探针 ApiProbes, 2026-09-17):
 * <ul>
 *   <li>几何形状阶次: component().sorder("automatic"|"linear"|"quadratic"|"cubic"|"quartic"),
 *       默认 "automatic"; 数值字符串 "2" 被拒 ("Invalid geometry shape function")。</li>
 *   <li>导出单元阶次由几何形状阶次决定: 曲面几何 + automatic/quadratic → edg2/tri2/tet2;
 *       sorder("linear") → edg/tri/tet。cubic/quartic 下导出**仍是** P2 (Mesh 导出上限为二阶)。</li>
 *   <li>平面几何 (Block) 即使 sorder("quadratic") 也导出 edg/tri/tet: 没有曲面实体就没有曲单元。</li>
 *   <li>Mesh 导出需要已求解的数据集: 无研究时 run() 不抛异常但**不写文件**。</li>
 *   <li>导出文件块: vtx/edg2/tri2/tet2 每单元 1/3/6/10 个节点, 中点节点排在角点之后。</li>
 * </ul>
 *
 * <p>验证在 scripts/verifications/analytic/ec_hollow_cylinder_stationary.py。
 * 运行: python scripts/run.py all EcHollowCylinderStationary <run-dir>
 * 参数: args[0]=mph 保存路径, args[1]=CSV 导出路径, args[2]=.mphtxt 导出路径
 */
public class EcHollowCylinderStationary {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("r_in", "0.01[m]", "内半径");
        model.param().set("r_out", "0.03[m]", "外半径");
        model.param().set("hcyl", "0.02[m]", "高度");
        model.param().set("V0", "1[V]", "内表面电压");
        model.param().set("sigma", "1[S/m]", "电导率");
        model.param().set("hmax", "0.003[m]", "最大单元尺寸");
        model.param().set("hmin", "0.0015[m]", "最小单元尺寸");

        String comp = "comp1";
        model.component().create(comp, true);
        // 二阶几何: 曲面几何默认 automatic 已给 P2, 显式声明使导出阶次与案例意图绑定
        model.component(comp).sorder("quadratic");

        // 几何: 外圆柱 - 内圆柱
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("cyl1", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl1").set("r", "r_out");
        model.component(comp).geom("geom1").feature("cyl1").set("h", "hcyl");
        model.component(comp).geom("geom1").create("cyl2", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl2").set("r", "r_in");
        model.component(comp).geom("geom1").feature("cyl2").set("h", "hcyl");
        model.component(comp).geom("geom1").create("dif1", "Difference");
        model.component(comp).geom("geom1").feature("dif1").selection("input").set(new String[] {"cyl1"});
        model.component(comp).geom("geom1").feature("dif1").selection("input2").set(new String[] {"cyl2"});
        model.component(comp).geom("geom1").run();

        // 面识别: 按采样点到轴的距离分类 (不假设实体编号)。
        // 布尔运算把每个圆柱面切成 4 片, 必须取该半径上的全部面, 只取一片会漏掉大部分边界。
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] innerFaces = facesAtRadius(gi, 0.01);
        int[] outerFaces = facesAtRadius(gi, 0.03);
        requireFaces("inner(r=r_in)", innerFaces);
        requireFaces("outer(r=r_out)", outerFaces);
        System.out.println(
                "INNER=" + java.util.Arrays.toString(innerFaces) + " OUTER=" + java.util.Arrays.toString(outerFaces));

        // 材料
        model.component(comp).material().create("mat1", "Common");
        model.component(comp).material("mat1").selection().set(new int[] {1});
        model.component(comp).material("mat1").propertyGroup("def").set(
                "electricconductivity", new String[][] {{"sigma"}});
        model.component(comp).material("mat1").propertyGroup("def").set("relpermittivity", new String[][] {{"1"}});

        // 物理场: 电流
        model.component(comp).physics().create("ec", "ConductiveMedia", "geom1");
        model.component(comp).physics("ec").create("term1", "Terminal", 2);
        model.component(comp).physics("ec").feature("term1").selection().set(innerFaces);
        model.component(comp).physics("ec").feature("term1").set("TerminalType", "Voltage");
        model.component(comp).physics("ec").feature("term1").set("V0", "V0");
        model.component(comp).physics("ec").create("gnd1", "Ground", 2);
        model.component(comp).physics("ec").feature("gnd1").selection().set(outerFaces);

        // 网格: 自由四面体 + 尺寸
        model.component(comp).mesh().create("mesh1");
        com.comsol.model.MeshFeature ftet1 = model.component(comp).mesh("mesh1").create("ftet1", "FreeTet");
        com.comsol.model.MeshFeature size1 = ftet1.create("size1", "Size");
        size1.set("custom", "on");
        size1.set("hmax", "hmax");
        size1.set("hmin", "hmin");
        model.component(comp).mesh("mesh1").run();

        // 研究: 稳态（Mesh 导出需要已求解数据集, 故先求解）
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 结果导出: 场 (CSV) 与网格 (mphtxt)
        String csvOut = args.length > 1 ? args[1] : "EcHollowCylinderStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"V"});
        model.result().export("data1").run();

        String meshOut = args.length > 2 ? args[2] : "mesh.mphtxt";
        model.result().export().create("mesh1", "Mesh");
        model.result().export("mesh1").set("data", "dset1");
        model.result().export("mesh1").set("filename", meshOut);
        model.result().export("mesh1").run();

        String outPath = args.length > 0 ? args[0] : "EcHollowCylinderStationary.mph";
        model.save(outPath);

        System.out.println("EcHollowCylinderStationary_OK");
    }

    /**
     * 按到 z 轴的距离选面（圆柱面: r=sqrt(x²+y²)），返回该半径上的**全部**面。
     *
     * <p>面参数域中点的 faceX 采样可能落在参数域外 (Difference 产生的面参数域非规则),
     * 采样失败时按面中心回退; 仍失败则视为不匹配。
     */
    private static int[] facesAtRadius(GeomInfo gi, double rTarget) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int count = 0;
        for (int f = 1; f <= n; f++) {
            double[] c = sampleFaceCenter(gi, f);
            if (c == null) continue;
            double r = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
            if (Math.abs(r - rTarget) < 1e-6) tmp[count++] = f;
        }
        int[] result = new int[count];
        System.arraycopy(tmp, 0, result, 0, count);
        return result;
    }

    /** 断言面集合非空。 */
    private static void requireFaces(String label, int[] faces) {
        if (faces == null || faces.length == 0) {
            throw new IllegalStateException("no face matched for '" + label + "'");
        }
    }

    /** 采样面中心坐标（参数范围中点; 参数域非规则时回退到 (0.5,0.5)）。 */
    private static double[] sampleFaceCenter(GeomInfo gi, int f) {
        double[] pr;
        try {
            pr = gi.faceParamRange(f);
        } catch (Exception e) {
            return null;
        }
        try {
            double p0 = (pr[0] + pr[1]) / 2;
            double p1 = (pr.length >= 4) ? (pr[2] + pr[3]) / 2 : 0.5;
            double[][] pts = gi.faceX(f, new double[][] {{p0, p1}});
            if (pts != null && pts.length > 0) return pts[0];
        } catch (Exception e) {
            // 中点超出参数域 → 回退
        }
        try {
            double[][] pts = gi.faceX(f, new double[][] {{0.5, 0.5}});
            if (pts != null && pts.length > 0) return pts[0];
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
