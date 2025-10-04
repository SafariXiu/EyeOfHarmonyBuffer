package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.block.ModBlocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static tectech.thing.casing.TTCasingsContainer.sBlockCasingsTT;

public class EOHB_BlueDogMachine extends WirelessEnergyMultiMachineBase<EOHB_BlueDogMachine> {

    private static IStructureDefinition<EOHB_BlueDogMachine> STRUCTURE_DEFINITION = null;

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
        return null;
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
