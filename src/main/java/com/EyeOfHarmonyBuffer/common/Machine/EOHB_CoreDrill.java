package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IWirelessEnergyHatchInformation;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_DynamoHatch;
import static com.Nxer.TwistSpaceTechnology.common.machine.ValueEnum.SPACE_ELEVATOR_BASE_CASING_INDEX;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_OFF;

public class EOHB_CoreDrill extends WirelessEnergyMultiMachineBase<EOHB_CoreDrill>
    implements IWirelessEnergyHatchInformation {

    public EOHB_CoreDrill(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.CoreDrill;
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
        tt.addMachineType(Tooltip_SolarEnergyArray_MachineType)
            .addInfo(Tooltip_SolarEnergyArray_Controller)
            .addInfo(Tooltip_SolarEnergyArray_00)
            .addInfo(Tooltip_SolarEnergyArray_01)
            .addInfo(Tooltip_SolarEnergyArray_02)
            .addInfo(Tooltip_SolarEnergyArray_03)
            .addInfo(Tooltip_SolarEnergyArray_04)
            .addInfo(Tooltip_SolarEnergyArray_05)
            .addSeparator()
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addDynamoHatch(add_DynamoHatch)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
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
        return 0;
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 0;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return null;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {

            if (aActive) {
                return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(SPACE_ELEVATOR_BASE_CASING_INDEX),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_DTPF_ON)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FUSION1_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }

            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(SPACE_ELEVATOR_BASE_CASING_INDEX),
                TextureFactory.builder()
                    .addIcon(OVERLAY_DTPF_OFF)
                    .extFacing()
                    .build() };
        }

        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(SPACE_ELEVATOR_BASE_CASING_INDEX) };
    }
}
