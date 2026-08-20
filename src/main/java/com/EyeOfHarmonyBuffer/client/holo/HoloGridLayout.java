package com.EyeOfHarmonyBuffer.client.holo;

/**
 * 通用栅格布局工具：纯布局数学 —— 计算 cell/原点、格子坐标换算、局部坐标 → 行列。
 * 不含任何业务配色/绘制（那是各业务侧的事）：机器要画自己的矩阵时复用（构造 + px/py + cellAt）。
 */
public class HoloGridLayout {

    private final int grid, gap, cell, originX, originY;

    /**
     * @param w      屏宽
     * @param h      屏高
     * @param grid   行列数（方形网格）
     * @param gap    格子间距（像素）
     * @param top    栅格区上边界
     * @param bottom 栅格区下边界
     * @param margin 水平边距（两侧各留白，用于 cell 计算的横向余量）
     */
    public HoloGridLayout(int w, int h, int grid, int gap, int top, int bottom, int margin) {
        this.grid = grid;
        this.gap = gap;
        int cell = Math.min((w - margin - gap * (grid - 1)) / grid,
            (bottom - top - gap * (grid - 1)) / grid);
        cell = Math.max(cell, 1);
        this.cell = cell;
        this.originX = (w - (cell * grid + gap * (grid - 1))) / 2;
        this.originY = top + ((bottom - top) - (cell * grid + gap * (grid - 1))) / 2;
    }

    public int getCell() {
        return cell;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    /** 列 → 格子左上角 x（col 从 0 开始）。 */
    public int px(int col) {
        return originX + col * (cell + gap);
    }

    /** 行 → 格子左上角 y（row 从 0 开始）。 */
    public int py(int row) {
        return originY + row * (cell + gap);
    }

    /** 局部坐标 → 行列。落在栅格内返回 {row, col}，否则 null。 */
    public int[] cellAt(int x, int y) {
        int col = (x - originX) / (cell + gap);
        int row = (y - originY) / (cell + gap);
        if (row < 0 || row >= grid || col < 0 || col >= grid) {
            return null;
        }
        return new int[] { row, col };
    }

}
