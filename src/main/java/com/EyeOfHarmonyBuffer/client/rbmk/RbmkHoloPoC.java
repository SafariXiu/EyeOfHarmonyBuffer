package com.EyeOfHarmonyBuffer.client.rbmk;

import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * 自写世界全息面板 PoC：/rbmkui 在玩家面前 4 格生成全息面板（纯渲染验证）。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class RbmkHoloPoC {

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(RbmkHoloEntity.class, new RbmkHoloRender());
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "rbmkui";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/rbmkui";
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 0;
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer == null || mc.theWorld == null) {
                    return;
                }
                Vec3 look = mc.thePlayer.getLookVec();
                double x = mc.thePlayer.posX + look.xCoord * 4;
                double y = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() + look.yCoord * 4;
                double z = mc.thePlayer.posZ + look.zCoord * 4;
                RbmkHoloEntity e = new RbmkHoloEntity(mc.theWorld);
                e.setPosition(x, y, z);
                mc.theWorld.spawnEntityInWorld(e);
            }
        });
    }
}
