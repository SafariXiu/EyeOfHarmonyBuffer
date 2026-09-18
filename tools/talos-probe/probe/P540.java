package probe;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import java.io.File; import java.io.PrintStream; import java.util.Locale;
/** P540：TalosField 的**并发正确性**验证（E123）。
 *
 * 为什么必须有它：E123 把静态窗口缓存搬进了 ThreadLocal，但**未测过的修复不算修复**。
 * 本探针让 4 个线程算**同一批坐标**（对窗口缓存的争用最强），与单线程结果逐位比对。
 */
public class P540 {
  static final Locale LF = Locale.ROOT;
  static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
  static PrintStream rep;
  static void S(String s){ rep.println("[P540] "+s); rep.flush(); System.out.println("[P540] "+s); System.out.flush(); }
  static final long SEED = 1022228679L;
  static final int N = 40000;
  static final double[] X = new double[N], Z = new double[N];
  /** 只取 bfield，用于把「预算场」与「高程映射」两层分开测。 */
  static double[] runB() {
    double[] r = new double[N];
    for (int i = 0; i < N; i++) r[i] = com.EyeOfHarmonyBuffer.sim.litho.TalosField.bfield(X[i], Z[i], SEED);
    return r; }
  static final double[] LVL = new double[8];
  static final int[] CALLER = new int[8];
  static double[] run() {
    double[] r = new double[N];
    for (int i = 0; i < N; i++) r[i] = PlateField.elevationWithCell((int) X[i], (int) Z[i], SEED, PlateField.PLATE_CELL);
    return r; }
  /** 本线程标定出的 LEVEL（若线程间不同 => 标定不确定；若相同 => 差异在下游）。 */
  static double lvlNow() { return com.EyeOfHarmonyBuffer.sim.litho.TalosField.level(SEED); }
  public static void main(String[] a) throws Exception {
    File dir=new File(ROOT,"build"+File.separator+"eoh_probe"+File.separator+"refs"); dir.mkdirs();
    rep=new PrintStream(new File(dir,"P540_threads.txt"),"UTF-8");
    PlateField.TALOS_TERRAIN = true;
    // 坐标要跨多个格窗，否则争用太弱
    for (int i = 0; i < N; i++) { X[i] = (i % 200) * 220_000.0 + 1000; Z[i] = (i / 200) * 1_900_000.0 + 2000; }
    S("=== P540 并发正确性 ===");
    S(String.format(LF,"TALOS_TERRAIN=%b  SEED=%d  N=%d  线程=4", PlateField.TALOS_TERRAIN, SEED, N));
    double[] q1 = runB();
    double[] q2 = runB();
    double[] q3 = runB();
    double bmax = 0; int bn = 0, bfirst = -1;
    for (int i = 0; i < N; i++) {
      double d = Math.abs(q1[i] - q2[i]); if (d > bmax) bmax = d; if (d != 0) { bn++; if (bfirst < 0) bfirst = i; } }
    S(String.format(LF,"bfield 同一线程两次: max|diff|=%.3e  不一致样本=%d  首个 i=%d", bmax, bn, bfirst));
    if (bfirst >= 0) S(String.format(LF,"   x=%.0f z=%.0f  q1=%.9f q2=%.9f q3=%.9f", X[bfirst], Z[bfirst], q1[bfirst], q2[bfirst], q3[bfirst]));
    double[] r1 = run();
    double[] r2 = run();
    double[] r3 = run();
    double d12 = 0, d23 = 0; int n12 = 0, n23 = 0; int firstBad = -1;
    for (int i = 0; i < N; i++) {
      double da = Math.abs(r1[i] - r2[i]), db = Math.abs(r2[i] - r3[i]);
      if (da > d12) d12 = da; if (db > d23) d23 = db;
      if (da != 0) n12++; if (db != 0) n23++;
      if (firstBad < 0 && (da != 0 || db != 0)) firstBad = i; }
    S(String.format(LF,"同线程连续三次: |r1-r2|max=%.3f (n=%d)   |r2-r3|max=%.3f (n=%d)", d12, n12, d23, n23));
    if (firstBad >= 0) {
      S(String.format(LF,"首个不一致 i=%d  x=%.0f z=%.0f  r1=%.6f r2=%.6f r3=%.6f", firstBad, X[firstBad], Z[firstBad], r1[firstBad], r2[firstBad], r3[firstBad]));
      com.EyeOfHarmonyBuffer.sim.litho.TalosField.bfield(X[firstBad], Z[firstBad], SEED);
      double b1 = com.EyeOfHarmonyBuffer.sim.litho.TalosField.bfield(X[firstBad], Z[firstBad], SEED);
      double b2 = com.EyeOfHarmonyBuffer.sim.litho.TalosField.bfield(X[firstBad], Z[firstBad], SEED);
      double h1 = com.EyeOfHarmonyBuffer.sim.litho.TalosField.hf(X[firstBad], Z[firstBad], SEED);
      double g1 = com.EyeOfHarmonyBuffer.sim.litho.TalosField.age(X[firstBad], Z[firstBad], SEED);
      S(String.format(LF,"  分量: bfield 连续两次 = %.9f / %.9f   (差 %.3e)", b1, b2, Math.abs(b1-b2)));
      S(String.format(LF,"        hf = %.9f   age = %.9f   (纯噪声，应确定)", h1, g1));
      S(String.format(LF,"        LEVEL = %.9f", com.EyeOfHarmonyBuffer.sim.litho.TalosField.level(SEED)));
    }
    double[] base = r3;
    double[][] out = new double[4][];
    Thread[] ts = new Thread[4];
    final Throwable[] err = new Throwable[4];
    long t0 = System.nanoTime();
    for (int k = 0; k < 4; k++) { final int kk = k;
      ts[k] = new Thread(() -> { try { out[kk] = run(); LVL[kk] = lvlNow(); } catch (Throwable e) { err[kk] = e; } }); ts[k].start(); }
    for (Thread t : ts) t.join();
    double ms = (System.nanoTime() - t0) / 1e6;
    int bad = 0; double maxAbs = 0;
    for (int k = 0; k < 4; k++) {
      if (err[k] != null) { S(String.format(LF,"线程 %d 抛异常: %s", k, err[k])); bad++; continue; }
      if (out[k] == null) { S(String.format(LF,"线程 %d 无结果", k)); bad++; continue; }
      for (int i = 0; i < N; i++) { double d = Math.abs(out[k][i] - base[i]); if (d > maxAbs) maxAbs = d; if (d != 0.0) bad++; }
    }
    S(String.format(LF,"主线程 LEVEL = %.9f", lvlNow()));
    for (int k = 0; k < 4; k++) S(String.format(LF,"  线程 %d  LEVEL = %.9f   out[0]=%.6f", k, LVL[k], out[k] == null ? Double.NaN : out[k][0]));
    S(String.format(LF,"单线程基准: [0]=%.6f  [%d]=%.6f", base[0], N-1, base[N-1]));
    S(String.format(LF,"4 线程 vs 单线程: max|diff| = %.1f m   不一致样本 = %d", maxAbs, bad));
    S(String.format(LF,"判定: %s   (4 线程耗时 %.0f ms)", maxAbs == 0.0 && bad == 0 ? "并发安全 ✓" : "**有数据竞争 —— 必须修**", ms));
    rep.close(); } }