package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.SpaceElevatorAccessor;
import com.gtnewhorizons.gtnhintergalactic.recipe.IG_Recipe;
import com.gtnewhorizons.gtnhintergalactic.spaceprojects.ProjectAsteroidOutpost;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;
import gregtech.api.enums.Materials;
import gregtech.api.recipe.check.CheckRecipeResult;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityModuleMiner.class, remap = false)
public class SpaceElevatorMixin {

    /**
     * 修改 getTierFromPlasma 方法，确保等离子体被识别，但不会消耗。
     * 该方法将根据流体类型返回等离子体等级，而不再检查等离子体数量。
     */
    @Inject(method = "getTierFromPlasma", at = @At("HEAD"), cancellable = true)
    private void modifyGetTierFromPlasma(FluidStack fluidStack, CallbackInfoReturnable<Integer> cir) {

        if(!MainConfig.SpaceElevatorMiningPlasma){
            return;
        }

        if (fluidStack == null) {
            cir.setReturnValue(0);
            return;
        }

        if (fluidStack.isFluidEqual(Materials.Radon.getPlasma(1))) {
            cir.setReturnValue(3);
        }

        else if (fluidStack.isFluidEqual(Materials.Bismuth.getPlasma(1))) {
            cir.setReturnValue(2);
        }

        else if (fluidStack.isFluidEqual(Materials.Helium.getPlasma(1))) {
            cir.setReturnValue(1);
        } else {
            cir.setReturnValue(0);
        }
    }

    /**
     * 修改 getPlasmaUsageFromTier 方法，使其始终返回 0，
     * 意味着不会消耗任何等离子体，无论等离子体等级如何。
     */
    @Inject(method = "getPlasmaUsageFromTier", at = @At("HEAD"), cancellable = true)
    private void modifyGetPlasmaUsageFromTier(int plasmaTier, CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.SpaceElevatorMiningPlasma){
            cir.setReturnValue(0);
        }
    }

}
