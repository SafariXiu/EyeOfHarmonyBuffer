package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 文本输入框组件：点击弹出 HoloInputGui（MC 原生输入框），回车把原始字符串交给
 * onCommit 做业务校验。display 提供当前显示文本（实时反映数据源，如滑块值）。
 */
public class HoloTextField extends HoloWidget {

    private final String prompt;
    private final int maxLength;
    private final Supplier<String> display;
    private final Consumer<String> onCommit;

    public HoloTextField(int z, int x, int y, int w, int h, String prompt, int maxLength,
                         Supplier<String> display, Consumer<String> onCommit) {
        super(z, x, y, w, h);
        this.prompt = prompt;
        this.maxLength = maxLength;
        this.display = display;
        this.onCommit = onCommit;
    }

    @Override
    public void draw(HoloCanvas c) {
        c.rect(x, y, w, h, 0xFF2A2A2A);
        c.border(x, y, w, h, hovered ? 0xFFFFFFFF : 0xFF555555, 1);
        String s = display != null ? display.get() : "";
        c.text(x + 6, y + 7, s, 0xFFFFFFFF);
    }

    @Override
    public void onClick(int u, int v) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        String init = display != null ? display.get() : "";
        mc.displayGuiScreen(new HoloInputGui(init, prompt, maxLength, s -> {
            if (onCommit != null) {
                onCommit.accept(s);
            }
        }));
    }
}
