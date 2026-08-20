package com.EyeOfHarmonyBuffer.client.rbmk;

import com.EyeOfHarmonyBuffer.client.holo.*;

/**
 * 控制面板屏（平级根屏之一）。控件全部由框架组件（HoloButton/HoloSlider/HoloTextField）
 * 组装，本类只保留面板专属的布局、数据与绘制，不再手写任何控件内部类。
 * 状态（棒位/AZ-5/激活态）为每屏独立实例，不再全局共享 —— 多台机器各管各的。
 */
public class PanelScreen extends HoloScreen {

    public static final int W = 480;
    public static final int H = 270;

    // ---- 每屏独立状态（数据源）----
    private HoloSlider rodSlider;
    private HoloToggleButton az5;
    private HoloTextField rodInput;

    public PanelScreen() {
        super(W, H);
    }

    @Override
    protected void buildWidgets() {
        rodSlider = new HoloSlider(10, 150, 70, 220, 18, 0.62, null);
        az5 = new HoloToggleButton(10, 150, 110, 220, 52, "AZ-5 紧急停堆",
            0xFFAA0000, 0xFFFF4444, 0xFFFF6600, 0xFFFFFFFF, 0xFFCC2222, null);
        rodInput = new HoloTextField(10, 378, 66, 84, 24, "输入控制棒深度 (0-100):", 6,
            () -> String.valueOf(rodPercent()),
            s -> {
                try {
                    double v = Double.parseDouble(s.trim());
                    if (v >= 0 && v <= 100) {
                        rodSlider.setValue(v / 100.0);
                    }
                } catch (NumberFormatException ignored) {
                    // 非法输入：保持旧值
                }
            });
        widgets.add(rodSlider);
        widgets.add(az5);
        widgets.add(rodInput);
    }

    @Override
    protected int baseColor() {
        // 全不透明底色：面板是一块实体屏，后面的世界/另一块屏不再透出（否则移动视角会出现透过伪影）
        return 0xFF101820;
    }

    @Override
    protected void drawBackground(HoloCanvas c) {
        // 底色由 baseColor() 提供；以下内容在偏移区段内，与底色不互剔
        c.rect(0, 0, w, 3, 0xFF2A6B8F);
        c.text(20, 14, "RBMK-1000 · 四号机组 · 自写面板", 0xFFFFFFFF);
        c.text(20, 72, "控制棒: " + rodPercent() + "%", 0xFFFFFFFF);
        c.text(150, 92, "0%", 0xFF888888);
        // 右对齐到滑块轨道右缘（150+220=370），与左侧"0%"左对齐轨道起点对称
        c.textRight(370, 92, "100%", 0xFF888888);
        c.text(348, 72, "手动:", 0xFF888888);
    }

    @Override
    protected void drawOverlay(HoloCanvas c) {
        if (!activated) {
            c.border(0, 0, w, h, 0xFFCCAA00, 2);
        }
        c.rect(20, 190, 440, 38, 0xFF0A0A0A);
        boolean pressed = az5.isPressed();
        String state = pressed ? "✓ AZ-5 已按下 (停堆状态)" : "运行中";
        c.text(24, 196, state, pressed ? 0xFFFF8800 : 0xFF88FF88);
        String ops = activated ? "已激活 - 右键退出面板" : "未激活 - 左键点击面板以激活";
        c.text(24, 208, ops, activated ? 0xFFAAAAAA : 0xFFFFCC00);
    }

    /** 当前棒位百分比（0-100，整数）。 */
    private int rodPercent() {
        return (int) Math.round(rodSlider.getValue() * 100);
    }
}
