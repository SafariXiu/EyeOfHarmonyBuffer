package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_GasExtractor extends OrundumWirelessMultiMachineBase<EOHB_GasExtractor>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_GasExtractor> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainGasExtractor";
    private static final int OffsetsX = 3;
    private static final int OffsetsY = 20;
    private static final int OffsetsZ = 1;
    private static final int CASING_INDEX = 16;

    private enum GasSource {
        INERGEN(GTCMItemList.DuoQiMainBlock, EOHBMaterialPool.Inergen.getFluidOrGas(1).getFluid(), 6000),
        XIRANGQI(GTCMItemList.XiRangQiMainBlock, EOHBMaterialPool.Xiragen.getFluidOrGas(1).getFluid(), 6000);

        private final GTCMItemList mainBlockEntry;
        private final Fluid outputFluid;
        private final int fluidAmount;

        GasSource(GTCMItemList mainBlockEntry, Fluid outputFluid, int fluidAmount) {
            this.mainBlockEntry = mainBlockEntry;
            this.outputFluid = outputFluid;
            this.fluidAmount = fluidAmount;
        }

        public Block getMainBlock() {
            return mainBlockEntry.getBlock();
        }

        public FluidStack getFluidStack() {
            return new FluidStack(outputFluid, fluidAmount);
        }

        @Nullable
        public static GasSource fromMainBlock(Block block) {
            if (block == null) return null;
            for (GasSource type : values()) {
                if (type.getMainBlock() == block) {
                    return type;
                }
            }
            return null;
        }
    }

    public EOHB_GasExtractor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_GasExtractor(String aName) {
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

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        GasSource gasType = scanMainGasSourceAndCleanOthers();
        if (gasType == null) {
            return SimpleCheckRecipeResult.ofFailure("NoMainVeinBlock");
        }

        int duration;
        switch (gasType) {
            case INERGEN:
            case XIRANGQI:
                duration = 200;
                break;
            default:
                return SimpleCheckRecipeResult.ofFailure("UnsupportedMainVeinBlock");
        }

        FluidStack out = gasType.getFluidStack();
        this.mOutputFluids = new FluidStack[]{out};

        this.mProgresstime = 0;
        this.mMaxProgresstime = duration;
        this.mEUt = 0;
        this.mEfficiency = 10000;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.GasExtractor;
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
        {"       ","  CBC  ","  CBC  ","  CCC  ","  CCC  ","       ","       "},
        {"  CBC  "," CCCCC "," C   C "," C   C "," C   C "," CCCCC ","       "},
        {"  CBC  "," CCCCC "," C   C "," C   C "," C   C "," CCCCC ","       "},
        {"  CBC  "," CCCCC "," C   C "," C   C "," C   C "," CCCCC ","       "},
        {"BBCBCBB"," CCCCC "," C   C "," C   C "," C   C "," CCCCC ","       "},
        {"B     B","B CBC B","  CCC  ","  C C  ","  CCC  ","       ","       "},
        {"       ","B     B","B AAA B","B A A B","B AAA B","BBCACBB","   A   "},
        {"       ","       ","  AAA  ","  A A  ","  AAA  ","  CCC  ","   A   "},
        {"       ","  CCC  "," CAAAC "," CA AC "," CAAAC ","  CCC  ","   A   "},
        {"       ","  CEC  "," CAAAC "," CA AC "," CAAAC ","  CCC  ","   A   "},
        {"       ","  CCC  ","  AAA  ","  A A  ","  AAA  ","   A   ","   A   "},
        {"       ","       ","  AAA  ","  A A  ","  AAA  ","       ","       "},
        {"       ","       ","  D D  ","   A   ","  D D  ","       ","       "},
        {"       ","       ","  D D  ","   A   ","  D D  ","       ","       "},
        {"       ","       ","  D D  ","   A   ","  D D  ","       ","       "},
        {"       "," D   D ","       ","   A   ","       "," D   D ","       "},
        {"       "," D   D ","       ","   A   ","       "," D   D ","       "},
        {"D     D","       ","  D D  ","   A   ","  D D  ","       ","D     D"},
        {"D     D","       ","  D D  ","   A   ","  D D  ","       ","D     D"},
        {"A     A"," AAAAA "," AAAAA "," AAAAA "," AAAAA "," AAAAA ","A     A"},
        {"B     B","  A~A  "," AAAAA "," AAAAA "," AAAAA ","  AAA  ","B     B"}
    };

    @Override
    public IStructureDefinition<EOHB_GasExtractor> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_GasExtractor>builder()
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
                    buildHatchAdder(EOHB_GasExtractor.class)
                        .atLeast(OutputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
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
        tt.addMachineType(Tooltip_GasExtractor_MachineType)
            .addInfo(Tooltip_GasExtractor_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_GasExtractor_00)
            .addInfo(Tooltip_GasExtractor_01)
            .addInfo(Tooltip_GasExtractor_02)
            .addInfo(Tooltip_GasExtractor_03)
            .addInfo(Tooltip_GasExtractor_04)
            .addInfo(Tooltip_GasExtractor_05)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addOutputHatch("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_GasExtractor(this.mName);
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

    @Nullable
    private GasSource scanMainGasSourceAndCleanOthers() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return null;

        World world = base.getWorld();
        if (world == null) return null;

        int cx = base.getXCoord();
        int cy = base.getYCoord();
        int cz = base.getZCoord();
        ForgeDirection front = base.getFrontFacing();
        ForgeDirection back = front.getOpposite();

        int bx = cx + back.offsetX;
        int bz = cz + back.offsetZ;

        GasSource firstType = null;

        for (int dy = 0; dy <= 3; dy++) {
            int y = cy - dy;
            if (y < 0 || y >= world.getHeight()) continue;

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int x = bx + dx;
                    int z = bz + dz;

                    Block block = world.getBlock(x, y, z);
                    if (block == null) continue;

                    GasSource thisType = GasSource.fromMainBlock(block);
                    if (thisType == null) continue;

                    if (firstType == null) {
                        firstType = thisType;
                    } else {
                        if (thisType != firstType) {
                            world.setBlockToAir(x, y, z);
                        }
                    }
                }
            }
        }
        return firstType;
    }
}
