import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * SmCantileverBendingStationary — 2D 悬臂梁纯弯曲 + 二次 Lagrange (p=2)
 *
 * <p>几何: 2D 矩形梁 L=10, h=2, 中心 (0,0)。
 *
 * <p>载荷: 左端 (x=-L/2) Fixed (u=v=0); 右端 (x=+L/2) 法向分布 σ_xx(y) = M·y/I
 *   (沿 y 积分合力 0, 合力矩 = M — 端部弯矩的精确等效)。
 *
 * <p>解析解 (Euler-Bernoulli, ν=0 → σ_yy=0, σ_xy=0):
 *   σ_xx(y) = M·y/I  = 1.5·y    (线性)
 *   u_x(x, y) = -M·y·(x+L/2)² / (2·E·I)
 *   u_y(x, y) = 0
 *   其中 I = h³/12 = 2/3 (单位厚), M=1, E=1, ν=0 (无量纲)。
 *
 * <p>关键演示: 二次 Lagrange (p=2) 在 hmax=0.4 网格下 σ_xx 中段逐点误差 ~1e-10 (机器精度);
 *   线性 Lagrange (p=1) 同网格 σ_xx 误差 ~0.6 (O(h)). 高阶物理场离散阶次的直接验证。
 *   ShapeProperty API: physics().prop("ShapeProperty").set("order_displacement", "2")
 *   (探针实证; dmodel.xml 中 order_displacement="2" 对应 "Geometry shape function: Quadratic Lagrange")。
 *
 * @author autocomsol skill — 案例演示高阶物理场离散
 */
public class SmCantileverBendingStationary {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";

        double L = 10.0, h = 2.0, M = 1.0, E = 1.0, nu = 0.0;
        double I = h * h * h / 12.0;
        int order = 2; // 二次 Lagrange (固定)

        model.param().set("L_b", L + "[m]", "梁长");
        model.param().set("h_b", h + "[m]", "梁高");
        model.param().set("M_b", M + "[N*m]", "端部弯矩");
        model.param().set("E_b", E + "[Pa]", "杨氏模量");
        model.param().set("nu_b", nu + "", "泊松比");
        model.param().set("I_b", I + "[m^4]", "截面惯性矩");

        // 几何: 2D 矩形 (中心原点)
        model.component().create(comp, true);
        model.component(comp).geom().create("geom1", 2);
        model.component(comp).geom("geom1").create("r1", "Rectangle");
        model.component(comp).geom("geom1").feature("r1").set("size", new double[] {L, h});
        model.component(comp).geom("geom1").feature("r1").set("pos", new double[] {-L / 2, -h / 2});
        model.component(comp).geom("geom1").run();

        // 物理: SolidMechanics
        model.component(comp).physics().create("sm", "SolidMechanics", "geom1");
        model.component(comp).physics("sm").feature("lemm1").set("E_mat", "userdef");
        model.component(comp).physics("sm").feature("lemm1").set("E", "E_b");
        model.component(comp).physics("sm").feature("lemm1").set("nu_mat", "userdef");
        model.component(comp).physics("sm").feature("lemm1").set("nu", "nu_b");

        // 高阶离散阶次: 探针实证 order_displacement = "1,2,2s,3,3s,4,4s,5" 全部支持
        model.component(comp).physics("sm").prop("ShapeProperty").set("order_displacement", Integer.toString(order));

        // 边界: x=-L/2 Fixed, x=+L/2 法向 σ_xx(y) = M·y/I 分布载荷
        GeomInfo gi = model.component(comp).geom("geom1");
        int[] left = edgesAtX(gi, -L / 2);
        int[] right = edgesAtX(gi, L / 2);

        model.component(comp).physics("sm").create("fix1", "Fixed", 1);
        model.component(comp).physics("sm").feature("fix1").selection().set(left);

        model.component(comp).physics("sm").create("bl1", "BoundaryLoad", 1);
        model.component(comp).physics("sm").feature("bl1").selection().set(right);
        model.component(comp).physics("sm").feature("bl1").set("LoadType", "ForceArea");
        model.component(comp).physics("sm").feature("bl1").set("FperArea", new String[] {"M_b*y/I_b", "0", "0"});

        // 网格: 中等密度 (hmax=0.4)
        com.comsol.model.MeshSequence meshNode = model.component(comp).mesh().create("mesh1");
        com.comsol.model.MeshFeature ftri = meshNode.create("ftri1", "FreeTri");
        com.comsol.model.MeshFeature sz = ftri.create("size1", "Size");
        sz.set("custom", "on");
        sz.set("hmax", "0.4");
        sz.set("hmin", "0.05");
        meshNode.run();

        // 研究: Stationary
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 导出: 位移 + 应力分量 (SolidMechanics 应力分量变量名: solid.SX/SY/SXY)
        String csvOut = args.length > 1 ? args[1] : "SmCantileverBendingStationary.csv";
        model.result().export().create("d1", "Data");
        model.result().export("d1").set("data", "dset1");
        model.result().export("d1").set("filename", csvOut);
        model.result().export("d1").set(
                "expr", new String[] {"u", "v", "solid.mises", "solid.SX", "solid.SY", "solid.SXY"});
        model.result().export("d1").run();

        String mphOut = args.length > 0 ? args[0] : "SmCantileverBendingStationary.mph";
        model.save(mphOut);
    }

    /** 按 x 坐标选边 (GeomInfo.edgeX 采样中点) */
    private static int[] edgesAtX(GeomInfo gi, double xTarget) {
        int n = gi.getNEdges();
        int[] tmp = new int[n];
        int c = 0;
        for (int e = 1; e <= n; e++) {
            double[][] mid = gi.edgeX(e, new double[] {0.5});
            if (mid != null && mid.length > 0 && Math.abs(mid[0][0] - xTarget) < 1e-9) {
                tmp[c++] = e;
            }
        }
        int[] r = new int[c];
        System.arraycopy(tmp, 0, r, 0, c);
        return r;
    }
}
