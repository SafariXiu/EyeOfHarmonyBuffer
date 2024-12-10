package com.EyeOfHarmonyBuffer.Mixins.EOH;

import java.lang.reflect.Method;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config.FluidConfig;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.FluidInfo;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyFluidMixin {

    @Inject(method = "outputAfterRecipe_EM", at = @At("TAIL"))
    private void injectFluidOutput(CallbackInfo ci) {

        try {
            if (!MainConfig.enableFluidOutPut) {
                return;
            }

            if (FluidConfig.outputFluids == null || FluidConfig.outputFluids.isEmpty()) {
                return;
            }

            for (FluidInfo fluidInfo : FluidConfig.outputFluids) {
                try {
                    String fluidName = fluidInfo.fluidName;
                    long amount = fluidInfo.amount;

                    Fluid fluid = FluidRegistry.getFluid(fluidName);
                    if (fluid == null) {
                        System.err.println("未找到流体：" + fluidName);
                        continue;
                    }
                    FluidStack fluidStack = new FluidStack(fluid, 1);

                    Class<?> clazz = MTEEyeOfHarmony.class;
                    Method method = clazz.getDeclaredMethod("outputFluidToAENetwork", FluidStack.class, long.class);
                    method.setAccessible(true);

                    method.invoke(this, fluidStack, amount);

                } catch (Exception e) {
                    System.err.println("处理流体时发生异常：" + fluidInfo.fluidName);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
