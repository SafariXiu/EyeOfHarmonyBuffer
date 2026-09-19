package probe;
import com.EyeOfHarmonyBuffer.sim.litho.TalosField;
import java.io.File; import java.io.PrintStream; import java.util.Locale;
/** P539：TalosField 的 NaN / 极端值 / 量级诊断（P268 慢 44 倍的排查）。 */
public class P539 {
  static final Locale LF = Locale.ROOT;
  static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
  static PrintStream rep;
  static void S(String s){ rep.println("[P539] "+s); rep.flush(); System.out.println("[P539] "+s); System.out.flush(); }
  public static void main(String[] a) throws Exception {
    File dir=new File(ROOT,"build"+File.separator+"eoh_probe"+File.separator+"refs"); dir.mkdirs();
    rep=new PrintStream(new File(dir,"P539_nan.txt"),"UTF-8");
    long seed=1022228679L;
    S("=== P539 TalosField 值与量级诊断 ===");
    S(String.format(LF,"LEVEL=%.6f", TalosField.level(seed)));
    int n=0, nan=0, inf=0; double mn=1e30, mx=-1e30; long t0=System.nanoTime();
    double fmin=1e30,fmax=-1e30; int land=0;
    for(int y=0;y<400;y++) for(int x=0;x<400;x++){
      double bx=(x+0.5)*5.0e7/400.0, bz=(y+0.5)*5.0e7/400.0;
      double e=TalosField.elevation(bx,bz,seed); double f=TalosField.fieldValue(bx,bz,seed);
      n++; if(Double.isNaN(e)||Double.isNaN(f)) nan++; if(Double.isInfinite(e)||Double.isInfinite(f)) inf++;
      if(e<mn)mn=e; if(e>mx)mx=e; if(f<fmin)fmin=f; if(f>fmax)fmax=f;
      if(TalosField.isLand(bx,bz,seed)) land++; }
    double ms=(System.nanoTime()-t0)/1e6;
    S(String.format(LF,"样本 n=%d  NaN=%d  Inf=%d", n, nan, inf));
    S(String.format(LF,"elevation 范围 %.1f .. %.1f m", mn, mx));
    S(String.format(LF,"fieldValue 范围 %.6f .. %.6f", fmin, fmax));
    S(String.format(LF,"陆地 %d (%.1f%%)", land, 100.0*land/n));
    S(String.format(LF,"耗时 %.1f ms = %.0f ns/列", ms, ms*1e6/n));
    // 采样 bfield / hf / age 各自的范围，定位异常来源
    double bmn=1e30,bmx=-1e30,hmn=1e30,hmx=-1e30,amn=1e30,amx=-1e30;
    for(int y=0;y<200;y++) for(int x=0;x<200;x++){
      double bx=(x+0.5)*5.0e7/200.0, bz=(y+0.5)*5.0e7/200.0;
      double b=TalosField.bfield(bx,bz,seed), h=TalosField.hf(bx,bz,seed), g=TalosField.age(bx,bz,seed);
      if(b<bmn)bmn=b; if(b>bmx)bmx=b; if(h<hmn)hmn=h; if(h>hmx)hmx=h; if(g<amn)amn=g; if(g>amx)amx=g; }
    S(String.format(LF,"bfield     %.6f .. %.6f   (必须落在 [0,1])", bmn, bmx));
    S(String.format(LF,"hf         %.6f .. %.6f", hmn, hmx));
    S(String.format(LF,"age        %.6f .. %.6f   (必须落在 [0,70])", amn, amx));

    // ---- B. 逐纬度陆地占比（8 种子）----
    // 为什么量它：P296 实测新地形 5/10/15 度的陆地占比是 99.3/100.0/99.0%，而地球赤道带约 78% 是海。
    // 必须判定这是「种子偶然」还是「系统性」—— 前者换个种子就好，后者是生成器的纬度分布问题。
    // ---- B0. 仪器自证：先证明 isLand 在算 ----
    S("");
    S("=== B0. 仪器自证（LEVEL vs 该窗口内 fieldValue 的范围）===");
    // ★ 必须用生产世界的种子派生！P296 用 `SimTerrain.seedOf(worldSeedInt)`，
    //   而我先前用裸种子 ⇒ 量的是另一个世界（实测同纬度差 72 个百分点，§326）。
    int[] worldSeeds = {1022228679, 1, 2, 3, 5, 7, 42, 12345};
    long[] seedsDbg = new long[worldSeeds.length];
    for (int i = 0; i < worldSeeds.length; i++) seedsDbg[i] = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(worldSeeds[i]);
    S("  （种子已按 SimTerrain.seedOf 派生，与生产/验收探针一致）");
    for (long s : seedsDbg) {
        double lv = TalosField.level(s);
        double fminW = 1e30, fmaxW = -1e30; int lcW = 0;
        for (int i = 0; i < 2000; i++) {
            double x = (i + 0.5) * 20_000_000.0 / 2000.0;
            double fv = TalosField.fieldValue(x, 555556.0, s);
            if (fv < fminW) fminW = fv; if (fv > fmaxW) fmaxW = fv;
            if (fv > lv) lcW++; }
        S(String.format(LF, "  seed %-11d LEVEL=%.6f   窗口内 F ∈ [%.6f, %.6f]   超过 LEVEL 的比例 = %.1f%%",
            s, lv, fminW, fmaxW, 100.0 * lcW / 2000));
    }
    S("");
    S("=== B. 逐纬度陆地占比（8 种子，每纬度 2000 个 x 采样）===");
    long[] seeds = seedsDbg;
    StringBuilder hdr = new StringBuilder("  纬度   地球海占比    ");
    for (long s : seeds) hdr.append(String.format(LF, "%8d ", s));
    hdr.append("  跨种子极差");
    S(hdr.toString());
    for (int latDeg = 5; latDeg <= 90; latDeg += 5) {
        long z = (long) (latDeg / 90.0 * 10_000_000.0);
        StringBuilder row = new StringBuilder(String.format(LF, "  %3d    ", latDeg));
        double mnLat = 200, mxLat = -200;
        double earthSea = 100.0 - 100.0 * 0.29;   // 粗略：全球平均海占比（仅作参照量级）
        row.append(String.format(LF, "   ~%.0f%%     ", earthSea));
        for (long s : seeds) {
            int lc = 0, cnt = 0;
            for (int i = 0; i < 2000; i++) {
                double x = (i + 0.5) * 20_000_000.0 / 2000.0;
                if (TalosField.isLand(x, z, s)) lc++; cnt++; }
            double pct = 100.0 * lc / cnt;
            if (pct < mnLat) mnLat = pct; if (pct > mxLat) mxLat = pct;
            row.append(String.format(LF, "%7.1f%% ", pct));
        }
        row.append(String.format(LF, "  %6.1f pt", mxLat - mnLat));
        S(row.toString());
    }
    // ---- C. 与 P296 单进程对拍：消除全部跨进程/跨文件假设 ----
    S("");
    S("=== C. 与 P296 对拍（同一 JVM、同一种子、两种 x 网格、两条调用路径）===");
    long sC = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(1022228679);
    long zC = (long) (5.0 / 90.0 * 10_000_000.0);
    S(String.format(LF, "  seedOf(1022228679) = %d", sC));
    S(String.format(LF, "  z(lat5) = %d      com.EyeOfHarmonyBuffer.sim.litho.PlateField.WORLD_IS_TALOS = %s", zC, com.EyeOfHarmonyBuffer.sim.litho.PlateField.WORLD_IS_TALOS));
    S(String.format(LF, "  LEVEL(seed) = %.6f", TalosField.level(sC)));
    int c1 = 0;
    for (int i = 0; i < 400; i++) { double xA = (double) i * 40_000.0; if (TalosField.isLand(xA, zC, sC)) c1++; }
    S(String.format(LF, "  C1 TalosField   x=i*40000     i<400   [0,15960km] = %5.1f%%  (%d/400)", 100.0 * c1 / 400, c1));
    int c2 = 0;
    for (int i = 0; i < 2000; i++) { double xB = (i + 0.5) * 10_000.0; if (TalosField.isLand(xB, zC, sC)) c2++; }
    S(String.format(LF, "  C2 TalosField   x=(i+.5)*10000 i<2000 [0,20000km] = %5.1f%%  (%d/2000)", 100.0 * c2 / 2000, c2));
    int c3 = 0;
    for (int i = 0; i < 400; i++) { if (com.EyeOfHarmonyBuffer.sim.litho.PlateField.isLandWithCell(i * 40_000, (int) zC, sC, com.EyeOfHarmonyBuffer.sim.litho.PlateField.PLATE_CELL)) c3++; }
    S(String.format(LF, "  C3 PlateField   x=i*40000     i<400   = %5.1f%%  (%d/400)   <- P296 实际路径", 100.0 * c3 / 400, c3));
    StringBuilder blk = new StringBuilder("  每 1000 km 的陆地占比: ");
    for (int b = 0; b < 20; b++) {
        int cc = 0;
        for (int i = 0; i < 50; i++) { double xD = b * 1_000_000.0 + (i + 0.5) * 20_000.0; if (TalosField.isLand(xD, zC, sC)) cc++; }
        blk.append(String.format(LF, "%3.0f ", 100.0 * cc / 50)); }
    S(blk.toString());
    S("  （20 段 x 50 点，覆盖 x 由 0 至 20000 km）");
    rep.close(); } }