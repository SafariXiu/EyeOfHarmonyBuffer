package com.EyeOfHarmonyBuffer.client.orbitalrailgun.post;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.client.orbitalrailgun.RailgunClientState;
import cpw.mods.fml.common.Loader;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

/**
 * 轨道炮后处理门控：
 * - 仅 Angelica 已加载时启用（Loader.isModLoaded 不触发 Angelica 类加载，安全）
 * - 光影包（Iris/Oculus）激活时自动跳过，避免与光影 composite 冲突
 * - 出错时永久降级（阶段一几何特效不受影响）
 */
public final class EOHBPostProcessor {

    private static final boolean ANGELICA_AVAILABLE = Loader.isModLoaded("angelica");

    private static EOHBPostChain chain;
    private static boolean failed;

    private EOHBPostProcessor() {}

    public static boolean isAngelicaAvailable() {
        return ANGELICA_AVAILABLE;
    }

    /** 在 RenderWorldLastEvent 中、几何特效渲染之后调用。 */
    public static void renderStrikePost(float partialTicks, RailgunClientState state) {
        if (!ANGELICA_AVAILABLE || failed || !MainConfig.OrbitalRailgunPostProcessEnable) {
            return;
        }
        // 光影包激活时自建后处理会与光影的 composite 冲突，跳过
        try {
            if (IrisApi.getInstance().isShaderPackInUse()) {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        // 色差窗口：打击 37 秒后（与 shader 内部 iTime-37 门控一致）
        if (state.getStrikeSeconds(partialTicks) < 37.0F) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();
        if (main == null) {
            return;
        }
        try {
            if (chain == null) {
                chain = new EOHBPostChain(state);
            }
            chain.render(main, partialTicks);
        } catch (Throwable t) {
            failed = true;
            EyeOfHarmonyBuffer.LOGGER.error("[EOHB] OrbitalRailgun post chain disabled after error", t);
        }
    }
}
