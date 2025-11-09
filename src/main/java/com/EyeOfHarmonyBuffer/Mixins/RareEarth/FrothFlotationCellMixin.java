package com.EyeOfHarmonyBuffer.Mixins.RareEarth;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gtPlusPlus.core.material.Material;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEFrothFlotationCell;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import java.util.Objects;

@Mixin(value = MTEFrothFlotationCell.class, remap = false)
public abstract class FrothFlotationCellMixin extends GTPPMultiBlockBase<MTEFrothFlotationCell> implements ISurvivalConstructable {

    public FrothFlotationCellMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private String lockedMaterialName = null;

    @Inject(
        method = "createProcessingLogic",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.FrothFlotationCellEnable){
            ProcessingLogic customLogic = new ProcessingLogic() {

                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    // Make sure we lock to a specific milled ore, checked via oredict
                    String milledName = getMilledStackName(recipe);
                    if (milledName == null) {
                        return CheckRecipeResultRegistry.NO_RECIPE;
                    }

                    // Set material locked for this controller
                    // "milled" check is to clear old save data since the name caching system changed
                    if (lockedMaterialName == null || !lockedMaterialName.startsWith("milled")) {
                        lockedMaterialName = milledName;
                    }

                    // Ensure oredict matches
                    if (!Objects.equals(lockedMaterialName, milledName)) {
                        return SimpleCheckRecipeResult.ofFailure("machine_locked_to_different_recipe");
                    }
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                private String getMilledStackName(GTRecipe recipe) {
                    if (recipe == null || recipe.mInputs == null) {
                        return null;
                    }

                    for (ItemStack stack : recipe.mInputs) {
                        for (int oreID : OreDictionary.getOreIDs(stack)) {
                            String oredict = OreDictionary.getOreName(oreID);
                            if (oredict.startsWith(OrePrefixes.milled.toString())) {
                                return oredict;
                            }
                        }
                    }
                    return null;
                }

                @NotNull
                @Override
                protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                    return new OverclockCalculator()
                        //.setSpeedBoost(100.0) // 速度提升 100 倍
                        .setParallel(Integer.MAX_VALUE) // 最大并行数
                        .setEUt(0); // 不耗电
                }

                @NotNull
                @Override
                protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
                    return new ParallelHelper()
                        .setRecipe(recipe)
                        .setItemInputs(inputItems)
                        .setFluidInputs(inputFluids)
                        .setAvailableEUt(Integer.MAX_VALUE) // 设置无限能量
                        .setMachine(machine, protectItems, protectFluids)
                        .setMaxParallel(Integer.MAX_VALUE) // 设置极大并行
                        .setEUtModifier(0.0) // 不耗电
                        .enableBatchMode(batchSize) // 启用批量模式
                        .setConsumption(true)
                        .setOutputCalculation(true);
                }

                @Override
                protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                                   @Nonnull OverclockCalculator calculator) {
                    return 10;
                }

                @Override
                public CheckRecipeResult process() {
                    return super.process();
                }
            };

            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
