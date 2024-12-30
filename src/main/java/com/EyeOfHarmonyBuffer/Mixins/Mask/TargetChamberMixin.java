package com.EyeOfHarmonyBuffer.Mixins.Mask;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.TickTime;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.util.GTUtility;
import gtnhlanth.common.tileentity.MTETargetChamber;
import gtnhlanth.common.tileentity.recipe.beamline.BeamlineRecipeAdder2;
import gtnhlanth.common.tileentity.recipe.beamline.RecipeTC;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(value = MTETargetChamber.class, remap = false)
public abstract class TargetChamberMixin extends MTEEnhancedMultiBlockBase<MTETargetChamber> implements ISurvivalConstructable {

    protected TargetChamberMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private ItemStack getFocusItemStack() {
        return null;
    }

    @Inject(method = "checkRecipe", at = @At("HEAD"), cancellable = true)
    private void bypassChecks(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (MainConfig.TargetChamberEnable) {
            MTETargetChamber chamber = (MTETargetChamber) (Object) this;

            // 获取输入物品
            ArrayList<ItemStack> tItems = chamber.getStoredInputs();
            ItemStack tFocusItem = getFocusItemStack();

            // 复制焦点物品并将其耐久设置为0
            ItemStack tFocusItemZeroDamage = null;
            if (tFocusItem != null) {
                tFocusItemZeroDamage = tFocusItem.copy();
                tFocusItemZeroDamage.setItemDamage(0);
            }

            // 创建包含焦点物品和普通输入物品的列表
            ArrayList<ItemStack> tItemsWithFocusItem = new ArrayList<>();
            tItemsWithFocusItem.add(tFocusItemZeroDamage);
            tItemsWithFocusItem.addAll(tItems);

            // 转换为数组
            ItemStack[] tItemsWithFocusItemArray = tItemsWithFocusItem.toArray(new ItemStack[0]);

            // 查找配方
            RecipeTC tRecipe = (RecipeTC) BeamlineRecipeAdder2.instance.TargetChamberRecipes
                .findRecipeQuery()
                .items(tItemsWithFocusItemArray)
                .voltage(chamber.getMaxInputVoltage())
                .find();

            // 如果配方不存在，返回 false
            if (tRecipe == null) {
                cir.setReturnValue(false);
                return;
            }

            // 默认的运行时间（从配方获取）
            double baseProgressTime = tRecipe.mDuration;

            int batchAmount;

            // 初始化批量处理量，设置为机器支持的最大值
            if(MainConfig.TargetChamberParallelEnable){
                batchAmount = Math.min(MainConfig.TargetChamberParallel, 1_000_000);
            } else {
                batchAmount = 1;
            }

            // 调用 maxParallelCalculatedByInputs 动态计算最大并行量
            double maxParallel = tRecipe.maxParallelCalculatedByInputs(batchAmount, new FluidStack[] {}, tItemsWithFocusItemArray);

            // 如果 maxParallel 小于 1，表示输入不足，直接返回 false
            if (maxParallel < 1) {
                cir.setReturnValue(false);
                return;
            }

            // 限制 batchAmount 为 maxParallel
            batchAmount = Math.min(batchAmount, (int) maxParallel);

            // 消耗输入物品
            tRecipe.consumeInput(batchAmount, new FluidStack[] {}, tItemsWithFocusItemArray);

            // 生成输出物品
            ItemStack[] itemOutputArray = GTUtility.copyItemArray(tRecipe.mOutputs);
            for (ItemStack stack : itemOutputArray) {
                stack.stackSize *= batchAmount;
            }

            // 缩短运行时间
            double progressTime = baseProgressTime / 20.0;
            if (progressTime < 1) {
                progressTime = 1; // 最小运行时间为 1 tick
            }

            // 更新机器状态
            chamber.mMaxProgresstime = (int) progressTime; // 设置缩短后的运行时间
            chamber.mEfficiency = 10000;
            chamber.mEfficiencyIncrease = 10000;
            chamber.mEUt = 0;
            chamber.mOutputItems = itemOutputArray;

            // 更新物品槽状态
            chamber.updateSlots();

            // 返回 true，表示配方处理成功
            cir.setReturnValue(true);
        }
    }
}
