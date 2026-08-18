package com.EyeOfHarmonyBuffer.client.orbitalrailgun;

import com.EyeOfHarmonyBuffer.client.orbitalrailgun.post.EOHBPostProcessor;
import com.EyeOfHarmonyBuffer.common.orbitalrailgun.OrbitalRailgunNetwork;
import com.EyeOfHarmonyBuffer.common.orbitalrailgun.PacketOrbitalFireRequest;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * 轨道炮客户端事件（阶段1）：
 * - ClientTickEvent  驱动状态机
 * - MouseEvent       蓄力时拦截左键（防止破坏方块）并触发开火
 * - RenderWorldLastEvent  世界空间瞄准标记 + 打击特效
 * - RenderGameOverlayEvent.Pre  充能时隐藏原版 HUD 并绘制自定义 HUD
 * - FOVUpdateEvent  充能时轻微缩放视野
 */
public class RailgunClientEvents {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        RailgunClientState.getInstance().tick(mc);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        RailgunClientState state = RailgunClientState.getInstance();
        if (event.buttonstate) {
            if (state.isCharging()) {
                // 蓄力期间左键不再执行原版攻击/挖掘
                event.setCanceled(true);
                if (state.isReady() && state.hasTarget() && !state.isFired() && !state.isStrikeActive()) {
                    state.markFired();
                    OrbitalRailgunNetwork.INSTANCE.sendToServer(
                        new PacketOrbitalFireRequest(state.getHitX(), state.getHitY(), state.getHitZ()));
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        RailgunClientState state = RailgunClientState.getInstance();
        if (state.isCharging() && state.hasTarget()) {
            // 后处理 GUI 激活时，瞄准选框/圆环由 gui.fsh 接管（深度感知、可被地形遮挡），
            // 世界空间几何标记作为无 Angelica/光影激活时的降级路径
            if (!EOHBPostProcessor.isPostGuiActive(state)) {
                RailgunWorldRenderer.renderAimMarker(event.partialTicks,
                    state.getHitX(), state.getHitY(), state.getHitZ());
            }
        }
        if (state.isStrikeActive()) {
            // 阶段一：世界空间几何特效（任何环境都可用，也是阶段二后处理的降级路径）
            RailgunWorldRenderer.renderStrike(event.partialTicks, state);
        }
        if (state.isCharging() || state.isStrikeActive()) {
            // 阶段二：Angelica 全屏后处理（色差 + GUI 瞄准覆盖），仅 Angelica 且无光影时生效
            EOHBPostProcessor.renderPost(event.partialTicks, state);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        RailgunClientState state = RailgunClientState.getInstance();
        if (state.isCharging()) {
            // 隐藏原版 HUD（含准星），改由自定义 HUD 接管
            event.setCanceled(true);
            Minecraft mc = Minecraft.getMinecraft();
            RailgunHudRenderer.render(mc, state, event.resolution,
                EOHBPostProcessor.isPostGuiActive(state));
        }
    }

    @SubscribeEvent
    public void onFov(FOVUpdateEvent event) {
        RailgunClientState state = RailgunClientState.getInstance();
        if (state.isCharging()) {
            // 充能时轻微缩放视野（0% -> 15%），平滑过渡
            float progress = Math.min(1.0F, state.getChargeTicks() / 40.0F);
            event.newfov = event.fov * (1.0F - 0.15F * progress);
        }
    }
}
