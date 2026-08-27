package com.EyeOfHarmonyBuffer.client.transition;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;

import net.minecraft.client.Minecraft;

/**
 * 维度转场客户端状态机（忠实移植 Nostalgia 的 RitualVisualManager 数值语义，CC0-1.0，见 LICENSE-nostalgia.txt）。
 * <p>
 * 相位与数值公式逐条对齐源库：
 * - phase 1：转场开始（whiteRadius=0，alphaRadius=1.5 → 白化球几乎不可见）
 * - phase 2：白化壳扩张（whiteRadius = 50 格/s 线性增长；环带内上方全白、下方渐晕）
 * - phase 3：白幕揭开（alphaRadius 1.5→300 三秒线性扩张，"透明洞"从中心吞噬白环）
 * - 换维后：shader 全透明（无白幕），fadeProgress 2s 收尾
 * <p>
 * 相位由服务端 PacketTransitionPhase 驱动（对齐源库 S2CRitualPhasePayload）。
 * 渲染层只读本类的 getter；视觉时间为暂停感知毫秒钟（源库 getVisualTime 同款）。
 */
public class TransitionClientState {

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_1 = 1;
    public static final int PHASE_2 = 2;
    public static final int PHASE_3 = 3;

    // ===== 源库常量（RitualVisualManager 逐条对齐）=====
    /** 白化壳扩张速度（格/秒）。 */
    private static final float WHITE_RADIUS_SPEED = 50.0F;
    /** alphaRadius 下限（phase<3 时）。 */
    private static final float ALPHA_RADIUS_MIN = 1.5F;
    /** alphaRadius 上限（换维后）。 */
    private static final float ALPHA_RADIUS_MAX = 300.0F;
    /** 换维后白幕淡出时长（毫秒，源库 2000）。 */
    private static final long FADE_MS = 2000;
    /** 换维后等待区块时长（毫秒，源库 2000）。 */
    private static final long WAIT_CHUNKS_MS = 2000;
    /** 防卡死：整个转场超过 30s 无进展强制收尾。 */
    private static final long TIMEOUT_MS = 30000;

    private static boolean transitioning = false;
    private static int phase = PHASE_IDLE;
    private static long transitionStartTime = 0;
    private static long phase2StartTime = 0;
    private static long phase3StartTime = 0;
    private static int centerX = 0;
    private static int centerY = 0;
    private static int centerZ = 0;
    private static int targetDimension = 0;
    private static boolean inNewDimension = false;
    private static boolean waitingForChunks = false;
    private static long dimensionChangeTime = 0;
    private static long arrivalTime = 0;
    private static int lastDimension = Integer.MIN_VALUE;

    // 暂停感知毫秒钟（源库 getVisualTime 同款）
    private static long lastRealTime = 0;
    private static boolean wasPaused = false;
    private static long pauseOffset = 0;

    private TransitionClientState() {}

    // ================= 驱动 =================

