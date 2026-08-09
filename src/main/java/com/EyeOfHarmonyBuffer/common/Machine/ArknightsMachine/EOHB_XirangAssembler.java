package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import bartworks.common.loaders.ItemRegistry;
import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentType;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.IGasEnvironmentConsumer;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.UpgradableOrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings10;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_UpgradeCard;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_XirangAssembler_00;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_XirangAssembler_01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_XirangAssembler_02;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_XirangAssembler_03;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_XirangAssembler_04;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_XirangAssembler_Controller;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_XirangAssembler_MachineType;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.GregTechAPI.sBlockGlass1;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_MULTI_AUTOCLAVE_ACTIVE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_XirangAssembler extends UpgradableOrundumWirelessMultiMachineBase<EOHB_XirangAssembler>
    implements IConstructable, ISurvivalConstructable, IGasEnvironmentConsumer, ICasingTextureProvider {

    private static IStructureDefinition<EOHB_XirangAssembler> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainXirangAssembler";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 6;
    private static final int OffsetsZ = 0;

    private static final GasEnvironmentType REQUIRED_ENVIRONMENT = GasEnvironmentType.XRANITE;

    private boolean environmentSatisfied = false;

    public EOHB_XirangAssembler(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_XirangAssembler(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.XirangAssembler;
    }

    @Override
    public GasEnvironmentType getRequiredEnvironmentType() {
        return REQUIRED_ENVIRONMENT;
    }

    @Override
    public void onEnvironmentNotSatisfied(GasEnvironmentType required, GasEnvironmentType actual) {
        this.environmentSatisfied = false;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) return;

        GasEnvironmentType actual = getCurrentEnvironment();
        this.environmentSatisfied = actual == REQUIRED_ENVIRONMENT;

        GasEnvironmentHelper.checkConsumer(
            this,
            aBaseMetaTileEntity.getWorld(),
            aBaseMetaTileEntity.getXCoord(),
            aBaseMetaTileEntity.getYCoord(),
            aBaseMetaTileEntity.getZCoord()
        );
    }

    @Override
    protected CheckRecipeResult wirelessPreCheck() {
        CheckRecipeResult base = super.wirelessPreCheck();
        if (!base.wasSuccessful()) return base;
        return requireXiraniteEnvironment();
    }

    @Override
    protected CheckRecipeResult doNormalModeCheck() {
        CheckRecipeResult env = requireXiraniteEnvironment();
        if (!env.wasSuccessful()) return env;
        return super.doNormalModeCheck();
    }

    protected CheckRecipeResult requireXiraniteEnvironment() {
        GasEnvironmentType actual = getCurrentEnvironment();
        this.environmentSatisfied = actual == REQUIRED_ENVIRONMENT;
        if (!this.environmentSatisfied) {
            onEnvironmentNotSatisfied(REQUIRED_ENVIRONMENT, actual);
            return SimpleCheckRecipeResult.ofFailure("EOHB_Environment_XRANITE");
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    protected GasEnvironmentType getCurrentEnvironment() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return GasEnvironmentType.NONE;
        return GasEnvironmentHelper.getEnvironmentAt(
            base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
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
        {"         ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","  FEFEF  ","         "},
        {" CCCCCCC ","CCCCCCCCC","DDCCCCCDD","CCCCCCCCC","DDCCCCCDD","CCCCCCCCC","DDCCCCCDD","CCCCCCCCC","DDCCCCCDD","CCCCCCCCC","DDCCCCCDD","CCCCCCCCC","DDCCCCCDD","CCCCCCCCC"," CCCCCCC "},
        {"GCCCCCCCG","CB     BC","DBGGGGGBD","CB     BC","DBGGGGGBD","CB     BC","DBGGGGGBD","CB     BC","DBGGGGGBD","CB     BC","DBGGGGGBD","CB     BC","DBGGGGGBD","CBHHHHHBC","GC     CG"},
        {"GC     CG","HAHHHHHAH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HAHHHHHAH","GC     CG"},
        {"GC     CG","HBHHHHHBH","DBG   GBD","HB     BH","DBG   GBD","HB     BH","DBG   GBD","HB     BH","DBG   GBD","HB     BH","DBG   GBD","HB     BH","DBG   GBD","HBHHHHHBH","GC     CG"},
        {"GC     CG","HAHHHHHAH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HA     AH","DAG   GAD","HAHHHHHAH","GC     CG"},
        {"GCII~IICG","CCCCCCCCC","DCCCCCCCD","CCCCCCCCC","DCCCCCCCD","CCCCCCCCC","DCCCCCCCD","CCCCCCCCC","DCCCCCCCD","CCCCCCCCC","DCCCCCCCD","CCCCCCCCC","DCCCCCCCD","CCCCCCCCC","GCJJJJJCG"}
    };

    @Override
    public IStructureDefinition<EOHB_XirangAssembler> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_XirangAssembler>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    ofBlock(GTCMItemList.XiRangWaiKeCasing.getBlock(), 0)
                )
                .addElement(
                    'B',
                    ofBlock(GTCMItemList.ZhongXiRangWaiKeCasing.getBlock(), 0)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings10, 3)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockCasings13, 2)
                )
                .addElement(
                    'E',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'F',
                    ofBlock(sBlockCasings8, 7)
                )
                .addElement(
                    'G',
                    ofBlock(sBlockFrames, 81)
                )
                .addElement(
                    'H',
                    ofBlock(sBlockGlass1, 10)
                )
                .addElement(
                    'I',
                    buildHatchAdder(EOHB_XirangAssembler.class)
                        .atLeast(InputBus, InputHatch)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings10, 3
                        )
                )
                .addElement(
                    'J',
                    buildHatchAdder(EOHB_XirangAssembler.class)
                        .atLeast(OutputBus, OutputHatch)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(2)
                        .buildAndChain(
                            sBlockCasings10, 3
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_XirangAssembler_MachineType)
            .addInfo(Tooltip_XirangAssembler_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_XirangAssembler_00)
            .addInfo(Tooltip_XirangAssembler_01)
            .addInfo(Tooltip_XirangAssembler_02)
            .addInfo(Tooltip_XirangAssembler_03)
            .addInfo(Tooltip_XirangAssembler_04)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addOutputBus("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_XirangAssembler(this.mName);
    }

    @Override
    public ITexture getCasingTexture() {
        return getCasingTextureForId(GTUtility.getCasingTextureIndex(sBlockCasings10, 3));
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_MULTI_AUTOCLAVE,
            OVERLAY_FRONT_MULTI_AUTOCLAVE_GLOW,
            OVERLAY_FRONT_MULTI_AUTOCLAVE_ACTIVE,
            OVERLAY_FRONT_MULTI_AUTOCLAVE_ACTIVE_GLOW);
    }
}
