package com.EyeOfHarmonyBuffer.client.holo;

/**
 * 通用数据驱动网格屏：把"一块矩阵数据"渲染成可交互的格子大屏。
 * 框架负责：栅格布局、格子生成、悬停高亮、左键固定选中、右键取消固定/关屏、
 * 未激活提示、信息卡路由（固定 > 悬停）。业务只需喂数据和外观：
 *
 *   cellValue(row,col)  格子数据（0=空，不画）
 *   cellColor(row,col)  格子颜色
 *   drawInfoCard(...)   信息卡内容（可选）
 *   drawCellExtra(...)  每格额外绘制（可选，如棒位指示）
 *
 * 任何机器要画自己的矩阵大屏，继承本类覆写这几个钩子即可，交互全部免费。
 */
public abstract class HoloGridView extends HoloScreen {

    protected final HoloGridLayout layout;

    private int hoverRow = -1, hoverCol = -1;
    private int fixedRow = -1, fixedCol = -1;

    public HoloGridView(int w, int h, int grid, int gap, int top, int bottom, int margin) {
        super(w, h);
        this.layout = new HoloGridLayout(w, h, grid, gap, top, bottom, margin);
        int cell = layout.getCell();
        for (int yy = 0; yy < grid; yy++) {
            for (int xx = 0; xx < grid; xx++) {
                widgets.add(new HoloGridCell(this, yy, xx, layout.px(xx), layout.py(yy), cell));
            }
        }
    }

    /** 本类用构造器生成网格格子，buildWidgets 留空（子类可覆写追加额外控件）。 */
    @Override
    protected void buildWidgets() {}

    // ==================== 业务钩子（子类实现） ====================

    /** 格子数据值；0 = 空（不填充）。 */
    protected abstract int cellValue(int row, int col);

    /** 格子填充色（cellValue != 0 时调用）。 */
    protected abstract int cellColor(int row, int col);

    /** 额外绘制（如棒位指示）；固定/悬停框由框架画，无需重复。 */
    protected void drawCellExtra(HoloCanvas c, int row, int col, int x, int y, int size, boolean fixed, boolean hovered) {}

    /** 信息卡内容（固定选中或悬停时调用）。默认不画。 */
    protected void drawInfoCard(HoloCanvas c, int row, int col, boolean fixed) {}

    /** 固定选中框颜色。 */
    protected int fixColor() {
        return 0xFF4488FF;
    }

    /** 未激活提示（子类可覆写文案/位置）。 */
    protected void drawInactiveHint(HoloCanvas c) {
        c.textCentered(w / 2, 30, "未激活 - 左键点击进入", 0xFFFFCC00);
    }

    // ==================== 通用选中/悬停状态 ====================

    public boolean isFixed(int row, int col) {
        return fixedRow == row && fixedCol == col;
    }

    public int fixedRow() {
        return fixedRow;
    }

    public int fixedCol() {
        return fixedCol;
    }

    // ==================== 基本交互模型（沿用 HoloScreen 激活门） ====================

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

    /** 左键进入：复位旧选中，从干净的"指针悬停模式"开始。 */
    @Override
    protected void activate() {
        super.activate();
        fixedRow = -1;
        fixedCol = -1;
    }

    /** 激活后的左键：固定命中的格子（未命中则取消固定）。 */
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

    /** 激活后的右键：先取消固定（回到指针悬停），再关屏。 */
    @Override
    protected void onRightClick() {
        if (fixedRow >= 0) {
            fixedRow = -1;
            fixedCol = -1;
            return;
        }
        super.onRightClick();
    }

    /** 信息卡：未激活 → 提示；激活后固定 > 悬停。 */
    @Override
    protected void drawOverlay(HoloCanvas c) {
        if (!activated) {
            drawInactiveHint(c);
            return;
        }
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
}