    /** 收到 PacketTransitionStart：进入 phase 1。 */
    public static void startTransition(int cx, int cy, int cz, int targetDim) {
        Minecraft mc = Minecraft.getMinecraft();
        transitioning = true;
        phase = PHASE_1;
        inNewDimension = false;
        waitingForChunks = false;
        centerX = cx;
        centerY = cy;
        centerZ = cz;
        targetDimension = targetDim;
        lastDimension = mc.thePlayer != null ? mc.thePlayer.dimension : Integer.MIN_VALUE;
        long now = visualTime();
        transitionStartTime = now;
        phase2StartTime = now;
        phase3StartTime = now;
        lastRealTime = System.currentTimeMillis();
        wasPaused = false;
        pauseOffset = 0;
        EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Client transition STARTED phase=1 center=({},{},{}) target={}",
            cx, cy, cz, targetDim);
    }

    /** 服务端相位同步（PacketTransitionPhase，对齐源库 setPhase）。 */
    public static void setPhase(int newPhase) {
        if (!transitioning || phase == newPhase) {
            return;
        }
        phase = newPhase;
        if (phase == PHASE_2) {
            phase2StartTime = visualTime();
        } else if (phase == PHASE_3) {
            phase3StartTime = visualTime();
        }
        EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Client transition phase={} (t={}ms)", phase, visualTime() - transitionStartTime);
    }

    /** 由 ClientTickEvent 驱动。 */
    public static void tick(Minecraft mc) {
        if (!transitioning) {
            return;
        }
        // 维度变化检测（换维瞬间 theWorld 短暂为 null，玩家实体跨维保留）
        if (mc.thePlayer != null && mc.thePlayer.dimension != lastDimension) {
            if (lastDimension != Integer.MIN_VALUE) {
                onDimensionChanged();
            }
            lastDimension = mc.thePlayer.dimension;
        }
        long now = visualTime();
        if (inNewDimension) {
            if (waitingForChunks) {
                if (now - dimensionChangeTime >= WAIT_CHUNKS_MS) {
                    waitingForChunks = false;
                    arrivalTime = now;
                }
            } else if (now - arrivalTime >= FADE_MS) {
                endTransition();
            }
        } else if (now - transitionStartTime > TIMEOUT_MS) {
            // 防卡死：相位长期无进展（如传送失败）强制收尾
            EyeOfHarmonyBuffer.LOGGER.warn("[EOHB] Transition timed out, forced end");
            endTransition();
        }
    }

    public static void endTransition() {
        transitioning = false;
        phase = PHASE_IDLE;
        inNewDimension = false;
        waitingForChunks = false;
        lastDimension = Integer.MIN_VALUE;
        EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Client transition ENDED");
    }

    private static void onDimensionChanged() {
        inNewDimension = true;
        waitingForChunks = true;
        dimensionChangeTime = visualTime();
        EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Client dimension changed -> inNewDimension");
    }

    // ================= 源库数值公式（RitualVisualManager 逐条对齐） =================

    /** 转场总秒数（源库 transitionTimeSeconds）。 */
    public static float transitionTimeSeconds() {
        if (!transitioning) {
            return 0.0F;
        }
        return (visualTime() - transitionStartTime) / 1000.0F;
    }

    /** 白幕强度（源库 getWhiteoutAlpha）：换维前恒 1；换维后 1-fadeProgress。 */
    public static float whiteoutAlpha() {
        if (!transitioning) {
            return 0.0F;
        }
        if (inNewDimension && !waitingForChunks) {
            return 1.0F - fadeProgress();
        }
        return 1.0F;
    }

    /** 淡入进度（源库 getFadeProgress）：换维且区块就绪后 2s 内 0→1。 */
    public static float fadeProgress() {
        if (transitioning && inNewDimension && !waitingForChunks) {
            long elapsed = Math.max(0, visualTime() - arrivalTime);
            return Math.min(1.0F, elapsed / (float) FADE_MS);
        }
        return 0.0F;
    }

    /** 白化壳半径（源库 getWhiteRadius）：phase>=2 且未换维时 50 格/s 线性增长。 */
    public static float whiteRadius() {
        if (!transitioning || inNewDimension || phase < PHASE_2) {
            return 0.0F;
        }
        long elapsed = Math.max(0, visualTime() - phase2StartTime);
        return elapsed / 1000.0F * WHITE_RADIUS_SPEED;
    }

    /** 内圈半径（源库 getTransitionAlphaRadius）：phase<3→1.5；换维后→300；phase3 三秒内 1.5→300。 */
    public static float alphaRadius() {
        if (!transitioning) {
            return 0.0F;
        }
        if (phase < PHASE_3) {
            return ALPHA_RADIUS_MIN;
        }
        if (inNewDimension) {
            return ALPHA_RADIUS_MAX;
        }
        long elapsed = Math.max(0, visualTime() - phase3StartTime);
        float progress = Math.min(elapsed / 1000.0F / 3.0F, 1.0F);
        return ALPHA_RADIUS_MIN + progress * (ALPHA_RADIUS_MAX - ALPHA_RADIUS_MIN);
    }

    /**
     * 全屏白幕强度（1.7.10 落地揭幕适配，非源库内容）：
     * - 传送前最后 {@code CoverMs} 毫秒：0→1 渐入（把世界+HUD+UI 全部糊住）；
     *   用总时间轴（transitionStartTime 起 = Phase1+Phase2+Phase3 全程）计算，
     *   与服务端传送时机天然对齐，不依赖相位包到达时刻；
     * - 换维后等待区块期间：保持 1（遮盖"下载地形"画面）；
     * - 区块就绪后：1-fadeProgress 淡出（衔接源库 fade 语义）。
     */
    public static float coverWhite() {
        if (!transitioning) {
            return 0.0F;
        }
        if (inNewDimension) {
            return waitingForChunks ? 1.0F : 1.0F - fadeProgress();
        }
        long elapsed = Math.max(0, visualTime() - transitionStartTime);
        long total = phase1Ms() + phase2Ms() + phase3Ms();
        long coverStart = total - coverMs();
        long t = elapsed - coverStart;
        if (t >= 0) {
            return Math.min(1.0F, t / (float) coverFadeInMs());
        }
        return 0.0F;
    }

    /** 服务端相位时长（毫秒，与 DimensionTransitionManager 的 tick 计算一致）。 */
    private static long phase1Ms() {
        return Math.max(1, Math.round(MainConfig.DimensionTransitionPhase1Ms));
    }

    private static long phase2Ms() {
        return Math.max(1, Math.round(MainConfig.DimensionTransitionPhase2Ms));
    }

    private static long phase3Ms() {
        return Math.max(1, Math.round(MainConfig.DimensionTransitionPhase3Ms));
    }

    private static long coverMs() {
        return Math.max(0, Math.round(MainConfig.DimensionTransitionCoverMs));
    }

    private static long coverFadeInMs() {
        long fade = Math.max(1, Math.round(MainConfig.DimensionTransitionCoverFadeInMs));
        return Math.min(fade, Math.max(1, coverMs()));
    }

    // ================= 只读 getter =================

    public static boolean isTransitioning() {
        return transitioning;
    }

    public static int getPhase() {
        return phase;
    }

    public static int getCenterX() {
        return centerX;
    }

    public static int getCenterY() {
        return centerY;
    }

    public static int getCenterZ() {
        return centerZ;
    }

    public static int getTargetDimension() {
        return targetDimension;
    }

    public static boolean isInNewDimension() {
        return inNewDimension;
    }

    public static boolean isWaitingForChunks() {
        return waitingForChunks;
    }

    /** 诊断：自转场开始经过的毫秒。 */
    public static long debugElapsedMs() {
        return transitioning ? Math.max(0, visualTime() - transitionStartTime) : -1;
    }

    // ================= 暂停感知毫秒钟（源库 getVisualTime 同款） =================

    private static long visualTime() {
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getMinecraft();
        boolean paused = mc.isSingleplayer() && mc.isGamePaused();
        if (paused) {
            if (!wasPaused) {
                wasPaused = true;
                lastRealTime = now;
            }
            return lastRealTime - pauseOffset;
        } else {
            if (wasPaused) {
                wasPaused = false;
                pauseOffset += now - lastRealTime;
            }
            return now - pauseOffset;
        }
    }
}
