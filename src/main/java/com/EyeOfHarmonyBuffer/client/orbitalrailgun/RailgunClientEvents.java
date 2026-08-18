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

/** 轨道炮客户端事件（部分移植自 orbital-railgun 的 ClientEvents，MIT License，见 LICENSE-orbital-railgun.txt）：状态驱动、左键开火、几何/后处理渲染分流、HUD、FOV。 */
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
                event.setCanceled(true); // 蓄力时左键不执行原版攻击/挖掘
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
            // 多人并发分流：自己的主打击由后处理 strike.fsh 全权呈现；
            // 其他打击（别人的/纯坐标的）逐个渲染阶段一几何特效（位置正确、轻量）。
            // 无 Angelica/光影时全部退回几何特效。
            RailgunClientState.ClientStrike primary = EOHBPostProcessor.isPostStrikeActive(state)
                ? state.getPrimaryStrike() : null;
            for (RailgunClientState.ClientStrike s : state.getStrikes()) {
                if (s != primary) {
                    RailgunWorldRenderer.renderStrike(event.partialTicks, state, s);
                }
            }
        }
        if (state.isCharging() || state.getPrimaryStrike() != null) {
            // 阶段二：Angelica 全屏后处理（strike + 色差 + GUI 瞄准覆盖），仅 Angelica 且无光影时生效
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
