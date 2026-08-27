package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.client.transition.TransitionClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 换维加载界面（"Downloading terrain..."）落地揭幕适配（方案 A）：
 * 维度转场白幕激活时（coverWhite &gt; 0，即传送前 2 秒至落地淡出结束），
 * 用全屏白替代加载界面的深色背景与文字，让"全屏白 → 淡出"无缝衔接。
 * 区块就绪后该界面由原版流程自动关闭，白幕转交 post chain（uCoverWhite）继续。
 * <p>
 * 注意：不用 @Shadow 访问 GuiScreen 的 width/height（Mixin 0.8.5 不自动向上查找继承字段，
 * 会导致 "field was not located" 应用失败崩溃），改用 ScaledResolution 取全屏尺寸。
 */
@Mixin(value = GuiDownloadTerrain.class, remap = false)
public class MixinGuiDownloadTerrain {

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void eohb$coverLoadingScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        float cover = TransitionClientState.coverWhite();
        if (cover <= 0.001F) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        // GUI 2D 投影环境：Gui.drawRect 全屏白（alpha=cover），替代加载界面内容
        int alpha = (int) (Math.min(1.0F, cover) * 255.0F);
        Gui.drawRect(0, 0, res.getScaledWidth(), res.getScaledHeight(), (alpha << 24) | 0xFFFFFF);
        ci.cancel();
        com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.LOGGER.info(
            "[EOHB] GuiDownloadTerrain covered (cover={})", cover);
    }
}
