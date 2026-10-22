import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** SmPlateHoleStationary — 3D 中心圆孔张力板 (Kirsch 应力集中), 精确解析 Kt=3 */
public class SmPlateHoleStationary {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";
        double a = 0.05; // 孔径 (quarter model: 圆心在原点角)
        double W = 0.5; // 板半宽 (quarter 模型边长), a/W=0.1 ≤ 0.2
        double t = 0.02; // 板厚, t/a=0.4 (薄板近似 plane stress)
        double sigma = 1e6; // 远端单轴应力 σ∞ (Pa), 沿 +x
        double E = 200e9, nu = 0.3;

        model.component().create(comp, true);
        model.component(comp).geom().create("geom1", 3);
        // Block: x∈[0,W], y∈[0,W], z∈[0,t]
        model.component(comp).geom("geom1").create("blk1", "Block");
        model.component(comp).geom("geom1").feature("blk1").set("pos", new double[] {0, 0, 0});
        model.component(comp).geom("geom1").feature("blk1").set("size", new double[] {W, W, t});
        // Cylinder: 半径 a, 轴 +z, 底在 z=0 → 切出 quarter 孔
        model.component(comp).geom("geom1").create("cyl1", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl1").set("r", a);
        model.component(comp).geom("geom1").feature("cyl1").set("h", t);
        model.component(comp).geom("geom1").feature("cyl1").set("pos", new double[] {0, 0, 0});
        // 3D Difference (2D 已验证; 3D 布尔为本次新维度)
        model.component(comp).geom("geom1").create("diff1", "Difference");
        model.component(comp).geom("geom1").feature("diff1").selection("input").set(new String[] {"blk1"});
        model.component(comp).geom("geom1").feature("diff1").selection("input2").set(new String[] {"cyl1"});
        model.component(comp).geom("geom1").run();

        model.component(comp).physics().create("solid", "SolidMechanics", "geom1");
        model.component(comp).physics("solid").feature("lemm1").set("E_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("E", E + "[Pa]");
        model.component(comp).physics("solid").feature("lemm1").set("nu_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("nu", nu + "");

        GeomInfo gi = model.component(comp).geom("geom1");
        int[] faceX0 = facesAtX(gi, 0.0); // x=0 对称面
        int[] faceY0 = facesAtY(gi, 0.0); // y=0 对称面
        int[] faceXW = facesAtX(gi, W); // x=W 加载面
        int[] holeFaces = facesAtRadius(gi, a); // 孔面 (局部加密)
        System.out.println("X0=" + java.util.Arrays.toString(faceX0));
        System.out.println("Y0=" + java.util.Arrays.toString(faceY0));
        System.out.println("XW=" + java.util.Arrays.toString(faceXW));
        System.out.println("HOLE=" + java.util.Arrays.toString(holeFaces));

        // 对称面: Roller (法向零位移)
        model.component(comp).physics("solid").create("sym_x", "Roller", 2);
        model.component(comp).physics("solid").feature("sym_x").selection().set(faceX0);
        model.component(comp).physics("solid").create("sym_y", "Roller", 2);
        model.component(comp).physics("solid").feature("sym_y").selection().set(faceY0);
        // x=W: 单轴拉伸
        model.component(comp).physics("solid").create("bl1", "BoundaryLoad", 2);
        model.component(comp).physics("solid").feature("bl1").selection().set(faceXW);
        model.component(comp).physics("solid").feature("bl1").set("LoadType", "ForceArea");
        model.component(comp).physics("solid").feature("bl1").set("FperArea", new String[] {sigma + "", "0", "0"});
        // 抑制刚体运动 (z 平移等; x/y 平移与转动已被两个对称面约束)
        model.component(comp).physics("solid").create("rms1", "RigidMotionSuppression", 3);
        model.component(comp).physics("solid").feature("rms1").selection().set(allDomains(gi));

        // 显式网格: FreeTet + 全局 Size + 孔面局部加密
        model.component(comp).mesh().create("mesh1");
        com.comsol.model.MeshFeature ftet = model.component(comp).mesh("mesh1").create("ftet1", "FreeTet");
        com.comsol.model.MeshFeature gsize = ftet.create("size_glob", "Size");
        gsize.set("custom", "on");
        gsize.set("hmax", "0.01[m]");
        gsize.set("hmin", "0.001[m]");
        gsize.set("hgrad", 1.5);
        com.comsol.model.MeshFeature hsize = ftet.create("size_hole", "Size");
        hsize.selection().geom("geom1", 2).set(holeFaces);
        hsize.set("custom", "on");
        hsize.set("hmax", "3[mm]");
        model.component(comp).mesh("mesh1").run();

        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();
        System.out.println("SMPLATEHOLE_SOLVE_OK");

        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        String csvOut = args.length > 1 ? args[1] : "SmPlateHoleStationary.csv";
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"solid.sx", "solid.sy", "solid.sxy", "solid.mises"});
        model.result().export("data1").run();
        String outPath = args.length > 0 ? args[0] : "SmPlateHoleStationary.mph";
        model.save(outPath);
        System.out.println("SMPLATEHOLE_OK");
    }

    private static int[] allDomains(GeomInfo gi) {
        int n = gi.getNDomains();
        int[] r = new int[n];
        for (int d = 1; d <= n; d++) r[d - 1] = d;
        return r;
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

    private static int[] facesAtY(GeomInfo gi, double yt) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int c = 0;
        for (int f = 1; f <= n; f++) {
            double[] ctr = sampleCenter(gi, f);
            if (ctr != null && Math.abs(ctr[1] - yt) < 1e-3) tmp[c++] = f;
        }
        return trim(tmp, c);
    }

    private static int[] facesAtRadius(GeomInfo gi, double r) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int c = 0;
        for (int f = 1; f <= n; f++) {
            double[] ctr = sampleCenter(gi, f);
            if (ctr != null) {
                double rr = Math.sqrt(ctr[0] * ctr[0] + ctr[1] * ctr[1]);
                if (Math.abs(rr - r) < 1e-2) tmp[c++] = f;
            }
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
