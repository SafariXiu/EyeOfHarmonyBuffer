package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.UpgradableOrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.angelica.shadow.javax.annotation.Nonnull;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_UpgradeCard;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.GregTechAPI.sBlockCasings2;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_Solid_GasTransmutingUnit extends UpgradableOrundumWirelessMultiMachineBase<EOHB_Solid_GasTransmutingUnit>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_Solid_GasTransmutingUnit> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainSolid_GasTransmutingUnit";
    private static final int OffsetsX = 7;
    private static final int OffsetsY = 15;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;
    private static final int CASING_INDEX1 = 183;

    private static final int FLUID_CONSUME_INTERVAL_TICKS = 20 * 60;
    private static final int FLUID_CONSUME_AMOUNT = 6000;

    private int mFluidConsumeTicker = 0;

    private static final net.minecraftforge.fluids.Fluid XIRANITE_FLUID =
        EOHBMaterialPool.Xiragen.getFluidOrGas(1).getFluid();

    private final List<MTEHatchInput> mInputHatchesG = new ArrayList<>();

    public EOHB_Solid_GasTransmutingUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_Solid_GasTransmutingUnit(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {
        mInputHatchesG.clear();
        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);

        if (!ok) return;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.Solid_GasTransmutingUnit;
    }

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        if (wirelessMode) {
            if (!drainXiraniteExactly(FLUID_CONSUME_AMOUNT)) {
                IGregTechTileEntity base = getBaseMetaTileEntity();
                if (base != null) {
                    base.setActive(false);
                }
                return CheckRecipeResultRegistry.NO_IMMERSION_FLUID;
            }
            mFluidConsumeTicker = 0;
        }

        return super.checkProcessing();
    }

    private boolean hasEnoughXiraniteInG(int amountNeeded) {
        if (mInputHatchesG.isEmpty()) return false;

        int total = 0;

        for (MTEHatchInput hatch : mInputHatchesG) {
            if (hatch == null) continue;

            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (base == null || base.isInvalidTileEntity()) continue;

            FluidStack stored = hatch.getFluid();
            if (stored == null || stored.getFluid() != XIRANITE_FLUID) continue;

            total += stored.amount;
            if (total >= amountNeeded) {
                return true;
            }
        }
        return false;
    }

    private boolean drainXiraniteExactly(int amountNeeded) {
        if (!hasEnoughXiraniteInG(amountNeeded)) {
            return false;
        }

        int remaining = amountNeeded;

        for (MTEHatchInput hatch : mInputHatchesG) {
            if (hatch == null) continue;

            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (base == null || base.isInvalidTileEntity()) continue;

            FluidStack stored = hatch.getFluid();
            if (stored == null || stored.getFluid() != XIRANITE_FLUID) continue;

            int canDrain = Math.min(remaining, stored.amount);
            if (canDrain <= 0) continue;

            FluidStack drained = hatch.drain(canDrain, true);

            if (drained != null && drained.amount > 0) {
                remaining -= drained.amount;
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        return remaining <= 0;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity.isClientSide()) {
            return;
        }

        if (!mMachine) {
            mFluidConsumeTicker = 0;
            return;
        }

        boolean isRunning = (mMaxProgresstime > 0 && mProgresstime > 0);

        if (!isRunning) {
            mFluidConsumeTicker = 0;
            return;
        }

        mFluidConsumeTicker++;

        if (mFluidConsumeTicker >= FLUID_CONSUME_INTERVAL_TICKS) {
            mFluidConsumeTicker = 0;

            if (!consumeMaintenanceFluidFromG()) {
                aBaseMetaTileEntity.setActive(false);
                stopMachine();
            }
        }
    }

    private boolean consumeMaintenanceFluidFromG() {
        return drainXiraniteExactly(FLUID_CONSUME_AMOUNT);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, OffsetsX, OffsetsY, OffsetsZ, elementBudget, env, false, true);
    }

    private static final String[][] shapeMain = new String[][]{
        {"               ","               ","               ","               ","      A A      ","     AAAAA     ","      A A      ","     AAAAA     ","      A A      ","               ","               ","               ","               "},
        {"               ","               ","               ","               ","               ","      A A      ","               ","      A A      ","               ","               ","               ","               ","               "},
        {"               ","               "," A           A ","AAAAAA   AAAAAA"," A  AAAAAAA  A "," A   ABABA   A "," A   AAAAA   A "," A   ABABA   A "," A  AAAAAAA  A ","AAAAAA   AAAAAA"," A           A ","               ","               "},
        {"               ","               ","               "," A           A ","  C         C  ","  C   CAC   C  ","  C   A A   C  ","  C   CAC   C  ","  C         C  "," A           A ","               ","               ","               "},
        {"               ","               ","               "," A           A ","  C         C  ","  CDDDCACDDDC  ","  C   A A   C  ","  CDDDCACDDDC  ","  C         C  "," A           A ","               ","               ","               "},
        {"               "," CC         CC "," CC         CC "," CC         CC "," CC         CC "," CC   CAC   CC "," CC   A A   CC "," CC   CAC   CC "," CC         CC "," CC         CC "," CC         CC "," CC         CC ","               "},
        {"               "," CC         CC "," BC         CB "," CC         CC "," CC         CC "," CC   CAC   CC "," CC   A A   CC "," CC   CAC   CC "," CC         CC "," CC         CC "," BC         CB "," CC         CC ","               "},
        {"               "," CC         CC "," CC         CC "," CC    F    CC "," CC    A    CC "," CCDDDAAADDDCC "," CC   A A   CC "," CCDDDAAADDDCC "," CC         CC "," CC         CC "," CC         CC "," CC         CC ","               "},
        {"               ","               ","               "," A           A ","  C         C  ","  C   CCC   C  ","  C  AC CA  C  ","  C   CCC   C  ","  C         C  "," A           A ","               ","               ","               "},
        {"               ","               ","               "," A           A ","  C   CCC   C  ","  C  C   C  C  ","  C AC   CA C  ","  C  C   C  C  ","  C   CCC   C  "," A           A ","               ","               ","               "},
        {"               ","               ","               "," A    B B    A ","  C  BAAAB  C  ","  C BA   AB C  ","  C AA   AA C  ","  C BA   AB C  ","  C  BAAAB  C  "," A    B B    A ","               ","               ","               "},
        {"               ","               ","      GGG      "," A   CCCCC   A "," AC CC   CC CA "," ACCC     CCCA "," ACCC     CCCA "," ACCC     CCCA "," AC CC   CC CA "," A   CCCCC   A ","      CCC      ","               ","               "},
        {"               ","               ","      GGG      "," A   CCCCC   A ","  C CC   CC C  ","  CCC     CCC  ","  CCC     CCC  ","  CCC     CCC  ","  C CC   CC C  "," A   CCCCC   A ","      CCC      ","               ","               "},
        {"               ","      AAA      "," A  AAAAAAA  A "," A BAAAAAAAB A "," AAAAAAAAAAAAA ","  AAAAAAAAAAA  ","  AAAAAAAAAAA  ","  AAAAAAAAAAA  "," AAAAAAAAAAAAA "," A BAAAAAAAB A "," A  AAAAAAA  A ","      AAA      ","               "},
        {" BAAAAEEEAAAAB "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," BAAAAAAAAAAAB "},
        {" BAAAAE~EAAAAB "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," AAAAAAAAAAAAA "," BAAAAAAAAAAAB "}
    };

    @Override
    public IStructureDefinition<EOHB_Solid_GasTransmutingUnit> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_Solid_GasTransmutingUnit>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement(
                    'A',
                    ofBlock(sBlockCasings2, 0)
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings8, 7)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockFrames, 305)
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_Solid_GasTransmutingUnit.class)
                        .atLeast(OutputBus, OutputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(3)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .addElement(
                    'F',
                    buildHatchAdder(EOHB_Solid_GasTransmutingUnit.class)
                        .atLeast(InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(2)
                        .adder(EOHB_Solid_GasTransmutingUnit::addInputHatchG)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .addElement(
                    'G',
                    buildHatchAdder(EOHB_Solid_GasTransmutingUnit.class)
                        .atLeast(InputBus, InputHatch)
                        .casingIndex(CASING_INDEX1)
                        .hint(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings8,7)
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_Solid_GasTransmutingUnit_MachineType)
            .addInfo(Tooltip_Solid_GasTransmutingUnit_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_Solid_GasTransmutingUnit_00)
            .addInfo(Tooltip_Solid_GasTransmutingUnit_01)
            .addInfo(Tooltip_Solid_GasTransmutingUnit_02)
            .addInfo(Tooltip_Solid_GasTransmutingUnit_03)
            .addInfo(Tooltip_Solid_GasTransmutingUnit_04)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addInputHatch("2+", EOHB_MachineType_1)
            .addOutputBus("1+", EOHB_MachineType_3)
            .addOutputHatch("1+", EOHB_MachineType_3)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_Solid_GasTransmutingUnit(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive)
                return new ITexture[]{
                    Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                        .extFacing()
                        .glow()
                        .build()
                };
            return new ITexture[]{
                Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build()
            };
        }
        return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_INDEX)};
    }

    public boolean addInputHatchG(IGregTechTileEntity aBaseMetaTileEntity, int aBaseCasingIndex) {
        if (aBaseMetaTileEntity == null) return false;
        IMetaTileEntity meta = aBaseMetaTileEntity.getMetaTileEntity();

        if (meta instanceof MTEHatchInput) {
            MTEHatchInput hatch = (MTEHatchInput) meta;
            hatch.updateTexture(aBaseCasingIndex);
            mInputHatchesG.add(hatch);
            return true;
        }
        return false;
    }
}
