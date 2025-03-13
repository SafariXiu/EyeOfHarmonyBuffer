package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IWirelessEnergyHatchInformation;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.ParallelHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_OFF;

public class EOHB_CoreDrill extends WirelessEnergyMultiMachineBase<EOHB_CoreDrill>
    implements IWirelessEnergyHatchInformation {

    public EOHB_CoreDrill(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_CoreDrill(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_CoreDrill(this.mName);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return false;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {

    }

    @Override
    public IStructureDefinition<EOHB_CoreDrill> getStructureDefinition() {
        return null;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_NameCoreDrill_MachineType)
            .addInfo(Tooltip_NameCoreDrill_Controller)
            .addInfo(Tooltip_NameCoreDrill_00)
            .addInfo(Tooltip_NameCoreDrill_01)
            .addInfo(Tooltip_NameCoreDrill_02)
            .addInfo(Tooltip_NameCoreDrill_03)
            .addSeparator()
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .addInputBus(add_InputBus)
            .addInputHatch(add_inputHatch)
            .addOutputBus(add_OutputBus)
            .addOutputHatch(add_outputHatch)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.CoreDrill;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic(){

            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
                return new ParallelHelper()
                    .setMaxParallel(Integer.MAX_VALUE);
            }
        };
    }

    @Override
    public boolean getDefaultWirelessMode() {
        return true;
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
    protected int getMaxParallelRecipes() {
        return 5;
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 100;
    }

    @Override
    @SuppressWarnings("ALL")
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) {
                return new ITexture[] { casingTexturePages[0][12], TextureFactory.builder()
                    .addIcon(OVERLAY_DTPF_ON)
                    .extFacing()
                    .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FUSION1_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }

            return new ITexture[] { casingTexturePages[0][12], TextureFactory.builder()
                .addIcon(OVERLAY_DTPF_OFF)
                .extFacing()
                .build() };
        }

        return new ITexture[] { casingTexturePages[0][12] };
    }
}
