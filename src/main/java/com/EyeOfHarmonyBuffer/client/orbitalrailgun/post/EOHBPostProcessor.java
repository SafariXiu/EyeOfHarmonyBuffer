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
 *
 * 渲染窗口（对齐 Forge 移植版）：充能（GUI 瞄准覆盖）或打击（色差）期间
 * 整链运行；shader 内部自行按阶段门控（chromatic 需要 iTime>=37 且 StrikeActive）。
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
    public static void renderPost(float partialTicks, RailgunClientState state) {
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
        // 充能（GUI 瞄准覆盖）或存在"自己的"主打击（strike/色差）期间运行。
        // 只渲染自己的打击：别人的打击只走几何特效，避免后处理链抢占
        if (!state.isCharging() && state.getPrimaryStrike() == null) {
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

    /**
     * 本帧是否由后处理链渲染打击特效（存在"自己的"主打击 + 后处理可用 + 无光影）。
     * 供世界空间几何特效做去重：主打击由 strike.fsh 全权呈现（忠实移植版）。
     */
    public static boolean isPostStrikeActive(RailgunClientState state) {
        if (!ANGELICA_AVAILABLE || failed || !MainConfig.OrbitalRailgunPostProcessEnable) {
            return false;
        }
        if (state.getPrimaryStrike() == null) {
            return false;
        }
        try {
            if (IrisApi.getInstance().isShaderPackInUse()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    /**
     * 本帧是否由后处理链渲染 GUI 瞄准覆盖（充能 + 后处理可用 + 无光影）。
     * 供 2D HUD / 世界空间瞄准标记做去重：后处理激活时由 shader 接管准星与选框。
     */
    public static boolean isPostGuiActive(RailgunClientState state) {
        if (!ANGELICA_AVAILABLE || failed || !MainConfig.OrbitalRailgunPostProcessEnable) {
            return false;
        }
        if (!state.isCharging()) {
            return false;
        }
        try {
            if (IrisApi.getInstance().isShaderPackInUse()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        return true;
    }
}
