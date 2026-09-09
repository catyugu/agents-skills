import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * TmSlabNonlinear.java — 实验 TmSlab: 3D 平板非线性导热稳态（k(T) 温度相关, 变量变换解析解）
 *
 * <p>几何: 单块立方体 (Block L=0.2m, 中心原点) → 单域。
 *
 * <p>物理: 纯固体传热 HeatTransfer (ht), 稳态 Stationary。
 *   - 材料: 非线性热导率 k(T)=k0·(1+beta·(T-Tref)), beta=0.004[1/K] (材料属性表达式直接引用 T)
 *   - x=-L/2 面: TemperatureBoundary T=T1=600K (左端高温)
 *   - x=+L/2 面: TemperatureBoundary T=T2=300K (右端低温)
 *   - 其余面默认绝热
 *
 * <p>解析解 (变量变换法): 引入 phi=T+beta·T²/2-beta·Tref·T, 则 d(phi)/dx 线性;
 *   phi(x)=phi1+(phi2-phi1)·(x+L/2)/L 由两端 phi 值确定。 反解
 *   T=(-a+sqrt(a²+2·beta·phi))/beta, a=1-beta·Tref。 与线性解 (beta=0) 对比:
 *   中点偏差约 26.8K, 非线性效果显著。
 *
 * <p>本机证据: - 非线性材料属性: propertyGroup("def").set("thermalconductivity",
 *   "k0*(1+beta*(T-Tref))") 直接写表达式 - Dirichlet BC: TemperatureBoundary +
 *   T0_src=userdef + T0 - 面选择: faceParamRange + faceX 采样面心, 按 x 坐标分类
 *
 * <p>验证在 scripts/verifications/analytic/tm_slab_nonlinear.py。
 * 运行: python scripts/run.py all TmSlabNonlinear <run-dir>
 * 参数: args[0]=mph 保存路径, args[1]=CSV 导出路径
 */
public class TmSlabNonlinear {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("L", "0.2[m]", "平板尺寸 (x 方向)");
        model.param().set("T1", "600[K]", "左端温度");
        model.param().set("T2", "300[K]", "右端温度");
        model.param().set("Tref", "293[K]", "参考温度 (k=k0 at Tref)");
        model.param().set("k0", "50[W/(m*K)]", "参考热导率");
        model.param().set("beta", "0.004[1/K]", "热导率温度系数 (k=k0*(1+beta*(T-Tref)))");

        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 立方体平板
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("blk1", "Block");
        model.component(comp).geom("geom1").feature("blk1").set("size", new String[] {"L", "L", "L"});
        model.component(comp).geom("geom1").feature("blk1").set("pos", new String[] {"-L/2", "-L/2", "-L/2"});
        model.component(comp).geom("geom1").run();

        GeomInfo gi = model.component(comp).geom("geom1");

        // 材料: 非线性热导率 k(T)=k0·(1+beta·(T-Tref))
        model.component(comp).material().create("mat1", "Common");
        model.component(comp).material("mat1").propertyGroup("def").set("thermalconductivity", "k0*(1+beta*(T-Tref))");

        // 传热: HeatTransfer (纯固体传热)
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");

        // 按 x 坐标选面: 左端 (x=-0.1) + 右端 (x=+0.1)
        int[] xMinus = facesAtX(gi, -0.1);
        int[] xPlus = facesAtX(gi, 0.1);
        requireFaces("x=-0.1", xMinus);
        requireFaces("x=+0.1", xPlus);
        System.out.println("X_MINUS=" + java.util.Arrays.toString(xMinus));
        System.out.println("X_PLUS=" + java.util.Arrays.toString(xPlus));

        // 左端: Dirichlet T=T1
        model.component(comp).physics("ht").create("temp1", "TemperatureBoundary", 2);
        model.component(comp).physics("ht").feature("temp1").selection().set(xMinus);
        model.component(comp).physics("ht").feature("temp1").set("T0_src", "userdef");
        model.component(comp).physics("ht").feature("temp1").set("T0", "T1");

        // 右端: Dirichlet T=T2
        model.component(comp).physics("ht").create("temp2", "TemperatureBoundary", 2);
        model.component(comp).physics("ht").feature("temp2").selection().set(xPlus);
        model.component(comp).physics("ht").feature("temp2").set("T0_src", "userdef");
        model.component(comp).physics("ht").feature("temp2").set("T0", "T2");

        // 网格: 自由四面体
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(3);
        model.component(comp).mesh("mesh1").run();

        // 研究: 稳态 Stationary
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 结果导出: CSV（T）
        String csvOut = args.length > 1 ? args[1] : "TmSlabNonlinear.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"T"});
        model.result().export("data1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "TmSlabNonlinear.mph";
        model.save(outPath);

        System.out.println("TmSlabNonlinear_OK");
        System.out.println("TAG_ht=" + (model.component(comp).physics("ht") != null));
    }

    /** 按 x 坐标选面（左右端面）。 */
    private static int[] facesAtX(GeomInfo gi, double xt) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int count = 0;
        for (int f = 1; f <= n; f++) {
            double[] pr = gi.faceParamRange(f);
            double[] ctr = sampleFaceCenter(gi, f, pr);
            if (ctr == null) continue;
            if (Math.abs(ctr[0] - xt) < 1e-3) tmp[count++] = f;
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
