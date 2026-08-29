package com.EyeOfHarmonyBuffer.Mixins.GT;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.space.talos.WorldProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;
import galacticgreg.api.Enums.DimensionType;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;

/**
 * 让 GT5U 的矿脉生成管线把 Talos2 识别为一个可生成矿石的星球维度。
 * DimensionDef 的静态表是写死的，原生 API 没有运行时注册入口，
 * 这里在 getDefForWorld 查询时直接返回 Talos2 的维度定义。
 */
@Mixin(value = DimensionDef.class, remap = false)
public class DimensionDefTalos2Mixin {

    @Unique
    private static final String DIM_TALOS2 = "talos2";

    @Unique
    private static final ModDimensionDef EOHB$TALOS2_DEF = new ModDimensionDef(
        DIM_TALOS2,
        ChunkProviderTalos2.class.getName(),
        DimensionType.Planet);

    @Inject(method = "getDefForWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private static void eohb$talos2(World world, CallbackInfoReturnable<ModDimensionDef> cir) {
        if (world.provider instanceof WorldProviderTalos2) {
            cir.setReturnValue(EOHB$TALOS2_DEF);
        }
    }

    @Inject(method = "getDefByName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void eohb$talos2ByName(String worldName, CallbackInfoReturnable<ModDimensionDef> cir) {
        if (DIM_TALOS2.equalsIgnoreCase(worldName)) {
            cir.setReturnValue(EOHB$TALOS2_DEF);
        }
    }
}
