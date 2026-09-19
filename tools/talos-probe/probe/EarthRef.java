package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * ★★★ 地球验证层的【唯一】地球知识所在。
 *
 * <p><b>为什么要单独一个文件（用户要求：「必须严格区分开，别把他们和我们自己的混在一起」）</b>：
 * <ul>
 *   <li>{@code src} 里只有两个可为 null 的接口（{@code PlateField.MASK} / {@code PlateField.ELEV}），
 *       <b>零地球知识</b> —— 没有文件路径、没有 ETOPO1、没有观测数据；</li>
 *   <li>所有地球文件路径、二进制布局、经纬度换算、安装/卸载，集中在<b>这一个</b>文件里；
 *       任何地球验证探针都必须走它，不许各写一份；</li>
 *   <li>{@link #uninstall()} 之后生产路径逐位回到原状（{@code P293} 复验）。</li>
 * </ul>
 *
 * <p>两个 bin 由 {@code refs/make_earth_mask_bin.py} 与 {@code refs/make_earth_elev_bin.py} 从
 * <b>同一个</b> {@code etopo1_025.npz} 生成，头部字段逐项相同 ⇒ <b>掩膜与高程逐格对齐</b>，
 * 这正是修掉「掩膜说陆地、高程说海底」那个矛盾的关键。
 *
 * <p>⚠ 已知限差：ETOPO1 的 {@code lon} 数组实际非严格等距（末端约差 1 格 = 0.25 度）。
 * 掩膜与高程用同一套等距索引公式 ⇒ <b>互相一致</b>；绝对经度位置在最东端最多偏 0.25 度（约 28 km）。
 */
public final class EarthRef {

    private EarthRef() {}

    public static final File REF = new File("K:" + File.separator + "moder" + File.separator
            + "EyeOfHarmonyBuffer" + File.separator + "build" + File.separator + "eoh_probe" + File.separator + "refs");
    public static final File MASKF = new File(REF, "earth_mask.bin");
    public static final File ELEVF = new File(REF, "earth_elev.bin");
    /** 赤道周长（与 WorldContract.Z_CYCLE 一致：1 格 = 1 m）。 */
    public static final double CIRC = 40_000_000.0;

    public static int nlat, nlon;
    public static double lat0, dlat, lon0, dlon;
    static byte[] land;
    static float[] elev;
    public static boolean loaded = false;

    public static synchronized void load() throws IOException {
        if (loaded) return;
        byte[] mb = Files.readAllBytes(MASKF.toPath());
        ByteBuffer m = ByteBuffer.wrap(mb).order(ByteOrder.LITTLE_ENDIAN);
        if (m.get() != 'E' || m.get() != 'M' || m.get() != 'S' || m.get() != 'K')
            throw new IOException("earth_mask.bin magic != EMSK");
        nlat = m.getInt(); nlon = m.getInt();
        lat0 = m.getDouble(); dlat = m.getDouble(); lon0 = m.getDouble(); dlon = m.getDouble();
        land = new byte[nlat * nlon];
        m.get(land);

        byte[] eb = Files.readAllBytes(ELEVF.toPath());
        ByteBuffer e = ByteBuffer.wrap(eb).order(ByteOrder.LITTLE_ENDIAN);
        if (e.get() != 'E' || e.get() != 'E' || e.get() != 'L' || e.get() != 'V')
            throw new IOException("earth_elev.bin magic != EELV");
        int elat = e.getInt(), elon = e.getInt();
        double ela0 = e.getDouble(), edla = e.getDouble(), elo0 = e.getDouble(), edlo = e.getDouble();
        // ★ 头部必须逐项一致，否则逐格错位 —— 这是硬门，不是注释。
        if (elat != nlat || elon != nlon || ela0 != lat0 || edla != dlat || elo0 != lon0 || edlo != dlon)
            throw new IOException("earth_mask 与 earth_elev 头部不一致 => 逐格会错位");
        elev = new float[nlat * nlon];
        e.asFloatBuffer().get(elev);
        loaded = true;
    }

    /** 与 earthIsLand 完全相同的取格公式（掩膜/高程共用，保证同格）。 */
    static int idx(int x, int z) {
        double lat = Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
        double lon = (x / CIRC) * 360.0;
        lon -= Math.floor(lon / 360.0) * 360.0;
        int i = (int) Math.round((lat - lat0) / dlat);
        int j = (int) Math.round((lon - lon0) / dlon);
        if (i < 0) i = 0; if (i >= nlat) i = nlat - 1;
        j = ((j % nlon) + nlon) % nlon;
        return i * nlon + j;
    }

    public static boolean isLand(int x, int z) { return land[idx(x, z)] != 0; }
    public static double elevMeters(int x, int z) { return elev[idx(x, z)]; }

    /** 装到生产钩子上（地球验证专用）。 */
    public static void install() throws IOException {
        load();
        PlateField.MASK = new PlateField.LandMask() { public boolean isLand(int x, int z) { return EarthRef.isLand(x, z); } };
        PlateField.ELEV = new PlateField.ElevSource() { public double elevMeters(int x, int z) { return EarthRef.elevMeters(x, z); } };
    }

    /** 卸掉，回到生产默认态。 */
    public static void uninstall() { PlateField.MASK = null; PlateField.ELEV = null; }

    public static boolean installed() { return PlateField.MASK != null || PlateField.ELEV != null; }
}
