import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** SmCylAxial — 3D空心圆柱轴向拉伸(M1), 精确单轴应力解 */
public class SmCylinderAxialStationary {
    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");
        String comp = "comp1";
        model.param().set("sigma_z", "1e6[Pa]", "轴向应力");
        model.component().create(comp, true);
        model.component(comp).geom().create("geom1", 3);
        model.component(comp).geom("geom1").create("cyl_out", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_out").set("r", 0.5);
        model.component(comp).geom("geom1").feature("cyl_out").set("h", 1.0);
        model.component(comp)
                .geom("geom1")
                .feature("cyl_out")
                .set("pos", new double[] {0, 0, -0.5});
        model.component(comp).geom("geom1").create("cyl_in", "Cylinder");
        model.component(comp).geom("geom1").feature("cyl_in").set("r", 0.3);
        model.component(comp).geom("geom1").feature("cyl_in").set("h", 1.0);
        model.component(comp).geom("geom1").feature("cyl_in").set("pos", new double[] {0, 0, -0.5});
        model.component(comp).geom("geom1").create("diff1", "Difference");
        model.component(comp)
                .geom("geom1")
                .feature("diff1")
                .selection("input")
                .set(new String[] {"cyl_out"});
        model.component(comp)
                .geom("geom1")
                .feature("diff1")
                .selection("input2")
                .set(new String[] {"cyl_in"});
        model.component(comp).geom("geom1").run();

        model.component(comp).physics().create("solid", "SolidMechanics", "geom1");
        model.component(comp).physics("solid").feature("lemm1").set("E_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("E", "200e9[Pa]");
        model.component(comp).physics("solid").feature("lemm1").set("nu_mat", "userdef");
        model.component(comp).physics("solid").feature("lemm1").set("nu", "0.3");

        GeomInfo gi = model.component(comp).geom("geom1");
        // 下端面(z=-0.5) Fixed, 上端面(z=0.5) z向拉伸
        int[] bottom = facesAtZ(gi, -0.5);
        int[] top = facesAtZ(gi, 0.5);
        System.out.println("BOTTOM=" + java.util.Arrays.toString(bottom));
        System.out.println("TOP=" + java.util.Arrays.toString(top));

        model.component(comp).physics("solid").create("fix1", "Fixed", 2);
        model.component(comp).physics("solid").feature("fix1").selection().set(bottom);
        model.component(comp).physics("solid").create("bl1", "BoundaryLoad", 2);
        model.component(comp).physics("solid").feature("bl1").selection().set(top);
        model.component(comp).physics("solid").feature("bl1").set("LoadType", "ForceArea");
        model.component(comp)
                .physics("solid")
                .feature("bl1")
                .set("FperArea", new String[] {"0", "0", "sigma_z"});

        model.component(comp).mesh().create("mesh1");
        model.component(comp).mesh("mesh1").autoMeshSize(3);
        model.component(comp).mesh("mesh1").run();
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();
        System.out.println("SMCYL_SOLVE_OK");
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        String csvOut = args.length > 1 ? args[1] : "SmCylinderAxialStationary.csv";
        model.result().export("data1").set("filename", csvOut);
        model.result().export("data1").set("expr", new String[] {"solid.mises", "solid.sz", "w"});
        model.result().export("data1").run();
        String outPath = args.length > 0 ? args[0] : "SmCylinderAxialStationary.mph";
        model.save(outPath);
        System.out.println("SMCYL_OK");
    }

    private static int[] facesAtZ(GeomInfo gi, double zt) {
        int n = gi.getNFaces();
        int[] tmp = new int[n];
        int c = 0;
        for (int f = 1; f <= n; f++) {
            double[] pr = gi.faceParamRange(f);
            double[] ctr = sampleCenter(gi, f, pr);
            if (ctr == null) continue;
            if (Math.abs(ctr[2] - zt) < 1e-3) tmp[c++] = f;
        }
        int[] r = new int[c];
        System.arraycopy(tmp, 0, r, 0, c);
        return r;
    }

    private static double[] sampleCenter(GeomInfo gi, int f, double[] pr) {
        try {
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
}
