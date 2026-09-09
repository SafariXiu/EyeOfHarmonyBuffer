package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

/**
 * V2 轨兼容辅助：旧轨（TectonicWorld / 宏气候 / RVR2 / 旧洞穴）调试指令在 V2 世界
 * 读到的是旧轨数据，与眼前世界不符，容易误判成"种子漂移"。给这类指令统一挂一条提示。
 */
public final class LegacyV2Note {

    private LegacyV2Note() {}

    /** V2 轨是否开启。 */
    public static boolean v2Active() {
        return V2TerrainConfigSection.terrainV2Enabled;
    }

    /** 在 V2 轨下打印旧轨提示（非 V2 轨零开销）。 */
    public static void note(ICommandSender sender) {
        if (V2TerrainConfigSection.terrainV2Enabled) {
            sender.addChatMessage(new ChatComponentText(
                "[talos] ⚠ V2 轨开启：本指令读取旧轨数据（TectonicWorld/宏气候/RVR2/旧洞穴），"
                    + "仅供参考，不再对应世界实际生成。"
            ));
        }
    }
}
