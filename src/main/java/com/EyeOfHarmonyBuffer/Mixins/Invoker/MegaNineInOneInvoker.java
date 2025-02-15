package com.EyeOfHarmonyBuffer.Mixins.Invoker;

import com.newmaa.othtech.machine.OTEMegaNineInOne;
import gregtech.api.recipe.RecipeMap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = OTEMegaNineInOne.class,remap = false)
public interface MegaNineInOneInvoker {

    @Invoker("getCircuit")
    ItemStack invokeGetCircuit(ItemStack[] inputItems);

    @Invoker("getCircuitID")
    int invokeGetCircuitID(net.minecraft.item.ItemStack circuit);

    @Invoker("getRecipeMap")
    static RecipeMap<?> invokeGetRecipeMap(int circuitID) {
        throw new AssertionError();
    }
}
