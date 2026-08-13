package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
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
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 制造模块：原料 → 戴森云组件 / 框架组件。成本按配方 EUt×时长×并行，
 * 全额计入 Orundum 账本；算力需求 10,000。
 */
public class DysonManufacturingModule extends DysonModuleBase<DysonManufacturingModule>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<DysonManufacturingModule> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainDysonManufacturing";
    private static final int OffsetsX = 2;
    private static final int OffsetsY = 1;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 183;

    public DysonManufacturingModule(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public DysonManufacturingModule(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public ModuleType getModuleType() {
        return ModuleType.MANUFACTURING;
    }

    @Override
    public BigInteger getRequiredCompute() {
        return BigInteger.valueOf(DysonMachineConfig.manufacturingCompute);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        if (isUpgradeActive(DysonUpgrade.MANUFACTURING_EFFICIENCY_III)) {
            return DysonMachineConfig.manufacturingEfficiencyTicksIII;
        }
        if (isUpgradeActive(DysonUpgrade.MANUFACTURING_EFFICIENCY_II)) {
            return DysonMachineConfig.manufacturingEfficiencyTicksII;
        }
        if (isUpgradeActive(DysonUpgrade.MANUFACTURING_EFFICIENCY_I)) {
            return DysonMachineConfig.manufacturingEfficiencyTicksI;
        }
        return DysonMachineConfig.manufacturingTimeTicks;
    }

    @Override
    public int getMaxParallelRecipes() {
        if (isUpgradeActive(DysonUpgrade.MANUFACTURING_PARALLEL_III)) {
            return DysonMachineConfig.manufacturingParallelIII;
        }
        if (isUpgradeActive(DysonUpgrade.MANUFACTURING_PARALLEL_II)) {
            return DysonMachineConfig.manufacturingParallelII;
        }
        return DysonMachineConfig.manufacturingMaxParallel;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.DysonManufacturing;
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!canOperate()) {
            scheduleRecipeCheckImmediate();
            this.lastUsedParallel = 0;
            this.mOutputItems = null;
            this.mOutputFluids = null;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 制造成本与配方/并行相关，而配方检查本身会吞掉原料；
        // 这里先用输入总线里的原料数预测本批成本，付不起就提前失败，避免白吞原料。
        BigInteger predicted = predictBatchCost();
        if (predicted != null && predicted.signum() > 0) {
            if (ownerUUID == null
                || OrundumEnergyService.getOrundumForUser(ownerUUID).compareTo(predicted) < 0) {
                this.lastUsedParallel = 0;
                this.mOutputItems = null;
                this.mOutputFluids = null;
                return CheckRecipeResultRegistry.insufficientPower(safeToLong(predicted));
            }
        }

        return super.doWirelessBusinessOnce();
    }

    /** 根据输入总线里的源石/息壤数量预测本批 Orundum 成本；不足一轮返回 null。 */
    private BigInteger predictBatchCost() {
        int duration = getWirelessModeProcessingTime();
        if (duration <= 0) {
            return BigInteger.ZERO;
        }
        Item yshi = GTCMItemList.YuanShi.getItem();
        Item xrg = GTCMItemList.XiRang.getItem();
        long cloudInput = 0;
        long frameInput = 0;
        for (ItemStack stack : getStoredInputsWithoutDualInputHatch()) {
            if (stack == null) {
                continue;
            }
            if (stack.getItem() == yshi) {
                cloudInput += stack.stackSize;
            } else if (stack.getItem() == xrg) {
                frameInput += stack.stackSize;
            }
        }

        if (cloudInput >= 64) {
            long parallel = Math.min(getMaxParallelRecipes(), cloudInput / 64);
            return DysonMachineConfig.CLOUD_COMPONENT_ORUNDUM_PER_TICK
                .multiply(BigInteger.valueOf(duration))
                .multiply(BigInteger.valueOf(parallel));
        }
        if (frameInput >= 64) {
            long parallel = Math.min(getMaxParallelRecipes(), frameInput / 64);
            return DysonMachineConfig.FRAME_COMPONENT_ORUNDUM_PER_TICK
                .multiply(BigInteger.valueOf(duration))
                .multiply(BigInteger.valueOf(parallel));
        }
        return null;
    }

    @Override
    protected BigInteger getWirelessCost() {
        if (processingLogic == null) {
            return BigInteger.ZERO;
        }
        ItemStack[] outputs = processingLogic.getOutputItems();
        if (outputs == null || outputs.length == 0) {
            return BigInteger.ZERO;
        }
        Item frameItem = GTCMItemList.DysonFrameComponent.getItem();
        boolean frame = false;
        for (ItemStack stack : outputs) {
            if (stack != null && stack.getItem() == frameItem) {
                frame = true;
                break;
            }
        }
        // 成本走队伍 Orundum 账本：每 tick 基准 × 实际时长 × 并行（1:1 等价）
        BigInteger perTick = frame
            ? DysonMachineConfig.FRAME_COMPONENT_ORUNDUM_PER_TICK
            : DysonMachineConfig.CLOUD_COMPONENT_ORUNDUM_PER_TICK;
        int duration = getWirelessModeProcessingTime();
        if (duration <= 0) {
            return BigInteger.ZERO;
        }
        long parallel = Math.max(1, processingLogic.getCurrentParallels());
        return perTick.multiply(BigInteger.valueOf(duration)).multiply(BigInteger.valueOf(parallel));
    }

    @Override
    protected void collectWirelessOutputs() {
        mOutputItems = null;
        mOutputFluids = null;
        if (processingLogic == null) {
            return;
        }

        Item cloudItem = GTCMItemList.DysonCloudComponent.getItem();
        Item frameItem = GTCMItemList.DysonFrameComponent.getItem();
        int clouds = 0;
        int frames = 0;
        ItemStack[] outputs = processingLogic.getOutputItems();
        if (outputs != null) {
            for (ItemStack stack : outputs) {
                if (stack == null) {
                    continue;
                }
                if (stack.getItem() == cloudItem) {
                    clouds += stack.stackSize;
                } else if (stack.getItem() == frameItem) {
                    frames += stack.stackSize;
                }
            }
        }

        if (clouds > 0 || frames > 0) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            long extraClouds = 0;
            long extraFrames = 0;
            // 制造并行 III：20% 概率额外产出 100~200 × 配方单份产物
            if (isUpgradeActive(DysonUpgrade.MANUFACTURING_PARALLEL_III)
                && base != null
                && base.getWorld() != null
                && base.getWorld().rand.nextFloat() < DysonMachineConfig.manufacturingExtraChance) {
                int multiplier = DysonMachineConfig.manufacturingExtraMin
                    + base.getWorld()
                        .rand
                        .nextInt(
                            DysonMachineConfig.manufacturingExtraMax
                                - DysonMachineConfig.manufacturingExtraMin
                                + 1);
                if (clouds > 0) {
                    extraClouds = (long) multiplier * DysonMachineConfig.CLOUD_RECIPE_OUTPUT;
                } else {
                    extraFrames = (long) multiplier * DysonMachineConfig.FRAME_RECIPE_OUTPUT;
                }
            }
            DysonSphereSystem.addComponentsToPlayer(
                base.getWorld(),
                ownerUUID,
                base.getOwnerName(),
                clouds + extraClouds,
                frames + extraFrames);
        }
    }

    private static final String[][] shapeMain = new String[][] {
        { "BAAAB", "AAAAA", "AAAAA" },
        { "BA~AB", "AAAAA", "AAAAA" },
        { "BAAAB", "AAAAA", "AAAAA" }
    };

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
                             List<StructureError> errors) {
        checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) {
            return -1;
        }
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            OffsetsX,
            OffsetsY,
            OffsetsZ,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public IStructureDefinition<DysonManufacturingModule> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<DysonManufacturingModule>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings8, 7))
                .addElement(
                    'B',
                    buildHatchAdder(DysonManufacturingModule.class)
                        .atLeast(InputBus, InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(ofBlock(sBlockCasings8, 7)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_DysonManufacturingModule_MachineType)
            .addInfo(Tooltip_DysonManufacturingModule_00)
            .addInfo(Tooltip_DysonManufacturingModule_01)
            .addInfo(Tooltip_DysonManufacturingModule_02)
            .addInfo(Tooltip_DysonManufacturingModule_03)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addInputHatch("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new DysonManufacturingModule(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side,
                                 ForgeDirection facing, int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) {
                return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.builder()
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
