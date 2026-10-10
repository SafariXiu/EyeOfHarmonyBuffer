package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.LandformField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.TalosSeed;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * {@code /talos_tp <区域> [半径] [首选方向]} —— TP 到【海陆分布层】的指定地形区域。
 *
 * <h3>为什么需要它</h3>
 * 海陆分布层的特征尺度是 125 km（{@code TalosLandField.SUPER_ABS}），
 * 靠走是走不到「台地 / 山地 / 海岸」的；本命令按地形判据直接定位并传送。
 *
 * <h3>支持的区域（判据来自 {@link OrographyField.OroSample}）</h3>
 * <pre>
 *   lowland  低地     isLand && kind==KIND_LOWLAND
 *   hill     丘陵     isLand && kind==KIND_HILL
 *   plateau  台地     isLand && kind==KIND_PLATEAU
 *   mountain 山地     isLand && kind==KIND_MOUNTAIN
 *   peak     峰       isLand && kind==KIND_PEAK
 *   high     高地     isLand && elev >= <高度>   （第二参数是米）
 *   low      低地(-)  isLand && elev <= <高度>   （第二参数是米）
 *   coast    海岸     陆地上 |coastDist| 最小
 *   shelf    浅海     !isLand && elev > -2000
 *   ocean    深海     !isLand && elev <= -2000
 *   any      任意陆地 isLand
 * </pre>
 *
 * <h3>用法</h3>
 * <pre>
 *   /talos_tp plateau              在当前点周围 200 km 内找最近的台地
 *   /talos_tp mountain 500000      搜索半径 500 km
 *   /talos_tp high 3000            找海拔 >= 3000 米的陆地
 *   /talos_tp coast                找最近的海岸线
 *   /talos_tp ocean 1000000        找最近的深海
 * </pre>
 *
 * <p>搜索用【螺旋外扩】（从玩家位置一圈圈往外），所以找到的总是【最近】的命中点；
 * 步长 {@link #STEP} = 250 格（= {@code LandformField.CELL}），与地形网格同粒度。
 */
public class CommandTalosTp extends CommandBase {

    /** 搜索步长（格）。= LandformField.CELL，与地形网格同粒度。 */
    private static final int STEP = 250;
    /** 默认搜索半径（格）= 200 km。 */
    private static final int DEFAULT_RADIUS = 200_000;
    /** 最大搜索半径（格）= 2000 km。 */
    private static final int MAX_RADIUS = 2_000_000;
    /** 海岸判据：|coastDist| 小于此值算「海岸」。 */
    private static final double COAST_BAND = 2_000.0;

    /** ★ 河流类目的搜索步长（格）。河宽只有 3 到 45 格，用 250 会大量漏掉。 */
    private static final int RIVER_STEP = 50;
    /** ★ 河流类目的默认搜索半径（格）= 20 km。这个范围内几乎必有河。 */
    private static final int RIVER_RADIUS = 20_000;
    /** ★ 干流判据：汇流面积（平方公里）大于等于它算「主干」，否则算「支流」。 */
    private static final double TRUNK_ACC = 500.0;
    /** ★ 吸附半径（格）：搜索点离河道中心线小于它就算命中。接入蜿蜒后必须要，否则会漏。 */
    private static final double SNAP_BLOCKS = 250.0;

    /** 是否河流类目。 */
    private static boolean isRiverKind(String w) {
        return "river".equals(w) || "trunk".equals(w) || "tributary".equals(w) || "lake".equals(w); }

    @Override
    public String getCommandName() {
        return "talos_tp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talos_tp <lowland|hill|plateau|mountain|peak|high|low|coast|shelf|ocean|any"
            + " | river|trunk|tributary|lake> [半径] [阈值]"
            + "\n  ★ 水系类目（需 TalosLandErosion.ENABLED）："
            + "\n    river      任意河道"
            + "\n    trunk      主干（汇流面积 大于等于 " + (int) TRUNK_ACC + " 平方公里）"
            + "\n    tributary  支流（汇流面积 小于 " + (int) TRUNK_ACC + " 平方公里）"
            + "\n    lake       湖面"
            + "\n  河流类目默认半径 " + (RIVER_RADIUS / 1000) + " km、步长 " + RIVER_STEP + " 格；"
            + "\n  第二参数若是公里数则当半径（乘 1000）。";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new WrongUsageException("该指令只能由玩家执行。");
        }
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.getEntityWorld();
        int worldSeedInt = TalosSeed.of(world);

        String what = args[0].toLowerCase();
        // ★ 河流类目用细步长 + 小默认半径（河宽 3 到 45 格，250 格步长会漏）
        final int step = isRiverKind(what) ? RIVER_STEP : STEP;
        int radius = isRiverKind(what) ? RIVER_RADIUS : DEFAULT_RADIUS;
        if (args.length >= 2) {
            radius = parseIntBounded(sender, args[1], step, MAX_RADIUS);
            // 河流类目：第二参数若 <= 2000 视为【公里】，乘 1000
            if (isRiverKind(what) && radius <= 2000) { radius *= 1000; }
        }
        // high/low 的阈值（米）；缺省 2000
        double threshold = 2000.0;
        if (args.length >= 3) {
            try { threshold = Double.parseDouble(args[2]); }
            catch (NumberFormatException e) { throw new WrongUsageException("高度必须是数字。"); }
        }
        if (!isKnown(what)) {
            throw new WrongUsageException("未知区域：" + what + "\n" + getCommandUsage(sender));
        }

        final int px = MathHelper.floor_double(player.posX);
        final int pz = MathHelper.floor_double(player.posZ);
        sender.addChatMessage(new ChatComponentText(String.format(
            "[TalosTp] 从 (%d, %d) 开始螺旋搜索 \"%s\"，半径 %d 格（%d km），步长 %d...",
            px, pz, what, radius, radius / 1000, step)));

        dbgTried = 0; dbgHit = 0; dbgTileNull = 0; dbgErr = 0; dbgFirstErr = null;
        long t0 = System.nanoTime();
        int[] hit = spiralSearch(px, pz, radius, what, threshold, worldSeedInt, step);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (isRiverKind(what)) {
            sender.addChatMessage(new ChatComponentText(String.format(
                "[TalosTp] ★ 诊断: 尝试 %d 点  命中 %d  installedSeed=%d  worldSeed=%d  seedMismatch=%d  异常=%d%s",
                dbgTried, dbgHit,
                com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion.installedSeed(),
                worldSeedInt, dbgTileNull, dbgErr,
                dbgFirstErr == null ? "" : ("  首个异常: " + dbgFirstErr))));
        }

        if (hit == null) {
            sender.addChatMessage(new ChatComponentText(String.format(
                "[TalosTp] 半径 %d 格内未找到 \"%s\"（用时 %d ms）。加大半径重试。",
                radius, what, ms)));
            return;
        }

        int tx = hit[0], tz = hit[1];
        // ★★ 水系类目：吸附到【中心线上】的点 —— 保证落点一定在水上
        if (isRiverKind(what)) {
            double[] pt = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                    .nearestChannelPoint((long) worldSeedInt, tx, tz);
            if (pt != null) {
                sender.addChatMessage(new ChatComponentText(String.format(
                    "[TalosTp] ★ 吸附到中心线: (%d, %d) -> (%.0f, %.0f)  汇流 %.0f 平方公里",
                    tx, tz, pt[0], pt[1], pt[2])));
                tx = (int) Math.floor(pt[0]);
                tz = (int) Math.floor(pt[1]);
            } }
        int y = world.getTopSolidOrLiquidBlock(tx, tz);
        if (y <= 0) { y = LandformField.SEA_LEVEL + 1; }
        y = Math.min(y, 250);   // 防越界
        player.setPositionAndUpdate(tx + 0.5, y + 1.5, tz + 0.5);

        double dist = Math.hypot(tx - (double) px, tz - (double) pz);
        sender.addChatMessage(new ChatComponentText(String.format(
            "[TalosTp] ★ 已传送到 \"%s\"：dist=%.1f 格, pos=(%d, %d, %d), 用时 %d ms",
            what, dist, tx, y + 1, tz, ms)));
        // 落地后补一份地形信息
        try {
            OrographyField.OroSample o = OrographyField.sample(tx, tz, worldSeedInt);
            sender.addChatMessage(new ChatComponentText(String.format(
                "[TalosTp] 该点: land=%s kind=%d elev01=%.3f relief01=%.3f belt01=%.3f coastDist=%.0f",
                o.isLand ? "陆" : "海", o.kind, o.elevation01, o.relief01, o.beltMask01, o.coastDist)));
        } catch (Throwable ignored) { }
        // ★ 水系类目：补一份水系信息（宽度 / 汇流面积 / 水面试探）
        try {
            final long ews = (long) worldSeedInt;
            final double acc = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                    .channelAccAt(ews, tx, tz);
            final double w = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                    .riverWidthBlocks(ews, tx, tz);
            final double surf = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                    .riverSurfaceBlocks(ews, tx, tz);
            final double dep = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                    .riverDepth(acc);
            final boolean lake = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                    .isLake(ews, tx, tz);
            if (w > 0.0 || lake) {
                sender.addChatMessage(new ChatComponentText(String.format(
                    "[TalosTp] ★ 水系: %s  河宽=%.1f 格  汇流=%.0f 平方公里  河深=%.1f 格  水面y=%s  湖深=%.1f 格",
                    lake ? "湖" : (acc >= TRUNK_ACC ? "主干" : "支流"),
                    w, acc, dep,
                    Double.isNaN(surf) ? "-" : String.valueOf((int) Math.round(surf)),
                    com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                            .lakeDepthBlocks(ews, tx, tz))));
            } else {
                sender.addChatMessage(new ChatComponentText(
                    "[TalosTp] 该点不是水系（可能 LEM 未开启或该点恰在岸边）。"));
            }
        } catch (Throwable ignored) { }
        sender.addChatMessage(new ChatComponentText("提示：用 /talos_here 看完整高度分解。"));
    }

    /** 名字是否已知。 */
    private static boolean isKnown(String what) {
        switch (what) {
            case "lowland": case "hill": case "plateau": case "mountain": case "peak":
            case "high": case "low": case "coast": case "shelf": case "ocean": case "any":
            case "river": case "trunk": case "tributary": case "lake":
                return true;
            default:
                return false;
        }
    }

    /**
     * 螺旋外扩搜索：从 (px,pz) 一圈圈往外，返回第一个命中点 {x, z}，找不到返回 null。
     * <p>「一圈」= 正方形环。环内按周长顺序遍历，保证结果近似「最近」。
     */
    private static int[] spiralSearch(int px, int pz, int radius, String what,
                                      double threshold, int seed, int step) {
        final int st = Math.max(1, step);
        int maxRing = radius / st;
        // 先看原点
        if (matches(px, pz, what, threshold, seed)) { return new int[]{ px, pz }; }
        for (int ring = 1; ring <= maxRing; ring++) {
            int d = ring * st;
            // 上边 + 下边
            for (int i = -ring; i <= ring; i++) {
                int x = px + i * st;
                if (matches(x, pz - d, what, threshold, seed)) { return new int[]{ x, pz - d }; }
                if (matches(x, pz + d, what, threshold, seed)) { return new int[]{ x, pz + d }; }
            }
            // 左边 + 右边（去掉角，避免重复）
            for (int j = -ring + 1; j <= ring - 1; j++) {
                int z = pz + j * st;
                if (matches(px - d, z, what, threshold, seed)) { return new int[]{ px - d, z }; }
                if (matches(px + d, z, what, threshold, seed)) { return new int[]{ px + d, z }; }
            }
        }
        return null;
    }

    /** ★ 诊断计数器（每次搜索重置）。 */
    private static int dbgTried = 0, dbgHit = 0, dbgTileNull = 0, dbgErr = 0;
    private static String dbgFirstErr = null;

    /** 单点判据。 */
    private static boolean matches(int x, int z, String what, double threshold, int seed) {
        if (isRiverKind(what)) {
            dbgTried++;
            try {
                boolean r = matchesRiver(x, z, what, seed);
                if (r) dbgHit++;
                return r;
            } catch (Throwable t) {
                dbgErr++;
                if (dbgFirstErr == null) { dbgFirstErr = t.getClass().getSimpleName() + ": " + t.getMessage(); }
                return false;
            }
        }
        return matchesPlain(x, z, what, threshold, seed);
    }

    private static boolean matchesRiver(int x, int z, String what, int seed) {
        if (!com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion.ENABLED) {
            return false; }
        final long ws = (long) seed;
        if (com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion.installedSeed()
                != seed) { dbgTileNull++; return false; }
        if ("lake".equals(what)) {
            return com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                    .isLake(ws, x, z); }
        // ★ 接入蜿蜒后，河道格的中心可能【不在河上】（中心线横移最多 120 格）。
        //   所以这里用「到中心线的距离 <= 吸附半径」判定，而不是 isRiver(格中心)。
        final double cd = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                .channelDistance(ws, x, z);
        if (!(cd <= SNAP_BLOCKS)) { return false; }
        if ("river".equals(what)) { return true; }
        final double acc = com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion
                .accAt(ws, x, z);
        if ("trunk".equals(what)) { return acc >= TRUNK_ACC; }
        return acc < TRUNK_ACC;                 // tributary
    }

    private static boolean matchesPlain(int x, int z, String what, double threshold, int seed) {
        OrographyField.OroSample o = OrographyField.sample(x, z, seed);
        switch (what) {
            case "lowland":  return o.isLand && o.kind == OrographyField.KIND_LOWLAND;
            case "hill":     return o.isLand && o.kind == OrographyField.KIND_HILL;
            case "plateau":  return o.isLand && o.kind == OrographyField.KIND_PLATEAU;
            case "mountain": return o.isLand && o.kind == OrographyField.KIND_MOUNTAIN;
            case "peak":     return o.isLand && o.kind == OrographyField.KIND_PEAK;
            case "coast":    return o.isLand && Math.abs(o.coastDist) <= COAST_BAND;
            case "shelf":    return !o.isLand && o.coastDist > -COAST_BAND * 10.0;
            case "ocean":    return !o.isLand && o.coastDist <= -COAST_BAND * 10.0;
            case "any":      return o.isLand;
            case "high":
            case "low": {
                if (!o.isLand) { return false; }
                if (o.coastDist > 0.0) { return false; }
                // 用生产同一个入口算实际米数
                // ★ 用生产同一个入口（与 SimTerrain.compose 的 elev 同源）
                double m = PlateField.elevation(x, z, (long) seed, PlateField.ERO_EXTRA_AMP);
                return what.equals("high") ? (m >= threshold) : (m <= threshold);
            }
            default:
                return false;
        }
    }
}