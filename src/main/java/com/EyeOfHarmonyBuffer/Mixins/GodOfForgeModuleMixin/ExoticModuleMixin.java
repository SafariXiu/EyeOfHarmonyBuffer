package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import gregtech.api.enums.MaterialsUEVplus;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

@Mixin(value = MTEExoticModule.class,remap = false)
public abstract class ExoticModuleMixin extends MTEBaseModule {

    public ExoticModuleMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private long actualParallel;

    @Shadow
    private int numberOfFluids;

    @Shadow
    private int numberOfItems;

    @Shadow
    private FluidStack[] randomizedFluidInput;

    @Shadow
    private ItemStack[] randomizedItemInput;

    @Shadow
    private native FluidStack[] convertItemToPlasma(ItemStack[] items, long multiplier);

    @Shadow
    private native FluidStack[] convertFluidToPlasma(FluidStack[] fluids, long multiplier);
    /**
     * @author EyeOfHarmonyBuffer
     * @reason 修改夸克胶子流体的处理逻辑
     */
    @Overwrite
    private GTRecipe generateQuarkGluonRecipe() {
        actualParallel = super.getMaxParallel();

        if (MainConfig.ExoticModuleEnable) {
            // 无输入模式
            numberOfFluids = 0;
            numberOfItems = 0;
            randomizedFluidInput = new FluidStack[0];
            randomizedItemInput = new ItemStack[0];

            return new GTRecipe(
                false,
                null,
                null,
                null,
                null,
                new FluidStack[0],
                new FluidStack[] {
                    MaterialsUEVplus.QuarkGluonPlasma.getFluid(1000 * actualParallel)
                },
                10 * SECONDS,
                (int) TierEU.RECIPE_MAX,
                0);
        } else {
            // 原版模式
            numberOfFluids = 3;
            numberOfItems = 4;
            randomizedFluidInput = getSpecificFluidInputs();
            randomizedItemInput = getSpecificItemInputs();

            if (numberOfFluids != 0) {
                for (FluidStack fluidStack : randomizedFluidInput) {
                    fluidStack.amount = 1000;
                }
            }

            if (numberOfItems != 0) {
                for (ItemStack itemStack : randomizedItemInput) {
                    itemStack.stackSize = 9;
                }
            }

            return new GTRecipe(
                false,
                null,
                null,
                null,
                null,
                ArrayUtils.addAll(
                    convertItemToPlasma(randomizedItemInput, 1),
                    convertFluidToPlasma(randomizedFluidInput, 1)),
                new FluidStack[] {
                    MaterialsUEVplus.QuarkGluonPlasma.getFluid(1000 * actualParallel)
                },
                10 * SECONDS,
                (int) TierEU.RECIPE_MAX,
                0);
        }
    }

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 修改磁物质流体的处理逻辑
     */
    @Overwrite
    private GTRecipe generateMagmatterRecipe() {
        actualParallel = super.getMaxParallel();

        if (MainConfig.ExoticModuleEnable) {
            // 无输入模式
            numberOfItems = 0;
            numberOfFluids = 0;
            randomizedItemInput = new ItemStack[0];
            randomizedFluidInput = new FluidStack[0];

            return new GTRecipe(
                false,
                null,
                null,
                null,
                null,
                new FluidStack[0],
                new FluidStack[] {
                    MaterialsUEVplus.MagMatter.getMolten(576 * actualParallel)
                },
                10 * SECONDS,
                (int) TierEU.RECIPE_MAX,
                0);
        } else {
            // 原版模式
            randomizedItemInput = getSpecificMagmatterItem();
            numberOfItems = 1;
            numberOfFluids = 2;

            int timeAmount = 25;
            int spaceAmount = 75;
            randomizedFluidInput = new FluidStack[] {
                MaterialsUEVplus.Time.getMolten(timeAmount * 1000L),
                MaterialsUEVplus.Space.getMolten(spaceAmount * 1000L)
            };

            return new GTRecipe(
                false,
                null,
                null,
                null,
                null,
                ArrayUtils.addAll(
                    convertItemToPlasma(randomizedItemInput, spaceAmount - timeAmount),
                    MaterialsUEVplus.Time.getMolten(timeAmount),
                    MaterialsUEVplus.Space.getMolten(spaceAmount)),
                new FluidStack[] {
                    MaterialsUEVplus.MagMatter.getMolten(576 * actualParallel)
                },
                10 * SECONDS,
                (int) TierEU.RECIPE_MAX,
                0);
        }
    }

    private FluidStack[] getSpecificFluidInputs() {
        return new FluidStack[0];
    }

    private ItemStack[] getSpecificItemInputs() {
        return new ItemStack[0];
    }

    private ItemStack[] getSpecificMagmatterItem() {
        return new ItemStack[0];
    }
}
