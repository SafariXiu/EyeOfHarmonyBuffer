package com.EyeOfHarmonyBuffer.client.transition;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * 维度转场客户端事件：
 * - ClientTickEvent：驱动 {@link TransitionClientState} 状态机（换维检测/相位/淡入收尾）；
 * - RenderWorldLastEvent：转场进行时渲染后处理链（白化球，盖世界画面）；
 * - RenderGameOverlayEvent.Pre：白幕激活期间隐藏 HUD（白幕本体由 post chain 的 uCoverWhite 渲染）。
 * 与天空盒零耦合：矩阵在渲染时当场捕获，深度从主 FBO blit，不读取任何天空盒状态。
 */
public class TransitionRenderEvents {

    /** 诊断日志节流。 */
    private static long lastCoverLog = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        TransitionClientState.tick(mc);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!TransitionClientState.isTransitioning()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getFramebuffer() == null) {
            return;
        }
        TransitionPostChain.getInstance().render(mc.getFramebuffer(), event.partialTicks);
    }

    /**
     * HUD 隐藏：白幕由 post chain（uCoverWhite）在世界层渲染（GLProgram 路径，已验证可用）。
     * 白幕激活期间隐藏原版 HUD（Pre(ALL) setCanceled 已验证有效），避免 HUD 画在白幕之上。
     * 不用 Post 事件/立即模式绘制：Angelica GLSM + lwjgl3ify 环境下主 FBO 的立即模式 quad 不生效
     * （glReadPixels 实测确认画不上）。
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        float cover = TransitionClientState.coverWhite();
        // 诊断：白幕激活期间每 500ms 打印一次（便于定位渐入/淡出问题）
        long now = System.currentTimeMillis();
        if (now - lastCoverLog >= 500) {
            lastCoverLog = now;
            com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.LOGGER.info(
                "[EOHB] cover={} phase={} inNew={} waiting={} t={}ms",
                cover, TransitionClientState.getPhase(),
                TransitionClientState.isInNewDimension(),
                TransitionClientState.isWaitingForChunks(),
                TransitionClientState.debugElapsedMs());
        }
        if (cover <= 0.001F) {
            return;
        }
        // 白幕存在期间隐藏 HUD（白幕在世界层，HUD 不渲染 = 全屏白）
        event.setCanceled(true);
    }
}
