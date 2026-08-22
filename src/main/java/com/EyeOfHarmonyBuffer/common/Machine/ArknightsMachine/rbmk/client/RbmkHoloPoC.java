package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.client;

import com.EyeOfHarmonyBuffer.client.holo.*;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
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
                int mode = args.length >= 1 ? parseMode(args[0]) : 0;

                // mode==0: 全部(默认)；1: 仅控制面板(panel)；2: 仅堆芯大屏(core)
                if (mode == 0 || mode == 1) {
                    HoloSpawn.spawnInFront(mc.thePlayer, "panel", 5, 1.4, 0.0);
                }
                if (mode == 0 || mode == 2) {
                    // 大屏沿视线方向后移 0.4 格：两块屏平面不再重叠，避免深度测试下互相 z-fighting 闪烁
                    HoloSpawn.spawnInFront(mc.thePlayer, "core", 5, -4.0, 0.4);
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
}