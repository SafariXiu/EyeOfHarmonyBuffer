package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.client.ExternalBlockTextures;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Objects;

import static com.EyeOfHarmonyBuffer.client.ExternalBlockTextures.HEMPCRETE_META12_INDEX;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Mods.Chisel;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_MonkeyShit extends WirelessEnergyMultiMachineBase<EOHB_MonkeyShit> {

    private static IStructureDefinition<EOHB_MonkeyShit> STRUCTURE_DEFINITION = null;
    protected static final String STRUCTURE_PIECE_MAIN = "mainMonkeyShit";
    private static final int OffsetsX = 11;
    private static final int OffsetsY = 17;
    private static final int OffsetsZ = 0;

    public EOHB_MonkeyShit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_MonkeyShit(String mName) {
        super(mName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_MonkeyShit(this.mName);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 0;
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
        return 0;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.MonkeyShit;
    }

    @Override
    protected ProcessingLogic createProcessingLogic(){
        return new ProcessingLogic(){
            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                return new OverclockCalculator()
                    .setParallel(Integer.MAX_VALUE);
            }

            @NotNull
            @Override
            protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
                return new ParallelHelper()
                    .setRecipe(recipe)
                    .setItemInputs(inputItems)
                    .setFluidInputs(inputFluids)
                    .setAvailableEUt(Integer.MAX_VALUE)
                    .setMachine(machine, protectItems, protectFluids)
                    .setMaxParallel(Integer.MAX_VALUE)
                    .setEUtModifier(0.0)
                    .enableBatchMode(batchSize)
                    .setConsumption(true)
                    .setOutputCalculation(true);
            }

            @Override
            protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                               @Nonnull OverclockCalculator calculator) {
                return 10;
            }
        }.setMaxParallel(Integer.MAX_VALUE);
    }

    protected static final String[][] shapeMain = new String[][]{
        {"                       ","          BB           ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"                       ","          BBB          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"                       ","         BBBBB         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"                       ","         BBBBB         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"                       ","       BBBBBBBBB       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"                       ","       BBBBBBBBB       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"                       ","      BBBBBBBBBBB      ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"       BBB   BBB       ","     BBBBBBBBBBBBB     ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"       ACA   ACA       ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"       ACA   ACA       ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
        {"       ACA   ACA       ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","  B                 B  "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B ","  B                 B  ","  B                 B  ","  B                 B  ","   B               B   ","   BB             BB   ","     BBB       BBB     ","        BBBBBBB        ","                       "},
        {"      BBBBBBBBBBB      ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","  B                 B  "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B ","  B                 B  ","  B                 B  ","  B                 B  ","   B               B   ","   BB             BB   ","     BBB       BBB     ","        BBBBBBB        ","                       "},
        {"      BBBAAAAABBB      ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","  B                 B  "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B ","  B                 B  ","  B                 B  ","  B                 B  ","   B               B   ","   BB             BB   ","     BBB       BBB     ","        BBBBBBB        ","                       "},
        {"      BBBBAAABBBB      ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","  B                 B  "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B ","  B                 B  ","  B                 B  ","  B                 B  ","   B               B   ","   BB             BB   ","     BBB       BBB     ","        BBBBBBB        ","                       "},
        {"      BBBBBBBBBBB      ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","  B                 B  "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B ","  B                 B  ","  B                 B  ","  B                 B  ","   B               B   ","   BB             BB   ","     BBB       BBB     ","        BBBBBBB        ","                       "},
        {"      BBBBBBBBBBB      ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","  B                 B  "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B ","  B                 B  ","  B                 B  ","  B                 B  ","   B               B   ","   BB             BB   ","     BBB       BBB     ","        BBBBBBB        ","                       "},
        {"        BBBBBBB        ","     BBBBBBBBBBBBB     ","   BB             BB   ","   B               B   ","  B                 B  ","  B                 B  ","  B                 B  "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B "," B                   B ","  B                 B  ","  B                 B  ","  B                 B  ","   B               B   ","   BB             BB   ","     BBB       BBB     ","        BBBBBBB        ","                       "},
        {"     BBBBBB~BBBBBB     ","   BBBBBBBBBBBBBBBBB   ","  BBBBBBBBBBBBBBBBBBB  ","  BBBBBBBBBBBBBBBBBBB  "," BBBBBBBBBBBBBBBBBBBBB "," BBBBBBBBBBBBBBBBBBBBB "," BBBBBBBBBBBBBBBBBBBBB ","BBBBBBBBBBBBBBBBBBBBBBB","BBBBBBBBBBBBBBBBBBBBBBB","BBBBBBBBBBBBBBBBBBBBBBB","BBBBBBBBBBBBBBBBBBBBBBB","BBBBBBBBBBBBBBBBBBBBBBB","BBBBBBBBBBBBBBBBBBBBBBB","BBBBBBBBBBBBBBBBBBBBBBB"," BBBBBBBBBBBBBBBBBBBBB "," BBBBBBBBBBBBBBBBBBBBB "," BBBBBBBBBBBBBBBBBBBBB ","  BBBBBBBBBBBBBBBBBBB  ","  BBBBBBBBBBBBBBBBBBB  ","   BBBBBBBBBBBBBBBBB   ","     BBBBBBBBBBBBB     ","        BBBBBBB        "}
    };

    @Override
    public IStructureDefinition<EOHB_MonkeyShit> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_MonkeyShit>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 0)
                )
                .addElement(
                    'B',
                    buildHatchAdder(EOHB_MonkeyShit.class)
                        .atLeast(InputHatch,InputBus,OutputHatch,OutputBus)
                        .casingIndex(HEMPCRETE_META12_INDEX)
                        .dot(1)
                        .buildAndChain(
                            ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 12)
                        )
                )
                .addElement(
                    'C',
                    ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 15)
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, OffsetsX, OffsetsY, OffsetsZ, elementBudget, env, false, true);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_MonkeyShit_MachineType)
            .addInfo(Tooltip_MonkeyShit_Controller)
            .addInfo(EOHB_Legendary_Machine_Project)
            .addInfo(Tooltip_MonkeyShit_00)
            .addInfo(Tooltip_MonkeyShit_01)
            .addInfo(Tooltip_MonkeyShit_02)
            .addInfo(Tooltip_MonkeyShit_03)
            .addInfo(Tooltip_MonkeyShit_04)
            .addInfo(Tooltip_MonkeyShit_05)
            .addSeparator()
            .addInputBus(add_InputBus)
            .addOutputBus(add_OutputBus)
            .addInputHatch(add_inputHatch)
            .addOutputHatch(add_outputHatch)
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)

            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    @SuppressWarnings("ALL")
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture base = ExternalBlockTextures.getCasingFromIndex(
            HEMPCRETE_META12_INDEX
        );

        if (base == null) {
            base = Textures.BlockIcons.casingTexturePages[0][12];
        }

        if (side == aFacing) {
            if (aActive) {
                return new ITexture[] {
                    base,
                    TextureFactory.builder()
                        .addIcon(OVERLAY_DTPF_ON)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FUSION1_GLOW)
                        .extFacing()
                        .glow()
                        .build()
                };
            }

            return new ITexture[] {
                base,
                TextureFactory.builder()
                    .addIcon(OVERLAY_DTPF_OFF)
                    .extFacing()
                    .build()
            };
        }

        return new ITexture[] { base };
    }
}
