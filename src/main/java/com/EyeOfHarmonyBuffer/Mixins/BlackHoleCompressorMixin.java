package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.CustomProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.compressor.MTEBlackHoleCompressor;
import gregtech.common.tileentities.render.TileEntityBlackhole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import static com.EyeOfHarmonyBuffer.Config.MainConfig.BlackHoleCompressorPowerConsumptionModification;
import static com.EyeOfHarmonyBuffer.Config.MainConfig.BlackHoleCompressorTimeConsumptionModification;

@Mixin(value = MTEBlackHoleCompressor.class,remap = false)
public abstract class BlackHoleCompressorMixin extends MTEExtendedPowerMultiBlockBase<MTEBlackHoleCompressor>
    implements ISurvivalConstructable {

    protected BlackHoleCompressorMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int collapseTimer = -1;

    @Shadow
    protected abstract void destroyRenderBlock();

    @Shadow
    private byte blackHoleStatus = 1;

    @Shadow
    private float blackHoleStability = 100;

    @Shadow
    private int catalyzingCostModifier = 1;

    @Shadow
    private boolean shouldRender = true;

    @Shadow
    private TileEntityBlackhole rendererTileEntity = null;

    @Shadow
    protected abstract boolean createRenderBlock();

    @Inject(method = "onPostTick", at = @At("HEAD"), cancellable = true)
    private void beforeOnPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if (MainConfig.BlackHoleCompressorStabilityLock) {
            super.onPostTick(aBaseMetaTileEntity, aTick);

            if (collapseTimer != -1) {
                if (collapseTimer == 0) {
                    destroyRenderBlock();
                }
                collapseTimer = 1;
            }

            if (!aBaseMetaTileEntity.isServerSide()) {
                if (FMLLaunchHandler.side().isClient()) {
                    try {
                        Method playSoundMethod = this.getClass().getMethod("playBlackHoleSounds");
                        playSoundMethod.setAccessible(true);
                        playSoundMethod.invoke(this);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                    }
                }
                return;
            }

            if (blackHoleStatus == 1 || aTick % 20 != 0) return;

            float stabilityDecrease = 0F;
            boolean didDrain = false;

            if (blackHoleStability >= 0) {
                didDrain = false;
            } else blackHoleStatus = 3;

            if (shouldRender) {
                if (rendererTileEntity != null || createRenderBlock()) {
                    rendererTileEntity.toggleLaser(didDrain);
                    rendererTileEntity.setStability(Math.max(0, blackHoleStability / 100F));
                }
            }

            blackHoleStability -= stabilityDecrease;

            if (blackHoleStability <= -900) {
                blackHoleStatus = 1;
                blackHoleStability = 100;
                catalyzingCostModifier = 1;
                rendererTileEntity = null;
                destroyRenderBlock();
            }

            ci.cancel();
        }
    }

    @Inject(method = "getMaxParallelRecipes", at = @At("RETURN"), cancellable = true)
    private void overrideMaxParallelRecipes(CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.BlackHoleCompressorParallelModificationEnabled){
            cir.setReturnValue(MainConfig.BlackHoleCompressorParallelCountModification);
        }
    }

    @Inject(method = "createProcessingLogic", at = @At("RETURN"))
    private void adjustEnergyAndSpeed(CallbackInfoReturnable<ProcessingLogic> cir) {
        String powerModifierStr = BlackHoleCompressorPowerConsumptionModification;
        String timeModifierStr = BlackHoleCompressorTimeConsumptionModification;

        float powerModifier = parseFloatConfig(powerModifierStr, 0.7F);
        float timeModifier = parseFloatConfig(timeModifierStr, 0.2F);

        ProcessingLogic logic = cir.getReturnValue();
        if(MainConfig.BlackHoleCompressorPowerConsumptionModificationEnabled){
            logic.setEuModifier(powerModifier);
        }
        if(MainConfig.BlackHoleCompressorTimeConsumptionModificationEnabled){
            logic.setSpeedBonus(timeModifier);
        }
    }

    private float parseFloatConfig(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            System.err.println("配置文件中数据异常：" + value + "，将使用默认值：" + defaultValue);
            return defaultValue;
        }
    }
}
