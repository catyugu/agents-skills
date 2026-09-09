import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EcSquareStationary.java — 实验 E1: 稳态电流基准 (2D, ConductiveMedia)
 *
 * <p>物理: 电流 (ConductiveMedia, ec) — 本机 EC 接口 几何: 2D 方板 1 m x 1 m，中心在原点 边界: 左边界(x=-0.5) Terminal
 * Voltage V=1V，右边界(x=0.5) Ground 0V 边界编号由按坐标选边界方法确定（不依赖硬编码编号） 材料: Common 材料，electricconductivity =
 * 5.998e7 S/m 研究: Stationary
 *
 * <p>已验证解析解（本机 ECProbe 实测）: - V = 0.5 - x（线性，稳态无源导电场），与解析解 diff=0 - 电流密度 J = sigma*E = 5.998e7 * 1
 * = 5.998e7 A/m^2
 *
 * <p>本机关键证据: - ElectricCurrents 接口在本机报 Unknown physics interface（不可用） - ConductiveMedia 是本机 EC
 * 接口（IfaceProbe 验证: tags cucn1/ein1/init1/dcont1） - 材料属性名 electricconductivity（simple_resistor.mph
 * 证据） - ConductiveMedia 域特征: cucn1(CurrentConservation), ein1(ElectricInsulation) - Terminal 参数:
 * TerminalType=Voltage, V0; Ground 特征: gnd1
 *
 * <p>模块需求: ACDC 模块（ConductiveMedia） 运行: comsolcompile EcSquareStationary.java; comsolbatch
 * -inputfile ... -outputfile ... <savepath.mph> <csv-out> 参数: args[0]=mph 保存路径, args[1]=CSV 导出路径
 */
public class EcSquareStationary {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // 参数（带单位）
        model.param().set("sigma", "5.998e7[S/m]", "电导率");
        model.param().set("V0", "1[V]", "施加电压");

        // 组件
        String comp = "comp1";
        model.component().create(comp, true);

        // 几何: 2D 方板，中心在原点
        model.component(comp).geom().create("geom1", 2);
        model.component(comp).geom("geom1").create("sq1", "Square");
        model.component(comp).geom("geom1").feature("sq1").set("size", 1.0);
        model.component(comp).geom("geom1").feature("sq1").set("pos", new double[] {-0.5, -0.5});
        model.component(comp).geom("geom1").run();

        // 材料: 电导率（Common 材料 def 属性组，本机 simple_resistor.mph 证据）
        model.component(comp).material().create("mat1", "Common");
        model.component(comp)
                .material("mat1")
                .propertyGroup("def")
                .set("electricconductivity", new String[][] {{"sigma"}});

        // 物理场: ConductiveMedia (本机 EC 接口)
        model.component(comp).physics().create("ec", "ConductiveMedia", "geom1");

        // 边界: 左边界(x=-0.5) Terminal V=V0；右边界(x=0.5) Ground。
        // 按坐标选边界（GeomInfo.edgeX 采样中点坐标）
        int[] leftBnd = boundariesAtX(model.component(comp).geom("geom1"), -0.5);
        int[] rightBnd = boundariesAtX(model.component(comp).geom("geom1"), 0.5);
        requireBoundaries("left(x=-0.5)", leftBnd);
        requireBoundaries("right(x=0.5)", rightBnd);

        model.component(comp).physics("ec").create("term1", "Terminal", 1);
        model.component(comp).physics("ec").feature("term1").selection().set(leftBnd);
        model.component(comp).physics("ec").feature("term1").set("TerminalType", "Voltage");
        model.component(comp).physics("ec").feature("term1").set("V0", "V0");

        model.component(comp).physics("ec").create("gnd1", "Ground", 1);
        model.component(comp).physics("ec").feature("gnd1").selection().set(rightBnd);

        // 网格: 自由三角形，中等密度
        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(2);
        model.component(comp).mesh("mesh1").run();

        // 研究: 稳态
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();

        // 结果导出: CSV（V 与电流密度范数 ec.normJ）
        String csvOut = args.length > 1 ? args[1] : "EcSquareStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"V", "ec.normJ"});
        model.result().export("data1").run();

        // 保存
        String outPath = args.length > 0 ? args[0] : "EcSquareStationary.mph";
        model.save(outPath);

        System.out.println("E1EC_OK");
        System.out.println("TAG_ec=" + (model.component(comp).physics("ec") != null));
        System.out.println("LEFT_BND=" + java.util.Arrays.toString(leftBnd));
        System.out.println("RIGHT_BND=" + java.util.Arrays.toString(rightBnd));
    }

    /**
     * 按 x 坐标选择边界（适用于 x=const 的竖直边界）。 原理: GeomInfo.edgeX(edge, {0.5}) 返回边界中点坐标，匹配接近目标 x 的边界。 本机验证:
     * 方板(1m, 中心原点) edge1=(-0.5,0)=左, edge4=(0.5,0)=右。
     */
    private static int[] boundariesAtX(GeomInfo geom, double xTarget) {
        return boundariesMatching(geom, xTarget, 0);
    }

    /** 按 y 坐标选择边界（适用于 y=const 的水平边界）。 */
    private static int[] boundariesAtY(GeomInfo geom, double yTarget) {
        return boundariesMatching(geom, yTarget, 1);
    }

    /** 通用匹配: 遍历边界编号 1..N，采样中点坐标，检查目标维度是否接近目标值。 */
    private static int[] boundariesMatching(GeomInfo geom, double target, int coordDim) {
        double tol = 1e-6;
        int n = geom.getNEdges();
        int[] tmp = new int[n];
        int count = 0;
        for (int e = 1; e <= n; e++) {
            double[][] mid = geom.edgeX(e, new double[] {0.5});
            if (mid == null || mid.length == 0) continue;
            double[] pt = mid[0];
            if (pt == null || pt.length <= coordDim) continue;
            if (Math.abs(pt[coordDim] - target) < tol) {
                tmp[count++] = e;
            }
        }
        int[] result = new int[count];
        System.arraycopy(tmp, 0, result, 0, count);
        return result;
    }

    /** 断言边界存在且非空，否则抛异常。 */
    private static void requireBoundaries(String label, int[] edges) {
        if (edges == null || edges.length == 0) {
            throw new IllegalStateException(
                    "boundariesAt: no boundary matched for '" + label + "'");
        }
    }
}
