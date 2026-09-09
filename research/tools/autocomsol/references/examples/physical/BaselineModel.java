import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * BaselineModel.java — 最小 Java 建模基线
 *
 * <p>目的: 验证 comsolcompile 能编译、comsolbatch 能运行一个最小 COMSOL Java 模型。 该模型只创建 Model 对象、一个 2D
 * 组件和一个几何，不求解，仅验证: - 环境/类路径正确 - API 调用链可用 - 模型可保存为 .mph
 *
 * <p>用法: comsolcompile BaselineModel.java; comsolbatch -inputfile BaselineModel.class ... 模块需求:
 * COMSOL Multiphysics 基础（无额外模块）
 */
public class BaselineModel {

    public static void main(String[] args) throws Exception {
        // 创建模型。标签 (tag) 使用稳定命名约定。
        Model model = ModelUtil.create("Model");

        // 组件标签
        String comp = "comp1";

        // 创建 2D 组件
        model.component().create(comp, true); // create(String tag, boolean geometric)

        // 几何序列
        model.component(comp).geom().create("geom1", 2);

        // 创建 2D 单位正方形，边长 1 m，中心在原点
        // 本机错误证据: Square 的 size 属性是标量(边长) "Expected a finite real
        // number, Expected unit is: m"
        model.component(comp).geom("geom1").create("sq1", "Square");
        model.component(comp).geom("geom1").feature("sq1").set("size", 1.0);
        model.component(comp).geom("geom1").feature("sq1").set("pos", new double[] {-0.5, -0.5});

        // 构建几何
        model.component(comp).geom("geom1").run();

        // 保存模型。用命令行参数传入路径，避免 "No filename given" 错误。
        // 本机错误证据: model.save() 无参数 → "No filename given."
        String outPath = args.length > 0 ? args[0] : "BaselineModel.mph";
        model.save(outPath);

        // 打印状态，供日志扫描
        System.out.println("BASELINE_OK");
        System.out.println("TAG_comp1_exists=" + (model.component("comp1") != null));
    }
}
