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
        System.out.println("injectFluidOutput 方法被调用。");

        try {
            if (!MainConfig.enableFluidOutPut) {
                System.out.println("流体注入功能已禁用，跳过流体注入逻辑。");
                return;
            }

            if (FluidConfig.outputFluids == null || FluidConfig.outputFluids.isEmpty()) {
                System.err.println("错误：Config.outputFluids 为空。");
                return;
            }

            System.out.println("要输出的流体数量：" + FluidConfig.outputFluids.size());

            for (FluidInfo fluidInfo : FluidConfig.outputFluids) {
                try {
                    String fluidName = fluidInfo.fluidName;
                    long amount = fluidInfo.amount; // 保持 long 类型

                    Fluid fluid = FluidRegistry.getFluid(fluidName);
                    if (fluid == null) {
                        System.err.println("未找到流体：" + fluidName);
                        continue;
                    }

                    // 创建初始的 FluidStack（注意：只在这里生成 FluidStack，不处理数量）
                    FluidStack fluidStack = new FluidStack(fluid, 1); // 数量暂时设置为 1，稍后在 outputFluidToAENetwork 中处理

                    // 使用反射调用私有方法 outputFluidToAENetwork，传递 long 类型的数量
                    Class<?> clazz = MTEEyeOfHarmony.class;
                    Method method = clazz.getDeclaredMethod("outputFluidToAENetwork", FluidStack.class, long.class);
                    method.setAccessible(true);

                    // 传递 fluidStack 和 long 类型的 amount，交由 outputFluidToAENetwork 处理
                    method.invoke(this, fluidStack, amount);
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
