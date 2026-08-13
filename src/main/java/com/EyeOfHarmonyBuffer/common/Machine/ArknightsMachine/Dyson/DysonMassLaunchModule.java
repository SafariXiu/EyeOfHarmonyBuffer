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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.DysonTeamProgress;
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
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 全量发射模块：一轮 60 秒，一轮把机主名下的全部云/框架组件一次性发射，计入队伍公共计数。
 * 需队伍点亮“全量发射”大节点才可使用；完工后永久失效；算力需求 100,000。
 */
public class DysonMassLaunchModule extends DysonModuleBase<DysonMassLaunchModule>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<DysonMassLaunchModule> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainDysonMassLaunch";
    private static final int OffsetsX = 1;
    private static final int OffsetsY = 1;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 183;

    public DysonMassLaunchModule(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public DysonMassLaunchModule(String aName) {
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
        return DysonMachineConfig.massLaunchTimeTicks;
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!canOperate()) {
            scheduleRecipeCheckImmediate();
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            this.mOutputItems = null;
            this.mOutputFluids = null;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 使用权：队伍必须已点亮“全量发射”大节点
        if (!isUpgradeActive(DysonUpgrade.MASS_LAUNCH)) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return SimpleCheckRecipeResult.ofFailure("DysonMassLaunchLocked");
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

        // 组件是机主个人资产：一轮全部打上去（受队伍云/框架上限钳制，超出部分留在个人库存）
        long cloudStock = ownerUUID == null
            ? 0
            : DysonSphereSystem.getPlayerCloudComponents(world, ownerUUID);
        long frameStock = ownerUUID == null
            ? 0
            : DysonSphereSystem.getPlayerFrameComponents(world, ownerUUID);

        int cloudRoom = Math.max(0, DysonSphereState.CLOUD_CAP - team.cloudCount);
        int frameRoom = Math.max(0, DysonSphereState.FRAME_COMPLETE - team.frameCount);
        long clouds = Math.min(cloudStock, cloudRoom);
        long frames = Math.min(frameStock, frameRoom);

        if (clouds + frames <= 0) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        // 发射成本在扣组件之前先校验，避免“组件打上天但付不起账”的免费发射
        BigInteger batchCost = BigInteger.valueOf(DysonMachineConfig.launchCostOrundum)
            .multiply(BigInteger.valueOf(clouds + frames));
        if (ownerUUID == null
            || OrundumEnergyService.getOrundumForUser(ownerUUID).compareTo(batchCost) < 0) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return CheckRecipeResultRegistry.insufficientPower(safeToLong(batchCost));
        }

        if (!DysonSphereSystem.consumeComponentsOfPlayer(world, ownerUUID, clouds, frames)) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return SimpleCheckRecipeResult.ofFailure("DysonComponentsUnavailable");
        }

        boolean accepted = DysonSphereSystem.addModules(
            world,
            getTeamId(),
            base.getOwnerName(),
            (int) clouds,
            (int) frames);
        if (!accepted) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return SimpleCheckRecipeResult.ofFailure("DysonSphereLocked");
        }

        pendingCost = batchCost;
        this.lastUsedParallel = (int) Math.min(clouds + frames, Integer.MAX_VALUE);
        mMaxProgresstime = getWirelessModeProcessingTime();
        mProgresstime = 0;
        mEUt = 0;
        mEfficiency = 10000;
        mEfficiencyIncrease = 0;
        mOutputItems = null;
        mOutputFluids = null;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public String[] getInfoData() {
        String[] origin = super.getInfoData();
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(origin));
        lines.add(
            EnumChatFormatting.AQUA + Dyson_Info_MassMode);
        return lines.toArray(new String[0]);
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
    public IStructureDefinition<DysonMassLaunchModule> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<DysonMassLaunchModule>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings8, 7))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_DysonMassLaunchModule_MachineType)
            .addInfo(Tooltip_DysonMassLaunchModule_00)
            .addInfo(Tooltip_DysonMassLaunchModule_01)
            .addInfo(Tooltip_DysonMassLaunchModule_02)
            .addInfo(Tooltip_DysonMassLaunchModule_03)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new DysonMassLaunchModule(this.mName);
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
