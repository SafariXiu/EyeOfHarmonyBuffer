package com.EyeOfHarmonyBuffer.client.rbmk;

import com.EyeOfHarmonyBuffer.client.holo.HoloCanvas;
import com.EyeOfHarmonyBuffer.client.holo.HoloGridLayout;
import com.EyeOfHarmonyBuffer.client.holo.HoloScreen;
import com.EyeOfHarmonyBuffer.client.holo.HoloWidget;

/**
 * 堆芯俯瞰大屏（平级根屏之一）。每个通道格（含石墨）都是 ChannelCell 组件：
 * 基本交互模型沿用 HoloScreen 基类（未激活时左键进入；激活后左键操作、右键退出）：
 * - 未激活：只提示"左键进入"，不响应组件
 * - 悬停：白框高亮 + 右上信息卡实时显示指针所指通道数据（类型/坐标/对称位/温度/棒位）
 * - 左键：固定选中（蓝框 + 信息卡保持显示选中组件）
 * - 右键：先取消固定（回到指针悬停模式），再右键关屏
 * 每屏持有自己的悬停状态（不读全局 HoloState，避免与其他屏串数据）。
 * 数据经 RbmkCoreData.channelAt() 获取（默认模拟，机器可注入 RbmkChannelProvider）。
 */
public class CoreViewScreen extends HoloScreen {

    public static final int CORE_W = 820;
    public static final int CORE_H = 830;

    private HoloGridLayout layout;
    /** 本屏自己的悬停通道（每 tick 由 onHover 更新；-1=未悬停）。 */
    private int hoverRow = -1, hoverCol = -1;
    /** 左键固定选中的通道（-1=未固定）。 */
    private int fixedRow = -1, fixedCol = -1;

    public CoreViewScreen() {
        super(CORE_W, CORE_H);
    }

    @Override
    protected void buildWidgets() {
        RbmkCoreData.ensureLoaded();
        int grid = RbmkCoreData.GRID;
        this.layout = new HoloGridLayout(CORE_W, CORE_H, grid, 2, 40, CORE_H - 26, 30);
        int cell = layout.getCell();
        for (int yy = 0; yy < grid; yy++) {
            for (int xx = 0; xx < grid; xx++) {
                widgets.add(new ChannelCell(yy, xx, layout.px(xx), layout.py(yy), cell));
            }
        }
    }

    @Override
    protected void drawBackground(HoloCanvas c) {
        c.rect(0, 0, w, h, 0xFF05070A);
        c.rect(0, 0, w, 3, 0xFF2A6B8F);
        c.text(20, 12, "堆芯俯瞰 · 通道组件化", 0xFFFFFFFF);
        // 图例 + 计数
        c.text(20, h - 26 + 4,
            "石墨=深灰 燃料=灰F 控制=绿R 缩短=黄S 自动=红A LAR=蓝L"
            + "    [燃料 " + RbmkCoreData.getFuel() + " / 控制棒 " + RbmkCoreData.getRods()
            + " (R" + RbmkCoreData.getControlRods() + " S" + RbmkCoreData.getShortRods()
            + " A" + RbmkCoreData.getAutoRods() + " L" + RbmkCoreData.getLarRods()
            + ") / 石墨 " + RbmkCoreData.getGraphite() + "]", 0xFFAAAAAA);
    }

    @Override
    public void onHover(int px, int py, boolean hovering) {
        super.onHover(px, py, hovering);
        int[] rc = hovering ? layout.cellAt(px, py) : null;
        if (rc != null) {
            hoverRow = rc[0];
            hoverCol = rc[1];
        } else {
            hoverRow = hoverCol = -1;
        }
    }

    // ---- 基本交互模型（左键进入/左键操作/右键退出）由 HoloScreen 基类提供 ----

    /** 左键进入：复位旧选中，从干净的"指针悬停模式"开始。 */
    @Override
    protected void activate() {
        super.activate();
        fixedRow = -1;
        fixedCol = -1;
    }

