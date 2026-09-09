import com.comsol.model.GeomInfo;
import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * EcTSmBusbarStationary: 铜母线 + 钛螺栓 稳态电→热→结构耦合（三场，含解析/守恒验证）
 *
 * <p>物理: 电流 (ConductiveMedia, ec) + 传热 (HeatTransfer, ht) + 固体力学 (SolidMechanics, solid)。 焦耳热经
 * ElectromagneticHeating 耦合 (ec→ht), 温度场经 ThermalExpansion (ht→solid)。 直流稳态、稳态传热、线弹性小应变、单向热-结构耦合。
 *
 * <p>几何 (按任务书 8 步): wp1: xz 工作平面 L 形截面 = 外矩形(L+2*tbb, 0.1[m]) 减 内矩形(L+tbb, 0.1-tbb@(0,tbb)), 内圆角
 * tbb + 外圆角 2*tbb; Extrude wbb 沿 y (母线宽度)。 3 个 Cylinder 贯穿螺栓 (r=rad_1): 竖直端螺栓沿 x 贯穿右侧竖条, 水平端两螺栓沿 z
 * 贯穿底部横条、沿宽度 y 对称。 **螺栓仅向外侧伸出 2*tbb**, 内侧端面与母线表面齐平: cyl1 x∈[0.095,0.11] (贯穿 tbb + 伸出 2*tbb), cyl2/3
 * z∈[-0.01,0.005]。 Form Union (intbnd on) 保留内部边界 → 7 个域 (母线 1 + 每螺栓 2 段 × 3 = 6)。
 *
 * <p>材料: Copper (母线域) + Titanium beta-21S (螺栓域)。 电/热属性按任务书; 结构属性用官方内置数据 (busbar.mph 实证): Copper:
 * E=110GPa, ν=0.35, α=17e-6/K; Ti beta-21S: E=105GPa, ν=0.33, α=7.06e-6/K。
 *
 * <p>边界: 电: 竖直端螺栓最外侧圆端面 V=Vtot (bnd_terminal_high); 水平端两螺栓最外侧圆端面 Ground (bnd_terminal_ground);
 * 其余外表面 Electric Insulation。 热: 全部外表面 (bnd_convection) 对流 htc→T0; 螺栓接触端面 Thermal Insulation。 力:
 * 三螺栓外端面 Fixed (bnd_fixed, u=v=w=0); 其余 Free。
 *
 * <p>识别方法 (确定性, 不依赖硬编码编号): 域: 母线 = bbox 体积最大的域; 其余 = 螺栓域 (dom_ti_bolts)。 外部面: getAdj(2,3) 邻接恰 1
 * 域的面; 内部界面邻接 2 域。 圆形端面: 外部面中采样点到某轴距离≈rad_1 且面心在螺栓轴线端部。 high = 竖直螺栓 (中心 x≈L+1.5tbb, y≈-wbb/2) 的 x
 * 最大端面; ground = 水平螺栓 (中心 x≈L/2) 的 z 最小端面。
 *
 * <p>本机证据: - WorkPlane 2D Fillet 顶点: 内圆角=dif1(1)第3顶点, 外圆角=fil1(1)第6顶点 (WorkPlaneProbe 实证) - Extrude
 * input: selection("input").set({"wp1"}) - Form Union: create("uni1","Union"), set("intbnd","on") -
 * getAdj(2,3) 返回每面邻接域 (Probe 实证)
 *
 * <p>模块需求: ACDC + Heat Transfer + Structural Mechanics 运行: python scripts/run.py all
 * EcTSmBusbarStationary <run-dir> args[0]=mph, args[1]=CSV
 */
public class EcTSmBusbarStationary {

