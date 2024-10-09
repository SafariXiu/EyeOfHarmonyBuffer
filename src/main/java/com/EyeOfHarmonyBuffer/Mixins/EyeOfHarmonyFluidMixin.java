package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Method;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config;
import com.EyeOfHarmonyBuffer.FluidInfo;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyFluidMixin {

    @Inject(method = "outputAfterRecipe_EM", at = @At("TAIL"))
    private void injectFluidOutput(CallbackInfo ci) {
        System.out.println("injectFluidOutput 方法被调用。");

        try {
            if (!Config.enableFluidOutPut) {
                System.out.println("流体注入功能已禁用，跳过流体注入逻辑。");
                return;
            }

            if (Config.outputFluids == null || Config.outputFluids.isEmpty()) {
                System.err.println("错误：Config.outputFluids 为空。");
                return;
            }

            System.out.println("要输出的流体数量：" + Config.outputFluids.size());

            for (FluidInfo fluidInfo : Config.outputFluids) {
                try {
                    String fluidName = fluidInfo.fluidName;
                    int amount = fluidInfo.amount;

                    Fluid fluid = FluidRegistry.getFluid(fluidName);
                    if (fluid == null) {
                        System.err.println("未找到流体：" + fluidName);
                        continue;
                    }

                    FluidStack fluidStack = new FluidStack(fluid, amount);

                    // 使用反射调用私有方法 outputFluidToAENetwork
                    Class<?> clazz = MTEEyeOfHarmony.class;
                    Method method = clazz.getDeclaredMethod("outputFluidToAENetwork", FluidStack.class, long.class);
                    method.setAccessible(true);

                    method.invoke(this, fluidStack, (long) amount);
                    System.out.println("成功注入 " + amount + " mB 的 " + fluidName + " 到 AE 网络中。");

                } catch (Exception e) {
                    System.err.println("处理流体时发生异常：" + fluidInfo.fluidName);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("injectFluidOutput 方法中发生异常：");
            e.printStackTrace();
        }

        System.out.println("injectFluidOutput 方法结束。");
    }
}
