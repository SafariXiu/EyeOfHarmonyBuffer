package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockCleanGlass;
import net.minecraft.block.BlockLiquid;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 干净玻璃：让相邻水体不在玻璃这一格画侧面水墙。
 * 水的侧面渲染判定在 BlockLiquid.shouldSideBeRendered，
 * 这里在头部直接返回 false，玻璃恢复普通材质后放置行为不受影响。
 */
@Mixin(BlockLiquid.class)
public abstract class WaterGlassMixin {

    @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true)
    private void talos$skipWaterFaceAgainstCleanGlass(
            IBlockAccess world, int x, int y, int z, int side,
            CallbackInfoReturnable<Boolean> cir) {
        if (world.getBlock(x, y, z) instanceof BlockCleanGlass) {
            cir.setReturnValue(false);
        }
    }
}
