package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class CommandTalosSuperCenter extends CommandBase {

    @Override
    public String getCommandName() {
        return "talosSuperCenter";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosSuperCenter - 传送到当前所在超级大陆（主/次级均可）的中心点（Debug 用）";
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
        LegacyV2Note.note(sender);

        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);

        int superId = TalosLandMask.getSuperId(px, pz, worldSeedInt);
        if (superId == 0) {
            sender.addChatMessage(new ChatComponentText(
                "当前位置 superId=0（海洋或未定义区域），无法定位超级大陆中心。"
            ));
            return;
        }

        int[] center = TalosLandMask.getSuperCenterXZById(superId, worldSeedInt);
        if (center == null) {
            sender.addChatMessage(new ChatComponentText(
                String.format(
                    "无法获取超级大陆中心信息（superId=%d，可能数据异常）。",
                    superId
                )
            ));
            return;
        }

        int centerX = center[0];
        int centerZ = center[1];

        int y = world.getTopSolidOrLiquidBlock(centerX, centerZ);
        if (y <= 0) {
            y = 64;
        }
        // 钳制到合法玩家高度（最高 255），避免山顶列 y+2 越界被踢 "Illegal position"
        y = Math.min(y, 253);

        double tpX = centerX + 0.5;
        double tpY = y + 2.0;
        double tpZ = centerZ + 0.5;

        player.setPositionAndUpdate(tpX, tpY, tpZ);

        String mainSub = TalosLandMask.isMainSupercontinent(superId)
            ? "主大陆" : "次级大陆";

        sender.addChatMessage(new ChatComponentText(
            String.format(
                "[TalosSuper] 跳转到%s中心: superId=%d, center=(%d, ~%d, %d)",
                mainSub,
                superId,
                centerX, y + 2,
                centerZ
            )
        ));
    }
}
