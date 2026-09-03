package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.misc.OverclockType;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvRecipeFlags;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentType;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
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
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_ForgeOfTheSky extends OrundumWirelessMultiMachineBase<EOHB_ForgeOfTheSky>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_ForgeOfTheSky> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainForgeOfTheSky";
    private static final int OffsetsX = 6;
    private static final int OffsetsY = 23;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX1 = 16;

    public EOHB_ForgeOfTheSky(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_ForgeOfTheSky(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 200;
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {
        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);

        if (!ok) return;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
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
        return RecipeMaps.ForgeOfTheSky;
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
        {"             ","             ","     A A     ","     A A     ","    AAAAA    ","  AAAAAAAAA  ","    AA AA    ","  AAAAAAAAA  ","    AAAAA    ","     A A     ","     A A     ","             ","             "},
        {"             ","             ","             ","             ","     BBB     ","    BBBBB    ","    BBBBB    ","    BBBBB    ","     BBB     ","             ","             ","             ","             "},
        {"             ","             ","             ","             ","     BBB     ","    BBBBB    ","    BBBBB    ","    BBBBB    ","     BBB     ","             ","             ","             ","             "},
        {"             ","             ","             ","             ","     BBB     ","    BBBBB    ","    BBBBB    ","    BBBBB    ","     BBB     ","             ","             ","             ","             "},
        {"             ","             ","             ","             ","     BBB     ","    BBBBB    ","    BBBBB    ","    BBBBB    ","     BBB     ","             ","             ","             ","             "},
        {"             ","             ","             ","             ","     BBB     ","    BBBBB    ","    BBBBB    ","    BBBBB    ","     BBB     ","             ","             ","             ","             "},
        {"             ","             ","             ","     BBB     ","    BBBBB    ","   BBBBBBB   ","   BBBBBBB   ","   BBBBBBB   ","    BBBBB    ","     BBB     ","             ","             ","             "},
        {"             ","             ","    BBBBB    ","   BBBBBBB   ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","   BBBBBBB   ","    BBBBB    ","             ","             "},
        {"             ","             ","    BBBBB    ","   BBBBBBB   ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","   BBBBBBB   ","    BBBBB    ","             ","             "},
        {"             ","   BBBBBBB   ","  BBBBBBBBB  "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB ","  BBBBBBBBB  ","   BBBBBBB   ","             "},
        {"             ","   BBBBBBB   ","  BBBBBBBBB  "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB "," BBBBBBBBBBB ","  BBBBBBBBB  ","   BBBBBBB   ","             "},
        {"             ","   BBBBBBB   ","  BBAAAAABB  "," BBAA   AABB "," BAA     AAB "," BA  BBB  AB "," BA  BBB  AB "," BA  BBB  AB "," BAA     AAB "," BBAA   AABB ","  BBAAAAABB  ","   BBBBBBB   ","             "},
        {"             ","             ","    AAAAA    ","   AA   AA   ","  AA     AA  "," BA       AB "," BA       AB "," BA       AB ","  AA     AA  ","   AA   AA   ","    AAAAA    ","             ","             "},
        {"             ","             ","             ","             ","             ","             ","             ","             ","             ","             ","             ","             ","             "},
        {"             ","             ","             ","             ","             ","             ","      D      ","             ","             ","             ","             ","             ","             "},
        {"             ","             ","             ","             ","             ","             ","             ","             ","             ","             ","             ","             ","             "},
        {"             ","             ","    AAAAA    ","   AA   AA   ","  AA     AA  "," BA       AB "," BA       AB "," BA       AB ","  AA     AA  ","   AA   AA   ","    AAAAA    ","             ","             "},
        {"             ","   BBBBBBB   ","  BBAAAAABB  "," BBAA   AABB "," BAA     AAB "," BA  BBB  AB "," BA  BBB  AB "," BA  BBB  AB "," BAA     AAB "," BBAA   AABB ","  BBAAAAABB  ","   BBBBBBB   ","             "},
        {"             ","             ","    BBBBB    ","   BBBBBBB   ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","   BBBBBBB   ","    BBBBB    ","             ","             "},
        {"             ","             ","    BBBBB    ","   BBBBBBB   ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","  BBBBBBBBB  ","   BBBBBBB   ","    BBBBB    ","             ","             "},
        {"             ","             ","      A      ","     AAA     ","      A      ","     BBB     ","   A BBB A   ","     BBB     ","      A      ","     AAA     ","      A      ","             ","             "},
        {"             ","             ","      A      ","     AAA     ","      A      ","             ","   A     A   ","             ","      A      ","     AAA     ","      A      ","             ","             "},
        {"             ","             ","      A      ","    AAAAA    ","   A  A  A   ","  A       A  ","  AA     AA  ","  A       A  ","   A  A  A   ","    AAAAA    ","      A      ","             ","             "},
        {" EEEEE~EEEEE ","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE","EEEEEEEEEEEEE"," EEEEEEEEEEE "}
    };

    @Override
    public IStructureDefinition<EOHB_ForgeOfTheSky> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_ForgeOfTheSky>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings2, 0))
                .addElement('B', ofBlock(sBlockCasings8, 7))
                .addElement('D', ofBlock(GTCMItemList.ForgeOfTheSkyCore.getBlock(), 0))
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_ForgeOfTheSky.class)
                        .atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                        .casingIndex(CASING_INDEX1)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings2, 0
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_ForgeOfTheSky_MachineType)
            .addInfo(Tooltip_ForgeOfTheSky_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_ForgeOfTheSky_00)
            .addInfo(Tooltip_ForgeOfTheSky_01)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addInputHatch("1+", EOHB_MachineType_1)
            .addOutputBus("1+", EOHB_MachineType_1)
            .addOutputHatch("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_ForgeOfTheSky(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive)
                return new ITexture[]{
                    Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1),
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
                Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1),
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
        return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1)};
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
