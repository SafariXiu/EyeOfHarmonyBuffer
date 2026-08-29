package com.EyeOfHarmonyBuffer.client.transition;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * 维度转场音效（移植自源库 RitualVisualManager.tick 的相位音效时机，音效映射到 1.7.10 可用音效）。
 * <p>
 * 相位音效（对应源库 soundPhase1/2/3：
 * - 源库 portal 0~0.1s：BASALT_DELTAS_MOOD + WARDEN_HEARTBEAT + END_PORTAL_FRAME_FILL → portal.trigger 低沉 + 低频鼓
 * - 源库 0.6s：WARDEN_SONIC_BOOM + CONDUIT_ACTIVATE + TRIDENT_THUNDER → 末地龙吼 + beacon 激活 + 雷
 * - 源库 1.5s：END_PORTAL_SPAWN + GLASS_BREAK + DRAGON_FIREBALL_EXPLODE → 末影人传送 + 玻璃碎 + 爆炸
 * - 白化壳扩张：CHORUS_FLOWER_GROW 随机 → 末影人传送低音量
 */
public class TransitionSoundManager {

    private static boolean phase1Played = false;
    private static boolean phase2Played = false;
    private static boolean phase3Played = false;
    private static boolean shakePlayed = false;
    private static long lastAmbientTick = 0;

    private TransitionSoundManager() {}

    /** 每次转场开始前调用。 */
    public static void reset() {
        phase1Played = false;
        phase2Played = false;
        phase3Played = false;
        shakePlayed = false;
        lastAmbientTick = 0;
    }

    /** 由 ClientTickEvent 驱动。 */
    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.theWorld == null) {
            return;
        }
        World world = mc.theWorld;
        int phase = TransitionClientState.getPhase();
        float tSec = TransitionClientState.transitionTimeSeconds();

        // phase 1：转场开始（低沉启动）
        if (!phase1Played && TransitionClientState.isTransitioning() && phase >= 1) {
            phase1Played = true;
            player.playSound("portal.trigger", 1.0F, 0.7F);
            player.playSound("mob.wither.idle", 0.4F, 0.6F);
        }

        // phase 2 撕裂：玻璃碎 + 爆裂（skyrip 活动）
        if (!phase2Played && phase >= 2 && TransitionClientState.isSkyRipActive()
            && tSec >= 2.3F && tSec < 3.5F) {
            phase2Played = true;
            player.playSound("dig.glass", 2.0F, 0.9F);
            player.playSound("random.explode", 1.2F, 1.4F);
            player.playSound("mob.wither.spawn", 1.0F, 0.8F);
        }

        // phase 2 中段：撕裂持续声
        if (phase >= 2 && TransitionClientState.isSkyRipActive()
            && tSec >= 3.5F && tSec < 9.0F && System.currentTimeMillis() - lastAmbientTick > 1200) {
            lastAmbientTick = System.currentTimeMillis();
            if (player.getRNG().nextInt(3) == 0) {
                player.playSound("mob.endermen.portal", 0.8F, 1.3F);
            }
        }

        // phase 3（白幕揭开 + 传送门洞）：末影传送门激活
        if (!phase3Played && phase >= 3 && tSec >= 6.5F && tSec < 9.0F) {
            phase3Played = true;
            player.playSound("mob.endermen.portal", 2.0F, 0.6F);
            player.playSound("portal.trigger", 1.5F, 1.1F);
        }

        // 传送前震声
        if (!shakePlayed && phase >= 3 && tSec >= 9.0F && tSec < 10.5F) {
            shakePlayed = true;
            player.playSound("random.explode", 2.0F, 0.5F);
            player.playSound("mob.enderdragon.growl", 1.0F, 0.7F);
        }
    }

    /** 触发重置（转场开始时由 TransitionClientState 调用）。 */
    static {
    }
}
