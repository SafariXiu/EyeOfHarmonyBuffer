package com.EyeOfHarmonyBuffer.common.Machine;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;

public class EOHB_SolarEnergyArray extends MTETooltipMultiBlockBaseEM implements IConstructable, ISurvivalConstructable {

    public EOHB_SolarEnergyArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    private IStructureDefinition<EOHB_SolarEnergyArray> multiDefinition = null;

    @Override
    public IStructureDefinition<EOHB_SolarEnergyArray> getStructure_EM() {
        if(multiDefinition == null){
            multiDefinition = StructureDefinition.<EOHB_SolarEnergyArray>builder()
                .addShape()
        }
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return null;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {

    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return null;
    }
}
