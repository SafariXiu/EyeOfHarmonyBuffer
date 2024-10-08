package com.EyeOfHarmonyBuffer.Mixins;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config;

import gregtech.api.enums.MaterialsUEVplus;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonySpaceTime {

    @Shadow
    private long successfulParallelAmount;

    @Shadow
    private void outputFluidToAENetwork(FluidStack fluid, long amount) {}

    @Inject(method = "outputFailedChance", at = @At("HEAD"))
    private void injectCustomLogic(CallbackInfo ci) {

        long FluidAmount;
        FluidAmount = Config.Fluid;

        if (successfulParallelAmount > 0) {
            outputFluidToAENetwork(MaterialsUEVplus.SpaceTime.getMolten(1), FluidAmount);
            if (Config.FluidOutPut) {
                outputFluidToAENetwork(MaterialsUEVplus.BlackDwarfMatter.getMolten(1), FluidAmount);
                outputFluidToAENetwork(MaterialsUEVplus.WhiteDwarfMatter.getMolten(1), FluidAmount);

                FluidStack FertileManureSlurry = new FluidStack(
                    FluidRegistry.getFluid("fluid.fertile.manure.slurry"),
                    1);

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

                FluidStack grade8purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade8purifiedwater"), 1);

                outputFluidToAENetwork(grade8purifiedwater, FluidAmount);

                FluidStack grade7purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade7purifiedwater"), 1);

                outputFluidToAENetwork(grade7purifiedwater, FluidAmount);

                FluidStack grade6purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade6purifiedwater"), 1);

                outputFluidToAENetwork(grade6purifiedwater, FluidAmount);

                FluidStack grade5purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade5purifiedwater"), 1);

                outputFluidToAENetwork(grade5purifiedwater, FluidAmount);

                FluidStack grade4purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade4purifiedwater"), 1);

                outputFluidToAENetwork(grade4purifiedwater, FluidAmount);

                FluidStack grade3purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade3purifiedwater"), 1);

                outputFluidToAENetwork(grade3purifiedwater, FluidAmount);

                FluidStack grade2purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade2purifiedwater"), 1);

                outputFluidToAENetwork(grade2purifiedwater, FluidAmount);

                FluidStack grade1purifiedwater = new FluidStack(FluidRegistry.getFluidID("grade1purifiedwater"), 1);

                outputFluidToAENetwork(grade1purifiedwater, FluidAmount);

                FluidStack flocculationwasteliquid = new FluidStack(
                    FluidRegistry.getFluidID("flocculationwasteliquid"),
                    1);

                outputFluidToAENetwork(flocculationwasteliquid, FluidAmount);

                FluidStack stablebaryonicmatter = new FluidStack(FluidRegistry.getFluidID("stablebaryonicmatter"), 1);

                outputFluidToAENetwork(stablebaryonicmatter, FluidAmount);

                FluidStack sgcrystalslurry = new FluidStack(FluidRegistry.getFluidID("sgcrystalslurry"), 1);

                outputFluidToAENetwork(sgcrystalslurry, FluidAmount);

                FluidStack creonPlasma = new FluidStack(FluidRegistry.getFluidID("plasma.creon"), 1);

                outputFluidToAENetwork(creonPlasma, FluidAmount);

                FluidStack primordialmatter = new FluidStack(FluidRegistry.getFluidID("primordialmatter"), 1);

                outputFluidToAENetwork(primordialmatter, FluidAmount);
            }

        }
    }
}
