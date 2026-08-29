package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.client.holo.HoloState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 全息屏交互吞键：准星悬停在全息屏（HoloEntity）上时，左/右键只作用于面板，
 * 不再触发世界的方块交互（打方块/攻击/激活方块/用物品/拾取）。
 *
 * <p>原理：Minecraft 每 tick 根据键位在多个入口执行世界动作，这里全部在 HEAD 短路：
 * <ul>
 *   <li>func_147116_af = clickMouse：左键按下（打方块/攻击实体）</li>
 *   <li>func_147115_a：左键按住时的持续挖掘（onPlayerDamageBlock，创造模式瞬爆也走它）</li>
 *   <li>func_147121_ag = rightClickMouse：右键按下/按住使用（激活方块/用物品）</li>
 *   <li>func_147112_ai = middleClickMouse：中键拾取方块</li>
 * </ul>
 * 面板自身的点击仍由 HoloInteraction 的 MouseInputEvent 处理，不受影响。
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftHoloInteraction {

    /** 准星悬停在面板上：吞掉左键按下（打方块/攻击实体）。func_147116_af = clickMouse */
    @Inject(method = "func_147116_af", at = @At("HEAD"), cancellable = true)
    private void eohb$suppressWorldClickOnPanel(CallbackInfo ci) {
        if (HoloState.hovering) {
            ci.cancel();
        }
    }

    /** 准星悬停在面板上：吞掉左键按住时的持续挖掘（onPlayerDamageBlock）。func_147115_a */
    @Inject(method = "func_147115_a", at = @At("HEAD"), cancellable = true)
    private void eohb$suppressHeldMiningOnPanel(boolean leftClick, CallbackInfo ci) {
        if (HoloState.hovering) {
            ci.cancel();
        }
    }

    /** 准星悬停在面板上：吞掉右键（激活方块/使用物品）。func_147121_ag = rightClickMouse */
    @Inject(method = "func_147121_ag", at = @At("HEAD"), cancellable = true)
    private void eohb$suppressWorldRightClickOnPanel(CallbackInfo ci) {
        if (HoloState.hovering) {
            ci.cancel();
        }
    }

    /** 准星悬停在面板上：吞掉中键拾取方块（pick block）。func_147112_ai = middleClickMouse */
    @Inject(method = "func_147112_ai", at = @At("HEAD"), cancellable = true)
    private void eohb$suppressPickBlockOnPanel(CallbackInfo ci) {
        if (HoloState.hovering) {
            ci.cancel();
        }
    }
}
