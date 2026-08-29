package com.EyeOfHarmonyBuffer.Mixins.RareEarth;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.tileentities.machines.multi.MTEFrothFlotationCell;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
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
public abstract class FrothFlotationCellMixin extends MTEExtendedPowerMultiBlockBase<MTEFrothFlotationCell>
    implements ISurvivalConstructable {

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
        if (MainConfig.FrothFlotationCellEnable) {
            ProcessingLogic customLogic = new FrothFlotationCellLogic(this);

            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }

    private static final class FrothFlotationCellLogic extends ProcessingLogic {

        private final FrothFlotationCellMixin outer;

        FrothFlotationCellLogic(FrothFlotationCellMixin outer) {
            this.outer = outer;
        }

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
            if (outer.lockedMaterialName == null || !outer.lockedMaterialName.startsWith("milled")) {
                outer.lockedMaterialName = milledName;
            }

            // Ensure oredict matches
            if (!Objects.equals(outer.lockedMaterialName, milledName)) {
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
            return OverclockCalculator.ofNoOverclock(recipe)
                .setEUtDiscount(0.0);
        }

        @NotNull
        @Override
        protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
            return new ParallelHelper()
                .setRecipe(recipe)
                .setItemInputs(inputItems)
                .setFluidInputs(inputFluids)
                .setAvailableEUt(Integer.MAX_VALUE)
                .setMachine(machine, protectItems, protectFluids)
                .setMaxParallel(Integer.MAX_VALUE)
                .setEUtModifier(0.0)
                .enableBatchMode(batchSize)
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
    }
}
