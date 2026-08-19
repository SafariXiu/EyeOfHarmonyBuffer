package com.EyeOfHarmonyBuffer.client.rbmk;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * 自写世界全息面板 PoC：/rbmkui 生成两个屏 ——
 * 左：堆芯俯瞰大屏（viewType 1）；右：控制面板（viewType 0）。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class RbmkHoloPoC {

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(RbmkHoloEntity.class, new RbmkHoloRender());
        FMLCommonHandler.instance().bus().register(new RbmkHoloInteraction());
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
                // 玩家视线"左"方向 = cross(look, up)
                Vec3 left = look.crossProduct(Vec3.createVectorHelper(0, 1, 0)).normalize();

                double bx = mc.thePlayer.posX + look.xCoord * 5;
                double by = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() + look.yCoord * 5;
                double bz = mc.thePlayer.posZ + look.zCoord * 5;

                // 左：堆芯俯瞰大屏
                RbmkHoloEntity coreView = new RbmkHoloEntity(mc.theWorld);
                coreView.viewType = 1;
                coreView.setPosition(bx + left.xCoord * 2.4, by, bz + left.zCoord * 2.4);
                mc.theWorld.spawnEntityInWorld(coreView);

                // 右：控制面板
                RbmkHoloEntity panel = new RbmkHoloEntity(mc.theWorld);
                panel.viewType = 0;
                panel.setPosition(bx - left.xCoord * 1.4, by, bz - left.zCoord * 1.4);
                mc.theWorld.spawnEntityInWorld(panel);
            }
        });
    }
}
