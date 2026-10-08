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

    @Override
    public String getCommandName() {
        return "talos_tp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talos_tp <lowland|hill|plateau|mountain|peak|high|low|coast|shelf|ocean|any> [半径] [高度(米)]";
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
        int radius = DEFAULT_RADIUS;
        if (args.length >= 2) {
            radius = parseIntBounded(sender, args[1], STEP, MAX_RADIUS);
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
            "[TalosTp] 从 (%d, %d) 开始螺旋搜索 \"%s\"，半径 %d 格，步长 %d...",
            px, pz, what, radius, STEP)));

        long t0 = System.nanoTime();
        int[] hit = spiralSearch(px, pz, radius, what, threshold, worldSeedInt);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        if (hit == null) {
            sender.addChatMessage(new ChatComponentText(String.format(
                "[TalosTp] 半径 %d 格内未找到 \"%s\"（用时 %d ms）。加大半径重试。",
                radius, what, ms)));
            return;
        }

        final int tx = hit[0], tz = hit[1];
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
        sender.addChatMessage(new ChatComponentText("提示：用 /talos_here 看完整高度分解。"));
    }

    /** 名字是否已知。 */
    private static boolean isKnown(String what) {
        switch (what) {
            case "lowland": case "hill": case "plateau": case "mountain": case "peak":
            case "high": case "low": case "coast": case "shelf": case "ocean": case "any":
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
                                      double threshold, int seed) {
        int maxRing = radius / STEP;
        // 先看原点
        if (matches(px, pz, what, threshold, seed)) { return new int[]{ px, pz }; }
        for (int ring = 1; ring <= maxRing; ring++) {
            int d = ring * STEP;
            // 上边 + 下边
            for (int i = -ring; i <= ring; i++) {
                int x = px + i * STEP;
                if (matches(x, pz - d, what, threshold, seed)) { return new int[]{ x, pz - d }; }
                if (matches(x, pz + d, what, threshold, seed)) { return new int[]{ x, pz + d }; }
            }
            // 左边 + 右边（去掉角，避免重复）
            for (int j = -ring + 1; j <= ring - 1; j++) {
                int z = pz + j * STEP;
                if (matches(px - d, z, what, threshold, seed)) { return new int[]{ px - d, z }; }
                if (matches(px + d, z, what, threshold, seed)) { return new int[]{ px + d, z }; }
            }
        }
        return null;
    }

    /** 单点判据。 */
    private static boolean matches(int x, int z, String what, double threshold, int seed) {
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