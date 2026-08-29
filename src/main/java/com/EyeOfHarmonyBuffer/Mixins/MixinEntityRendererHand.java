package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.client.transition.TransitionClientState;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 落地揭幕：白幕激活期间跳过手臂渲染（第一款式手臂/手持物画在主 FBO、
 * RenderWorldLastEvent 之后，直接拦截渲染成本最小且必然生效 —— 与 HUDCaching 无关）。
 */
@Mixin(value = EntityRenderer.class, remap = false)
public class MixinEntityRendererHand {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void eohb$skipHandDuringCover(float partialTicks, int renderPass, CallbackInfo ci) {
        if (TransitionClientState.coverWhite() > 0.001F) {
            ci.cancel();
        }
    }
}
