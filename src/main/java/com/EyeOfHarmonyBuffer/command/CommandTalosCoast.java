package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.TalosSeed;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * /talos_coast [方向] [最大搜索千格] -- 沿指定方向找到最近的【海岸线】并把玩家传送过去。
 *
 * <h3>为什么需要它</h3>
 * Talos2 的大陆尺度是【大陆级】的（`DCELL = 2,222,222` 方块，约 2222 km），
 * 站在大陆内部时海岸线可能在几千公里之外，手动飞过去不现实。
 *
 * <h3>判据必须与生产一致</h3>
 * 本指令用 {@link TalosField#isLand(double, double, long)} —— 与 {@code ChunkProviderTalos2:213} 的
 * 方块海陆判定【同一个入口】（{@code PlateField.isLandWithCell} 最终就调它）。
 * 所以指令报告的海岸线就是游戏里真正看到的那条线，不存在口径漂移。
 *
 * <h3>算法</h3>
 * <ol>
 *   <li>从玩家出发沿 8 个方向（或指定方向）做【粗略推进】，步长自适应：
 *       近处 250 方块（粗定位精确的海岸），远处逐步放大到 20,000 方块（快速跨越大陆）</li>
 *   <li>一旦检测到 land/sea 翻转，在最后一个区间内做【二分】到约 4 方块精度</li>
 *   <li>取 8 个方向里最近的那个（或指定方向）</li>
 * </ol>
 * 粗步长的存在让最坏情况的采样数保持在【几千次】，不会卡服。
 */
public class CommandTalosCoast extends CommandBase {

    /** 8 个方向：E, NE, N, NW, W, SW, S, SE。命名用罗盘方位。 */
    private static final String[] DIR_NAMES = { "E", "NE", "N", "NW", "W", "SW", "S", "SE" };

    @Override
    public String getCommandName() {
        return "talos_coast";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talos_coast [方向 E|NE|N|NW|W|SW|S|SE|auto] [最大搜索千格，默认 4000]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("[Talos] 该指令只能由玩家执行。"));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.getEntityWorld();

        final int px = MathHelper.floor_double(player.posX);
        final int pz = MathHelper.floor_double(player.posZ);

        final int worldSeedInt = TalosSeed.of(world);
        final long seed = SimTerrain.seedOf(worldSeedInt);

        // ---- 参数 ----
        int onlyDir = -1;
        if (args.length >= 1 && !"auto".equalsIgnoreCase(args[0])) {
            String want = args[0].toUpperCase();
            for (int i = 0; i < DIR_NAMES.length; i++) {
                if (DIR_NAMES[i].equals(want)) { onlyDir = i; break; }
            }
            if (onlyDir < 0) {
                sender.addChatMessage(new ChatComponentText("[Talos] 方向必须是 E|NE|N|NW|W|SW|S|SE 或 auto。"));
                return;
            }
        }
        long maxK = 4000;
        if (args.length >= 2) {
            try {
                maxK = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText("[Talos] 第二个参数必须是整数（千格）。"));
                return;
            }
        }
        final long maxDist = maxK * 1000L;

        final boolean insideIsLand = PlateField.isLand(px, pz, seed);
        sender.addChatMessage(new ChatComponentText(String.format(
            "[Talos] 起点 (%d, %d) 当前是%s；沿%s方向搜索海岸线（上限 %,d 千格）...",
            px, pz, insideIsLand ? "陆地" : "海洋",
            onlyDir < 0 ? "8 个" : DIR_NAMES[onlyDir], maxK)));

        // ---- 逐方向搜索 ----
        double bestDist = Double.MAX_VALUE;
        double bestX = 0, bestZ = 0;
        String bestDir = "?";

        for (int d = 0; d < 8; d++) {
            if (onlyDir >= 0 && d != onlyDir) continue;
            double ang = Math.toRadians(45.0 * d);
            double ux = Math.cos(ang), uz = Math.sin(ang);

            // 自适应步长推进：近处细，远处粗。
            double t = 0;
            double prevT = 0;
            boolean prev = insideIsLand;
            boolean found = false;
            while (t < maxDist) {
                double step;
                if (t < 2_000) step = 250;
                else if (t < 20_000) step = 1_000;
                else if (t < 200_000) step = 5_000;
                else step = 20_000;

                prevT = t;
                t += step;
                if (t > maxDist) t = maxDist;

                double x = px + ux * t, z = pz + uz * t;
                boolean now = PlateField.isLand(x, z, seed);
                if (now != prev) {
                    // 在 [prevT, t] 内二分到约 4 方块
                    double lo = prevT, hi = t;
                    for (int it = 0; it < 24 && (hi - lo) > 4.0; it++) {
                        double mid = 0.5 * (lo + hi);
                        boolean vm = PlateField.isLand(px + ux * mid, pz + uz * mid, seed);
                        if (vm == prev) lo = mid; else hi = mid;
                    }
                    double fx = px + ux * hi, fz = pz + uz * hi;
                    double dist = Math.sqrt((fx - px) * (fx - px) + (fz - pz) * (fz - pz));
                    if (dist < bestDist) {
                        bestDist = dist; bestX = fx; bestZ = fz; bestDir = DIR_NAMES[d];
                    }
                    found = true;
                    break;
                }
                prev = now;
            }
            if (!found && onlyDir >= 0) {
                sender.addChatMessage(new ChatComponentText(
                    "[Talos] 沿 " + DIR_NAMES[d] + " 方向在 " + maxK + " 千格内没找到海岸线（可能一直在同一侧）。"));
            }
        }

        if (bestDist == Double.MAX_VALUE) {
            sender.addChatMessage(new ChatComponentText(
                "[Talos] 未找到海岸线。试试更大的上限，例如 /talos_coast auto 12000"));
            return;
        }

        // ---- 传送 ----
        final int tx = MathHelper.floor_double(bestX);
        final int tz = MathHelper.floor_double(bestZ);
        // 目的地的地面高度（会顺带加载该区块）
        int ty = world.getTopSolidOrLiquidBlock(tx, tz) + 1;
        // 保险：海岸线的陆地点高度地板是 seaLevel+1（SimTerrain:634），但若落点恰好落在
        // 【刚过线的那一格】（isLand 已经为 true、高度却还没抬起来），玩家会站进水里。
        // 直接按生产高度链算一次并取两者较大值。
        final double elevM = PlateField.elevation(tx, tz, seed);
        if (elevM > 0.0) {
            final int hBlocks = (int) Math.floor(63.0 + SimTerrain.ELEV_TO_BLK * elevM) + 1;
            if (hBlocks > ty) ty = hBlocks;
        }

        player.setPositionAndUpdate(tx + 0.5, ty, tz + 0.5);

        sender.addChatMessage(new ChatComponentText(String.format(
            "[Talos] 已传送到海岸线：方向 %s，距离 %.1f 格（%.1f km），落点 (%d, %d, %d)",
            bestDir, bestDist, bestDist / 1000.0, tx, ty, tz)));
        sender.addChatMessage(new ChatComponentText(String.format(
            "[Talos] 落点海陆：%s（同一条判据 PlateField.isLand）",
            PlateField.isLand(tx, tz, seed) ? "陆地" : "海洋")));
    }
}