    public static void main(String[] args) throws Exception {
        Model model = ModelUtil.create("Model");

        // ---- 全局参数 ----
        model.param().set("L", "9[cm]", "母线水平段特征长度");
        model.param().set("rad_1", "6[mm]", "螺栓半径");
        model.param().set("tbb", "5[mm]", "母线厚度");
        model.param().set("wbb", "5[cm]", "母线宽度");
        model.param().set("Vtot", "20[mV]", "输入端电势");
        model.param().set("T0", "293.15[K]", "环境温度和无应变参考温度");
        model.param().set("htc", "5[W/(m^2*K)]", "自然对流换热系数");
        model.param().set("mh", "6[mm]", "最大网格单元尺寸");
        // 材料属性
        model.param().set("sigma_Cu", "5.998e7[S/m]", "铜电导率");
        model.param().set("k_Cu", "400[W/(m*K)]", "铜热导率");
        model.param().set("rho_Cu", "8700[kg/m^3]", "铜密度");
        model.param().set("Cp_Cu", "385[J/(kg*K)]", "铜比热");
        model.param().set("E_Cu", "110[GPa]", "铜杨氏模量");
        model.param().set("nu_Cu", "0.35", "铜泊松比");
        model.param().set("alpha_Cu", "17e-6[1/K]", "铜热膨胀系数");
        model.param().set("sigma_Ti", "7.407e5[S/m]", "钛电导率");
        model.param().set("k_Ti", "7.5[W/(m*K)]", "钛热导率");
        model.param().set("rho_Ti", "4940[kg/m^3]", "钛密度");
        model.param().set("Cp_Ti", "710[J/(kg*K)]", "钛比热");
        model.param().set("E_Ti", "105[GPa]", "钛杨氏模量");
        model.param().set("nu_Ti", "0.33", "钛泊松比");
        model.param().set("alpha_Ti", "7.06e-6[1/K]", "钛热膨胀系数");

        String comp = "comp1";
        model.component().create(comp, true);

        // ---- 几何 ----
        com.comsol.model.GeomSequence g3 = model.component(comp).geom().create("geom1", 3);

        // 1-5: xz 工作平面 L 形截面 + 圆角
        g3.create("wp1", "WorkPlane");
        g3.feature("wp1").set("quickplane", "xz");
        com.comsol.model.GeomSequence g2 = g3.feature("wp1").geom();
        g2.create("r1", "Rectangle");
        g2.feature("r1").set("size", new String[] {"L+2*tbb", "0.1[m]"});
        g2.feature("r1").set("pos", new double[] {0, 0});
        g2.create("r2", "Rectangle");
        g2.feature("r2").set("size", new String[] {"L+tbb", "0.1[m]-tbb"});
        g2.feature("r2").set("pos", new double[] {0, 0.005}); // (0, tbb) 数值化
        g2.create("dif1", "Difference");
        g2.feature("dif1").selection("input").set(new String[] {"r1"});
        g2.feature("dif1").selection("input2").set(new String[] {"r2"});
        g2.create("fil1", "Fillet");
        g2.feature("fil1").selection("point").set("dif1(1)", new int[] {3});
        g2.feature("fil1").set("radius", "tbb");
        g2.create("fil2", "Fillet");
        g2.feature("fil2").selection("point").set("fil1(1)", new int[] {6});
        g2.feature("fil2").set("radius", "2*tbb");
        g2.run();
        System.out.println("GEOM section built");

        // 6: 拉伸 wbb (母线宽度, 方向为工作平面法向 → y 负向, Probe 实证)
        g3.create("ext1", "Extrude");
        g3.feature("ext1").selection("input").set(new String[] {"wp1"});
        g3.feature("ext1").set("distance", "wbb");
        g3.run("ext1");

        // 7: 三个贯穿螺栓 (只向外侧伸出 2*tbb, 内侧端面与母线表面齐平)
        // 竖直端螺栓: 贯穿右侧竖条 x∈[L+tbb,L+2tbb]=[0.095,0.10], 沿 +x 轴,
        //   内侧 x=0.095 齐平 → 贯穿 tbb → 外侧伸出 2*tbb → x∈[0.095,0.11], 总长
        //   3*tbb
        double yc = -0.025; // wbb/2 (母线在 y∈[-wbb,0])
        double xVert0 = 0.095; // 竖条内表面 (L+tbb)
        double zVert = 0.05; // 竖条 z 中心
        double xHorz = 0.045; // 水平端螺栓 x 位置 (L/2)
        double zHorz0 = 0.005; // 横条内表面 (tbb)
        double yHalf = 0.0125; // 对称偏移
        g3.create("cyl1", "Cylinder");
        g3.feature("cyl1").set("r", "rad_1");
        g3.feature("cyl1").set("h", "3*tbb");
        g3.feature("cyl1").set("pos", new double[] {xVert0, yc, zVert});
        g3.feature("cyl1").set("axis", new double[] {1, 0, 0});
        g3.run("cyl1");
        g3.create("cyl2", "Cylinder");
        g3.feature("cyl2").set("r", "rad_1");
        g3.feature("cyl2").set("h", "3*tbb");
        g3.feature("cyl2").set("pos", new double[] {xHorz, yc - yHalf, zHorz0 - 0.015});
        g3.feature("cyl2").set("axis", new double[] {0, 0, 1});
        g3.run("cyl2");
        g3.create("cyl3", "Cylinder");
        g3.feature("cyl3").set("r", "rad_1");
        g3.feature("cyl3").set("h", "3*tbb");
        g3.feature("cyl3").set("pos", new double[] {xHorz, yc + yHalf, zHorz0 - 0.015});
        g3.feature("cyl3").set("axis", new double[] {0, 0, 1});
        g3.run("cyl3");

        // 8: Form Union (保留材料分区内部边界)
        g3.create("uni1", "Union");
        g3.feature("uni1").selection("input").set(new String[] {"ext1", "cyl1", "cyl2", "cyl3"});
        g3.feature("uni1").set("intbnd", "on");
        g3.run();
        System.out.println("GEOM built");

        // ---- 域/面识别 (确定性, 基于几何探测) ----
        GeomInfo gi = model.component(comp).geom("geom1");
        int nDom = gi.getNDomains();
        int nFace = gi.getNFaces();
        int[] upDom = gi.getUpDown()[1];
        int[][] adj = gi.getAdj(2, 3); // 每面邻接域

        // 域 bbox 体积 → 母线 = 最大
        double[] domVol = new double[nDom + 1];
        double[][] bmin = new double[nDom + 1][3];
        double[][] bmax = new double[nDom + 1][3];
        for (int d = 1; d <= nDom; d++) {
            bmin[d][0] = bmin[d][1] = bmin[d][2] = Double.MAX_VALUE;
            bmax[d][0] = bmax[d][1] = bmax[d][2] = -Double.MAX_VALUE;
        }
        for (int f = 1; f <= nFace; f++) {
            int d = upDom[f - 1];
            if (d < 1 || d > nDom) continue;
            double[][] pts = sampleFaceGrid(gi, f, 4);
            if (pts == null) continue;
            for (double[] p : pts) {
                for (int k = 0; k < 3; k++) {
                    bmin[d][k] = Math.min(bmin[d][k], p[k]);
                    bmax[d][k] = Math.max(bmax[d][k], p[k]);
                }
            }
        }
        for (int d = 1; d <= nDom; d++) {
            double dx = bmax[d][0] - bmin[d][0];
            double dy = bmax[d][1] - bmin[d][1];
            double dz = bmax[d][2] - bmin[d][2];
            domVol[d] = dx * dy * dz;
        }
        int busDom = 1;
        for (int d = 2; d <= nDom; d++) {
            if (domVol[d] > domVol[busDom]) busDom = d;
        }
        // 螺栓域 = 全部域 - 母线
        java.util.List<Integer> boltDoms = new java.util.ArrayList<>();
        for (int d = 1; d <= nDom; d++) {
            if (d != busDom) boltDoms.add(d);
        }
        int[] boltsArr = new int[boltDoms.size()];
        for (int i = 0; i < boltDoms.size(); i++) boltsArr[i] = boltDoms.get(i);
        System.out.println(
                "BUS_DOM=" + busDom + " BOLT_DOMS=" + java.util.Arrays.toString(boltsArr));
        for (int d = 1; d <= nDom; d++) {
            System.out.println(
                    "DOM "
                            + d
                            + " vol="
                            + String.format("%.3e", domVol[d])
                            + " bbox=("
                            + String.format("%.3f", bmin[d][0])
                            + ".."
                            + String.format("%.3f", bmax[d][0])
                            + ", "
                            + String.format("%.3f", bmin[d][1])
                            + ".."
                            + String.format("%.3f", bmax[d][1])
                            + ", "
                            + String.format("%.3f", bmin[d][2])
                            + ".."
                            + String.format("%.3f", bmax[d][2])
                            + ")");
        }

        // 外部面 = 邻接恰 1 域; 记录每个外部面的所属域和中心
        java.util.List<Integer> extFaces = new java.util.ArrayList<>();
        int[] faceDom = new int[nFace + 1];
        double[][] faceC = new double[nFace + 1][3];
        for (int f = 1; f <= nFace; f++) {
            int[] ad = adj[f - 1];
            int cnt = 0;
            for (int a : ad) if (a > 0) cnt++;
            if (cnt == 1) {
                extFaces.add(f);
                faceDom[f] = ad[0] > 0 ? ad[0] : ad[ad.length - 1];
            }
            double[] pr = gi.faceParamRange(f);
            double[][] pts;
            try {
                pts =
                        gi.faceX(
                                f,
                                new double[][] {
                                    {
                                        (pr[0] + pr[1]) / 2,
                                        pr.length >= 4 ? (pr[2] + pr[3]) / 2 : 0.5
                                    }
                                });
            } catch (Exception e) {
                try {
                    pts = gi.faceX(f, new double[][] {{0.5, 0.5}});
                } catch (Exception e2) {
                    continue;
                }
            }
            if (pts != null && pts.length > 0) faceC[f] = pts[0];
        }
        System.out.println("EXT_FACES=" + extFaces.size() + " / " + nFace);

        // 螺栓外端面 (仅外侧伸出端, 内侧端面与母线表面齐平/重合不独立存在)
        // cyl1 (沿+x): pos=(0.095,-0.025,0.05) h=3*tbb=0.015 → 外端面 x=0.110
        // cyl2 (沿-z): pos=(0.045,-0.0375,-0.01) h=0.015 → 外端面 z=-0.010
        // cyl3 (沿-z): pos=(0.045,-0.0125,-0.01) h=0.015 → 外端面 z=-0.010
        double[][] boltEndCenters = {
            {xVert0 + 3 * 0.005, yc, zVert}, // cyl1 外端 (x=0.110)
            {xHorz, yc - yHalf, -0.010}, // cyl2 外端 (z=-0.010)
            {xHorz, yc + yHalf, -0.010}, // cyl3 外端 (z=-0.010)
        };
        java.util.List<Integer> boltEndFaces = new java.util.ArrayList<>();
        for (int f : extFaces) {
            double[] c = faceC[f];
            if (c == null) continue;
            for (double[] ec : boltEndCenters) {
                double d =
                        Math.sqrt(
                                (c[0] - ec[0]) * (c[0] - ec[0])
                                        + (c[1] - ec[1]) * (c[1] - ec[1])
                                        + (c[2] - ec[2]) * (c[2] - ec[2]));
                if (d < 1e-3) {
                    boltEndFaces.add(f);
                    break;
                }
            }
        }
        System.out.println(
                "BOLT_END_FACES="
                        + java.util.Arrays.toString(boltEndFaces.toArray())
                        + " (expect 3: cyl1 outer + cyl2/cyl3 outer)");
        if (boltEndFaces.size() != 3) {
            throw new IllegalStateException("bolt end face count != 3, got " + boltEndFaces.size());
        }

        // high = cyl1 外端面 (x 最大), ground = cyl2/cyl3 外端面 (z 最小)
        int highFace = -1;
        java.util.List<Integer> groundMin = new java.util.ArrayList<>();
        for (int f : boltEndFaces) {
            double[] c = faceC[f];
            if (Math.abs(c[1] - yc) < 1e-3 && Math.abs(c[2] - zVert) < 1e-3) {
                highFace = f;
            } else {
                groundMin.add(f);
            }
        }
        if (highFace < 0 || groundMin.size() != 2) {
            throw new IllegalStateException(
                    "high/ground face identification failed: high="
                            + highFace
                            + " ground="
                            + groundMin.size());
        }
        int[] contact = new int[3];
        contact[0] = highFace;
        contact[1] = groundMin.get(0);
        contact[2] = groundMin.get(1);
        System.out.println(
                "HIGH_FACE="
                        + highFace
                        + " GROUND="
                        + java.util.Arrays.toString(groundMin.toArray())
                        + " BND_BOLT_CONTACT="
                        + java.util.Arrays.toString(contact));

        // 外部面对流: 全部外部面 - bolt contact
        java.util.Set<Integer> contactSet = new java.util.HashSet<>();
        for (int c : contact) contactSet.add(c);
        java.util.List<Integer> convFaces = new java.util.ArrayList<>();
        for (int f : extFaces) {
            if (!contactSet.contains(f)) convFaces.add(f);
        }
        int[] convArr = new int[convFaces.size()];
        for (int i = 0; i < convFaces.size(); i++) convArr[i] = convFaces.get(i);

        // ---- 材料 ----
        setMaterial(
                model,
                comp,
                "mat_Cu",
                "Copper",
                new int[] {busDom},
                "sigma_Cu",
                "k_Cu",
                "rho_Cu",
                "Cp_Cu",
                "alpha_Cu",
                "E_Cu",
                "nu_Cu");
        setMaterial(
                model,
                comp,
                "mat_Ti",
                "Titanium beta-21S",
                boltsArr,
                "sigma_Ti",
                "k_Ti",
                "rho_Ti",
                "Cp_Ti",
                "alpha_Ti",
                "E_Ti",
                "nu_Ti");
        System.out.println(
                "MATERIALS set: Cu->dom"
                        + busDom
                        + ", Ti->dom"
                        + java.util.Arrays.toString(boltsArr));

        // ---- 物理场 ----
        model.component(comp).physics().create("ec", "ConductiveMedia", "geom1");
        model.component(comp).physics().create("ht", "HeatTransfer", "geom1");
        model.component(comp).physics().create("solid", "SolidMechanics", "geom1");

        // 电边界: high 端 V=Vtot, ground 端 Ground
        int[] groundArr = new int[groundMin.size()];
        for (int i = 0; i < groundMin.size(); i++) groundArr[i] = groundMin.get(i);
        model.component(comp).physics("ec").create("term1", "Terminal", 2);
        model.component(comp).physics("ec").feature("term1").selection().set(new int[] {highFace});
        model.component(comp).physics("ec").feature("term1").set("TerminalType", "Voltage");
        model.component(comp).physics("ec").feature("term1").set("V0", "Vtot");
        model.component(comp).physics("ec").create("gnd1", "Ground", 2);
        model.component(comp).physics("ec").feature("gnd1").selection().set(groundArr);
        System.out.println(
                "EC boundary: V("
                        + highFace
                        + ")="
                        + "Vtot, Gnd("
                        + java.util.Arrays.toString(groundArr)
                        + ")");

        // 热边界: 全部外表面对流 (bnd_conv), 螺栓端面绝热
        addConvective(comp, model, "hf1", convArr);

        // 多物理场: 焦耳热
        model.component(comp).multiphysics().create("emh1", "ElectromagneticHeating");
        model.component(comp).multiphysics("emh1").set("EMHeat_physics", "ec");
        model.component(comp).multiphysics("emh1").set("Heat_physics", "ht");

        // 结构: Fixed 约束在螺栓外端面 (u=v=w=0)
        // lemm1: E,ν 从材料读 (from_mat) — 铜/钛 E,ν 不同, 由各自材料 Enu 组提供
        model.component(comp).physics("solid").feature("lemm1").set("E_mat", "from_mat");
        model.component(comp).physics("solid").feature("lemm1").set("nu_mat", "from_mat");
        model.component(comp).physics("solid").create("fix1", "Fixed", 2);
        model.component(comp).physics("solid").feature("fix1").selection().set(contact);
        System.out.println("FIXED on faces=" + java.util.Arrays.toString(contact));

        // 热膨胀耦合 (ht→solid), alpha 从材料
        model.component(comp).multiphysics().create("te1", "ThermalExpansion");
        int[] allDoms = new int[nDom];
        for (int d = 1; d <= nDom; d++) allDoms[d - 1] = d;
        model.component(comp).multiphysics("te1").selection().set(allDoms);
        model.component(comp).multiphysics("te1").set("Heat_physics", "ht");
        model.component(comp).multiphysics("te1").set("Solid_physics", "solid");
        model.component(comp).multiphysics("te1").set("alpha_mat", "from_mat");
        model.component(comp)
                .multiphysics("te1")
                .set("minput_strainreferencetemperature_src", "userdef");
        model.component(comp).multiphysics("te1").set("minput_strainreferencetemperature", "T0");
        System.out.println("TE1 created");

        // ---- 网格 ----
        model.component(comp).mesh().create("mesh1");
        // Free Tetrahedral + 自定义 Size (任务书参数化序列: 最大 mh, 最小 mh-mh/3,
        // 曲率 0.2)
        com.comsol.model.MeshFeature ftet1 =
                model.component(comp).mesh("mesh1").create("ftet1", "FreeTet");
        com.comsol.model.MeshFeature size1 = ftet1.create("size1", "Size");
        size1.set("custom", "on");
        size1.set("hmax", "mh");
        size1.set("hmin", "mh-mh/3");
        size1.set("hcurve", 0.2);
        size1.set("hgrad", 1.5);
        // 螺栓域局部细化 (薄壁 5mm, 半径 6mm): 螺栓域 hmax=3*tbb
        try {
            com.comsol.model.MeshFeature size2 = ftet1.create("size2", "Size");
            size2.selection().geom("geom1", 3).set(boltsArr);
            size2.set("custom", "on");
            size2.set("hmax", "3*tbb");
            System.out.println("SIZE2 created");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("SIZE2 EXC: " + e.getMessage());
        }
        model.component(comp).mesh("mesh1").run();
        System.out.println("MESH built");

        // ---- 研究 ----
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").createAutoSequences("stat");
        model.study("std1").run();
        System.out.println("SOLVE done");

        // ---- 导出 CSV ----
        String csvOut = args.length > 1 ? args[1] : "EcTSmBusbarStationary.csv";
        model.result().export().create("data1", "Data");
        model.result().export("data1").set("data", "dset1");
        model.result().export("data1").set("filename", csvOut);
        model.result()
                .export("data1")
                .set(
                        "expr",
                        new String[] {
                            "V",
                            "T",
                            "ec.normJ",
                            "ec.Qrh",
                            "solid.disp",
                            "solid.mises",
                            "solid.sx",
                            "solid.sy",
                            "solid.sz"
                        });
        model.result().export("data1").run();

        String outPath = args.length > 0 ? args[0] : "EcTSmBusbarStationary.mph";
        model.save(outPath);
        System.out.println("EcTSmBusbar_OK");
    }

