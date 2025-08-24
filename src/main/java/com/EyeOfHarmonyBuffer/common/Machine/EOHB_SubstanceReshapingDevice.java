package com.EyeOfHarmonyBuffer.common.Machine;

import bartworks.common.loaders.ItemRegistry;
import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import gregtech.api.enums.TAE;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import static com.EyeOfHarmonyBuffer.common.Block.BasicBlocks.SingularityStabilizationRingCasingsUpgrade;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.WriteOnceOnly.isSubstanceReshapingDeviceEnabled;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static goodgenerator.loader.Loaders.FRF_Coil_3;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_OFF;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static tectech.thing.casing.TTCasingsContainer.sBlockCasingsTT;

public class EOHB_SubstanceReshapingDevice extends WirelessEnergyMultiMachineBase<EOHB_SubstanceReshapingDevice> {

    public EOHB_SubstanceReshapingDevice(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_SubstanceReshapingDevice(String aName) {
        super(aName);
    }

    protected static final String STRUCTURE_PIECE_MAIN = "mainSubstanceReshapingDevice";
    protected static IStructureDefinition<EOHB_SubstanceReshapingDevice> STRUCTURE_DEFINITION = null;
    private int totalSpeedIncrement = 0;
    protected static final int DIM_INJECTION_CASING = 13;

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_SubstanceReshapingDevice(this.mName);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        if(isSubstanceReshapingDeviceEnabled()){
            return checkPiece(STRUCTURE_PIECE_MAIN, 4, 4, 0);
        }
        return checkPiece(STRUCTURE_PIECE_MAIN, 19, 19, 0);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        if(isSubstanceReshapingDeviceEnabled()){
            buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 4, 4, 0);
        } else {
            buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 19, 19, 0);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (this.mMachine) return -1;
        if(isSubstanceReshapingDeviceEnabled()){
            return this.survivalBuildPiece(
                STRUCTURE_PIECE_MAIN,
                stackSize,
                4,
                4,
                0,
                elementBudget,
                env,
                false,
                true);
        }
        return this.survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            19,
            19,
            0,
            elementBudget,
            env,
            false,
            true);
    }

    protected static final String[][] shapeMain = new String[][]{
        {"         ","   BCB   ","   BCB   "," BBCCCBB "," CCCACCC "," BBCCCBB ","   BCB   ","   BCB   ","         "},
        {"   BCB   "," AA   AA "," AA   AA ","B       B","C       C","B       B"," AA   AA "," AA   AA ","   BCB   "},
        {"   BCB   "," AA   AA "," A     A ","B       B","C       C","B       B"," A     A "," AA   AA ","   BCB   "},
        {" BBCCCBB ","B       B","B       B","C       C","C       C","C       C","B       B","B       B"," BBCCCBB "},
        {" CCC~CCC ","CB     BC","C       C","C       C","A       A","C       C","C       C","CB     BC"," CCCCCCC "},
        {" BBCCCBB ","B       B","B       B","C       C","C       C","C       C","B       B","B       B"," BBCCCBB "},
        {"   BCB   "," AA   AA "," A     A ","B       B","C       C","B       B"," A     A "," AA   AA ","   BCB   "},
        {"   BCB   "," AA   AA "," AA   AA ","B       B","C       C","B       B"," AA   AA "," AA   AA ","   BCB   "},
        {"         ","   BCB   ","   BCB   "," BBCCCBB "," CCCACCC "," BBCCCBB ","   BCB   ","   BCB   ","         "}
    };

    protected static final String[][] shapeMain2 = new String[][]{
        {"                                       ","                 DDDDD                 ","                DDEEEDD                ","             DDDDEEDEEDDDD             ","             EEEDEDGDEDEEE             ","             DDDDEEDEEDDDD             ","                DDEEEDD                ","                 DDDDD                 ","                  DED                  ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","          DDD      A      DDD          ","          EEE   C AFA C   EEE          ","          DDD      A      DDD          ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                  DED                  ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","        DD         A         DD        ","        EE      C AFA C      EE        ","        DD         A         DD        ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                    C                  ","      DD         C A           DD      ","      EE          AGA          EE      ","      DD           A C         DD      ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                    C                  ","     D           C A             D     ","     E            AFA            E     ","     D             A C           D     ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                  C                    ","    D              A C            D    ","    E             AFA             E    ","    D            C A              D    ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                  C                    ","   D               A C             D   ","   E              AGA              E   ","   D             C A               D   ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","   D               A               D   ","   E            C AFA C            E   ","   D               A               D   ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","  D                A                D  ","  E             C AFA C             E  ","  D                A                D  ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       "},
        {"                                       ","                                       ","                    C                  ","  D              C A                D  ","  E               AGA               E  ","  D                A C              D  ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       "},
        {"                                       ","                                       ","                    C                  "," D               C A                 D "," E                AFA                E "," D                 A C               D ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       "},
        {"                                       ","                                       ","                  C                    "," D                 A C               D "," E                AFA                E "," D               C A                 D ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       "},
        {"                                       ","                                       ","                  C                    "," D                 A C               D "," E                AGA                E "," D               C A                 D ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       "},
        {"                                       ","                   C                   ","                                       ","D                  A                  D","E               C AFA C               E","D                  A                  D","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  "},
        {"                                       ","                   C                   ","                                       ","D                  A                  D","E               C AFA C               E","D                  A                  D","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  "},
        {"                                       ","                  DED                  ","                  DED                  ","D               DDEEEDD               D","E               EEEAEEE               E","D               DDEEEDD               D","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  "},
        {"                  DED                  ","                AA   AA                ","D               AA   AA               D","D              D       D              D","DCC    CC    CCE   F   ECC    CC    CCD","D              D       D              D","D               AA   AA               D","                AA   AA                ","                  DED                  ","                   D                   ","                   D                   ","                  DDD                  ","                   D                   ","                   D                   ","                   D                   ","                  DDD                  ","                   D                   ","                   D                   ","                   D                   ","                  DDD                  ","                   D                   ","                   D                   ","                   D                   ","                 DDDDD                 "},
        {"                  DED                  ","D               AA   AA               D","D               A     A               D","E    CC    CC  D       D    CC    CC  E","E              E   F   E              E","E  CC    CC    D       D  CC    CC    E","D               A     A               D","D               AA   AA               D","                  DED                  ","                                       ","                                       ","                 D   D                 ","                 EBBBE                 ","                 EBBBE                 ","                 EBBBE                 ","                 DEEED                 ","                 EBBBE                 ","                 EBBBE                 ","                 EBBBE                 ","                 DEEED                 ","                 EBBBE                 ","                 EBBBE                 ","                 EBBBE                 ","                DDEEEDD                "},
        {"                DDEEEDD                ","D              D       D              D","E  CC    CC    D       D  CC    CC    E","E              E       E              E","DAAAAAAAAAAAAAAE       EAAAAAAAAAAAAAAD","E              E       E              E","E    CC    CC  D       D    CC    CC  E","D              D       D              D","                DDEEEDD                ","                                       ","                                       ","                D     D                ","                 B   B                 ","                 B   B                 ","                 B   B                 ","                DE   ED                ","                 B   B                 ","                 B   B                 ","                 B   B                 ","                DE   ED                ","                 B   B                 ","                 B   B                 ","                 B   B                 ","                DEEDEED                "},
        {"                EEE~EEE                ","DCC    CC    CCED  F  DECC    CC    CCD","E              E   F   E              E","DAAAAAAAAAAAAAAE       EAAAAAAAAAAAAAAD","GFFGFFGFFGFFGFFAFF G FFAFFGFFGFFGFFGFFG","DAAAAAAAAAAAAAAE       EAAAAAAAAAAAAAAD","E              E   F   E              E","DCC    CC    CCED  F  DECC    CC    CCD","                EEEEEEE                ","                D     D                ","                D  G  D                ","                D  G  D                ","                DB F BD                ","                DB F BD                ","                DB F BD                ","                DE G ED                ","                DB F BD                ","                DB F BD                ","                DB F BD                ","                DE G ED                ","                DB F BD                ","                DB F BD                ","                DB F BD                ","                DEDGDED                "},
        {"                DDEEEDD                ","D              D       D              D","E    CC    CC  D       D    CC    CC  E","E              E       E              E","DAAAAAAAAAAAAAAE       EAAAAAAAAAAAAAAD","E              E       E              E","E  CC    CC    D       D  CC    CC    E","D              D       D              D","                DDEEEDD                ","                                       ","                                       ","                D     D                ","                 B   B                 ","                 B   B                 ","                 B   B                 ","                DE   ED                ","                 B   B                 ","                 B   B                 ","                 B   B                 ","                DE   ED                ","                 B   B                 ","                 B   B                 ","                 B   B                 ","                DEEDEED                "},
        {"                  DED                  ","D               AA    A               D","D               A     A               D","E  CC    CC    D       D  CC    CC    E","E              E   F   E              E","E    CC    CC  D       D    CC    CC  E","D               A     A               D","D               AA   AA               D","                  DED                  ","                                       ","                                       ","                 D   D                 ","                 EBBBE                 ","                 EBBBE                 ","                 EBBBE                 ","                 DEEED                 ","                 EBBBE                 ","                 EBBBE                 ","                 EBBBE                 ","                 DEEED                 ","                 EBBBE                 ","                 EBBBE                 ","                 EBBBE                 ","                DDEEEDD                "},
        {"                  DED                  ","                AA   AA                ","D               AA   AA               D","D              D       D              D","DCC    CC    CCE   F   ECC    CC    CCD","D              D       D              D","D               AA   AA               D","                AA   AA                ","                  DED                  ","                   D                   ","                   D                   ","                  DDD                  ","                   D                   ","                   D                   ","                   D                   ","                  DDD                  ","                   D                   ","                   D                   ","                   D                   ","                  DDD                  ","                   D                   ","                   D                   ","                   D                   ","                 DDDDD                 "},
        {"                                       ","                  DED                  ","                  DED                  ","D               DDEEEDD               D","E               EEEAEEE               E","D               DDEEEDD               D","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  "},
        {"                                       ","                   C                   ","                                       ","D                  A                  D","E               C AFA C               E","D                  A                  D","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  "},
        {"                                       ","                   C                   ","                                       ","D                  A                  D","E               C AFA C               E","D                  A                  D","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  "},
        {"                                       ","                                       ","                    C                  "," D               C A                 D "," E                AGA                E "," D                 A C               D ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       "},
        {"                                       ","                                       ","                    C                  "," D               C A                 D "," E                AFA                E "," D                 A C               D ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       "},
        {"                                       ","                                       ","                  C                    "," D                 A C               D "," E                AFA                E "," D               C A                 D ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       "},
        {"                                       ","                                       ","                  C                    ","  D                A C              D  ","  E               AGA               E  ","  D              C A                D  ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","  D                A                D  ","  E             C AFA C             E  ","  D                A                D  ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","   D               A               D   ","   E            C AFA C            E   ","   D               A               D   ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                    C                  ","   D             C A               D   ","   E              AGA              E   ","   D               A C             D   ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                    C                  ","    D            C A              D    ","    E             AFA             E    ","    D              A C            D    ","                  C                    ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                  C                    ","     D             A C           D     ","     E            AFA            E     ","     D           C A             D     ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                                       ","                  C                    ","      DD           A C         DD      ","      EE          AGA          EE      ","      DD         C A           DD      ","                    C                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","        DD         A         DD        ","        EE      C AFA C      EE        ","        DD         A         DD        ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                   C                   ","                                       ","          DDD      A      DDD          ","          EEE   C AFA C   EEE          ","          DDD      A      DDD          ","                                       ","                   C                   ","                                       ","                                       ","                                       ","                  DED                  ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "},
        {"                                       ","                 DDDDD                 ","                DDEEEDD                ","             DDDDEEDEEDDDD             ","             EEEDEDGDEDEEE             ","             DDDDEEDEEDDDD             ","                DDEEEDD                ","                 DDDDD                 ","                  DED                  ","                  DED                  ","                  DED                  ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       ","                                       "}
    };

    @Override
    public IStructureDefinition<EOHB_SubstanceReshapingDevice> getStructureDefinition() {
        if (!isSubstanceReshapingDeviceEnabled()) {
            if(STRUCTURE_DEFINITION == null) {
                STRUCTURE_DEFINITION = IStructureDefinition.<EOHB_SubstanceReshapingDevice>builder()
                    .addShape(STRUCTURE_PIECE_MAIN,transpose(shapeMain2))
                    .addElement(
                        'A',
                        ofBlock(ItemRegistry.bw_realglas2,0)
                    )
                    .addElement(
                        'B',
                        ofBlock(FRF_Coil_3,0)
                    )
                    .addElement(
                        'C',
                        withChannel(
                            "SingularityStabilizationRingCasings",
                            ofBlocksTiered(
                                EOHB_SubstanceReshapingDevice::getSingularityStabilizationRingCasingsLevel,
                                ImmutableList.of(
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 0),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 1),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 2),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 3),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 4),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 5),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 6),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 7),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 8),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 9),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 10),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 11),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 12),
                                    Pair.of(SingularityStabilizationRingCasingsUpgrade, 13)
                                ),
                                0,
                                (m, t) -> m.totalSpeedIncrement = t,
                                m -> m.totalSpeedIncrement)))
                    .addElement(
                        'D',
                        ofBlock(sBlockCasings1,12)
                    )
                    .addElement(
                        'E',
                        buildHatchAdder(EOHB_SubstanceReshapingDevice.class)
                            .atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                            .casingIndex(DIM_INJECTION_CASING)
                            .dot(1)
                            .buildAndChain(
                                ofBlock(sBlockCasings1, 13)
                            )
                    )
                    .addElement(
                        'F',
                        ofBlock(sBlockCasings1,14)
                    )
                    .addElement(
                        'G',
                        ofBlock(sBlockCasingsTT,9)
                    )
                    .build();
            }
            return STRUCTURE_DEFINITION;
        }
        if(isSubstanceReshapingDeviceEnabled()){
            if(STRUCTURE_DEFINITION == null) {
                STRUCTURE_DEFINITION = IStructureDefinition.<EOHB_SubstanceReshapingDevice>builder()
                    .addShape(STRUCTURE_PIECE_MAIN,transpose(shapeMain))
                    .addElement('A',
                        ofBlock(ItemRegistry.bw_realglas, 2)
                    )
                    .addElement(
                        'B',
                        ofBlock(sBlockCasings4,0)
                    )
                    .addElement(
                        'C',
                        buildHatchAdder(EOHB_SubstanceReshapingDevice.class)
                            .atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                            .casingIndex(getCasingTextureIndex())
                            .dot(1)
                            .buildAndChain(
                                ofBlock(ModBlocks.blockCasings2Misc,4)
                            )
                    )
                    .build();
            }
            return STRUCTURE_DEFINITION;
        }
        return null;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_SubstanceReshapingDevice_MachineType)
            .addInfo(Tooltip_SubstanceReshapingDevice_Controller)
            .addInfo(EOHB_Starry_Miracle_Project)
            .addInfo(EOHB_Text_SeparatingLine)
            .addInfo(Tooltip_SubstanceReshapingDevice_00)
            .addInfo(Tooltip_SubstanceReshapingDevice_01)
            .addInfo(Tooltip_SubstanceReshapingDevice_02)
            .addInfo(Tooltip_SubstanceReshapingDevice_03)
            .addInfo(Tooltip_SubstanceReshapingDevice_04)
            .addInfo(Tooltip_SubstanceReshapingDevice_05)
            .addInfo(EOHB_Text_SeparatingLine)
            .addInfo(Tooltip_SubstanceReshapingDevice_06)
            .addInfo(Tooltip_SubstanceReshapingDevice_07)
            .addInfo(Tooltip_SubstanceReshapingDevice_08)
            .addInfo(EOHB_Text_SeparatingLine)
            .addInfo(Tooltip_SubstanceReshapingDevice_09)
            .addInfo(Tooltip_SubstanceReshapingDevice_10)
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

    private static int getSingularityStabilizationRingCasingsLevel(Block block,int meta){
        if(!isSubstanceReshapingDeviceEnabled()){
            if(block == SingularityStabilizationRingCasingsUpgrade) return meta + 1;
            return -1;
        }
        return Integer.MAX_VALUE;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.SubstanceReshapingDevice;
    }

    @Override
    protected ProcessingLogic createProcessingLogic(){
        return new ProcessingLogic(){
            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe){
                if (recipe.mSpecialValue <= totalSpeedIncrement){
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                } else
                    return SimpleCheckRecipeResult.ofFailure("SingularityStabilizationRingCasingsLive");
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
        return 1;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 64;
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

    public byte getCasingTextureIndex() {
        return (byte) TAE.GTPP_INDEX(11);
    }
}
