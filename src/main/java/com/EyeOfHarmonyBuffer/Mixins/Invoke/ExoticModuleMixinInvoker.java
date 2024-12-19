package com.EyeOfHarmonyBuffer.Mixins.Invoke;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

@Mixin(value = MTEExoticModule.class,remap = false)
public interface ExoticModuleMixinInvoker {

    @Invoker("convertFluidToPlasma")
    FluidStack[] invokeConvertFluidToPlasma(FluidStack[] fluids, long multiplier);

    @Invoker("convertItemToPlasma")
    FluidStack[] invokeConvertItemToPlasma(ItemStack[] items, long multiplier);
}