    /** 写材料 (电/热/密度/比热/热膨胀/结构参数)。E,ν 通过 Enu 材料模型写入, 供 lemm1 from_mat */
    private static void setMaterial(
            Model model,
            String comp,
            String tag,
            String name,
            int[] doms,
            String sigma,
            String k,
            String rho,
            String cp,
            String alpha,
            String E,
            String nu) {
        model.component(comp).material().create(tag, "Common");
        model.component(comp).material(tag).label(name);
        model.component(comp).material(tag).selection().set(doms);
        com.comsol.model.Material mat = model.component(comp).material(tag);
        mat.propertyGroup("def").set("electricconductivity", new String[][] {{sigma}});
        mat.propertyGroup("def").set("relpermittivity", new String[][] {{"1"}});
        mat.propertyGroup("def").set("thermalconductivity", new String[][] {{k}});
        mat.propertyGroup("def").set("density", new String[][] {{rho}});
        mat.propertyGroup("def").set("heatcapacity", new String[][] {{cp}});
        mat.propertyGroup("def")
                .set(
                        "thermalexpansioncoefficient",
                        new String[] {alpha, "0", "0", "0", alpha, "0", "0", "0", alpha});
        // 结构参数: Enu 材料模型 (MaterialEnuProbe 实证), 供 solid.lemm1 from_mat
        mat.materialModel().create("Enu", "YoungsModulusAndPoissonsRatio");
        mat.propertyGroup("Enu").set("E", new String[][] {{E}});
        mat.propertyGroup("Enu").set("nu", new String[][] {{nu}});
    }

