package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
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
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 接收模块：每 1 秒按本队功率结算一次（云 × 2^41 + 贴片 × 2^79，完工后 10^200），
 * 产出按 GUI 配置的 Orundum 占比（0~100）拆分无线 EU 与 Orundum 两本账，需点亮“能量分配”大节点；
 * 不耗算力；每队至多 1 台。
 */
public class DysonReceiverModule extends DysonModuleBase<DysonReceiverModule>
    implements IConstructable, ISurvivalConstructable {

    /** 队级唯一注册表：teamId → 当前在线的接收模块。 */
    private static final Map<UUID, DysonReceiverModule> ACTIVE_TEAM_RECEIVERS = new HashMap<>();

    private static IStructureDefinition<DysonReceiverModule> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainDysonReceiver";
    private static final int OffsetsX = 1;
    private static final int OffsetsY = 1;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 183;

    /** 产出拆分：给 Orundum 的百分比（0~100），其余进无线 EU。 */
    private int orundumSharePercent = 100;

    /** 当前在队级唯一注册表里使用的键，换队时用于精确清理旧映射。 */
    private UUID registeredTeamId = null;

    /** 尝试注册为本队唯一接收模块：无占用或自己已占用时成功，否则失败。 */
    public static boolean tryRegisterTeamReceiver(UUID teamId, DysonReceiverModule module) {
        if (teamId == null || module == null) {
            return false;
        }
        synchronized (ACTIVE_TEAM_RECEIVERS) {
            // 换队重注册时先清掉旧键，避免旧队伍留下失效占用
            if (module.registeredTeamId != null && !module.registeredTeamId.equals(teamId)) {
                if (ACTIVE_TEAM_RECEIVERS.get(module.registeredTeamId) == module) {
                    ACTIVE_TEAM_RECEIVERS.remove(module.registeredTeamId);
                }
            }
            DysonReceiverModule existing = ACTIVE_TEAM_RECEIVERS.get(teamId);
            if (existing == null || existing == module) {
                ACTIVE_TEAM_RECEIVERS.put(teamId, module);
                module.registeredTeamId = teamId;
                return true;
            }
            return false;
        }
    }

    public static boolean isTeamReceiverOnline(UUID teamId) {
        synchronized (ACTIVE_TEAM_RECEIVERS) {
            return ACTIVE_TEAM_RECEIVERS.containsKey(teamId);
        }
    }

    /** 当前为本队工作的接收模块的机主；无在线接收模块时返回 null。 */
    public static UUID getTeamReceiverOwner(UUID teamId) {
        synchronized (ACTIVE_TEAM_RECEIVERS) {
            DysonReceiverModule module = ACTIVE_TEAM_RECEIVERS.get(teamId);
            return module == null ? null : module.getOwnerUUID();
        }
    }

    private void unregisterIfActive() {
        synchronized (ACTIVE_TEAM_RECEIVERS) {
            if (registeredTeamId != null && ACTIVE_TEAM_RECEIVERS.get(registeredTeamId) == this) {
                ACTIVE_TEAM_RECEIVERS.remove(registeredTeamId);
            }
            registeredTeamId = null;
        }
    }

    @Override
    public void disconnect() {
        if (connected) {
            unregisterIfActive();
        }
        super.disconnect();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide() || !connected) {
            return;
        }
        long elapsed = aBaseMetaTileEntity.getWorld().getTotalWorldTime() - lastConnectTick;
        if (elapsed > DysonMachineConfig.CORE_HEARTBEAT_TICKS) {
            disconnect();
        }
    }

    @Override
    public void onRemoval() {
        unregisterIfActive();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        unregisterIfActive();
        super.onUnload();
    }

    public DysonReceiverModule(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public DysonReceiverModule(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    public int getOrundumSharePercent() {
        return orundumSharePercent;
    }

    /** “能量分配”大节点：点亮后才允许调整 EU / Orundum 拆分。 */
    public boolean isSplitUnlocked() {
        return isUpgradeActive(DysonUpgrade.SPLIT_UNLOCK);
    }

    public void setOrundumSharePercent(int value) {
        // 未点亮“能量分配”大节点时锁定为全额 Orundum（服务端强制，防客户端/存档直改）
        if (!isSplitUnlocked()) {
            this.orundumSharePercent = 100;
            return;
        }
        orundumSharePercent = Math.max(0, Math.min(100, value));
    }

    /** 接收模块是发电方，Waila 那一行显示“每轮输出”而不是“每轮消耗”。 */
    @Override
    protected String getWailaCostLabel() {
        return Dyson_Waila_OutputOrundum;
    }

    @Override
    protected void writeWailaRoundStats(NBTTagCompound tag, World world) {
        tag.setInteger("dysonOruPct", orundumSharePercent);
    }

    @Override
    protected void appendWailaRoundStats(NBTTagCompound tag, List<String> currentTip) {
        if (tag.hasKey("dysonOruPct")) {
            int pct = tag.getInteger("dysonOruPct");
            currentTip.add(
                EnumChatFormatting.AQUA + Dyson_Info_Split
                    + EnumChatFormatting.GOLD + pct + "%"
                    + EnumChatFormatting.AQUA + " / EU "
                    + EnumChatFormatting.GOLD + (100 - pct) + "%");
        }
    }

    @Override
    public ModuleType getModuleType() {
        return ModuleType.RECEIVER;
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return DysonMachineConfig.TICKS_PER_SETTLEMENT;
    }

    @Override
    protected boolean actsAsComputeConsumer() {
        return false;
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!canOperate()) {
            scheduleRecipeCheckImmediate();
            pendingGain = BigInteger.ZERO;
            this.lastUsedParallel = 0;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        World world = base.getWorld();
        DysonTeamProgress team = getTeamProgress(world);
        BigInteger cloud = team == null ? BigInteger.ZERO : BigInteger.valueOf(team.cloudCount);
        BigInteger paste = team == null ? BigInteger.ZERO : BigInteger.valueOf(team.pasteCount);

        BigInteger perTick;
        if (isTeamCompleted(world)) {
            perTick = DysonMachineConfig.COMPLETED_POWER;
        } else {
            perTick = cloud.multiply(DysonMachineConfig.CLOUD_POWER)
                .add(paste.multiply(DysonMachineConfig.PASTE_POWER));
        }

        // 维度功率：塔罗斯-2 空间站全功率；塔罗斯-2 地面仅 60%（整体削减 40%）
        if (!DysonMachineConfig.isInTalosStation(world)) {
            perTick = perTick
                .multiply(BigInteger.valueOf((long) (DysonMachineConfig.RECEIVER_POWER_MULTIPLIER_ON_SURFACE * 100)))
                .divide(BigInteger.valueOf(100L));
        }

        pendingGain = perTick.multiply(BigInteger.valueOf(DysonMachineConfig.TICKS_PER_SETTLEMENT));
        this.lastUsedParallel = 1;
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
    protected BigInteger getWirelessGain() {
        return pendingGain;
    }

    @Override
    protected void creditGain(BigInteger total) {
        if (total == null || total.signum() <= 0 || ownerUUID == null) {
            return;
        }
        // 结算时同样强制门控：洗点/未点亮状态下永远全额入 Orundum
        int oruPct = isSplitUnlocked() ? Math.max(0, Math.min(100, orundumSharePercent)) : 100;
        BigInteger orundum = total.multiply(BigInteger.valueOf(oruPct)).divide(BigInteger.valueOf(100L));
        BigInteger eu = total.subtract(orundum);
        if (orundum.signum() > 0) {
            produceOrundumForOwner(ownerUUID, orundum);
        }
        if (eu.signum() > 0) {
            produceWirelessEUForOwner(ownerUUID, eu);
        }
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new ReceiverGui(this);
    }

    /** 接收模块专属 GUI（MODUI2）：在右侧按钮列追加“产出拆分”按钮。 */
    protected static class ReceiverGui extends MTEMultiBlockBaseGui<DysonReceiverModule> {

        public ReceiverGui(DysonReceiverModule multiblock) {
            super(multiblock);
        }

        @Override
        protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
            return super.createButtonColumn(panel, syncManager)
                .child(createSplitConfigButton(panel, syncManager));
        }

        protected IWidget createSplitConfigButton(ModularPanel panel, PanelSyncManager syncManager) {
            IPanelHandler splitPanel = syncManager.syncedPanel(
                "dysonSplit",
                true,
                (panelSyncManager, _) -> openSplitConfigPanel(panelSyncManager, panel));
            return new ButtonWidget<>()
                .size(18, 18)
                .child(
                    new TextWidget<>(IKey.str("%"))
                        .size(18, 18)
                        .textAlign(Alignment.Center))
                .onMousePressed(d -> {
                    if (!splitPanel.isPanelOpen()) {
                        splitPanel.openPanel();
                    } else {
                        splitPanel.closePanel();
                    }
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(IKey.str(Dyson_Gui_SplitTooltip)))
                .tooltipShowUpTimer(TOOLTIP_DELAY);
        }

        protected ModularPanel openSplitConfigPanel(PanelSyncManager syncManager, ModularPanel parent) {
            BooleanSyncValue unlockedSyncer = new BooleanSyncValue(multiblock::isSplitUnlocked);
            syncManager.syncValue("dysonSplitUnlocked", unlockedSyncer);
            IntSyncValue shareSyncer = new IntSyncValue(
                multiblock::getOrundumSharePercent,
                multiblock::setOrundumSharePercent).allowC2S();
            return new ModularPanel("dysonSplit")
                .size(150, 78)
                .child(
                    Flow.column()
                        .full()
                        .padding(4)
                        .child(new TextWidget<>(IKey.str(Dyson_Gui_SplitTitle)))
                        .child(
                            new TextFieldWidget()
                                .value(shareSyncer)
                                .setTextAlignment(Alignment.Center)
                                .numbersInt(() -> 0, () -> 100)
                                .size(70, 14)
                                .marginBottom(4))
                        .child(
                            new TextWidget<>(
                                IKey.dynamic(
                                    () -> unlockedSyncer.getValue()
                                        ? EnumChatFormatting.AQUA
                                            + Dyson_Gui_SplitEUText
                                            + (100 - multiblock.getOrundumSharePercent())
                                            + "%"
                                        : EnumChatFormatting.RED + Dyson_Upgrade_SplitLocked))));
        }
    }

    @Override
    public String[] getInfoData() {
        String[] origin = super.getInfoData();
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(origin));
        lines.add(
            EnumChatFormatting.AQUA + Dyson_Info_Split
                + EnumChatFormatting.GOLD
                + orundumSharePercent
                + "%"
                + EnumChatFormatting.AQUA
                + " / EU "
                + EnumChatFormatting.GOLD
                + (100 - orundumSharePercent)
                + "%");
        return lines.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setInteger("OrundumSharePercent", orundumSharePercent);
        super.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("OrundumSharePercent")) {
            orundumSharePercent = Math.max(0, Math.min(100, aNBT.getInteger("OrundumSharePercent")));
        } else {
            orundumSharePercent = 100;
        }
        super.loadNBTData(aNBT);
    }

    /** 占位结构（后续替换为设计稿）：3×3×3 封闭外壳，控制器位于前脸中心。 */
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

    /**
     * 标准 GT 多方块结构定义（EOHB_WindTurbine 同款模式）。
     * <p>
     * 结构替换点：只需要改 {@link #shapeMain}、元素定义与 OffsetsX/Y/Z，其余三件套
     * （checkMachine / construct / survivalConstruct）无需变动。
     */
    @Override
    public IStructureDefinition<DysonReceiverModule> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<DysonReceiverModule>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings8, 7))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_DysonReceiverModule_MachineType)
            .addInfo(Tooltip_DysonReceiverModule_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_DysonReceiverModule_00)
            .addInfo(Tooltip_DysonReceiverModule_01)
            .addInfo(Tooltip_DysonReceiverModule_02)
            .addInfo(Tooltip_DysonReceiverModule_03)
            .addInfo(Tooltip_DysonReceiverModule_04)
            .addInfo(Tooltip_DysonReceiverModule_05)
            .addInfo(Tooltip_DysonReceiverModule_06)
            .addInfo(Tooltip_DysonReceiverModule_07)
            .addInfo(Tooltip_DysonReceiverModule_08)
            .addInfo(Tooltip_DysonReceiverModule_09)
            .addInfo(Tooltip_DysonReceiverModule_10)
            .addInfo(Tooltip_DysonReceiverModule_11)
            .addInfo(Tooltip_DysonModule_Link)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new DysonReceiverModule(this.mName);
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
