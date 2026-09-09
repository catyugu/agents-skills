import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** TFinArrayStationary — 3D 散热片稳态传热 (Array 阵列翅片), 孤立翅 1D cosh 解析解 */
public class TFinArrayStationary {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";
        // 几何参数
        double baseX = 0.05, baseY = 0.02, baseZ = 0.005; // 基板尺寸
        double finT = 0.001, finY = baseY, finL = 0.10;   // 翅厚(x)/翅深(y)/翅高(z)
        double pitch = 0.0075;                             // 翅片间距
        int nFin = 5;                                      // 翅片数
        double x0 = 0.005;                                 // 首个翅片位置
        // 物性 (孤立翅 1D 解: k=200, h=25, m=16.2, mL=1.62, Bi_c=6.3e-5)
        double k = 200.0, h = 25.0;
        double T0 = 350.0, Tinf = 293.0;

        // 参数名避开 COMSOL 保留字: "h" 是内置变量名 (Duplicate parameter/variable name)。
        model.param().set("k_fin", k + "[W/(m*K)]", "热导率");
        model.param().set("h_conv", h + "[W/(m^2*K)]", "对流系数");
        model.param().set("T0", T0 + "[K]", "基板底部温度");
        model.param().set("Tinf", Tinf + "[K]", "环境温度");

        model.component().create(comp, true);
        model.component(comp).geom().create("geom1", 3);
        // 基板
        model.component(comp).geom("geom1").create("base", "Block");
        model.component(comp).geom("geom1").feature("base").set("pos", new double[] {0, 0, 0});
        model.component(comp).geom("geom1").feature("base").set("size", new double[] {baseX, baseY, baseZ});
        // 单个翅片
        model.component(comp).geom("geom1").create("fin1", "Block");
        model.component(comp).geom("geom1").feature("fin1").set("pos", new double[] {x0, 0, baseZ});
        model.component(comp).geom("geom1").feature("fin1").set("size", new double[] {finT, finY, finL});
        // Array 阵列 (新字符串, 探针已实证): nFin 个, x 向间距 pitch
        model.component(comp).geom("geom1").create("arr1", "Array");
        model.component(comp).geom("geom1").feature("arr1").selection("input").set(new String[] {"fin1"});
        model.component(comp).geom("geom1").feature("arr1").set("size", new String[] {nFin + "", "1", "1"});
        model.component(comp).geom("geom1").feature("arr1").set("displ", new String[] {pitch + "[m]", "0", "0"});
        // Union 合并基板+翅片为连通域 (Array 产生不相交块, 必须 Union 才能传热)
        model.component(comp).geom("geom1").create("uni1", "Union");
        model.component(comp).geom("geom1").feature("uni1").selection("input").set(new String[] {"base", "arr1"});
        model.component(comp).geom("geom1").feature("uni1").set("intbnd", "on");
        model.component(comp).geom("geom1").run();

        // 材料 (全域)
        model.component(comp).material().create("mat1", "Common");
        model.component(comp).material("mat1").propertyGroup("def").set("thermalconductivity", new String[][] {{"k_fin"}});

        // 传热
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");
        model.component(comp).physics("ht").feature("init1").set("T", "Tinf");

        GeomInfo gi = model.component(comp).geom("geom1");
        // 基板底面 (z=0) → Dirichlet T=T0
        int[] z0 = facesAtZ(gi, 0.0);
        int[] zTop = facesAtZ(gi, baseZ + finL);
        System.out.println("Z0=" + java.util.Arrays.toString(z0));
        System.out.println("ZTOP=" + java.util.Arrays.toString(zTop));
        model.component(comp).physics("ht").create("temp_bot", "TemperatureBoundary", 2);
        model.component(comp).physics("ht").feature("temp_bot").selection().set(z0);
        model.component(comp).physics("ht").feature("temp_bot").set("T0_src", "userdef");
        model.component(comp).physics("ht").feature("temp_bot").set("T0", "T0");

        // 其余外表面: 对流换热 h → Tinf (排除 z=0 底面)
        int[] conv = exteriorFacesExcept(gi, z0);
        System.out.println("CONV=" + java.util.Arrays.toString(conv));
        model.component(comp).physics("ht").create("hf_conv", "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature("hf_conv").selection().set(conv);
        model.component(comp).physics("ht").feature("hf_conv").set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp).physics("ht").feature("hf_conv").set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature("hf_conv").set("minput_temperature", "Tinf");
        model.component(comp).physics("ht").feature("hf_conv").set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature("hf_conv").set("h", "h_conv");

        // 网格
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(3);
        model.component(comp).mesh("mesh1").run();

        // 研究
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();
        System.out.println("TFINARRAY_SOLVE_OK");

        // 结果导出: CSV (T)
        String csvOut = args.length > 1 ? args[1] : "TFinArrayStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"T"});
        model.result().export("data1").run();

        // 方向 4 载荷 1: PlotGroup3D + Surface 温度图 + Image PNG 导出 (探针已验证)
        String imgPath = args.length > 2 ? args[2] : "TFinArrayStationary.png";
        model.result().create("pg3", "PlotGroup3D");
        model.result("pg3").set("data", "dset1");
        model.result("pg3").create("surf1", "Surface");
        model.result("pg3").feature("surf1").set("expr", "T");
        model.result("pg3").run();
        model.result().export().create("img1", "Image");
        model.result().export("img1").set("plotgroup", "pg3");
        model.result().export("img1").set("filename", imgPath);
        model.result().export("img1").run();
        System.out.println("TFINARRAY_IMAGE_DONE");

        // 方向 4 载荷 2: 派生值 — 体平均温度 (AvVolume)。
        // 实测: numerical().create("av1","AvVolume") 节点创建/序列化正常, 但
        // batch 上下文中 getReal() 返回未填充空表 [[0.0]] (computeResult/getReal(true)
        // 均无效)。Global 图也求不了空间场变量 T (S-参数是全局标量, T 是场)。
        // 结论: 保留 AvVolume 节点作为 API 模式演示; 体积平均由验证脚本从
        // field.csv 计算 (既有纪律: 域量用 CSV 导出离线分析)。
        model.result().numerical().create("av1", "AvVolume");
        model.result().numerical("av1").set("data", "dset1");
        model.result().numerical("av1").set("expr", new String[] {"T"});
        model.result().numerical("av1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "TFinArrayStationary.mph";
        model.save(outPath);
        System.out.println("TFINARRAY_OK");
    }

    private static int[] facesAtZ(GeomInfo gi, double zt) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int c = 0;
        for (int f = 1; f <= n; f++) {
            double[] ctr = sampleCenter(gi, f);
            if (ctr != null && Math.abs(ctr[2] - zt) < 1e-3) tmp[c++] = f;
        }
        return trim(tmp, c);
    }

    private static int[] exteriorFacesExcept(GeomInfo gi, int[] except) {
        int n = gi.getNFaces();
        int[][] up = gi.getUpDown();
        int[] faceDom = (up != null && up.length > 1) ? up[1] : null;
        int[] tmp = new int[n];
        int c = 0;
        for (int f = 1; f <= n; f++) {
            // 排除 except
            boolean skip = false;
            for (int e : except) if (e == f) { skip = true; break; }
            if (skip) continue;
            // 仅外部面 (邻接 1 域); getUpDown[1][f-1] = 面 f 的域或 -1
            if (faceDom != null && f - 1 < faceDom.length && faceDom[f - 1] < 0) continue;
            double[] ctr = sampleCenter(gi, f);
            if (ctr != null) tmp[c++] = f;
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