    private static void addConvective(String comp, Model model, String tag, int[] faces) {
        model.component(comp).physics("ht").create(tag, "HeatFluxBoundary", 2);
        model.component(comp).physics("ht").feature(tag).selection().set(faces);
        model.component(comp).physics("ht").feature(tag).set("HeatFluxType", "ConvectiveHeatFlux");
        model.component(comp).physics("ht").feature(tag).set("minput_temperature_src", "userdef");
        model.component(comp).physics("ht").feature(tag).set("minput_temperature", "T0");
        model.component(comp)
                .physics("ht")
                .feature(tag)
                .set("HeatTransferCoefficientType", "UserDef");
        model.component(comp).physics("ht").feature(tag).set("h", "htc");
    }

    /** 面内采样 N×N 网格点 (用 faceParamRange 参数域) */
    private static double[][] sampleFaceGrid(GeomInfo gi, int f, int n) {
        double[] pr;
        try {
            pr = gi.faceParamRange(f);
        } catch (Exception e) {
            return null;
        }
        if (pr == null || pr.length < 2) return null;
        java.util.List<double[]> out = new java.util.ArrayList<>();
        double u0 = pr[0], u1 = pr[1];
        double v0 = pr.length >= 4 ? pr[2] : 0, v1 = pr.length >= 4 ? pr[3] : 1;
        double du = (u1 - u0) / n, dv = (v1 - v0) / n;
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                try {
                    double[][] pts = gi.faceX(f, new double[][] {{u0 + i * du, v0 + j * dv}});
                    if (pts != null && pts.length > 0) out.add(pts[0]);
                } catch (Exception e) {
                }
            }
        }
        return out.toArray(new double[0][]);
    }
}
