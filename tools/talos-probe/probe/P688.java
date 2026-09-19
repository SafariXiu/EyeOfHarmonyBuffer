package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P688 -- §483: 目标第 (3) 项 —— eddyMfc 在 DJF 30~50N 的量级（§441 记「偏大 3~5 倍」）。
//   模型 eddyMfc(lat,theta) vs ZonalTables 的观测月表（45~60 度年均值为 1 ⇒ 两边同归一化后比）。
public class P688 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P688] " + s); System.out.println("[P688] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p688_report.txt"), "UTF-8");
        say("P688: eddyMfc 的 DJF 量级（目标第 (3) 项，§483）");
        EarthRef.install();
        SimClimate.clearCache();
        double thD = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double thJ = Atmosphere.theta(0.0);
        say("");
        say("     纬度 | 模型DJF(kg/m2/s)  模型JJA    | 观测DJF(归一) 观测JJA | DJF 模型/观测 | JJA 模型/观测");
        // ★★★★★★ §530：归一化口径 —— 由【代码】确定，不是猜的：
        //   ZonalTables.java:204 与 :215 的 javadoc 逐字写着 EDDY_MFC_OBS_MONTH 是
        //        「19 纬 x 12 月…【无量纲，45~60 度的年均值为 1】」/「【无量纲】，保留符号」
        //   ⇒ 观测侧是【归一化形】⇒ 模型侧必须做【同样的归一】再比。
        //   原版没做归一化（探针头自己写着「两边同归一化后比」但代码里没有），
        //   再加上观测列当时是外推假值 ⇒ 比值列一直没有意义（§529）。
        double mNorm = 0.0; int mN = 0;
        for (double la = 45.0; la <= 60.0; la += 2.5)
            for (int mo = 0; mo < 12; mo++) {
                mNorm += PrecipField.eddyMfc(Math.toRadians(la),
                         Atmosphere.theta(mo * WorldContract.DAYS_PER_YEAR / 12.0));
                mN++;
            }
        mNorm /= mN;
        say(String.format(LF, "  归一化：模型 45~60N 年均值 = %+.4e kg/m2/s  ⇒ 两边都归一到该处 = 1", mNorm));
        double sMd = 0, sOd = 0, sMj = 0, sOj = 0; int nd = 0, nj = 0;
        for (double latd = 25.0; latd <= 55.0; latd += 2.5) {
            double mD = PrecipField.eddyMfc(Math.toRadians(latd), thD);
            double mJ = PrecipField.eddyMfc(Math.toRadians(latd), thJ);
            // ★★★★★★ §529 修复（审计面 1，P1-9）：原来是 eddyMfcObsMonth(latd, ...) ——
            //   把【度】传给了收【弧度】的访问器。这正是 CALIBERS.md:465 记的「第 6 次同类错误」。
            //   后果不是「略有偏差」而是【纯外推】：toDegrees(25) = 1432 度
            //   ⇒ ZonalTables 内部 k 被夹到 17，但 tlat = a/5 - k = 269.48 【未被夹】
            //   ⇒ 那两列「观测 DJF/JJA」在原版里【全是假值】。
            //   本探针的孪生 P689 一直是正确的（用 lr = Math.toRadians(latd)）；此处对齐之。
            double lr = Math.toRadians(latd);
            double oD = ZonalTables.eddyMfcObsMonth(lr, thD);
            double oJ = ZonalTables.eddyMfcObsMonth(lr, thJ);
            say(String.format(LF, "     %5.1f | %+13.3e %+12.3e | %+12.4f %+8.4f | %s | %s",
                latd, mD, mJ, oD, oJ,
                Math.abs(oD) < 1e-9 ? "  n/a  " : String.format(LF, "%7.2f", (mD / mNorm) / oD),
                Math.abs(oJ) < 1e-9 ? "  n/a  " : String.format(LF, "%7.2f", (mJ / mNorm) / oJ)));
            if (latd >= 30.0 && latd <= 50.0) {
                if (Math.abs(oD) > 1e-9) { sMd += (mD / mNorm) / oD; nd++; }
                if (Math.abs(oJ) > 1e-9) { sMj += (mJ / mNorm) / oJ; nj++; }
            }
        }
        say("");
        say(String.format(LF, "     30~50N 平均 模型/观测：  DJF = %.2f   JJA = %.2f",
            nd == 0 ? Double.NaN : sMd / nd, nj == 0 ? Double.NaN : sMj / nj));
        say("     （判据：§441 记 DJF 30~50N 偏大 3~5 倍 ⇒ 现在是几倍？）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}