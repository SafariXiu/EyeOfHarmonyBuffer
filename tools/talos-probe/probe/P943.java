package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;

// P943 -- zonalSlTemp 极地冷偏差的完整分解：年均项 / 季节项 / kappa 选择。
public class P943 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p943_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thJ = Atmosphere.theta(0.0), thD = Atmosphere.theta(365.25/2.0);
        sb.append("P943: zonalSlTemp 偏差分解  GAMMA=").append(Atmosphere.GAMMA)
          .append("  KAPPA_MEAN=").append(Atmosphere.KAPPA_MEAN)
          .append("  PHI0=").append(ZonalTables.SEASON_SHAPE_PHI0).append("\n");
        sb.append(String.format(LF,"thetaJJA=%.4f thetaDJF=%.4f%n%n", thJ, thD));
        sb.append("[A] 年均恒等式:  T_ZM_SL_MONTH 的12月均值 =? tOcean + ZF_earth*DELTA\n");
        sb.append(" lat |  tOcean   tLand  elev_m  Gam*el    DELTA |  ZFe    kap |   obsAnn  tO+ZFe*D    diff |   zmslk  zmslk-obs |  tZmSAnn ann-obs\n");
        int N = 19; double[] obsAnn = new double[N];
        for (int j = 0; j < N; j++) { double s=0; for (int m=0;m<12;m++) s += ZonalTables.T_ZM_SL_MONTH[m*N+j]; obsAnn[j]=s/12.0; }
        for (double dg = 25; dg <= 85.0001; dg += 5) {
            double lr = Math.toRadians(dg);
            double tO = ZonalTables.tOceanK(lr), tL = ZonalTables.tLandK(lr), el = ZonalTables.landMeanElev(lr);
            double D = Atmosphere.landMinusOceanSLK(lr), zf = ZonalTables.zfEarth(lr);
            double zmslk = Atmosphere.zonalMeanSeaLevelK(lr);
            double pred = tO + zf*D;
            sb.append(String.format(LF," %3.0f | %7.2f %7.2f %6.0f %7.2f %8.2f | %5.3f %6.3f | %8.2f %9.2f %+7.2f | %7.2f %+8.2f | %8.2f %+7.2f%n",
                dg, tO, tL, el, Atmosphere.GAMMA*el, D, zf, Atmosphere.KAPPA_MEAN, obsAnn[(int)Math.round(dg/5)], pred, obsAnn[(int)Math.round(dg/5)]-pred, zmslk, zmslk-obsAnn[(int)Math.round(dg/5)], ZonalTables.tZmSlAnnual(lr), ZonalTables.tZmSlAnnual(lr)-obsAnn[(int)Math.round(dg/5)]));
        }
        sb.append("\n[B] 季节谐波 (首谐波, 从 T_ZM_SL_MONTH 直接算; A_ZM_K 表值并列)\n");
        sb.append(" lat |   A_obs    A_ZM |  psi_obs_d  psi_zm_d |  modJJA  modDJF |  obsJJA  obsDJF\n");
        double p0 = 2.0*Math.PI*ZonalTables.SEASON_SHAPE_PHI0;
        for (double dg = 25; dg <= 85.0001; dg += 5) {
            int j = (int)Math.round(dg/5); double lr = Math.toRadians(dg);
            double sc=0, ss=0; for (int m=0;m<12;m++){ double v=ZonalTables.T_ZM_SL_MONTH[m*N+j]; double an=2*Math.PI*m/12.0; sc+=v*Math.cos(an); ss+=v*Math.sin(an); }
            double Aobs = 2.0*Math.sqrt(sc*sc+ss*ss)/12.0, psiObs = Math.atan2(ss,sc)+p0;
            double Azm = ZonalTables.aZonalMean(dg), pzm = Atmosphere.phiZonalMean(dg);
            sb.append(String.format(LF," %3.0f | %7.2f %7.2f | %+10.2f %+9.2f | %7.2f %7.2f | %7.2f %7.2f%n",
                dg, Aobs, Azm, Math.toDegrees(psiObs), Math.toDegrees(pzm),
                PrecipField.zonalSlTemp(lr,thJ), PrecipField.zonalSlTemp(lr,thD),
                ZonalTables.tZmSlMonth(lr,thJ), ZonalTables.tZmSlMonth(lr,thD)));
        }
        sb.append("\n[C] 偏差分解:  bias = (kap - ZFe)*DELTA_pred  +  残差\n");
        sb.append(" lat |  biasJJA  biasDJF | predAnn(kap-ZFe)*D | residAnn | zmslk_used\n");
        for (double dg = 25; dg <= 85.0001; dg += 5) {
            double lr = Math.toRadians(dg), D = Atmosphere.landMinusOceanSLK(lr);
            double predAnn = (Atmosphere.KAPPA_MEAN - ZonalTables.zfEarth(lr))*D;
            double bj = PrecipField.zonalSlTemp(lr,thJ)-ZonalTables.tZmSlMonth(lr,thJ);
            double bd = PrecipField.zonalSlTemp(lr,thD)-ZonalTables.tZmSlMonth(lr,thD);
            sb.append(String.format(LF," %3.0f | %+8.2f %+8.2f | %+17.2f | %+8.2f | %9.3f%n",
                dg, bj, bd, predAnn, 0.5*(bj+bd)-predAnn, Atmosphere.zonalMeanSeaLevelK(lr)));
        }
        sb.append("\n[D] ★判据: 把 kappa 换成 ZF_earth 后, 模型能否复现观测表?\n");
        sb.append(" lat |  modJJA(kap) modJJA(ZFe)  obsJJA |  modDJF(kap) modDJF(ZFe)  obsDJF |  rms(kap)  rms(ZFe)\n");
        for (double dg = 25; dg <= 85.0001; dg += 5) {
            double lr = Math.toRadians(dg), zf = ZonalTables.zfEarth(lr);
            double mjk = PrecipField.zonalSlTemp(lr,thJ), mjz = Atmosphere.annualSeaLevelTemp(lr,zf,0.0)+Atmosphere.seasonalAnomalyZonal(lr,thJ);
            double mdk = PrecipField.zonalSlTemp(lr,thD), mdz = Atmosphere.annualSeaLevelTemp(lr,zf,0.0)+Atmosphere.seasonalAnomalyZonal(lr,thD);
            double oj = ZonalTables.tZmSlMonth(lr,thJ), od = ZonalTables.tZmSlMonth(lr,thD);
            double rk=0, rz=0; for (int m=0;m<12;m++){ double t=2*Math.PI*m/12.0;
                double ok=ZonalTables.tZmSlMonth(lr,t);
                rk+=(PrecipField.zonalSlTemp(lr,t)-ok)*(PrecipField.zonalSlTemp(lr,t)-ok);
                double zz=Atmosphere.annualSeaLevelTemp(lr,zf,0.0)+Atmosphere.seasonalAnomalyZonal(lr,t);
                rz+=(zz-ok)*(zz-ok); }
            sb.append(String.format(LF," %3.0f | %11.2f %11.2f %7.2f | %12.2f %11.2f %7.2f | %8.3f %8.3f%n",
                dg, mjk, mjz, oj, mdk, mdz, od, Math.sqrt(rk/12), Math.sqrt(rz/12)));
        }
        sb.append("\n判读: [D] rms(ZFe) 远小于 rms(kap)  => 唯一差异是 kappa 选择, 模型无错, 标尺是地球.\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
