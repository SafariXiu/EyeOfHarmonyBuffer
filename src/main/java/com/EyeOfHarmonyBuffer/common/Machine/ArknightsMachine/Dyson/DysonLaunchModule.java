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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
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
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

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

    /** 单型阶段（未解锁双轨）的发射优先级：true = 云优先，false = 框架优先。 */
    private boolean launchCloudFirst = true;

    /** 本轮实际发射的云/框架组件数（Waila 显示用，服务端）。 */
    private long lastRoundClouds = 0;
    private long lastRoundFrames = 0;

    public DysonLaunchModule(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public DysonLaunchModule(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    public boolean isLaunchCloudFirst() {
        return launchCloudFirst;
    }

    public void setLaunchCloudFirst(boolean value) {
        launchCloudFirst = value;
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
        if (isUpgradeActive(DysonUpgrade.LAUNCH_EFFICIENCY_II)) {
            return DysonMachineConfig.launchEfficiencyTicksII;
        }
        if (isUpgradeActive(DysonUpgrade.LAUNCH_EFFICIENCY_I)) {
            return DysonMachineConfig.launchEfficiencyTicksI;
        }
        return DysonMachineConfig.launchTimeTicks;
    }

    /** 当前单轮批量：16 → 批量 I 64 → 批量 II 128。 */
    protected int getLaunchBatch() {
        if (isUpgradeActive(DysonUpgrade.LAUNCH_BATCH_II)) {
            return DysonMachineConfig.launchBatchII;
        }
        if (isUpgradeActive(DysonUpgrade.LAUNCH_BATCH_I)) {
            return DysonMachineConfig.launchBatchI;
        }
        return DysonMachineConfig.launchBatch;
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new LaunchGui(this);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setBoolean("LaunchCloudFirst", launchCloudFirst);
        super.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        launchCloudFirst = !aNBT.hasKey("LaunchCloudFirst") || aNBT.getBoolean("LaunchCloudFirst");
        super.loadNBTData(aNBT);
    }

    @Override
    public String[] getInfoData() {
        String[] origin = super.getInfoData();
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(origin));
        lines.add(
            EnumChatFormatting.AQUA + Dyson_Info_LaunchPriority
                + EnumChatFormatting.GOLD
                + (launchCloudFirst ? Dyson_Gui_PriorityCloud : Dyson_Gui_PriorityFrame));
        lines.add(
            EnumChatFormatting.AQUA + Dyson_Info_LaunchBatch
                + EnumChatFormatting.GOLD
                + getLaunchBatch());
        return lines.toArray(new String[0]);
    }

    /** 发射模块专属 GUI：右侧按钮列追加“发射优先级”切换按钮。 */
    protected static class LaunchGui extends MTEMultiBlockBaseGui<DysonLaunchModule> {

        public LaunchGui(DysonLaunchModule multiblock) {
            super(multiblock);
        }

        @Override
        protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
            return super.createButtonColumn(panel, syncManager)
                .child(createPriorityButton(syncManager));
        }

        protected IWidget createPriorityButton(PanelSyncManager syncManager) {
            BooleanSyncValue cloudFirst = new BooleanSyncValue(
                multiblock::isLaunchCloudFirst,
                multiblock::setLaunchCloudFirst).allowC2S();
            syncManager.syncValue("dysonLaunchCloudFirst", cloudFirst);
            return new ToggleButton()
                .size(18, 18)
                .value(cloudFirst)
                .child(
                    true,
                    new TextWidget<>(IKey.str(Dyson_Gui_PriorityCloud))
                        .size(18, 18)
                        .textAlign(Alignment.Center)
                        .color(Color.WHITE.main)
                        .shadow(true))
                .child(
                    false,
                    new TextWidget<>(IKey.str(Dyson_Gui_PriorityFrame))
                        .size(18, 18)
                        .textAlign(Alignment.Center)
                        .color(Color.WHITE.main)
                        .shadow(true))
                .tooltipBuilder(t -> t.addLine(IKey.str(Dyson_Gui_PriorityTooltip)));
        }
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        this.lastRoundClouds = 0;
        this.lastRoundFrames = 0;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!canOperate()) {
            scheduleRecipeCheckImmediate();
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

        int batch = getLaunchBatch();
        boolean dual = isUpgradeActive(DysonUpgrade.DUAL_LAUNCH);
        // 组件是机主个人资产：发射只扣自己的组件
        long cloudStock = ownerUUID == null
            ? 0
            : DysonSphereSystem.getPlayerCloudComponents(world, ownerUUID);
        long frameStock = ownerUUID == null
            ? 0
            : DysonSphereSystem.getPlayerFrameComponents(world, ownerUUID);
        int clouds = 0;
        int frames = 0;
        if (dual) {
            // 双轨：云与框架各自按批量独立发射，互不占用
            clouds = (int) Math.min(batch, cloudStock);
            frames = (int) Math.min(batch, frameStock);
        } else if (launchCloudFirst) {
            // 单型阶段：只发射优先级类型，无库存不回退
            if (cloudStock > 0) {
                clouds = (int) Math.min(batch, cloudStock);
            }
        } else {
            if (frameStock > 0) {
                frames = (int) Math.min(batch, frameStock);
            }
        }

        // 上限保护：云/框架已满时不发射、不扣组件、不收费
        int cloudRoom = Math.max(0, DysonSphereState.CLOUD_CAP - team.cloudCount);
        int frameRoom = Math.max(0, DysonSphereState.FRAME_COMPLETE - team.frameCount);
        clouds = Math.min(clouds, cloudRoom);
        frames = Math.min(frames, frameRoom);

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
            clouds,
            frames);
        if (!accepted) {
            pendingCost = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return SimpleCheckRecipeResult.ofFailure("DysonSphereLocked");
        }

        this.lastRoundClouds = clouds;
        this.lastRoundFrames = frames;
        pendingCost = batchCost;
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

    @Override
    protected void writeWailaRoundStats(NBTTagCompound tag, World world) {
        tag.setLong("dysonLaunchedCloud", lastRoundClouds);
        tag.setLong("dysonLaunchedFrame", lastRoundFrames);
    }

    @Override
    protected void appendWailaRoundStats(NBTTagCompound tag, List<String> currentTip) {
        if (tag.hasKey("dysonLaunchedCloud") && tag.hasKey("dysonLaunchedFrame")) {
            currentTip.add(
                EnumChatFormatting.AQUA + Dyson_Info_LaunchedCloud
                    + EnumChatFormatting.GOLD + tag.getLong("dysonLaunchedCloud"));
            currentTip.add(
                EnumChatFormatting.AQUA + Dyson_Info_LaunchedFrame
                    + EnumChatFormatting.GOLD + tag.getLong("dysonLaunchedFrame"));
        }
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
        tt.addMachineType(Tooltip_DysonLaunchModule_MachineType)
            .addInfo(Tooltip_DysonLaunchModule_00)
            .addInfo(Tooltip_DysonLaunchModule_01)
            .addInfo(Tooltip_DysonLaunchModule_02)
            .addInfo(Tooltip_DysonLaunchModule_03)
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
