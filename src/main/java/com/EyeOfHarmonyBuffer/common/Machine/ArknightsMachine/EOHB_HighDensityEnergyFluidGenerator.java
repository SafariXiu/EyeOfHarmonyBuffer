package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtnhlanth.common.register.LanthItemList;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoTunnel;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_UpgradeCard;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_InputBus;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_MaintenanceHatch;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_OutputBus;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_inputHatch;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_HighDensityEnergyFluidGenerator extends OrundumWirelessMultiMachineBase<EOHB_HighDensityEnergyFluidGenerator>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_HighDensityEnergyFluidGenerator> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainHighDensityEnergyFluidGenerator";
    private static final int OffsetsX = 7;
    private static final int OffsetsY = 13;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 183;

    private MTEHatchDynamoTunnel laserSource = null;
    private int laserSourceCount = 0;
    private int laserTotalAmps = 0;
    private int laserAmps = 1;
    private int laserTier = 0;

    public EOHB_HighDensityEnergyFluidGenerator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_HighDensityEnergyFluidGenerator(String aName) {
        super(aName);
        setWirelessCycleNum(1);
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
        return this.laserAmps;
    }

    @Override
    public int getWirelessModeProcessingTime() {
        float factor = 1.0f - this.laserTier * 0.10f;
        factor = Math.max(factor, 0.2f);

        return Math.round(200 * factor);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.HighDensityEnergyFluidGenerator;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {
        this.laserSource = null;
        this.laserSourceCount = 0;
        this.laserTotalAmps = 0;
        this.laserAmps = 1;
        this.laserTier = 0;

        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);
        if (!ok) return;

        if (this.laserSourceCount < 2) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.highdensityenergyfluidgenerator.need_laser_source"));
            return;
        }
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
        {"               ","               ","               ","               ","               ","               ","               ","               ","               ","               ","               ","      LLL      ","      LLL      ","      LLL      ","               "},
        {"               ","               ","               ","               ","               ","               ","               ","               ","               ","               ","      EEE      ","     EGBGE     ","     EB BE     ","     EGBGE     ","      EEE      "},
        {"               ","               ","               ","               ","               ","               ","       E       ","       E       ","   EEEEEEEEE   ","               ","      GBG      ","     G   G     ","     B   B     ","     G   G     ","      GBG      "},
        {"               ","               ","               ","               ","               ","       E       ","      EFE      ","   EEEEFEEEE   ","  EFFFFFFFFFE  ","   EEEEEEEEE   ","      ABA      ","     A   A     ","     B   B     ","     A   A     ","      ABA      "},
        {"               ","               ","               ","               ","               ","       E       ","      EFE      ","   E   E   E   ","  EFEEEEEEEFE  ","   E       E   ","      GBG      ","     G   G     ","     B   B     ","     G   G     ","      GBG      "},
        {"               ","               ","               ","               ","               ","       E       ","      EFE      ","   E   E   E   ","  EFE     EFE  ","   E       E   ","   E  ABA  E   ","   E A   A E   ","   EEB   BEE   ","     A   A     ","      ABA      "},
        {"               ","               ","               ","               ","               ","       E       ","      EFE      ","   E   E   E   ","  EFE     EFE  ","  EFE     EFE  ","  EFE GBG EFE  ","  EFEG   GEFE  ","  EFFF   FFFE  ","   EEG   GEE   ","      GBG      "},
        {"               ","               ","               ","               ","               ","       E       ","      EFE      ","   J   E   J   ","  JEJ     JEJ  ","   E       E   ","   E  ABA  E   ","   E A   A E   ","  JEEB   BEEJ  ","   J A   A J   ","      ABA      "},
        {"               ","               ","               ","               ","               ","       E       ","      EFE      ","   J   E   J   ","  JJJ     JJJ  ","   J       J   ","   J  GBG  J   ","   J G   G J   ","  JJ B   B JJ  ","   J G   G J   ","      GBG      "},
        {"  DHHHHBHHHHD  ","  DHHKKKKKHHD  ","  DHHHHBHHHHD  ","       E       ","       E       ","       E       ","      EFE      ","   J   E   J   ","  J J     J J  ","               ","      ABA      ","     A   A     ","  J  B   B  J  ","   J A   A J   ","      ABA      "},
        {"  DBBKKKKKBBD  ","  MCC     CCM  ","  DBBBBFBBBBD  ","      EFE      ","      EFE      ","      EFE      ","      EFE      ","   J   E   J   ","  J J     J J  ","               ","     IGBGI     ","     G   G     ","  J  B   B  J  ","   J G   G J   ","     IGBGI     "},
        {"  DHHHHBHHHHD  ","  DHHKKKKKHHD  ","  DHHHHBHHHHD  ","       E       ","       E       ","       E       ","       E       ","   J       J   ","  J J     J J  ","               ","     IEEEI     ","     EGBGE     ","  J  EBBBE  J  ","   J EGBGE J   ","     IEEEI     "},
        {" IIIIIIIIIIIII ","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII"," IIIIINNNIIIII "},
        {"      I~I      ","  EEEEEEEEEEE  "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE "," EEEEEEEEEEEEE ","  EEEEEEEEEEE  ","      NNN      "},
        {" IIIIIIIIIIIII ","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII","IIIIIIIIIIIIIII"," IIIIINNNIIIII "}
    };

    @Override
    public IStructureDefinition<EOHB_HighDensityEnergyFluidGenerator> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_HighDensityEnergyFluidGenerator>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    Casings.ShieldedAcceleratorCasing.asElement()
                )
                .addElement(
                    'B',
                    Casings.FieldRestrictionCasing.asElement()
                )
                .addElement(
                    'C',
                    Casings.FieldRestrictionGlass.asElement()
                )
                .addElement(
                    'D',
                    ofBlock(sBlockCasings10, 1)
                )
                .addElement(
                    'E',
                    ofBlock(sBlockCasings10, 3)
                )
                .addElement(
                    'F',
                    ofBlock(sBlockCasings13, 2)
                )
                .addElement(
                    'G',
                    ofBlock(sBlockCasings13, 3)
                )
                .addElement(
                    'H',
                    ofBlock(sBlockCasings3, 12)
                )
                .addElement(
                    'I',
                    ofBlock(sBlockCasings8, 7)
                )
                .addElement(
                    'J',
                    ofBlock(sBlockFrames, 405)
                )
                .addElement(
                    'K',
                    ofBlock(LanthItemList.SHIELDED_ACCELERATOR_GLASS, 0)
                )
                .addElement(
                    'L',
                    buildHatchAdder(EOHB_HighDensityEnergyFluidGenerator.class)
                        .atLeast(InputBus, InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .addElement(
                    'M',
                    buildHatchAdder(EOHB_HighDensityEnergyFluidGenerator.class)
                        .anyOf(LaserSource)
                        .adder(EOHB_HighDensityEnergyFluidGenerator::addLaserSource)
                        .casingIndex(CASING_INDEX)
                        .hint(3)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .addElement(
                    'N',
                    buildHatchAdder(EOHB_HighDensityEnergyFluidGenerator.class)
                        .atLeast(OutputBus, OutputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(2)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    /*@Override
    public IStructureDefinition<EOHB_HighDensityEnergyFluidGenerator> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_HighDensityEnergyFluidGenerator>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    buildHatchAdder(EOHB_HighDensityEnergyFluidGenerator.class)
                        .atLeast(InputBus,OutputBus,InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings8, 7)
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }*/

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_HighDensityEnergyFluidGenerator_MachineType)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_00)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_01)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_02)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_03)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_04)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_05)
            .addInfo(Tooltip_HighDensityEnergyFluidGenerator_06)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputBus(add_InputBus)
            .addOutputBus(add_OutputBus)
            .addInputHatch(add_inputHatch)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_HighDensityEnergyFluidGenerator(this.mName);
    }

    private boolean addLaserSource(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;

        IMetaTileEntity meta = aTileEntity.getMetaTileEntity();
        if (!(meta instanceof MTEHatchDynamoTunnel source)) {
            return false;
        }

        if (this.laserSource == null) {
            this.laserSource = source;
            this.laserSource.updateTexture(aBaseCasingIndex);
        }

        this.laserSourceCount++;

        int maxAmps = (int) source.maxAmperesOut();
        this.laserTotalAmps += maxAmps;

        this.laserAmps = Math.max(1, (int) Math.cbrt(this.laserTotalAmps));

        int tier = (int) source.getOutputTier();
        this.laserTier = Math.max(this.laserTier, tier);

        return true;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
    }
}
