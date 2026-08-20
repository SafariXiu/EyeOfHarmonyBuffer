package com.EyeOfHarmonyBuffer.client.rbmk;

import com.EyeOfHarmonyBuffer.client.holo.*;

/**
 * 堆芯俯瞰大屏（平级根屏之一）。纯展示；栅格绘制复用 HoloGridLayout。
 * 后续大屏自己的操作逻辑（点格查看/缩放/切层等）在 onMouse 里扩展。
 */
public class CoreViewScreen extends HoloScreen {

    public static final int CORE_W = 820;
    public static final int CORE_H = 830;

    public CoreViewScreen() {
        super(CORE_W, CORE_H);
    }

    @Override
    protected void buildWidgets() {
        // 暂无控件；后续大屏操作接入点
    }

    @Override
    protected void drawBackground(HoloCanvas c) {
        RbmkCoreData.ensureLoaded();
        int grid = RbmkCoreData.GRID;
        c.rect(0, 0, w, h, 0xFF05070A);
        c.rect(0, 0, w, 3, 0xFF2A6B8F);
        c.text(20, 12, "堆芯俯瞰 · 完整复刻示意", 0xFFFFFFFF);

        int top = 40;
        int bottom = h - 26;
        int gap = 2;
        int margin = 30;
        HoloGridLayout layout = new HoloGridLayout(w, h, grid, gap, top, bottom, margin);
        layout.draw(c, (y, x) -> RbmkCoreData.at(y, x));

        // 图例 + 计数
        c.text(20, bottom + 4,
            "石墨=深灰 燃料=灰F 控制=绿R 缩短=黄S 自动=红A LAR=蓝L"
            + "    [燃料 " + RbmkCoreData.getFuel() + " / 控制棒 " + RbmkCoreData.getRods()
            + " (R" + RbmkCoreData.getControlRods() + " S" + RbmkCoreData.getShortRods()
            + " A" + RbmkCoreData.getAutoRods() + " L" + RbmkCoreData.getLarRods()
            + ") / 石墨 " + RbmkCoreData.getGraphite() + "]", 0xFFAAAAAA);
    }

    @Override
    public void onMouse(int button, int u, int v) {
        if (button == 1) {
            requestClose(); // 右键关闭大屏
        }
        // 左键暂不响应，留给后续大屏自己的操作逻辑
    }
}
