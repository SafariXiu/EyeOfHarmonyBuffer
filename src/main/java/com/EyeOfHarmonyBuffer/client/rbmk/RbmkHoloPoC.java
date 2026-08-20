package com.EyeOfHarmonyBuffer.client.rbmk;

import com.EyeOfHarmonyBuffer.client.holo.*;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * 全息屏 PoC：/rbmkui 生成两块平级根屏 ——
 * 左：堆芯俯瞰大屏（core）；右：控制面板（panel）。
 * 屏幕都通过 HoloScreenRegistry 创建（每次独立实例，状态互不共享）。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class RbmkHoloPoC {

    public static void register() {
        // 业务侧注册：RBMK 的两块平级根屏（框架不关心具体屏，只由这里告诉注册表）
        HoloScreenRegistry.register("panel", PanelScreen::new);
        HoloScreenRegistry.register("core", CoreViewScreen::new);
        RenderingRegistry.registerEntityRenderingHandler(HoloEntity.class, new HoloRender());
        FMLCommonHandler.instance().bus().register(new HoloInteraction());
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "rbmkui";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/rbmkui [1|2]";
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

                int mode = args.length >= 1 ? parseMode(args[0]) : 0;

                // mode==0: 全部(默认)；1: 仅控制面板(panel)；2: 仅堆芯大屏(core)
                if (mode == 0 || mode == 1) {
                    spawnScreen(mc, "panel", bx, by, bz, look, left, 1.4, 0.0);
                }
                if (mode == 0 || mode == 2) {
                    // 大屏沿视线方向后移 0.4 格：两块屏平面不再重叠，避免深度测试下互相 z-fighting 闪烁
                    spawnScreen(mc, "core", bx, by, bz, look, left, -4.0, 0.4);
                }
            }

            private int parseMode(String s) {
                if ("1".equals(s)) {
                    return 1;  // 控制面板
                }
                if ("2".equals(s)) {
                    return 2;  // 堆芯大屏
                }
                return 0;
            }
        });
    }

    /** 通过注册表创建一块屏并生成实体。
     * off 为相对视线"左"方向的偏移（可正可负）；depth 为沿视线"后移"的深度偏移
     * （>0 让屏离玩家更远，用于把多块屏的平面错开，避免重叠处 z-fighting）。 */
    private static void spawnScreen(Minecraft mc, String id, double bx, double by, double bz,
                                    Vec3 look, Vec3 left, double off, double depth) {
        HoloScreen screen = HoloScreenRegistry.create(id);
        if (screen == null) {
            return;
        }
        HoloEntity e = new HoloEntity(mc.theWorld);
        e.setScreen(screen);
        e.setPosition(
            bx - left.xCoord * off + look.xCoord * depth,
            by + look.yCoord * depth,
            bz - left.zCoord * off + look.zCoord * depth);
        mc.theWorld.spawnEntityInWorld(e);
    }
}
