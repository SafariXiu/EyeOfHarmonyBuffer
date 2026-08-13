package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import com.EyeOfHarmonyBuffer.Mixins.Accessor.GodOfForgeModule.MTEExoticModuleAccessor;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTStreamUtil;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.misc.WirelessNetworkManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Stream;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;

@Mixin(value = MTEExoticModule.class, remap = false)
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
    private boolean recipeInProgress;

    @Shadow
    private boolean magmatterMode;

    @Shadow
    private GTRecipe plasmaRecipe;

    @Shadow
    private boolean recipeRegenerated;

    @Shadow
    private BigInteger powerForRecipe;

    @Shadow
    private long EUt;

    /**
     * @author eyeofharmonybuffer
     * @reason 修改夸克胶子流体的处理逻辑
     */
    @Inject(method = "generateQuarkGluonRecipe", at = @At("HEAD"), cancellable = true)
    private void injectGenerateQuarkGluonRecipe(CallbackInfoReturnable<GTRecipe> cir) {
        if (MainConfig.ExoticModuleEnable) {
            actualParallel = getActualParallel();

            numberOfFluids = 0;
            numberOfItems = 0;
            randomizedFluidInput = new FluidStack[0];
            randomizedItemInput = new ItemStack[0];

            GTRecipe recipe = new GTRecipe(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FluidStack[0],
                new FluidStack[]{
                    Materials.QuarkGluonPlasma.getFluid((int) (1000L * actualParallel))
                },
                10 * SECONDS,
                (int) TierEU.RECIPE_MAX,
                0
            );

            cir.setReturnValue(recipe);
            cir.cancel();
        }
    }

    /**
     * @author eyeofharmonybuffer
     * @reason 修改磁物质流体的处理逻辑
     */
    @Inject(method = "generateMagmatterRecipe", at = @At("HEAD"), cancellable = true)
    private void injectGenerateMagmatterRecipe(CallbackInfoReturnable<GTRecipe> cir) {
        if (MainConfig.ExoticModuleEnable) {
            actualParallel = getActualParallel();

            numberOfItems = 0;
            numberOfFluids = 0;
            randomizedItemInput = new ItemStack[0];
            randomizedFluidInput = new FluidStack[0];

            GTRecipe recipe = new GTRecipe(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FluidStack[0],
                new FluidStack[]{
                    Materials.MagMatter.getMolten((int) (576L * actualParallel))
                },
                10 * SECONDS,
                (int) TierEU.RECIPE_MAX,
                0
            );

            cir.setReturnValue(recipe);
            cir.cancel();
        }
    }

    /**
     * @reason 夸克胶子/磁物质模块超频处理逻辑
     */
    @Inject(method = "createProcessingLogic", at = @At("HEAD"), cancellable = true)
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if (MainConfig.ExoticModuleOverClock) {
            ProcessingLogic wrappedLogic = new ExoticModuleLogic(this);

            wrappedLogic
                .setEuModifier(0.0F)
                .setMaxParallelSupplier(() -> 200000);

            cir.setReturnValue(wrappedLogic);
            cir.cancel();
        }
    }

    private static final class ExoticModuleLogic extends ProcessingLogic {

        private final ExoticModuleMixin outer;

        ExoticModuleLogic(ExoticModuleMixin outer) {
            this.outer = outer;
        }

        @NotNull
        @Override
        protected Stream<GTRecipe> findRecipeMatches(@Nullable RecipeMap<?> map) {
            if (!outer.recipeInProgress) {
                MTEExoticModuleAccessor accessor = (MTEExoticModuleAccessor) (Object) outer;
                outer.plasmaRecipe = outer.magmatterMode
                    ? accessor.invokeGenerateMagmatterRecipe()
                    : accessor.invokeGenerateQuarkGluonRecipe();
            }
            return GTStreamUtil.ofNullable(outer.plasmaRecipe);
        }

        @NotNull
        @Override
        protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
            if (!outer.recipeInProgress || outer.recipeRegenerated) {
                outer.powerForRecipe = BigInteger
                    .valueOf(outer.getProcessingVoltage())
                    .multiply(BigInteger.valueOf((long) recipe.mDuration * outer.actualParallel));

                if (WirelessNetworkManager.getUserEU(outer.userUUID).compareTo(outer.powerForRecipe) < 0) {
                    outer.plasmaRecipe = null;
                    return CheckRecipeResultRegistry.insufficientStartupPower(outer.powerForRecipe);
                }

                if (outer.numberOfFluids != 0) {
                    FluidStack[] outputs = Arrays.stream(outer.randomizedFluidInput)
                        .map(fluid -> {
                            FluidStack copy = fluid.copy();
                            copy.amount /= 1000;
                            return copy;
                        })
                        .toArray(FluidStack[]::new);

                    outer.addFluidOutputs(outputs, outer.mOutputHatches);
                }

                if (outer.numberOfItems != 0) {
                    outer.addItemOutputs(outer.randomizedItemInput);
                }

                outer.recipeInProgress = true;
                outer.recipeRegenerated = false;
            }

            for (FluidStack stack : recipe.mFluidInputs) {
                if (!ArrayUtils.contains(inputFluids, stack)
                    || inputFluids[ArrayUtils.indexOf(inputFluids, stack)].amount != stack.amount) {
                    return SimpleCheckRecipeResult.ofFailure("waiting_for_inputs");
                }
            }
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        @NotNull
        @Override
        protected CheckRecipeResult onRecipeStart(@NotNull GTRecipe recipe) {
            outer.EUt = calculatedEut;
            outer.powerForRecipe = BigInteger.valueOf(outer.EUt)
                .multiply(BigInteger.valueOf((long) duration * outer.actualParallel));

            if (!addEUToGlobalEnergyMap(outer.userUUID, outer.powerForRecipe.negate())) {
                return CheckRecipeResultRegistry.insufficientStartupPower(outer.powerForRecipe);
            }

            outer.addToPowerTally(outer.powerForRecipe);
            outer.addToRecipeTally(calculatedParallels);
            overwriteCalculatedEut(0);
            outer.plasmaRecipe = null;
            outer.recipeInProgress = false;
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        @NotNull
        @Override
        protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
            return super.createOverclockCalculator(recipe)
                .setEUt(outer.getProcessingVoltage())
                .setDurationDecreasePerOC(outer.getOverclockTimeFactor());
        }

        @Override
        protected double calculateDuration(@Nonnull GTRecipe recipe,
            @Nonnull ParallelHelper helper,
            @Nonnull OverclockCalculator calculator) {
            return 10;
        }
    }
}