    /** 激活后的左键：固定命中的通道（未命中任何通道则取消固定）。 */
    @Override
    protected void onLeftClick(int u, int v) {
        int[] rc = layout.cellAt(u, v);
        if (rc != null) {
            fixedRow = rc[0];
            fixedCol = rc[1];
        } else {
            fixedRow = -1;
            fixedCol = -1;
        }
    }

    /** 激活后的右键：先取消固定（回到"指针悬停"看参数），再关屏。 */
    @Override
    protected void onRightClick() {
        if (fixedRow >= 0) {
            fixedRow = -1;
            fixedCol = -1;
            return;
        }
        super.onRightClick();
    }

    @Override
    protected void drawOverlay(HoloCanvas c) {
        // 基本交互模型：未激活时不显示信息卡，只提示"左键进入"
        if (!activated) {
            c.text(w / 2 - 70, 30, "未激活 - 左键点击进入", 0xFFFFCC00);
            return;
        }
        // 信息卡：固定选中优先，其次指针悬停
        int row = -1, col = -1;
        boolean fixed = false;
        if (fixedRow >= 0) {
            row = fixedRow;
            col = fixedCol;
            fixed = true;
        } else if (hoverRow >= 0) {
            row = hoverRow;
            col = hoverCol;
        }
        if (row < 0) {
            return;
        }
        drawInfoCard(c, row, col, fixed);
    }

    /** 信息卡：类型色块/名称/坐标/180°对称位/温度/棒位。 */
    private void drawInfoCard(HoloCanvas c, int row, int col, boolean fixed) {
        RbmkChannel ch = RbmkCoreData.channelAt(row, col);
        int cx = 620, cy = 40, cw = 185, chh = 110;
        c.rect(cx, cy, cw, chh, 0xD0000000);
        c.border(cx, cy, cw, chh, fixed ? 0xFF4488FF : 0xFF555555, 1);
        // 类型色块 + 名称
        int color = HoloGridLayout.colorFor(RbmkCoreData.at(row, col));
        c.rect(cx + 6, cy + 8, 10, 10, color);
        c.text(cx + 22, cy + 7, RbmkCoreData.typeName(ch.type), 0xFFFFFFFF);
        // 坐标 + 对称位
        c.text(cx + 6, cy + 24, "通道 (" + row + ", " + col + ")", 0xFFAAAAAA);
        c.text(cx + 6, cy + 38, "对称位 (" + (47 - row) + ", " + (47 - col) + ")", 0xFF777777);
        // 温度
        c.text(cx + 6, cy + 54, "温度: " + String.format("%.0f", ch.getTemperature()) + " °C", 0xFFFFE08A);
        // 棒位
        if (ch.hasRod()) {
            c.text(cx + 6, cy + 70, "棒位: " + String.format("%.1f", ch.getRodDepth()) + " %", 0xFF88FF88);
        } else {
            c.text(cx + 6, cy + 70, "棒位: —", 0xFF888888);
        }
        c.text(cx + 6, cy + 96, fixed ? "右键取消固定" : "左键固定 · 右键关屏", 0xFF999999);
    }

    /**
     * 通道格组件：一个 250mm 栅格位的可视化（含石墨，石墨不填充但可悬停/固定）。
     * 悬停白框、固定蓝框由框架 updateHover 与本屏状态驱动。
     */
    private class ChannelCell extends HoloWidget {
        final int row, col;

        ChannelCell(int row, int col, int x, int y, int size) {
            super(5, x, y, size, size);
            this.row = row;
            this.col = col;
        }

        @Override
        public void draw(HoloCanvas c) {
            int t = RbmkCoreData.at(row, col);
            if (t != 0) {
                c.rect(x, y, w, h, HoloGridLayout.colorFor(t));
            }
            if (fixedRow == row && fixedCol == col) {
                c.border(x, y, w, h, 0xFF4488FF, Math.max(1, w / 6));
            }
            if (hovered) {
                c.border(x, y, w, h, 0xFFFFFFFF, Math.max(1, w / 6));
            }
        }

        @Override
        public void onClick() {
            // 固定选中由 onMouse 统一处理（此组件仅负责绘制 + hover 高亮）
        }
    }
}
