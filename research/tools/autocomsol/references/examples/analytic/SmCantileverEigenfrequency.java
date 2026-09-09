import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** SmCantileverEigenfrequency — 3D 方形截面悬臂梁固有频率 (Euler-Bernoulli 解析解) */
public class SmCantileverEigenfrequency {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";
        double L = 1.5;    // 梁长
        double b = 0.1;    // 方形截面边长, b=h → 两个一阶弯曲模态退化
        double E = 200e9, nu = 0.3, rho = 7850;

        model.param().set("L_b", L + "[m]", "梁长");
        model.param().set("b_b", b + "[m]", "截面边长");
        model.param().set("rho_b", rho + "[kg/m^3]", "密度");

        model.component().create(comp, true);
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("blk1", "Block");
        model.component(comp).geom("geom1").feature("blk1").set("pos", new double[] {0, -b / 2, -b / 2});
        model.component(comp).geom("geom1").feature("blk1").set("size", new double[] {L, b, b});
        model.component(comp).geom("geom1").run();

        // 材料 (密度 + E/ν)
        model.component(comp).material().create("mat1", "Common");
        model.component(comp).material("mat1").propertyGroup("def").set("density", new String[][] {{"rho_b"}});
        model.component(comp).physics().create("solid", "SolidMechanics", "geom1");
        model.component(comp).physics("solid").feature("lemm1").set("E_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("E", E + "[Pa]");
        model.component(comp).physics("solid").feature("lemm1").set("nu_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("nu", nu + "");

        // 固定端 (x=0)
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] x0 = facesAtX(gi, 0.0);
        int[] xL = facesAtX(gi, L);
        System.out.println("X0=" + java.util.Arrays.toString(x0));
        System.out.println("XL=" + java.util.Arrays.toString(xL));
        model.component(comp).physics("solid").create("fix1", "Fixed", 2);
        model.component(comp).physics("solid").feature("fix1").selection().set(x0);

        // 网格: 显式 Size 控制 (hmax≈b/3 避免软化弯曲模态, 已验证配方)
        model.component(comp).mesh().create("mesh1");
        com.comsol.model.MeshFeature ftet = model.component(comp).mesh("mesh1").create("ftet1", "FreeTet");
        com.comsol.model.MeshFeature gsize = ftet.create("size1", "Size");
        gsize.set("custom", "on");
        gsize.set("hmax", "0.035[m]");
        gsize.set("hmin", "0.01[m]");
        gsize.set("hgrad", 1.5);
        model.component(comp).mesh("mesh1").run();

        // 研究: Eigenfrequency (新字符串, 挖掘 ladder_frame.mph 已证 op="Eigenfrequency")
        model.study().create("std1");
        model.study("std1").create("eig", "Eigenfrequency");
        model.study("std1").feature("eig").set("neigsactive", "on");
        model.study("std1").feature("eig").set("neigs", "4");
        model.study("std1").createAutoSequences("eig");
        model.study("std1").run();
        System.out.println("SMCANTEIG_SOLVE_OK");

        // 结果: 各模态频率经 Global 图 + Plot 导出 (EmwSlab 范式)
        // xdataexpr 不设 → 默认按解序号 (模态索引) 作为 x 轴
        model.result().create("pg1", "PlotGroup1D");
        model.result("pg1").set("data", "dset1");
        model.result("pg1").create("glob1", "Global");
        model.result("pg1").feature("glob1").set("expr", new String[] {"freq"});
        model.result("pg1").run();
        String csvOut = args.length > 1 ? args[1] : "SmCantileverEigenfrequency.csv";
        model.result().export().create("data1", "Plot");
        model.result().export("data1").set("plotgroup", "pg1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").run();

        // 模态位移导出 (形状识别用: 主位移轴分类)。
        // SolidMechanics 位移变量不带 solid. 前缀 (u/v/w), 本机已验证 (SmCylinder)。
        model.result().export().create("data2", "Data");
        model.result().export("data2").set("data", "dset1");
        String dispOut = args.length > 2 ? args[2] : "modes.csv";
        model.result().export("data2").set("filename", dispOut);
        model.result().export("data2").set("expr", new String[] {"u", "v", "w"});
        model.result().export("data2").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "SmCantileverEigenfrequency.mph";
        model.save(outPath);
        System.out.println("SMCANTEIG_OK");
    }

    private static int[] facesAtX(GeomInfo gi, double xt) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int c = 0;
        for (int f = 1; f <= n; f++) {
            double[] ctr = sampleCenter(gi, f);
            if (ctr != null && Math.abs(ctr[0] - xt) < 1e-3) tmp[c++] = f;
        }
        return trim(tmp, c);
    }

    private static double[] sampleCenter(GeomInfo gi, int f) {
        try {
            double[] pr = gi.faceParamRange(f);
            if (pr == null || pr.length < 2) return null;
            double p0 = (pr[0] + pr[1]) / 2, p1 = (pr.length >= 4) ? (pr[2] + pr[3]) / 2 : 0.5;
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

    private static int[] trim(int[] arr, int len) {
        int[] r = new int[len];
        System.arraycopy(arr, 0, r, 0, len);
        return r;
    }
}
