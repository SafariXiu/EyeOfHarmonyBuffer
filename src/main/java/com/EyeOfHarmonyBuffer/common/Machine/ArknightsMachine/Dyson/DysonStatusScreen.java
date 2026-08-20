package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import com.EyeOfHarmonyBuffer.client.holo.HoloCanvas;
import com.EyeOfHarmonyBuffer.client.holo.HoloScreen;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;

/**
 * 戴森核心机载状态屏（机器一体、固定朝向、纯展示，无交互控件）。
 *
 * 数据来源：DysonCore 客户端实例解码的渲染数据（IMTERenderer.encodeRenderData →
 * sendRenderDataToClient → decodeRenderData），与主方块 GUI 显示同一套队伍/个人数值。
 * 开机即画、关机即消失：由 renderTESR 在 holoMachineOn=false 时直接跳过绘制。
 */
public class DysonStatusScreen extends HoloScreen {

    public static final int W = 576;
    public static final int H = 384;

    /** 屏数据源：同包的核心实例（读取其客户端解码字段）。 */
    private final DysonCore core;

    // ---- 配色 ----
    private static final int C_BG = 0xF0101820;
    private static final int C_ACCENT = 0xFF2A6B8F;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GRAY = 0xFFAAAAAA;
    private static final int C_DIM = 0xFF666666;
    private static final int C_GOLD = 0xFFFFAA00;
    private static final int C_GREEN = 0xFF88FF88;
    private static final int C_RED = 0xFFFF5555;
    private static final int C_TRACK = 0xFF0A0A0A;
    private static final int C_TRACK_BORDER = 0xFF444444;
    private static final int C_CLOUD = 0xFF55AAFF;
    private static final int C_FRAME = 0xFF55FFAA;
    private static final int C_PASTE = 0xFFFFAA55;

    /** 画布尺寸由本类自管（W/H）；机器创建屏时不传尺寸。 */
    public DysonStatusScreen(DysonCore core) {
        super(W, H);
        this.core = core;
    }

    @Override
    protected void buildWidgets() {
        // 纯展示屏：无控件
    }

    /** 整屏底色：由框架在真实深度绘制（唯一参照层），内容全在偏移区段，不会与它互剔。 */
    @Override
    protected int baseColor() {
        return C_BG;
    }

    @Override
    protected void drawBackground(HoloCanvas c) {
        // 本屏静态内容（装饰条/标题/进度条）在 drawOverlay 绘制；此处无需背景内容
    }

    @Override
    protected void drawOverlay(HoloCanvas c) {
        // 顶部蓝色装饰条
        c.rect(0, 0, w, 3, C_ACCENT);

        // 标题
        c.textCentered(w / 2, 14, "戴森核心 · 机载状态屏", C_WHITE);
        c.textCentered(w / 2, 32, "队伍: " + teamName(), C_GRAY);

        // 左列：轨道进度（队伍）
        c.text(40, 58, "—— 轨道进度（队伍）——", C_GOLD);
        int barX = 40, barW = 330, barY0 = 0;
        drawStatBar(c, barX, 78, barW, "云", core.holoCloud, DysonSphereState.CLOUD_CAP, C_CLOUD);
        drawStatBar(c, barX, 122, barW, "框架", core.holoFrame, DysonSphereState.FRAME_COMPLETE, C_FRAME);
        drawStatBar(c, barX, 166, barW, "贴片", core.holoPaste, DysonSphereState.PASTE_COMPLETE, C_PASTE);
        c.text(barX, 196, "进度: " + percent(core.holoCloud, DysonSphereState.CLOUD_CAP) + " / "
            + percent(core.holoFrame, DysonSphereState.FRAME_COMPLETE) + " / "
            + percent(core.holoPaste, DysonSphereState.PASTE_COMPLETE), C_DIM);

        // 右列：个人库存
        c.text(400, 58, "—— 个人库存 ——", C_GOLD);
        drawStatValue(c, 400, 78, "云组件", core.holoCloudComp);
        drawStatValue(c, 400, 122, "框架组件", core.holoFrameComp);
        drawStatValue(c, 400, 166, "奇异物质", core.holoStrange);

        // 底部状态条
        int sx = 20, sy = 300, sw = w - 40, sh = h - 300 - 16;
        c.rect(sx, sy, sw, sh, 0xFF0A0A0A);
        c.border(sx, sy, sw, sh, 0xFF333333, 1);
        drawStatusChip(c, sx + 12, sy + 8, "链接模块", core.holoLinked + " / " + core.holoSlots,
            core.holoLinked > 0 ? C_GREEN : C_GRAY);
        drawStatusChip(c, sx + 220, sy + 8, "算力", core.holoComputeOk ? "OK" : "不足",
            core.holoComputeOk ? C_GREEN : C_RED);
        drawStatusChip(c, sx + 380, sy + 8, "戴森球", core.holoComplete ? "已完工" : "建设中",
            core.holoComplete ? C_GOLD : C_GRAY);
        if (core.holoDuplicate) {
            c.text(sx + 12, sy + 28, "⚠ 检测到重复核心：本机已停机，请拆除多余核心", C_RED);
        } else if (core.holoComplete) {
            c.text(sx + 12, sy + 28, core.holoWinning ? "本队已完工 · 戴森球建成" : "戴森球已被其他队伍完工", C_GOLD);
        } else {
            c.text(sx + 12, sy + 28, "核心在线 · 全息屏同步中", C_DIM);
        }
    }

    // ---- 布局原语 ----

    /** 左侧标签 + 右侧数值 + 进度条。 */
    private void drawStatBar(HoloCanvas c, int x, int labelY, int barW, String label,
                             int value, int cap, int fillColor) {
        int valueX = x + barW;
        c.text(x, labelY, label + ":", C_GRAY);
        c.textRight(valueX, labelY, fmt(value) + " / " + fmt(cap), C_WHITE);
        int barY = labelY + 18, barH = 8;
        c.rect(x, barY, barW, barH, C_TRACK);
        c.border(x, barY, barW, barH, C_TRACK_BORDER, 1);
        float ratio = cap <= 0 ? 0f : (float) value / cap;
        int fw = (int) Math.round(barW * Math.max(0f, Math.min(1f, ratio)));
        if (fw > 0) {
            c.rect(x, barY, fw, barH, fillColor);
        }
    }

    /** 左侧标签 + 右侧数值。 */
    private void drawStatValue(HoloCanvas c, int x, int y, String label, long value) {
        c.text(x, y, label + ":", C_GRAY);
        c.textRight(x + 100, y, fmt(value), C_WHITE);
    }

    /** 状态条内的小标签块：标签 + 值。 */
    private void drawStatusChip(HoloCanvas c, int x, int y, String label, String value, int color) {
        c.text(x, y, label + ":", C_DIM);
        c.textRight(x + 150, y, value, color);
    }

    // ---- 工具 ----

    private String teamName() {
        String n = core.holoTeamName;
        return n == null || n.isEmpty() ? "—" : n;
    }

    private static String percent(int value, int cap) {
        return cap <= 0 ? "0%" : Math.round(100f * value / cap) + "%";
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }
}
