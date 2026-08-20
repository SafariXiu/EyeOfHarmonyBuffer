package com.EyeOfHarmonyBuffer.client.rbmk;

import com.EyeOfHarmonyBuffer.client.holo.HoloCanvas;
import com.EyeOfHarmonyBuffer.client.holo.HoloGridView;

/**
 * 堆芯俯瞰大屏（平级根屏之一）。继承通用数据驱动网格 HoloGridView：
 * 格子生成/悬停/固定/信息卡路由/激活门全由框架提供，本类只喂 RBMK 数据与外观。
 * 数据经 RbmkCoreData.channelAt() 获取（默认模拟，机器可注入 RbmkChannelProvider）。
 */
public class CoreViewScreen extends HoloGridView {

    public static final int CORE_W = 820;
    public static final int CORE_H = 830;

    public CoreViewScreen() {
        super(CORE_W, CORE_H, RbmkCoreData.GRID, 2, 40, CORE_H - 26, 30);
        RbmkCoreData.ensureLoaded();
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

    // ---- 数据-视图桥：把 RbmkCoreData 的数据喂给通用网格 ----

    @Override
    protected int cellValue(int row, int col) {
        return RbmkCoreData.at(row, col);
    }

    @Override
    protected int cellColor(int row, int col) {
        return RbmkCoreData.colorFor(RbmkCoreData.at(row, col));
    }

    @Override
    protected void drawInfoCard(HoloCanvas c, int row, int col, boolean fixed) {
        RbmkChannel ch = RbmkCoreData.channelAt(row, col);
        int cx = 620, cy = 40, cw = 185, chh = 110;
        c.rect(cx, cy, cw, chh, 0xD0000000);
        c.border(cx, cy, cw, chh, fixed ? 0xFF4488FF : 0xFF555555, 1);
        // 类型色块 + 名称
        int color = RbmkCoreData.colorFor(RbmkCoreData.at(row, col));
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
}
