package com.EyeOfHarmonyBuffer.common.dyson;

/**
 * 戴森球全局状态的客户端/服务端共享缓存。
 * <p>
 * 服务端通过 {@link DysonSphereNetwork} 将状态广播给客户端，
 * 客户端天空盒渲染器直接读取本缓存绘制对应阶段。
 * 后续接入实际系统时，发射机/接收机也通过这里读取当前进度。
 */
public final class DysonSphereState {

    /** 戴森云上限。 */
    public static final int CLOUD_CAP = 50_000;
    /** 3 级戴森云所需数量（三环）。 */
    public static final int CLOUD_LEVEL_3 = 30_000;
    /** 框架开始接收云所需数量。 */
    public static final int FRAME_MIN = 50_000;
    /** 框架阶段 1 / 2 / 3 / 建成。 */
    public static final int FRAME_STAGE_1 = 50_000;
    public static final int FRAME_STAGE_2 = 150_000;
    public static final int FRAME_STAGE_3 = 300_000;
    public static final int FRAME_COMPLETE = 500_000;

    /** 未开始。 */
    public static final int STAGE_NONE = 0;
    /** 阶段1：全是戴森云。 */
    public static final int STAGE_CLOUD = 1;
    /** 阶段2：戴森云 + 25% 框架。 */
    public static final int STAGE_FRAME_25 = 2;
    /** 阶段3：50% 戴森云 + 50% 框架。 */
    public static final int STAGE_HALF = 3;
    /** 阶段4：80% 框架 + 少量戴森云。 */
    public static final int STAGE_FRAME_80 = 4;
    /** 阶段5：完工，太阳被完全遮蔽，缝隙漏出幽暗蓝光。 */
    public static final int STAGE_COMPLETE = 5;

    private static int stage = STAGE_NONE;
    private static float progress = 0.0F;
    private static int cloudCount = 0;
    private static int frameCount = 0;
    private static String ownerName = "";

    private DysonSphereState() {}

    /** 应用来自服务端的戴森球状态。 */
    public static void apply(int newStage, float newProgress, int newCloudCount, int newFrameCount,
                             String newOwnerName) {
        stage = Math.max(STAGE_NONE, Math.min(STAGE_COMPLETE, newStage));
        progress = Math.max(0.0F, Math.min(1.0F, newProgress));
        cloudCount = Math.max(0, newCloudCount);
        frameCount = Math.max(0, newFrameCount);
        ownerName = newOwnerName == null ? "" : newOwnerName;
    }

    public static int getStage() {
        return stage;
    }

    public static float getProgress() {
        return progress;
    }

    public static int getCloudCount() {
        return cloudCount;
    }

    public static int getFrameCount() {
        return frameCount;
    }

    public static String getOwnerName() {
        return ownerName;
    }
}
