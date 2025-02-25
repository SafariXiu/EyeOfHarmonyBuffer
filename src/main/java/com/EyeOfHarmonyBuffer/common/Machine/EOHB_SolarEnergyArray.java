package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import tectech.thing.casing.BlockGTCasingsTT;
import tectech.thing.casing.TTCasingsContainer;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.casingTexturePages;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_SolarEnergyArray extends MTETooltipMultiBlockBaseEM implements IConstructable, ISurvivalConstructable {

    public EOHB_SolarEnergyArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_SolarEnergyArray(String aName) {
        super(aName);
    }

    private IStructureDefinition<EOHB_SolarEnergyArray> multiDefinition = null;

    @Override
    public IStructureDefinition<? extends TTMultiblockBase> getStructure_EM() {
        if (multiDefinition == null) {
            multiDefinition = StructureDefinition.<EOHB_SolarEnergyArray>builder()
                .addShape(
                    mName,
                    transpose(
                        new String[][]{
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   AAAAAAAAAAAAAAAAAAAAAAAAA   ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","   AAAAAAAAAAAAAAAAAAAAAAAAA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   ADDDDDDDDDDDDDDDDDDDDDDDA   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","                               ","                               ","                               ","              BBB              ","              B B              ","              BBB              ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","   CCCCCCCCCCCCCCCCCCCCCCCCC   ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","              BBB              ","              B B              ","              BBB              ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","              BBB              ","              B B              ","              BBB              ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","              BBB              ","              B B              ","              BBB              ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","              BBB              ","              B B              ","              BBB              ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","              BBB              ","              B B              ","              BBB              ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","              B~B              ","              B B              ","              BBB              ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               ","                               "},
                            {"                               ","               F               ","               F               ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FFF              ","                               ","                               ","                               ","   FFFFFFFF   BBB   FFFFFFFF   "," GGGGGGGGGF   B B   FGGGGGGGGG ","   FFFFFFFF   BBB   FFFFFFFF   ","                               ","                               ","                               ","              FFF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","              FGF              ","               G               ","               G               ","                               "},
                            {"              EEE              ","              EEE              ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","              EEE              ","  EEEEEEEEEE  EEE  EEEEEEEEEE  ","EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE","EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE","EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE","  EEEEEEEEEE  EEE  EEEEEEEEEE  ","              EEE              ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","             EEEEE             ","              EEE              ","              EEE              "}
                        }
                    )
                )
                .addElement('E',
                    ofChain(
                        buildHatchAdder(EOHB_SolarEnergyArray.class)
                            .atLeast(
                                tectech.thing.metaTileEntity.multi.base.TTMultiblockBase.HatchElement.DynamoMulti
                                    .or(gregtech.api.enums.HatchElement.Dynamo),
                                gregtech.api.enums.HatchElement.Maintenance
                            )
                            .casingIndex(BlockGTCasingsTT.textureOffset + 1)
                            .dot(1)
                            .build(),
                        ofBlock(TTCasingsContainer.sBlockCasingsTT,1)
                ))
                .addElement('F', ofBlock(TTCasingsContainer.sBlockCasingsTT,2))
                .addElement('G', ofBlock(TTCasingsContainer.sBlockCasingsTT,3))
                .addElement('A', ofBlock(sBlockCasings10,6))
                .addElement('D', ofBlock(sBlockCasings9,13))
                .addElement('B', ofBlock(sBlockCasings10,7))
                .addElement('C', ofBlock(sBlockCasings8,7))
                .build();
        }
        return multiDefinition;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_SolarEnergyArray(this.mName);
    }
    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(mName, stackSize, hintsOnly, 15, 10, 14);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (this.mMachine) return -1;
        return this.survivialBuildPiece(
            mName,
            stackSize,
            15,
            10,
            14,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public boolean checkMachine_EM(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return structureCheck_EM(mName, 15, 10, 14)
            && mDynamoHatches.size() + eDynamoMulti.size() == 1;
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
            .addSeparator()
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addDynamoHatch(add_DynamoHatch)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
    }

    public void addAutoEnergy(){

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
