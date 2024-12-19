package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import com.EyeOfHarmonyBuffer.utils.PredeterminedValues;
import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = MTEExoticModule.class,remap = false)
public abstract class ExoticModuleMixin extends MTEBaseModule {

    public ExoticModuleMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int numberOfFluids = 0;

    @Shadow
    private int numberOfItems = 0;

    /**
     * @author EeyOfHarmonyBuffer
     * @reason 改随机流体选择
     */
    @Overwrite
    private FluidStack[] getRandomFluidInputs(HashMap<FluidStack, Integer> fluidMap, int numberOfFluids) {
        int cumulativeWeight = 0;
        List<Map.Entry<FluidStack, Integer>> fluidEntryList = new ArrayList<>(fluidMap.entrySet());

        List<Integer> cumulativeWeights = new ArrayList<>();
        for (Map.Entry<FluidStack, Integer> entry : fluidEntryList) {
            cumulativeWeight += entry.getValue();
            cumulativeWeights.add(cumulativeWeight);
        }

        List<FluidStack> pickedFluids = new ArrayList<>();
        for (int i = 0; i < numberOfFluids; i++) {
            int predictableWeight = PredeterminedValues.getNextFluidWeight();

            for (int j = 0; j < cumulativeWeights.size(); j++) {
                if (predictableWeight <= cumulativeWeights.get(j)) {
                    FluidStack pickedFluid = fluidEntryList.get(j).getKey();
                    if (pickedFluids.contains(pickedFluid)) {
                        i--;
                        break;
                    }
                    pickedFluids.add(pickedFluid);
                    break;
                }
            }
        }
        return pickedFluids.toArray(new FluidStack[0]);
    }

    /**
     * @author EeyOfHarmonyBuffer
     * @reason 修改随机物品选择
     */
    @Overwrite
    private ItemStack[] getRandomItemInputs(HashMap<ItemStack, Integer> itemMap, int numberOfItems) {
        int cumulativeWeight = 0;
        List<Map.Entry<ItemStack, Integer>> itemEntryList = new ArrayList<>(itemMap.entrySet());

        List<Integer> cumulativeWeights = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> entry : itemEntryList) {
            cumulativeWeight += entry.getValue();
            cumulativeWeights.add(cumulativeWeight);
        }

        List<ItemStack> pickedItems = new ArrayList<>();
        for (int i = 0; i < numberOfItems; i++) {
            int predictableWeight = PredeterminedValues.getNextItemWeight();

            for (int j = 0; j < cumulativeWeights.size(); j++) {
                if (predictableWeight <= cumulativeWeights.get(j)) {
                    ItemStack pickedItem = itemEntryList.get(j).getKey();
                    if (pickedItems.contains(pickedItem)) {
                        i--;
                        break;
                    }
                    pickedItems.add(pickedItem);
                    break;
                }
            }
        }
        return pickedItems.toArray(new ItemStack[0]);
    }

    @Inject(method = "generateQuarkGluonRecipe", at = @At("HEAD"), cancellable = true)
    private void onGenerateQuarkGluonRecipe(CallbackInfoReturnable<GTRecipe> cir) {
        numberOfFluids = 3;
        numberOfItems = 4;

        cir.cancel();
    }

    @Inject(method = "generateMagmatterRecipe", at = @At("HEAD"), cancellable = true)
    private void onGenerateMagmatterRecipe(CallbackInfoReturnable<GTRecipe> cir) {
        int timeAmount = 25;
        int spaceAmount = 75;

        cir.cancel();
    }


}
