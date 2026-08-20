package com.EyeOfHarmonyBuffer.client.holo;

import java.util.function.IntBinaryOperator;

/**
 * 通用栅格布局工具：计算 cell/原点，绘制矩阵，支持"局部坐标 → 行列"换算。
 * 机器要画自己的矩阵/粒子布局时直接复用（构造 + draw + cellAt）。
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

    /** 通道类型 → 颜色（大屏配色）。0=石墨砌体（不画，返回 0 由调用方跳过）。 */
    public static int colorFor(int t) {
        switch (t) {
            case 1:  return 0xFF9AA4AE; // 燃料压力管（灰）
            case 2:  return 0xFF44C955; // 普通控制棒（绿）
            case 3:  return 0xFFE6CC3A; // 缩短吸收棒 UA（黄）
            case 4:  return 0xFFE04848; // 自动控制棒（红）
            case 5:  return 0xFF3D6BE0; // LAR 棒（蓝）
            default: return 0xFF343C46;
        }
    }

    /** 绘制整个栅格。typeAt(row, col) 返回通道类型；返回 0 的格子（石墨）不画。 */
    public void draw(HoloCanvas c, IntBinaryOperator typeAt) {
        for (int yy = 0; yy < grid; yy++) {
            for (int xx = 0; xx < grid; xx++) {
                int t = typeAt.applyAsInt(yy, xx);
                if (t == 0) {
                    continue;
                }
                c.rect(px(xx), py(yy), cell, cell, colorFor(t));
            }
        }
    }
}
