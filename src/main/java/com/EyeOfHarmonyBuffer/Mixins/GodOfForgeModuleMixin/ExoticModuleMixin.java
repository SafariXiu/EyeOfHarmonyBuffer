package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import gregtech.api.enums.MaterialsUEVplus;
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
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.stream.Stream;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;

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

    @Shadow
    private boolean recipeInProgress = false;

    @Shadow
    private boolean magmatterMode = false;

    @Shadow
    private GTRecipe plasmaRecipe = null;

    @Shadow
    private boolean recipeRegenerated = false;

    @Shadow
    private BigInteger powerForRecipe = BigInteger.ZERO;

    @Shadow
    private long EUt = 0;

    /**
     * @author eyeofharmonybuffer
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
     * @author eyeofharmonybuffer
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

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    private void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir){
        if(MainConfig.ExoticModuleOverClock) {
            ProcessingLogic wrappedLogic = new ProcessingLogic() {
                @NotNull
                @Override
                protected Stream<GTRecipe> findRecipeMatches(@Nullable RecipeMap<?> map) {
                    if (!recipeInProgress) {
                        if (magmatterMode) {
                            plasmaRecipe = generateMagmatterRecipe();
                        } else {
                            plasmaRecipe = generateQuarkGluonRecipe();
                        }
                    }
                    return GTStreamUtil.ofNullable(plasmaRecipe);
                }

                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    if (!recipeInProgress || recipeRegenerated) {
                        powerForRecipe = BigInteger.valueOf(getProcessingVoltage())
                            .multiply(BigInteger.valueOf(recipe.mDuration * actualParallel));
                        if (getUserEU(userUUID).compareTo(powerForRecipe) < 0) {
                            plasmaRecipe = null;
                            return CheckRecipeResultRegistry.insufficientStartupPower(powerForRecipe);
                        }

                        if (numberOfFluids != 0) {
                            for (FluidStack fluidStack : randomizedFluidInput) {
                                dumpFluid(
                                    mOutputHatches,
                                    new FluidStack(fluidStack.getFluid(), fluidStack.amount / 1000),
                                    false);
                            }
                        }

                        if (numberOfItems != 0) {
                            for (ItemStack itemStack : randomizedItemInput) {
                                addOutput(itemStack);
                            }
                        }

                        recipeInProgress = true;
                        recipeRegenerated = false;
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
                    EUt = calculatedEut;
                    powerForRecipe = BigInteger.valueOf(EUt)
                        .multiply(BigInteger.valueOf(duration * actualParallel));

                    if (!addEUToGlobalEnergyMap(userUUID, powerForRecipe.negate())) {
                        return CheckRecipeResultRegistry.insufficientStartupPower(powerForRecipe);
                    }

                    addToPowerTally(powerForRecipe);
                    addToRecipeTally(calculatedParallels);
                    overwriteCalculatedEut(0);
                    plasmaRecipe = null;
                    recipeInProgress = false;
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @NotNull
                @Override
                protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                    return super.createOverclockCalculator(recipe).setEUt(getProcessingVoltage())
                        .setDurationDecreasePerOC(getOverclockTimeFactor());
                }

                @Override
                protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                                   @Nonnull OverclockCalculator calculator) {
                    return 10;
                }
            };
            wrappedLogic
                .setEuModifier(0.0F)
                .setMaxParallelSupplier(() -> 200000);

            cir.setReturnValue(wrappedLogic);
            cir.cancel();
        }
    }
}
