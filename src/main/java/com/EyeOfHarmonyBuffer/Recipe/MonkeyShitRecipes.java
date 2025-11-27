package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gtPlusPlus.core.fluids.GTPPFluids;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.MonkeyShit;
import static gregtech.api.enums.Mods.Forestry;
import static gregtech.api.enums.Mods.IndustrialCraft2;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class MonkeyShitRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(Blocks.stone,1)
            )
            .fluidInputs(

            )
            .itemOutputs(
                getModItem(Forestry.ID, "fertilizerCompound",1024),
                getModItem(Forestry.ID, "soil",1024,0),
                new ItemStack(Blocks.dirt,1024),
                getModItem(IndustrialCraft2.ID, "itemFertilizer",1024)
            )
            .fluidOutputs(
                new FluidStack(GTPPFluids.PoopJuice, 1000000),
                new FluidStack(GTPPFluids.FertileManureSlurry, 1000000)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(1)
            .addTo(MonkeyShit);
    }
}
