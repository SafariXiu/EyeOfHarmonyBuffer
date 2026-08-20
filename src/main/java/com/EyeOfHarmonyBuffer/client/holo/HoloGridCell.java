package com.EyeOfHarmonyBuffer.client.holo;

/**
 * 通用网格单元格组件：由 HoloGridView 按布局自动生成。
 * 外观数据全部来自宿主网格（cellValue/cellColor/fixColor/drawCellExtra），
 * 自身只负责：填充色、固定蓝框、悬停白框、额外绘制。业务无需再写格子控件。
 */
public class HoloGridCell extends HoloWidget {

    public final int row, col;
    protected final HoloGridView owner;

    public HoloGridCell(HoloGridView owner, int row, int col, int x, int y, int size) {
        super(5, x, y, size, size);
        this.owner = owner;
        this.row = row;
        this.col = col;
    }

    @Override
    public void draw(HoloCanvas c) {
        int v = owner.cellValue(row, col);
        if (v != 0) {
            c.rect(x, y, w, h, owner.cellColor(row, col));
        }
        boolean fixed = owner.isFixed(row, col);
        if (fixed) {
            c.border(x, y, w, h, owner.fixColor(), Math.max(1, w / 6));
        }
        if (hovered) {
            c.border(x, y, w, h, 0xFFFFFFFF, Math.max(1, w / 6));
        }
        owner.drawCellExtra(c, row, col, x, y, w, fixed, hovered);
    }

    @Override
    public void onClick(int u, int v) {
        // 固定选中由 HoloGridView.onLeftClick 统一处理（本组件仅负责绘制 + hover 高亮）
    }
}
