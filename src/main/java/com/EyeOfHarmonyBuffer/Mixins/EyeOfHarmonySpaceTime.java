package com.EyeOfHarmonyBuffer.Mixins;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;

import gregtech.api.enums.MaterialsUEVplus;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonySpaceTime {

    @Shadow
    private long successfulParallelAmount;

    @Shadow
    private void outputFluidToAENetwork(FluidStack fluid, long amount) {}

    @Inject(method = "outputFailedChance", at = @At("HEAD"))
    private void injectCustomLogic(CallbackInfo ci) {

        long FluidAmount;
        FluidAmount = 2000000000;

        if (successfulParallelAmount > 0) {
            outputFluidToAENetwork(MaterialsUEVplus.SpaceTime.getMolten(1), FluidAmount);
            outputFluidToAENetwork(MaterialsUEVplus.BlackDwarfMatter.getMolten(1), FluidAmount);
            outputFluidToAENetwork(MaterialsUEVplus.WhiteDwarfMatter.getMolten(1), FluidAmount);

            FluidStack FertileManureSlurry = new FluidStack(FluidRegistry.getFluid("fluid.fertile.manure.slurry"), 1);

            outputFluidToAENetwork(FertileManureSlurry, FluidAmount);

            FluidStack Infinity = new FluidStack(FluidRegistry.getFluidID("molten.infinity"), 1);

            outputFluidToAENetwork(Infinity, FluidAmount);

            FluidStack exciteddtsc = new FluidStack(FluidRegistry.getFluidID("exciteddtsc"), 1);

            outputFluidToAENetwork(exciteddtsc, FluidAmount);

            FluidStack exciteddtec = new FluidStack(FluidRegistry.getFluidID("exciteddtec"), 1);

            outputFluidToAENetwork(exciteddtec, FluidAmount);

            FluidStack exciteddtrc = new FluidStack(FluidRegistry.getFluidID("exciteddtrc"), 1);

            outputFluidToAENetwork(exciteddtrc, FluidAmount);

            FluidStack exciteddtpc = new FluidStack(FluidRegistry.getFluidID("exciteddtpc"), 1);

            outputFluidToAENetwork(exciteddtpc, FluidAmount);

            FluidStack exciteddtcc = new FluidStack(FluidRegistry.getFluidID("exciteddtcc"), 1);

            outputFluidToAENetwork(exciteddtcc, FluidAmount);

            FluidStack universium = new FluidStack(FluidRegistry.getFluidID("molten.universium"), 1);

            outputFluidToAENetwork(universium, FluidAmount);

            FluidStack rawstarmatter = new FluidStack(FluidRegistry.getFluidID("rawstarmatter"), 1);

            outputFluidToAENetwork(rawstarmatter, FluidAmount);

        }
    }
}
