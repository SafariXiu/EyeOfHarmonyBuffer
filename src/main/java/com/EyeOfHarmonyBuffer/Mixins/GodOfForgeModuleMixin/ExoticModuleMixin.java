package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import com.EyeOfHarmonyBuffer.Mixins.Invoke.ExoticModuleMixinInvoker;
import com.EyeOfHarmonyBuffer.utils.PredeterminedValues;
import gregtech.api.enums.MaterialsUEVplus;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.ArrayUtils;
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

import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static tectech.loader.recipe.Godforge.*;

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
            if (pickedFluids.size() >= fluidEntryList.size()) {
                break; // 避免死循环
            }

            int predictableWeight = PredeterminedValues.getNextFluidWeight();

            for (int j = 0; j < cumulativeWeights.size(); j++) {
                if (predictableWeight <= cumulativeWeights.get(j)) {
                    FluidStack pickedFluid = fluidEntryList.get(j).getKey();
                    if (pickedFluids.contains(pickedFluid)) {
                        i--; // 跳过重复选择
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
            if (pickedItems.size() >= itemEntryList.size()) {
                break; // 避免死循环
            }

            int predictableWeight = PredeterminedValues.getNextItemWeight();

            for (int j = 0; j < cumulativeWeights.size(); j++) {
                if (predictableWeight <= cumulativeWeights.get(j)) {
                    ItemStack pickedItem = itemEntryList.get(j).getKey();
                    if (pickedItems.contains(pickedItem)) {
                        i--; // 跳过重复选择
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
        // 固定流体和物品数量
        numberOfFluids = 3; // 固定为 3 种流体
        numberOfItems = 4; // 固定为 4 种物品

        // 获取随机流体和物品
        FluidStack[] fluids = getRandomFluidInputs(exoticModulePlasmaFluidMap, numberOfFluids);
        ItemStack[] items = getRandomItemInputs(exoticModulePlasmaItemMap, numberOfItems);

        if (fluids == null || fluids.length == 0) {
            System.out.println("No fluids generated for Quark Gluon recipe!");
            cir.cancel();
            return;
        }

        if (items == null || items.length == 0) {
            System.out.println("No items generated for Quark Gluon recipe!");
            cir.cancel();
            return;
        }

        // 调用私有方法 convertFluidToPlasma，通过访问器
        FluidStack[] plasmaFluids = ((ExoticModuleMixinInvoker) this).invokeConvertFluidToPlasma(fluids, 1);

        // 调用私有方法 convertItemToPlasma，通过访问器
        FluidStack[] plasmaItems = ((ExoticModuleMixinInvoker) this).invokeConvertItemToPlasma(items, 1);

        // 构建配方
        GTRecipe customRecipe = new GTRecipe(
            false,
            null,
            null,
            null,
            null,
            ArrayUtils.addAll(plasmaItems, plasmaFluids), // 合并等离子物品和等离子流体
            new FluidStack[]{ MaterialsUEVplus.QuarkGluonPlasma.getFluid(1000 * getMaxParallel()) }, // 输出配方
            10 * SECONDS,
            (int) TierEU.RECIPE_MAX,
            0
        );

        // 设置返回值并取消原方法执行
        cir.setReturnValue(customRecipe);
        cir.cancel();
    }

    @Inject(method = "generateMagmatterRecipe", at = @At("HEAD"), cancellable = true)
    private void onGenerateMagmatterRecipe(CallbackInfoReturnable<GTRecipe> cir) {
        try {
            // 固定时间和空间量
            int timeAmount = 25; // 固定时间量
            int spaceAmount = 75; // 固定空间量

            // 构建输入流体
            FluidStack[] fluids = new FluidStack[] {
                MaterialsUEVplus.Time.getMolten(timeAmount * 1000L),
                MaterialsUEVplus.Space.getMolten(spaceAmount * 1000L)
            };

            // 获取随机物品输入
            ItemStack[] items = getRandomItemInputs(exoticModuleMagmatterItemMap, 1);

            if (items == null || items.length == 0) {
                System.out.println("No items generated for Magmatter recipe!");
                cir.cancel();
                return;
            }

            // 调用私有方法 convertItemToPlasma，通过访问器
            FluidStack[] plasmaItems = ((ExoticModuleMixinInvoker) this)
                .invokeConvertItemToPlasma(items, spaceAmount - timeAmount);

            // 构建配方
            GTRecipe customRecipe = new GTRecipe(
                false,
                null,
                null,
                null,
                null,
                ArrayUtils.addAll(plasmaItems, fluids), // 合并等离子物品和输入流体
                new FluidStack[] { MaterialsUEVplus.MagMatter.getMolten(576 * getMaxParallel()) }, // 输出配方
                10 * SECONDS,
                (int) TierEU.RECIPE_MAX,
                0
            );

            // 设置返回值并取消原方法执行
            cir.setReturnValue(customRecipe);
            cir.cancel();
        } catch (Exception e) {
            // 捕获任何异常并记录日志
            System.err.println("Error generating Magmatter recipe: " + e.getMessage());
            e.printStackTrace();
            cir.cancel();
        }
    }
}
