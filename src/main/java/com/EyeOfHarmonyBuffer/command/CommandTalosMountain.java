package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.api.TalosMountainSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime.MountainBelt;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime.MountainWorldState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;

/**
 * /talmountain - 山地系统调试指令。
 *
 * 输出当前玩家位置的：
 *   - 构造风格层级（0=非山地，1=HIGHLAND，2=MOUNTAINS，3=PEAK）
 *   - 所在山带（id / 类型 / 网格 / 蒙版 / 高程）
 *   - 当前群系
 *   - 缓存规模（已构建山带 / 索引格 / 样式缓存 / 已扫描 tile）
 */
public class CommandTalosMountain extends CommandBase {

    @Override
    public String getCommandName() {
        return "talmountain";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talmountain - 查看当前位置的山地系统状态";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("必须由玩家在服务端执行。"));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.worldObj;
        int worldSeedInt = TalosLandMask.getWorldSeedInt(world);
        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        if (!TalosMountainSystem.isEnabled()) {
            send(sender, "[TALMOUNTAIN] 山地系统已禁用 (talos.mountain.enabled=false)");
            return;
        }

        MountainWorldState state = TalosMountainSystem.getState(worldSeedInt);
        if (state == null) {
            send(sender, "[TALMOUNTAIN] 山地状态不存在：WorldEvent.Load 未触发，"
                + "或当前维度不是 Talos（seed=" + worldSeedInt + "）");
            return;
        }

        int tier = state.debugStyleTier(px, pz);
        MountainBelt belt = state.beltAt(px, pz);
        BiomeGenBase biome = world.getBiomeGenForCoords(px, pz);

        send(sender, String.format(
            "[TALMOUNTAIN] pos=(%d,%d) seed=%d styleTier=%d biome=%s",
            px, pz, worldSeedInt, tier,
            biome != null ? biome.biomeName : "null"
        ));

        if (belt != null) {
            send(sender, String.format(
                "  山带 id=%d kind=%d grid=%dx%d mask=%.3f elev=%.3f "
                    + "center=(%.0f,%.0f) len=%.0f wid=%.0f",
                belt.beltId,
                belt.kind,
                belt.gridW,
                belt.gridH,
                belt.sampleMask01(px, pz),
                belt.sampleElevation01(px, pz),
                belt.centerX,
                belt.centerZ,
                belt.halfLength * 2.0,
                belt.halfWidth * 2.0
            ));
        } else {
            send(sender, "  当前位置不在任何已构建山带内");
        }

        send(sender, String.format(
            "  缓存: belts=%d indexedCells=%d styleCache=%d scannedTiles=%d",
            state.debugBeltCount(),
            state.debugIndexedCellCount(),
            state.debugStyleCacheSize(),
            state.debugScannedTileCount()
        ));

        List<MountainBelt> belts = state.debugBelts();
        if (!belts.isEmpty()) {
            send(sender, "  已构建山带 " + belts.size() + " 条:");
            int shown = 0;
            for (MountainBelt b : belts) {
                if (shown >= 6) {
                    send(sender, "    ...");
                    break;
                }
                send(sender, String.format(
                    "    id=%d kind=%d grid=%dx%d center=(%.0f,%.0f) len=%.0f wid=%.0f",
                    b.beltId, b.kind, b.gridW, b.gridH,
                    b.centerX, b.centerZ,
                    b.halfLength * 2.0, b.halfWidth * 2.0
                ));
                shown++;
            }
        } else {
            send(sender, "  尚未构建任何山带（后台预构建可能在工作中）");
        }
    }

    private static void send(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
