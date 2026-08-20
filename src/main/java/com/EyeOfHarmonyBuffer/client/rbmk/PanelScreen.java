package com.EyeOfHarmonyBuffer.client.rbmk;

import com.EyeOfHarmonyBuffer.client.holo.*;
import net.minecraft.client.Minecraft;

/**
 * 控制面板屏（平级根屏之一）。原 RbmkHoloPanel 的控件容器逻辑已上移到 HoloScreen，
 * 本类保留：面板专属布局、绘制与交互。
 * 状态（棒位/AZ-5/激活态）为每屏独立实例，不再全局共享 —— 多台机器各管各的。
 */
public class PanelScreen extends HoloScreen {

    public static final int W = 480;
    public static final int H = 270;

    // ---- 每屏独立状态 ----
    private double rodPos = 62;
    private boolean az5Pressed = false;

    public PanelScreen() {
        super(W, H);
    }

    @Override
    protected void buildWidgets() {
        widgets.add(new RodSlider());
        widgets.add(new Az5Button());
        widgets.add(new RodInputField());
    }

    @Override
    protected void drawBackground(HoloCanvas c) {
        // 全不透明背景：面板是一块实体屏，后面的世界/另一块屏不再透出（否则移动视角会出现透过伪影）
        c.rect(0, 0, w, h, 0xFF101820);
        c.rect(0, 0, w, 3, 0xFF2A6B8F);
        c.text(20, 14, "RBMK-1000 · 四号机组 · 自写面板", 0xFFFFFFFF);
        c.text(20, 72, "控制棒: " + (int) rodPos + "%", 0xFFFFFFFF);
        c.text(150, 92, "0%", 0xFF888888);
        c.text(355, 92, "100%", 0xFF888888);
        c.text(348, 72, "手动:", 0xFF888888);
    }

    @Override
    protected void drawOverlay(HoloCanvas c) {
        if (!activated) {
            c.border(0, 0, w, h, 0xFFCCAA00, 2);
        }
        c.rect(20, 190, 440, 38, 0xFF0A0A0A);
        String state = az5Pressed ? "✓ AZ-5 已按下 (停堆状态)" : "运行中";
        c.text(24, 196, state, az5Pressed ? 0xFFFF8800 : 0xFF88FF88);
        String ops = activated ? "已激活 - 右键退出面板" : "未激活 - 左键点击面板以激活";
        c.text(24, 208, ops, activated ? 0xFFAAAAAA : 0xFFFFCC00);
    }

    // ---- 交互 ----
    // 基本交互模型（左键进入 / 左键操作 / 右键退出）已由 HoloScreen 基类提供，无需重复实现。

    // ==================== 控件 ====================

    /** 棒位滑块：点击轨道设值。 */
    private class RodSlider extends HoloWidget {
        RodSlider() {
            super(10, 150, 70, 220, 18);
        }

        @Override
        public void draw(HoloCanvas c) {
            c.rect(x, y, w, h, 0xFF2A2A2A);
            int rodX = x + (int) ((rodPos / 100.0) * w);
            c.rect(rodX - 5, y - 4, 10, h + 8, 0xFF55AAFF);
            if (hovered) {
                c.border(x, y, w, h, 0xFFFFFFFF, 2);
            }
        }

        @Override
        public void onClick() {
            double ratio = (HoloState.hoverX - x) / (double) w;
            rodPos = Math.max(0, Math.min(100, ratio * 100));
        }
    }

    /** AZ-5 紧急停堆按钮。 */
    private class Az5Button extends HoloWidget {
        Az5Button() {
            super(10, 150, 110, 220, 52);
        }

        @Override
        public void draw(HoloCanvas c) {
            int color = az5Pressed ? 0xFFFF6600 : (hovered ? 0xFFFF4444 : 0xFFAA0000);
            c.rect(x, y, w, h, color);
            c.rect(x + 2, y + 2, w - 4, h - 4, 0xFFCC2222);
            if (hovered) {
                c.border(x, y, w, h, 0xFFFFFFFF, 2);
            }
            c.text(190, 128, "AZ-5 紧急停堆", 0xFFFFFFFF);
        }

        @Override
        public void onClick() {
            az5Pressed = !az5Pressed;
        }
    }

    /** 棒位输入框：点击弹出 MC 原生输入界面，回车应用（业务校验 0-100）。 */
    private class RodInputField extends HoloWidget {

        RodInputField() {
            super(10, 378, 66, 84, 24);
        }

        @Override
        public void draw(HoloCanvas c) {
            c.rect(x, y, w, h, 0xFF2A2A2A);
            c.border(x, y, w, h, hovered ? 0xFFFFFFFF : 0xFF555555, 1);
            c.text(x + 6, y + 7, String.valueOf((int) rodPos), 0xFFFFFFFF);
        }

        @Override
        public void onClick() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) {
                return;
            }
            mc.displayGuiScreen(new HoloInputGui(String.valueOf((int) rodPos), s -> {
                try {
                    double v = Double.parseDouble(s.trim());
                    if (v >= 0 && v <= 100) {
                        rodPos = v;
                    }
                } catch (NumberFormatException ignored) {
                    // 非法输入：保持旧值
                }
            }));
        }
    }
}
