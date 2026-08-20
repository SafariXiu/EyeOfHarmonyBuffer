package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;

/**
 * 复用 MC 原生 GuiTextField 的输入界面（聊天栏同款输入逻辑）。
 * 回车确认（交给 onConfirm 做业务校验），ESC 取消。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class HoloInputGui extends GuiScreen {

    private GuiTextField field;
    private final String initial;
    private final String title;
    private final int maxLength;
    private final Consumer<String> onConfirm;

    public HoloInputGui(String initial, String title, int maxLength, Consumer<String> onConfirm) {
        this.initial = initial == null ? "" : initial;
        this.title = title == null ? "输入数值:" : title;
        this.maxLength = maxLength;
        this.onConfirm = onConfirm;
    }

    /** 兼容便捷构造：默认标题与长度。 */
    public HoloInputGui(String initial, Consumer<String> onConfirm) {
        this(initial, "输入数值:", 6, onConfirm);
    }

    @Override
    public void initGui() {
        int w = 180;
        int h = 20;
        this.field = new GuiTextField(this.fontRendererObj, this.width / 2 - w / 2, this.height / 2 - h / 2, w, h);
        this.field.setMaxStringLength(this.maxLength);
        this.field.setText(this.initial);
        this.field.setFocused(true);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.onConfirm != null) {
                this.onConfirm.accept(this.field.getText());
            }
            this.mc.displayGuiScreen(null);
        } else {
            this.field.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.fontRendererObj.drawString(this.title, this.width / 2 - 90, this.height / 2 - 32, 0xFFFFFF);
        this.field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
