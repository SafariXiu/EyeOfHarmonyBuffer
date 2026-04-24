package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class EOHB_ElectricTypeOneMiningMachine extends OrundumWirelessMultiMachineBase<EOHB_ElectricTypeOneMiningMachine> implements IConstructable, ISurvivalConstructable {

    public EOHB_ElectricTypeOneMiningMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 0;
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
    public void construct(ItemStack stackSize, boolean hintsOnly) {

    }

    @Override
    public IStructureDefinition<EOHB_ElectricTypeOneMiningMachine> getStructureDefinition() {
        return null;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_ElectricTypeOneMiningMachine_MachineType)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_OrundumDynamo_00)
            .addInfo(Tooltip_OrundumDynamo_01)
            .addInfo(Tooltip_OrundumDynamo_02)
            .addInfo(Tooltip_OrundumDynamo_03)
            .addInfo(Tooltip_OrundumDynamo_04)
            .addInfo(Tooltip_OrundumDynamo_05)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputHatch(add_inputHatch)
            .addInputBus(add_InputBus)
            .addOutputHatch(add_outputHatch)
            .addOutputBus(add_OutputBus)
            .toolTipFinisher(ModName);
        return tt;
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
