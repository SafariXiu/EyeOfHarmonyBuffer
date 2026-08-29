package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.misc.OverclockType;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvRecipeFlags;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentType;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.UpgradableOrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.processingLogics.GTCM_ProcessingLogic;
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
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_GasReactorGlobe extends UpgradableOrundumWirelessMultiMachineBase<EOHB_GasReactorGlobe>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_GasReactorGlobe> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainGasReactorGlobe";
    private static final int OffsetsX = 6;
    private static final int OffsetsY = 15;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    public EOHB_GasReactorGlobe(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_GasReactorGlobe(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {
        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);

        if (!ok) return;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {

        return new GTCM_ProcessingLogic() {

            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                GasEnvironmentType env = getCurrentEnvironment();

                int mask = recipe.mSpecialValue;

                if (GasEnvRecipeFlags.isEnvAllowed(mask, env)) {
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                } else {
                    return SimpleCheckRecipeResult.ofFailure("ForgeOfTheSky.EnvMismatch");
                }
            }

            @NotNull
            @Override
            public CheckRecipeResult process() {

                setEuModifier(getEuModifier());
                setSpeedBonus(getSpeedBonus());
                setOverclockType(
                    isEnablePerfectOverclock()
                        ? OverclockType.PerfectOverclock
                        : OverclockType.NormalOverclock
                );

                return super.process();
            }

            @Nonnull
            @Override
            protected OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                return wirelessMode
                    ? OverclockCalculator.ofNoOverclock(recipe)
                    : super.createOverclockCalculator(recipe);
            }

        }.setMaxParallelSupplier(this::getLimitedMaxParallel);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.GasReactorGlobe;
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
        {"             ","             ","             ","             ","             ","     CCC     ","    CCBCC    ","     CCC     ","             ","             ","             ","             ","             "},
        {"             ","             ","             ","             "," A         A "," A   BBB   A "," A   BBB   A "," A   BBB   A "," A         A ","             ","             ","             ","             "},
        {"             ","             ","             ","             "," CA  AAA  AC "," C AAAAAAA C "," DAAAAAAAAAD "," C AAAAAAA C "," CA  AAA  AC ","             ","             ","             ","             "},
        {"             ","             ","     BBB     ","     BBB     "," C   AAA   C "," C  AAAAA  C "," D  AAAAA  D "," C  AAAAA  C "," C   AAA   C ","     BBB     ","     BBB     ","             ","             "},
        {"             ","     BBB     ","     BBB     ","     CCC     "," C  CC CC  C "," CACC   CCAC "," D C     C D "," CACC   CCAC "," C  CC CC  C ","     CCC     ","     BBB     ","     BBB     ","             "},
        {"     EEE     ","     CCC     ","    CCCCC    ","   CC   CC   "," CCC     CCC "," CC       CC "," DC       CD "," CC       CC "," CCC     CCC ","   CC   CC   ","    CCCCC    ","     CCC     ","     FFF     "},
        {"     EEE     ","     CCC     ","    CCCCC    "," C CC   CC C "," CCC     CCC "," CC       CC "," DC       CD "," CC       CC "," CCC     CCC "," C CC   CC C ","    CCCCC    ","     CCC     ","     FFF     "},
        {"             ","             ","    CCCCC    "," C CC   CC C "," BCC     CCB "," CC       CC "," DC       CD "," CC       CC "," BCC     CCB "," C CC   CC C ","    CCCCC    ","             ","             "},
        {"             ","             ","    CCCCC    "," C CC   CC C "," CCC     CCC "," CC       CC "," DC       CD "," CC       CC "," CCC     CCC "," C CC   CC C ","    CCCCC    ","             ","             "},
        {"             ","             ","    CCCCC    ","   CC   CC   "," CCC     CCC "," CC       CC "," DC       CD "," CC       CC "," CCC     CCC ","   CC   CC   ","    CCCCC    ","             ","             "},
        {"             ","             ","             ","     AAA     "," C  AAAAA  C "," C AAAAAAA C "," D AAAAAAA D "," C AAAAAAA C "," C  AAAAA  C ","     AAA     ","             ","             ","             "},
        {"             ","             ","     CCC     ","   CCCCCC    "," C CCCCCCC C "," CCCCCCCCCCC "," DCCCCCCCCCD "," CCCCCCCCCCC "," C CCCCCCC C ","   CCCCCCC   ","     CCC     ","             ","             "},
        {"             ","             ","     CCC     "," C BCCCCCB C "," C CCCCCCC C "," CCCCCCCCCCC "," DCCCCCCCCCD "," CCCCCCCCCCC "," C CCCCCCC C "," C BCCCCCB C ","     CCC     ","             ","             "},
        {"             ","     CCC     "," C   AAA   C "," C BAAAAAB C "," C AAAAAAA C "," CAAAAAAAAAC "," DAAAAAAAAAD "," CAAAAAAAAAC "," C AAAAAAA C "," C BAAAAAB C "," C   AAA   C ","             ","             "},
        {"BAAAAAAAAAAAB","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","BAAAAAAAAAAAB"},
        {"BAAAAA~AAAAAB","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","AAAAAAAAAAAAA","BAAAAAAAAAAAB"}
    };

    @Override
    public IStructureDefinition<EOHB_GasReactorGlobe> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_GasReactorGlobe>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
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
                    ofBlock(sBlockCasings8,7)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockFrames, 305)
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_GasReactorGlobe.class)
                        .atLeast(InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .addElement(
                    'F',
                    buildHatchAdder(EOHB_GasReactorGlobe.class)
                        .atLeast(OutputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(2)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_GasReactorGlobe_MachineType)
            .addInfo(Tooltip_GasReactorGlobe_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_GasReactorGlobe_00)
            .addInfo(Tooltip_GasReactorGlobe_01)
            .addInfo(Tooltip_GasReactorGlobe_02)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputHatch("1+", EOHB_MachineType_1)
            .addOutputHatch("1+", EOHB_MachineType_2)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_GasReactorGlobe(this.mName);
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

    private GasEnvironmentType getCurrentEnvironment() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return GasEnvironmentType.NONE;

        return GasEnvironmentHelper.getEnvironmentAt(
            base.getWorld(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord()
        );
    }
}
