package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.EyeOfHarmonyBuffer.utils.Utils;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static tectech.thing.casing.TTCasingsContainer.sBlockCasingsTT;

public class EOHB_BlueDogMachine extends WirelessEnergyMultiMachineBase<EOHB_BlueDogMachine> {

    private static IStructureDefinition<EOHB_BlueDogMachine> STRUCTURE_DEFINITION = null;
    private static Boolean BlueDogModel = false;

    public EOHB_BlueDogMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Override
    public IStructureDefinition<EOHB_BlueDogMachine> getStructureDefinition(){
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_BlueDogMachine>builder()
                .addShape(
                    mName,
                    transpose(
                        new String[][] {
                            { "CCC", "CCC", "CCC" },
                            { "C~C", "C-C", "CCC" },
                            { "CCC", "CCC", "CCC" },
                        }))
                .addElement(
                    'C',
                    ofBlock(sBlockCasingsTT,9)
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 0;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return false;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 0;
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 0;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {

    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_BlueDogMachine_MachineType)
            .addInfo(Tooltip_BlueDogMachine_Controller)
            .addInfo(Tooltip_BlueDogMachine_00)
            .addInfo(Tooltip_BlueDogMachine_01)
            .addInfo(Tooltip_BlueDogMachine_02)
            .addInfo(Tooltip_BlueDogMachine_03)
            .addInfo(Tooltip_BlueDogMachine_04)
            .addInfo(Tooltip_BlueDogMachine_05)
            .addInfo(Tooltip_BlueDogMachine_06)
            .addSeparator()
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputHatch(add_inputHatch)
            .addOutputHatch(add_outputHatch)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        if(CheckMachineBluDogMode()){
            return RecipeMaps.BlueDogMachineMax;
        }
        return RecipeMaps.BlueDogMachine;
    }

    private boolean CheckMachineBluDogMode() {
        BlueDogModel = Utils.metaItemEqual(this.getControllerSlot(), MiscHelper.ASTRAL_ARRAY_FABRICATOR);
        return BlueDogModel;
    }

    @Override
    protected ProcessingLogic createProcessingLogic(){
        return new ProcessingLogic(){
            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                return new OverclockCalculator()
                    .setParallel(Integer.MAX_VALUE);
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
        }.setMaxParallel(Integer.MAX_VALUE);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return null;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing, int colorIndex, boolean active, boolean redstoneLevel) {
        return new ITexture[0];
    }
}
