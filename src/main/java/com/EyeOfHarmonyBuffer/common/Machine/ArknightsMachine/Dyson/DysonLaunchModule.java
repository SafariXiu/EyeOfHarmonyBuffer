package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.DysonTeamProgress;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 发射模块：吃戴森云组件/框架组件 → 本队计数器 +1。
 * 每次发射 10,000（EU 等价，可配置）全额计入 Orundum 账本；算力需求 100,000；完工后永久失效。
 */
public class DysonLaunchModule extends DysonModuleBase<DysonLaunchModule>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<DysonLaunchModule> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainDysonLaunch";
    private static final int OffsetsX = 1;
    private static final int OffsetsY = 1;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 183;

    public DysonLaunchModule(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public DysonLaunchModule(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public ModuleType getModuleType() {
        return ModuleType.LAUNCH;
    }

    @Override
    public BigInteger getRequiredCompute() {
        return BigInteger.valueOf(DysonMachineConfig.launchCompute);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return DysonMachineConfig.TICKS_PER_SETTLEMENT;
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!canOperate()) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            this.mOutputItems = null;
            this.mOutputFluids = null;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        World world = base.getWorld();
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data != null && data.isCompleted()) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return SimpleCheckRecipeResult.ofFailure("DysonSphereLocked");
        }

        DysonTeamProgress team = getTeamProgress(world);
        if (team == null) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int batch = DysonMachineConfig.launchBatch;
        int clouds = (int) Math.min(batch, team.cloudComponents);
        int frames = (int) Math.min(batch - clouds, team.frameComponents);

        if (clouds + frames <= 0) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        if (!DysonSphereSystem.consumeComponents(world, getTeamId(), clouds, frames)) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return SimpleCheckRecipeResult.ofFailure("DysonComponentsUnavailable");
        }

        boolean accepted = DysonSphereSystem.addModules(
            world,
            getTeamId(),
            base.getOwnerName(),
            clouds,
            frames);
        if (!accepted) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return SimpleCheckRecipeResult.ofFailure("DysonSphereLocked");
        }

        pendingCost = BigInteger.valueOf(DysonMachineConfig.launchCostEU)
            .multiply(BigInteger.valueOf(clouds + frames));
        this.lastUsedParallel = clouds + frames;
        mMaxProgresstime = getWirelessModeProcessingTime();
        mProgresstime = 0;
        mEUt = 0;
        mEfficiency = 10000;
        mEfficiencyIncrease = 0;
        mOutputItems = null;
        mOutputFluids = null;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static final String[][] shapeMain = new String[][] {
        { "AAA", "AAA", "AAA" },
        { "A~A", "A A", "AAA" },
        { "AAA", "AAA", "AAA" }
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
    public IStructureDefinition<DysonLaunchModule> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<DysonLaunchModule>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings8, 7))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("戴森发射模块")
            .addInfo("从队伍组件库存发射（1 组件 = 1 计数）")
            .addInfo("消耗 100,000 算力")
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new DysonLaunchModule(this.mName);
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
