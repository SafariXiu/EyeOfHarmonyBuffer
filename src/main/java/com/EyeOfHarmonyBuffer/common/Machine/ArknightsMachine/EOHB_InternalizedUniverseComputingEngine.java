package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import tectech.thing.casing.BlockGTCasingsTT;
import tectech.thing.casing.TTCasingsContainer;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

import java.math.BigInteger;
import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_2;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_InternalizedUniverseComputingEngine extends OrundumWirelessMultiMachineBase<EOHB_InternalizedUniverseComputingEngine>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_InternalizedUniverseComputingEngine> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainInternalizedUniverseComputingEngine";
    private static final int OffsetsX = 0;
    private static final int OffsetsY = 1;
    private static final int OffsetsZ = 0;
    private static IIconContainer ScreenOFF;
    private static IIconContainer ScreenON;

    public EOHB_InternalizedUniverseComputingEngine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_InternalizedUniverseComputingEngine(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 1200;
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
    public int getMaxParallelRecipes() {
        return 1;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.InternalizedUniverseComputingEngine;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {

        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);
        if (!ok) return;
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
        {"DD      ","AAAAAAAA","AAAAAAAA","BBBBBBBB"},
        {"~C      ","CCCCCCCC","CAAAAAAC","BBBBBBBB"},
        {"DD      ","AAAAAAAA","AAAAAAAA","BBBBBBBB"}
    };

    @Override
    public IStructureDefinition<EOHB_InternalizedUniverseComputingEngine> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_InternalizedUniverseComputingEngine>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement(
                    'A',
                    ofBlock(TTCasingsContainer.sBlockCasingsTT, 1)
                )
                .addElement(
                    'B',
                    ofBlock(TTCasingsContainer.sBlockCasingsTT, 2)
                )
                .addElement(
                    'C',
                    ofBlock(TTCasingsContainer.sBlockCasingsTT, 3)
                )
                .addElement(
                    'D',
                    buildHatchAdder(EOHB_InternalizedUniverseComputingEngine.class)
                        .atLeast(InputBus, InputHatch)
                        .casingIndex(BlockGTCasingsTT.textureOffset)
                        .hint(1)
                        .buildAndChain(
                            ofBlock(TTCasingsContainer.sBlockCasingsTT, 0)
                        )
                )

                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_InternalizedUniverseComputingEngine_MachineType)
            .addInfo(Tooltip_InternalizedUniverseComputingEngine_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_InternalizedUniverseComputingEngine_00)
            .addInfo(Tooltip_InternalizedUniverseComputingEngine_01)
            .addInfo(Tooltip_InternalizedUniverseComputingEngine_02)
            .addInfo(Tooltip_InternalizedUniverseComputingEngine_03)
            .addInfo(Tooltip_InternalizedUniverseComputingEngine_04)
            .addInfo(Tooltip_InternalizedUniverseComputingEngine_05)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addOutputBus("4+", EOHB_MachineType_2)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_InternalizedUniverseComputingEngine(this.mName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        ScreenOFF = Textures.BlockIcons.custom("iconsets/EM_COMPUTER");
        ScreenON = Textures.BlockIcons.custom("iconsets/EM_COMPUTER_ACTIVE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.casingTexturePages[BlockGTCasingsTT.texturePage][3],
                new TTRenderedExtendedFacingTexture(aActive ? ScreenON : ScreenOFF) };
        }
        return new ITexture[] { Textures.BlockIcons.casingTexturePages[BlockGTCasingsTT.texturePage][3] };
    }

    @Override
    protected BigInteger getProvidedComputeForCurrentState() {
        return BigInteger.valueOf(150);
    }
}
