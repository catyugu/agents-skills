import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.physics.PhysicsField;
import com.comsol.model.physics.PhysicsProp;
import com.comsol.model.util.ModelUtil;
import java.util.Arrays;

/**
 * FieldDiscretizationDemonstration — 非标量场的有限元离散类型控制 (API 用法演示)。
 *
 * <p>本案例只演示 API 契约，不含任何验证检查、求解与导出。
 *
 * <p>演示要点:
 *
 * <ol>
 *   <li><b>自由度归属</b>: 场的分量由物理接口的 field() 声明，而不是由网格或几何决定。
 *       <pre>physics("solid").field("displacement") -&gt; fieldname [u, v, w]</pre>
 *       非标量场 = 多分量场；标量场 (T, V, p) 只有一个分量。
 *   <li><b>离散阶次入口</b>: 挂在物理接口节点的 ShapeProperty 上，键名 = "order_" + 场标识。
 *       <pre>physics("solid").prop("ShapeProperty").set("order_displacement", "2s")</pre>
 *       分量的阶次不单独设置；整个场共用同一阶次与单元族。
 *   <li><b>合法取值由接口族决定</b>: 用 getAllowedPropertyValues("order_&lt;场&gt;") 查询， 不要跨接口套用。
 *       <ul>
 *         <li>矢量/标量 Lagrange (SolidMechanics, Shell, PressureAcoustics, HeatTransfer): {@code 1, 2,
 *             2s, 3, 3s, 4, 4s, 5}，'s' = serendipity (单纯形上等于同阶 Lagrange， 四边形/六面体上自由度更少)。
 *         <li>curl 元 (ElectromagneticWaves* 的电场): {@code 1, 1t2, 2, 2t2, ..., 7t2}， 't2' = curl
 *             type 2 元 (H(curl) 场的切向连续单元族)。
 *         <li>边界元接口 (ElectrostaticsBoundaryElements 等): 键名是 shapeorder， 取值 {@code p11, p21, p22,
 *             p32, p33, p43, p44, p54, p55}。
 *         <li>不可压缩流 (LaminarFlow): 只有一个 order_fluid 键，速度与压力同阶。
 *       </ul>
 *   <li><b>读回与自省</b>: {@code prop.properties()}, {@code prop.hasProperty(key)}, {@code
 *       prop.getString(key)}。
 *   <li><b>与几何形状阶次无关</b>: {@code component().sorder(...)} 只改几何/网格的形状函数 (二阶曲单元)，不改物理场的离散阶次；反之
 *       ShapeProperty 也不改网格。
 * </ol>
 *
 * <p>运行: {@code python scripts/run.py all FieldDiscretizationDemonstration <run-dir> <mph>}
 */
public class FieldDiscretizationDemonstration {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";

        model.component().create(comp, true);
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("blk1", "Block");
        model.component(comp).geom("geom1").feature("blk1").set("size", new double[] {0.1, 0.1, 0.1});
        model.component(comp).geom("geom1").run();

        // 几何/网格形状阶次: 与场的离散阶次是两个独立的旋钮
        System.out.println("GEOM_SORDER = " + model.component(comp).sorder());

        // 非标量场 (矢量) 与标量场对照
        model.component(comp).physics().create("solid", "SolidMechanics", "geom1");
        model.component(comp).physics().create("ewfd", "ElectromagneticWavesFrequencyDomain", "geom1");
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");

        showDiscretization(model, comp, "solid");
        showDiscretization(model, comp, "ewfd");
        showDiscretization(model, comp, "ht");

        // 设置阶次: 矢量 Lagrange 与 curl 元各演示一次改值 + 读回
        setOrder(model, comp, "solid", "order_displacement", "1");
        setOrder(model, comp, "solid", "order_displacement", "2s");
        setOrder(model, comp, "ewfd", "order_electricfield", "1");
        setOrder(model, comp, "ewfd", "order_electricfield", "2t2");
        setOrder(model, comp, "ht", "order_temperature", "3");

        // 分量的阶次不单独设置: 分量名不是合法键
        try {
            model.component(comp).physics("solid").prop("ShapeProperty").set("order_u", "2");
            System.out.println("ORDER_COMPONENT_KEY = accepted");
        } catch (Throwable t) {
            System.out.println("ORDER_COMPONENT_KEY = rejected :: " + firstLine(t));
        }

        String mphOut = args.length > 0 ? args[0] : "FieldDiscretizationDemonstration.mph";
        model.save(mphOut);
        System.out.println("FieldDiscretizationDemonstration_OK " + mphOut);
    }

    /** 打印一个物理接口的场清单与 ShapeProperty 的离散阶次契约。 */
    private static void showDiscretization(Model model, String comp, String tag) {
        Physics ph = model.component(comp).physics(tag);
        System.out.println("== " + tag + " (" + ph.getType() + ")");
        for (String ft : ph.field().tags()) {
            PhysicsField f = ph.field(ft);
            String[] comps = f.component();
            System.out.println("   field " + ft + ": " + f.field() + " -> " + Arrays.toString(f.fieldname())
                    + (comps.length > 1 ? " [vector, " + comps.length + " components]" : " [scalar]"));
        }
        PhysicsProp sp = ph.prop("ShapeProperty");
        for (String key : sp.properties()) {
            if (!key.startsWith("order_")) continue;
            System.out.println("   " + key + " = " + sp.getString(key)
                    + "  allowed=" + Arrays.toString(sp.getAllowedPropertyValues(key)));
        }
    }

    /** 设置一个场的离散阶次并读回。 */
    private static void setOrder(Model model, String comp, String tag, String key, String code) {
        PhysicsProp sp = model.component(comp).physics(tag).prop("ShapeProperty");
        try {
            sp.set(key, code);
            System.out.println("SET " + tag + "." + key + " = " + code + " -> readback " + sp.getString(key));
        } catch (Throwable t) {
            System.out.println("SET " + tag + "." + key + " = " + code + " REJECTED :: " + firstLine(t));
        }
    }

    private static String firstLine(Throwable t) {
        return String.valueOf(t).split("\n")[0];
    }
}
