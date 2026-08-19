package com.EyeOfHarmonyBuffer.client.rbmk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * 世界面板控件容器：
 * - 绘制按 z 升序（上层盖下层），命中按 z 降序（上层优先）
 * - 焦点管理：输入框聚焦后接收键盘
 * - 层栈：进入子层保存当前层，右键弹回；层栈空时由调用方关闭面板
 */
public class RbmkHoloPanel {

    public static final RbmkHoloPanel INSTANCE = new RbmkHoloPanel();

    private final List<RbmkHoloControl> controls = new ArrayList<>();
    private final Deque<List<RbmkHoloControl>> backStack = new ArrayDeque<>();
    private RbmkHoloControl focus;

    private RbmkHoloPanel() {
        controls.add(new RodSlider());
        controls.add(new Az5Button());
        controls.add(new RodInputField());
    }

    /** 命中：按 z 从高到低找最上层命中的控件（重叠时上层优先）。 */
    public RbmkHoloControl hitAt(int px, int py) {
        RbmkHoloControl top = null;
        for (RbmkHoloControl c : controls) {
            if (c.contains(px, py) && (top == null || c.z > top.z)) {
                top = c;
            }
        }
        return top;
    }

    /** 更新各控件 hover 状态（准星在面板上时，命中最上层控件）。 */
    public void updateHover(int px, int py, boolean hovering) {
        RbmkHoloControl hit = hovering ? hitAt(px, py) : null;
        for (RbmkHoloControl c : controls) {
            c.hovered = (c == hit);
        }
    }

    /** 绘制：按 z 升序（底层先画，高层盖上面）。 */
    public void draw(FontRenderer font) {
        List<RbmkHoloControl> sorted = new ArrayList<>(controls);
        sorted.sort(Comparator.comparingInt(c -> c.z));
        for (RbmkHoloControl c : sorted) {
            c.draw(font);
        }
    }

    // ---- 焦点 ----

    public boolean hasFocus() {
        return focus != null;
    }

    /** 请求聚焦；仅可聚焦控件会获得焦点，其他点击清空焦点。 */
    public void requestFocus(RbmkHoloControl c) {
        if (focus != null && focus != c) {
            focus.onFocusLost();
        }
        focus = (c != null && c.isFocusable()) ? c : null;
    }

    public void clearFocus() {
        requestFocus(null);
    }

    /** 键盘路由给聚焦控件。返回 true 表示该键已被消费。 */
    public boolean handleKey(char c, int key) {
        return focus != null && focus.onKey(c, key);
    }

    // ---- 层栈（右键退出） ----

    /** 进入子层：保存当前层，切换为子层控件。 */
    public void pushLayer(List<RbmkHoloControl> layer) {
        backStack.push(new ArrayList<>(controls));
        controls.clear();
        controls.addAll(layer);
        clearFocus();
    }

    /** 弹回上一层。返回 false 表示没有上层（调用方应关闭面板）。 */
    public boolean goBack() {
        if (backStack.isEmpty()) {
            return false;
        }
        controls.clear();
        controls.addAll(backStack.pop());
        clearFocus();
        return true;
    }

    // ---- 控件 ----

    /** 棒位滑块：点击轨道设值。 */
    private static class RodSlider extends RbmkHoloControl {
        RodSlider() {
            super(10, 150, 70, 220, 18);
        }

        @Override
        public void draw(FontRenderer font) {
            double rod = RbmkHoloState.rodPos;
            RbmkHoloDraw.drawRect(x, y, x + w, y + h, 0xFF2A2A2A);
            int rodX = x + (int) ((rod / 100.0) * w);
            RbmkHoloDraw.drawRect(rodX - 5, y - 4, rodX + 5, y + h + 4, 0xFF55AAFF);
            if (hovered) {
                RbmkHoloDraw.drawBorder(x, y, w, h, 0xFFFFFFFF, 2);
            }
        }

        @Override
        public void onClick() {
            double ratio = (RbmkHoloState.hoverX - x) / (double) w;
            RbmkHoloState.rodPos = Math.max(0, Math.min(100, ratio * 100));
        }
    }

    /** AZ-5 紧急停堆按钮。 */
    private static class Az5Button extends RbmkHoloControl {
        Az5Button() {
            super(10, 150, 110, 220, 52);
        }

        @Override
        public void draw(FontRenderer font) {
            boolean az5 = RbmkHoloState.az5Pressed;
            int color = az5 ? 0xFFFF6600 : (hovered ? 0xFFFF4444 : 0xFFAA0000);
            RbmkHoloDraw.drawRect(x, y, x + w, y + h, color);
            RbmkHoloDraw.drawRect(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFCC2222);
            if (hovered) {
                RbmkHoloDraw.drawBorder(x, y, w, h, 0xFFFFFFFF, 2);
            }
            font.drawString("AZ-5 紧急停堆", 190, 128, 0xFFFFFFFF);
        }

        @Override
        public void onClick() {
            RbmkHoloState.az5Pressed = !RbmkHoloState.az5Pressed;
        }
    }

    /** 棒位输入框：点击弹出 MC 原生输入界面，回车应用（业务校验 0-100）。 */
    private static class RodInputField extends RbmkHoloControl {

        RodInputField() {
            super(10, 378, 66, 84, 24);
        }

        @Override
        public void draw(FontRenderer font) {
            RbmkHoloDraw.drawRect(x, y, x + w, y + h, 0xFF2A2A2A);
            RbmkHoloDraw.drawBorder(x, y, w, h, hovered ? 0xFFFFFFFF : 0xFF555555, 1);
            font.drawString(String.valueOf((int) RbmkHoloState.rodPos), x + 6, y + 7, 0xFFFFFFFF);
        }

        @Override
        public void onClick() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) {
                return;
            }
            mc.displayGuiScreen(new RbmkInputGui(String.valueOf((int) RbmkHoloState.rodPos), s -> {
                try {
                    double v = Double.parseDouble(s.trim());
                    if (v >= 0 && v <= 100) {
                        RbmkHoloState.rodPos = v;
                    }
                } catch (NumberFormatException ignored) {
                    // 非法输入：保持旧值
                }
            }));
        }
    }

}