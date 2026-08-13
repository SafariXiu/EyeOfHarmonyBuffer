package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgradeStorage;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.dyson.DysonTeamProgress;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeHelper;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.SimpleShutDownReason;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 戴森核心：每队一台的模块化巨构枢纽。
 * <p>
 * 占位结构 11×9×9，含 32 个模块位；按本队贴片数激活槽位（8/12/16/20/32），
 * 完工后解锁后 12 槽；接收模块每核心至多 1 台；核心算力 100 万，不足则全部模块断开。
 */
public class DysonCore extends OrundumWirelessMultiMachineBase<DysonCore>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<DysonCore> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainDysonCore";
    private static final int OffsetsX = 16;
    private static final int OffsetsY = 2;
    private static final int OffsetsZ = 1;
    private static final int CASING_INDEX = 183;

    /** 每位玩家一台核心的注册表（服务端运行时，按 ownerUUID）。 */
    private static final Map<UUID, DysonCore> CORE_BY_OWNER = new HashMap<>();

    /** 重复核心的停机原因（文案在语言文件 GT5U.gui.text.shutdown_reason.dyson_duplicate_core）。 */
    private static final ShutDownReason DUPLICATE_CORE_REASON =
        SimpleShutDownReason.ofNormal("dyson_duplicate_core");

    public final ArrayList<DysonModuleBase<?>> moduleHatches = new ArrayList<>();

    /** 本机因该玩家已存在另一台核心而被停机。 */
    private boolean duplicateRejected = false;

    public DysonCore(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public DysonCore(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 20;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 0.0F;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @Override
    protected boolean usesOrundumCost() {
        return false;
    }

    @Override
    protected BigInteger getRequiredComputeForCurrentRecipe() {
        return BigInteger.valueOf(DysonMachineConfig.coreCompute);
    }

    protected UUID getTeamId() {
        UUID resolved = OrundumEnergyService.getTeamIdForUser(ownerUUID);
        return resolved != null ? resolved : ownerUUID;
    }

    public static boolean isOwnerCoreRegistered(UUID ownerUUID) {
        return ownerUUID != null && CORE_BY_OWNER.containsKey(ownerUUID);
    }

    /** 本队进度（GUI 与对外查询共用）。 */
    public DysonTeamProgress getTeamProgress() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            return null;
        }
        DysonSphereWorldData data = DysonSphereWorldData.get(base.getWorld());
        if (data == null) {
            return null;
        }
        return data.getTeam(getTeamId());
    }

    public int getTeamCloudCount() {
        DysonTeamProgress team = getTeamProgress();
        return team == null ? 0 : team.cloudCount;
    }

    public int getTeamFrameCount() {
        DysonTeamProgress team = getTeamProgress();
        return team == null ? 0 : team.frameCount;
    }

    public int getTeamPasteCount() {
        DysonTeamProgress team = getTeamProgress();
        return team == null ? 0 : team.pasteCount;
    }

    public long getTeamCloudComponents() {
        DysonTeamProgress team = getTeamProgress();
        return team == null ? 0 : team.cloudComponents;
    }

    public long getTeamFrameComponents() {
        DysonTeamProgress team = getTeamProgress();
        return team == null ? 0 : team.frameComponents;
    }

    public DysonUpgradeStorage getTeamUpgradesStorage() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            return null;
        }
        return DysonSphereSystem.getTeamUpgrades(base.getWorld(), getTeamId());
    }

    public boolean isUpgradeActive(DysonUpgrade upgrade) {
        DysonUpgradeStorage storage = getTeamUpgradesStorage();
        return storage != null && storage.isUpgradeActive(upgrade);
    }

    public int getUpgradePaid(DysonUpgrade upgrade) {
        DysonUpgradeStorage storage = getTeamUpgradesStorage();
        if (storage == null) {
            return 0;
        }
        return storage.getTotalPaid(upgrade);
    }

    public boolean tryUnlockUpgrade(DysonUpgrade upgrade) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            return false;
        }
        DysonUpgradeStorage storage = DysonSphereSystem.getTeamUpgrades(base.getWorld(), getTeamId());
        if (storage == null || storage.isUpgradeActive(upgrade)) {
            return false;
        }
        if (!storage.checkPrerequisites(upgrade)
            || !storage.checkSplit(upgrade, 1)
            || !storage.checkCost(upgrade)) {
            return false;
        }
        storage.unlockUpgrade(upgrade);
        markTeamDataDirty(base);
        return true;
    }

    public boolean tryRespecUpgrade(DysonUpgrade upgrade) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            return false;
        }
        DysonUpgradeStorage storage = DysonSphereSystem.getTeamUpgrades(base.getWorld(), getTeamId());
        if (storage == null || !storage.isUpgradeActive(upgrade)) {
            return false;
        }
        if (!storage.checkDependents(upgrade)) {
            return false;
        }
        storage.respecUpgrade(upgrade);
        markTeamDataDirty(base);
        return true;
    }

    /** 从投料格的物品处理器向节点成本充入（神锻 payCost 模式）。 */
    public void payUpgradeFromHandler(DysonUpgrade upgrade, ItemStackHandler handler) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || handler == null) {
            return;
        }
        DysonUpgradeStorage storage = DysonSphereSystem.getTeamUpgrades(base.getWorld(), getTeamId());
        if (storage == null) {
            return;
        }
        storage.payFromHandler(upgrade, handler);
        markTeamDataDirty(base);
    }

    private void markTeamDataDirty(IGregTechTileEntity base) {
        DysonSphereWorldData data = DysonSphereWorldData.get(base.getWorld());
        if (data != null) {
            data.markDirty();
        }
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new DysonCoreGui(this);
    }

    /** 核心专属 GUI（MODUI2）：终端左下角实时显示本队云/框架/贴片与组件库存。 */
    protected static class DysonCoreGui extends MTEMultiBlockBaseGui<DysonCore> {

        private IntSyncValue cloudSyncer;
        private IntSyncValue frameSyncer;
        private IntSyncValue pasteSyncer;
        private LongSyncValue cloudComponentsSyncer;
        private LongSyncValue frameComponentsSyncer;
        private final BooleanSyncValue[] activeSyncers = new BooleanSyncValue[DysonUpgrade.VALUES.length];
        private final IntSyncValue[] paidSyncers = new IntSyncValue[DysonUpgrade.VALUES.length];

        public DysonCoreGui(DysonCore multiblock) {
            super(multiblock);
        }

        @Override
        protected void registerSyncValues(PanelSyncManager syncManager) {
            super.registerSyncValues(syncManager);
            cloudSyncer = new IntSyncValue(multiblock::getTeamCloudCount);
            frameSyncer = new IntSyncValue(multiblock::getTeamFrameCount);
            pasteSyncer = new IntSyncValue(multiblock::getTeamPasteCount);
            cloudComponentsSyncer = new LongSyncValue(multiblock::getTeamCloudComponents);
            frameComponentsSyncer = new LongSyncValue(multiblock::getTeamFrameComponents);
            syncManager.syncValue("dysonCloud", cloudSyncer);
            syncManager.syncValue("dysonFrame", frameSyncer);
            syncManager.syncValue("dysonPaste", pasteSyncer);
            syncManager.syncValue("dysonCloudComponents", cloudComponentsSyncer);
            syncManager.syncValue("dysonFrameComponents", frameComponentsSyncer);

            for (DysonUpgrade upgrade : DysonUpgrade.VALUES) {
                BooleanSyncValue active = new BooleanSyncValue(() -> multiblock.isUpgradeActive(upgrade));
                IntSyncValue paid = new IntSyncValue(() -> multiblock.getUpgradePaid(upgrade));
                syncManager.syncValue("dysonUpgActive" + upgrade.ordinal(), active);
                syncManager.syncValue("dysonUpgPaid" + upgrade.ordinal(), paid);
                activeSyncers[upgrade.ordinal()] = active;
                paidSyncers[upgrade.ordinal()] = paid;
            }
        }

        @Override
        protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
            // 与神锻主 GUI 一致：不放控制器/维护物品格，避免玩家背包 Shift 时物品被它抢先截走。
            // 用等尺寸的透明占位保住其余按钮的原有位置（列内子控件按“自下而上”顺序添加）。
            return Flow.column()
                .width(18)
                .leftRel(1, -2, 1)
                .mainAxisAlignment(Alignment.MainAxis.END)
                .reverseLayout(true)
                .child(
                    new Widget<>()
                        .size(18, 18)
                        .marginTop(4)
                        .invisible())
                .child(createPowerSwitchButton())
                .child(createStructureUpdateButton(syncManager))
                .child(createUpgradeTreeButton(panel, syncManager));
        }

        protected IWidget createUpgradeTreeButton(ModularPanel panel, PanelSyncManager syncManager) {
            IPanelHandler[] individualPanels = new IPanelHandler[DysonUpgrade.VALUES.length];
            IPanelHandler[] depositPanels = new IPanelHandler[DysonUpgrade.VALUES.length];
            for (DysonUpgrade upgrade : DysonUpgrade.VALUES) {
                int index = upgrade.ordinal();
                depositPanels[index] = syncManager.syncedPanel(
                    "dysonUpgradeDeposit" + index,
                    true,
                    (panelSyncManager, _) -> createUpgradeDepositPanel(
                        panelSyncManager,
                        panel,
                        upgrade,
                        depositPanels[index]));
                individualPanels[index] = syncManager.syncedPanel(
                    "dysonUpgradeIndividual" + index,
                    true,
                    (panelSyncManager, _) -> createIndividualUpgradePanel(
                        panelSyncManager,
                        panel,
                        upgrade,
                        depositPanels[index],
                        individualPanels[index]));
            }
            IPanelHandler treePanel = syncManager.syncedPanel(
                "dysonUpgradeTree",
                true,
                (panelSyncManager, _) -> createUpgradeTreePanel(
                    panelSyncManager,
                    panel,
                    individualPanels));
            return new ButtonWidget<>()
                .size(18, 18)
                .overlay(UITexture.fullImage("eyeofharmonybuffer", "gui/EyeOfHarmonyBuffer"))
                .onMousePressed(d -> {
                    treePanel.openPanel();
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(IKey.lang("eohb.dyson.upgrade.tree")));
        }

        protected ModularPanel createUpgradeTreePanel(PanelSyncManager syncManager, ModularPanel parent,
                                                      IPanelHandler[] individualPanels) {
            ModularPanel panel = new ModularPanel("dysonUpgradeTree")
                .relative(parent)
                .leftRel(1)
                .topRel(0)
                .size(300, 250);
            panel.background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);

            for (DysonUpgrade upgrade : DysonUpgrade.VALUES) {
                for (DysonUpgrade prerequisite : upgrade.getPrerequisites()) {
                    addConnectorSegments(panel, prerequisite, upgrade);
                }
                panel.child(createUpgradeNodeBox(upgrade, individualPanels[upgrade.ordinal()]));
            }
            return panel;
        }

        protected void addConnectorSegments(ModularPanel panel, DysonUpgrade parent, DysonUpgrade child) {
            int parentX = parent.getTreeX() + 18;
            int parentY = parent.getTreeY() + 14;
            int childX = child.getTreeX() + 18;
            int childY = child.getTreeY() + 7;
            if (childY > parentY) {
                panel.child(
                    new IDrawable.DrawableWidget(new Rectangle().setColor(0xFF5A6B8C))
                        .pos(parentX, parentY)
                        .size(2, childY - parentY));
            }
            if (childX != parentX) {
                int lineX = Math.min(parentX, childX);
                int width = Math.abs(childX - parentX) + 2;
                panel.child(
                    new IDrawable.DrawableWidget(new Rectangle().setColor(0xFF5A6B8C))
                        .pos(lineX, childY)
                        .size(width, 2));
            }
        }

        protected IWidget createUpgradeNodeBox(DysonUpgrade upgrade, IPanelHandler individualPanel) {
            BooleanSyncValue active = activeSyncers[upgrade.ordinal()];
            return new ButtonWidget<>()
                .pos(upgrade.getTreeX(), upgrade.getTreeY())
                .size(36, 14)
                .background(
                    new DynamicDrawable(
                        () -> new Rectangle()
                            .setColor(
                                active.getValue()
                                    ? 0xFF2E8B57
                                    : (upgrade.isMajor() ? 0xFF8B5E2E : 0xFF404A63))))
                .child(
                    new TextWidget<>(IKey.lang(upgrade.getShortNameKey()))
                        .size(36, 14)
                        .textAlign(Alignment.Center))
                .onMousePressed(button -> {
                    if (button == 0) {
                        individualPanel.openPanel();
                    }
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(IKey.lang(upgrade.getNameKey())));
        }

        protected ModularPanel createIndividualUpgradePanel(PanelSyncManager syncManager, ModularPanel parent,
                                                            DysonUpgrade upgrade, IPanelHandler depositPanel,
                                                            IPanelHandler selfPanel) {
            ModularPanel panel = new ModularPanel("dysonUpgradeIndividual" + upgrade.ordinal())
                .relative(parent)
                .leftRel(1)
                .topRel(0)
                .size(240, 150);
            panel.background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);

            panel.child(
                new ButtonWidget<>()
                    .pos(222, 4)
                    .size(12, 12)
                    .background(new Rectangle().setColor(0xFF8A3A3A))
                    .child(new TextWidget<>(IKey.str("x")).size(12, 12).textAlign(Alignment.Center))
                    .onMousePressed(button -> {
                        selfPanel.closePanel();
                        return true;
                    }));
            panel.child(
                new TextWidget<>(StatCollector.translateToLocal(upgrade.getNameKey()))
                    .pos(8, 8)
                    .size(224, 14)
                    .textAlign(Alignment.Center));
            panel.child(
                new TextWidget<>(StatCollector.translateToLocal(upgrade.getEffectKey()))
                    .pos(8, 26)
                    .size(224, 46));
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> costText(upgrade)))
                    .pos(8, 76)
                    .size(224, 12)
                    .textAlign(Alignment.Center));
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> prerequisiteText(upgrade)))
                    .pos(8, 92)
                    .size(224, 12)
                    .textAlign(Alignment.Center));

            // 解锁 / 洗点
            BooleanSyncValue actionSyncer = new BooleanSyncValue(
                () -> multiblock.isUpgradeActive(upgrade),
                val -> {
                    if (val) {
                        multiblock.tryUnlockUpgrade(upgrade);
                    } else {
                        multiblock.tryRespecUpgrade(upgrade);
                    }
                }).allowC2S();
            panel.child(
                new ToggleButton()
                    .pos(70, 116)
                    .size(100, 18)
                    .value(actionSyncer)
                    .background(new Rectangle().setColor(0xFF35507A))
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> confirmLabel(upgrade)))
                            .size(100, 18)
                            .textAlign(Alignment.Center)));
            panel.child(
                new ButtonWidget<>()
                    .pos(175, 116)
                    .size(50, 18)
                    .background(new Rectangle().setColor(0xFF7A5A35))
                    .child(
                        new TextWidget<>(IKey.lang("eohb.dyson.upgrade.depositOpen"))
                            .size(50, 18)
                            .textAlign(Alignment.Center))
                    .onMousePressed(d -> {
                        if (upgrade.hasExtraCost()) {
                            depositPanel.openPanel();
                        }
                        return true;
                    }));
            return panel;
        }

        protected ModularPanel createUpgradeDepositPanel(PanelSyncManager syncManager, ModularPanel parent,
                                                         DysonUpgrade upgrade, IPanelHandler selfPanel) {
            ItemStackHandler handler = new ItemStackHandler(16);
            ModularPanel panel = new ModularPanel("dysonUpgradeDeposit" + upgrade.ordinal())
                .relative(parent)
                .leftRel(1)
                .topRel(0)
                .size(189, 190);
            panel.background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);

            // 关闭按钮
            panel.child(
                new ButtonWidget<>()
                    .pos(179, 0)
                    .size(10, 10)
                    .background(new Rectangle().setColor(0xFF8A3A3A))
                    .child(
                        new TextWidget<>(IKey.str("x"))
                            .size(10, 10)
                            .textAlign(Alignment.Center))
                    .onMousePressed(button -> {
                        selfPanel.closePanel();
                        return true;
                    }));

            // 所需材料图标（左侧 3 列 × 4 行，同神锻布局）
            ItemStack[] costs = upgrade.getExtraCost();
            for (int i = 0; i < costs.length; i++) {
                final ItemStack cost = costs[i];
                panel.child(
                    new ItemDisplayWidget()
                        .item(cost)
                        .displayAmount(false)
                        .size(16, 16)
                        .pos(5 + 36 * (i / 4), 6 + 18 * (i % 4))
                        .tooltipBuilder(
                            t -> t.addLine(IKey.str(cost.getDisplayName() + " x" + cost.stackSize))));
            }

            // 16 格投料区（右侧 4 × 4，同神锻布局），每格按材料自动过滤
            for (int i = 0; i < 16; i++) {
                // 槽位按成本顺序轮询绑定材料：同一材料可占多格，便于 Shift 批量填入
                final ItemStack filterCost = costs.length == 0 ? null : costs[i % costs.length];
                ModularSlot slot = new ModularSlot(handler, i).singletonSlotGroup();
                if (filterCost != null) {
                    slot.filter(
                        stack -> selfPanel.isPanelOpen()
                            && stack != null
                            && GTUtility.areStacksEqual(stack, filterCost));
                } else {
                    slot.filter(stack -> selfPanel.isPanelOpen());
                }
                panel.child(
                    new ItemSlot()
                        .slot(slot)
                        .pos(112 + (i % 4) * 18, 6 + (i / 4) * 18));
            }

            BooleanSyncValue depositSyncer = new BooleanSyncValue(
                () -> false,
                val -> {
                    if (val) {
                        multiblock.payUpgradeFromHandler(upgrade, handler);
                    }
                }).allowC2S();
            panel.child(
                new ToggleButton()
                    .pos(5, 82)
                    .size(179, 18)
                    .value(depositSyncer)
                    .background(new Rectangle().setColor(0xFF7A5A35))
                    .child(
                        new TextWidget<>(IKey.lang("eohb.dyson.upgrade.deposit"))
                            .size(179, 18)
                            .textAlign(Alignment.Center)));

            // 玩家背包（下方 4 行），用于 Shift 点击批量填料到上方投料格
            panel.child(
                SlotGroupWidget.playerInventory(false)
                    .pos(5, 108));
            return panel;
        }

        protected String confirmLabel(DysonUpgrade upgrade) {
            if (activeSyncers[upgrade.ordinal()].getValue()) {
                return StatCollector.translateToLocal("eohb.dyson.upgrade.respec");
            }
            if (!upgrade.hasExtraCost() || paidSyncers[upgrade.ordinal()].getValue() >= upgrade.getTotalItemCost()) {
                return StatCollector.translateToLocal("eohb.dyson.upgrade.unlock");
            }
            return StatCollector.translateToLocal("eohb.dyson.upgrade.unpaid");
        }

        protected String costText(DysonUpgrade upgrade) {
            if (!upgrade.hasExtraCost()) {
                return StatCollector.translateToLocal("eohb.dyson.upgrade.noCost");
            }
            return StatCollector.translateToLocalFormatted(
                "eohb.dyson.upgrade.cost",
                upgrade.getTotalItemCost(),
                paidSyncers[upgrade.ordinal()].getValue());
        }

        protected String prerequisiteText(DysonUpgrade upgrade) {
            DysonUpgrade[] prerequisites = upgrade.getPrerequisites();
            if (prerequisites.length == 0) {
                return StatCollector.translateToLocal("eohb.dyson.upgrade.prereqNone");
            }
            StringBuilder builder = new StringBuilder(
                StatCollector.translateToLocal("eohb.dyson.upgrade.prereq"));
            for (int i = 0; i < prerequisites.length; i++) {
                if (i > 0) {
                    builder.append(upgrade.requiresAllPrerequisites() ? " + " : " 或 ");
                }
                builder.append(StatCollector.translateToLocal(prerequisites[i].getNameKey()));
            }
            return builder.toString();
        }

        @Override
        protected ParentWidget<?> createTerminalParentWidget(ModularPanel panel, PanelSyncManager syncManager) {
            ParentWidget<?> parent = super.createTerminalParentWidget(panel, syncManager);
            // 与终端右下角列一致：自由子控件用 leftRel/bottomRel 相对定位
            parent.child(createDysonStatsColumn().leftRel(0, 4, 0).bottomRel(0, 4, 0));
            return parent;
        }

        protected Flow createDysonStatsColumn() {
            return Flow.column()
                .width(120)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .coverChildrenHeight(0)
                .child(makeStat("云", () -> String.valueOf(cloudSyncer.getValue())))
                .child(makeStat("框架", () -> String.valueOf(frameSyncer.getValue())))
                .child(makeStat("贴片", () -> String.valueOf(pasteSyncer.getValue())))
                .child(
                    makeStat(
                        "组件",
                        () -> "云 "
                            + cloudComponentsSyncer.getValue()
                            + " / 框架 "
                            + frameComponentsSyncer.getValue()));
        }

        private TextWidget<?> makeStat(String label, Supplier<String> textSupplier) {
            return new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.AQUA
                        + label
                        + ": "
                        + EnumChatFormatting.GOLD
                        + textSupplier.get()))
                    .color(Color.WHITE.main);
        }
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || base == null || !base.isAllowedToWork()
            || !DysonMachineConfig.isInTalos(base.getWorld())) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        mEfficiency = 10000;
        mEfficiencyIncrease = 0;
        mMaxProgresstime = getWirelessModeProcessingTime();
        mProgresstime = 0;
        mEUt = 0;
        mOutputItems = null;
        mOutputFluids = null;

        this.lastOrundumCost = BigInteger.ZERO;
        this.lastUsedParallel = 1;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) {
            return;
        }

        // 维度强约束：只能在塔罗斯 2 运行，否则全部模块断开
        if (!DysonMachineConfig.isInTalos(aBaseMetaTileEntity.getWorld())) {
            disconnectAll();
            return;
        }

        // 每人一台：注册/校验唯一核心
        UUID owner = ownerUUID;
        if (mMachine && owner != null) {
            DysonCore existing = CORE_BY_OWNER.get(owner);
            if (existing == null) {
                CORE_BY_OWNER.put(owner, this);
                duplicateRejected = false;
            } else if (existing != this) {
                duplicateRejected = true;
                disableWorking();
                stopMachine(DUPLICATE_CORE_REASON);
                disconnectAll();
                return;
            }
        } else if (owner != null && CORE_BY_OWNER.get(owner) == this) {
            CORE_BY_OWNER.remove(owner);
        }

        // 核心算力门控：不足则全部模块断开
        boolean computeOk = false;
        if (ownerUUID != null) {
            WirelessComputeHelper.updateConsumer(this);
            computeOk = WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
        }
        // 核心不在线（未成型 / 算力不足 / 被停机）时全部模块断开
        if (!mMachine || !computeOk || !aBaseMetaTileEntity.isActive()) {
            disconnectAll();
            return;
        }

        World world = aBaseMetaTileEntity.getWorld();
        long worldTime = world.getTotalWorldTime();
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        DysonTeamProgress team = data == null ? null : data.getTeam(getTeamId());
        int paste = team == null ? 0 : team.pasteCount;
        int activeSlots = DysonMachineConfig.activeSlotsForPaste(paste);

        int connectedCount = 0;
        int receivers = 0;
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module == null) {
                continue;
            }
            if (!module.isFormed() || connectedCount >= activeSlots || module.getRequiredPaste() > paste) {
                module.disconnect();
                continue;
            }
            // 接收模块每核心至多 1 台
            if (module.getModuleType() == DysonModuleBase.ModuleType.RECEIVER) {
                if (receivers > 0) {
                    module.disconnect();
                    continue;
                }
                receivers++;
            }
            // 模块自身算力
            BigInteger moduleCompute = module.getRequiredCompute();
            if (moduleCompute.signum() > 0) {
                WirelessComputeHelper.updateConsumer(module);
                if (!WirelessComputeHelper.isConsumerSatisfiedInGroup(module)) {
                    module.disconnect();
                    continue;
                }
            }
            module.connect(worldTime);
            connectedCount++;
        }
    }

    private void disconnectAll() {
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module != null) {
                module.disconnect();
            }
        }
    }

    @Override
    public void onRemoval() {
        UUID owner = ownerUUID;
        if (owner != null && CORE_BY_OWNER.get(owner) == this) {
            CORE_BY_OWNER.remove(owner);
        }
        super.onRemoval();
    }

    @Override
    public String[] getInfoData() {
        String[] origin = super.getInfoData();
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(origin));

        int connectedCount = 0;
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module != null && module.isConnected()) {
                connectedCount++;
            }
        }

        IGregTechTileEntity base = getBaseMetaTileEntity();
        DysonSphereWorldData data = base == null ? null : DysonSphereWorldData.get(base.getWorld());
        DysonTeamProgress team = data == null ? null : data.getTeam(getTeamId());
        int paste = team == null ? 0 : team.pasteCount;

        lines.add(
            EnumChatFormatting.AQUA + "已连接模块: "
                + EnumChatFormatting.GOLD
                + connectedCount
                + EnumChatFormatting.AQUA
                + " / 激活槽位: "
                + EnumChatFormatting.GOLD
                + DysonMachineConfig.activeSlotsForPaste(paste));
        lines.add(
            EnumChatFormatting.AQUA + "本队贴片: "
                + EnumChatFormatting.GOLD
                + paste);
        if (team != null) {
            lines.add(
                EnumChatFormatting.AQUA + "组件库存: 云 "
                    + EnumChatFormatting.GOLD
                    + team.cloudComponents
                    + EnumChatFormatting.AQUA
                    + " / 框架 "
                    + EnumChatFormatting.GOLD
                    + team.frameComponents);
        }

        if (duplicateRejected) {
            lines.add(
                EnumChatFormatting.RED + "该玩家已有一台核心，本机已停机");
        }

        if (base != null && base.isServerSide()) {
            boolean computeOk = ownerUUID != null && WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
            lines.add(
                EnumChatFormatting.AQUA + "核心算力: "
                    + (computeOk
                        ? EnumChatFormatting.GREEN + "满足"
                        : EnumChatFormatting.RED + "不足（需 1,000,000）"));
        }
        return lines.toArray(new String[0]);
    }

    public boolean addModuleTile(IGregTechTileEntity tileEntity) {
        if (tileEntity == null) {
            return false;
        }
        IMetaTileEntity metaTileEntity = tileEntity.getMetaTileEntity();
        if (metaTileEntity instanceof DysonModuleBase) {
            DysonModuleBase<?> module = (DysonModuleBase<?>) metaTileEntity;
            if (!moduleHatches.contains(module)) {
                return moduleHatches.add(module);
            }
            return true;
        }
        return false;
    }

    public enum moduleElement implements IHatchElement<DysonCore> {

        Module((core, tileEntity, index) -> core.addModuleTile(tileEntity), DysonModuleBase.class) {

            @Override
            public long count(DysonCore tileEntity) {
                return tileEntity.moduleHatches.size();
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<DysonCore> adder;

        @SafeVarargs
        moduleElement(IGTHatchAdder<DysonCore> adder, Class<? extends IMetaTileEntity>... mteClasses) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super DysonCore> adder() {
            return adder;
        }
    }

    private static final String SP = "                                ";
    private static final String FULL_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String CTRL = "                ~               ";
    private static final String ACOL = "                A               ";
    private static final String CLUSTER_A = "  AAA   AAA   AAA   AAA   AAA   ";
    private static final String CLUSTER_GAP = "  A A   A A   A A   A A   A A   ";

    private static final String[][] shapeMain = new String[][] {
        // y=0 空
        { SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP,
            SP, SP, SP, SP, SP, SP, SP, SP },
        // y=1 空
        { SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP,
            SP, SP, SP, SP, SP, SP, SP, SP },
        // y=2 控制器 + 平台底
        { SP, SP, CTRL, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A,
            SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP,
            SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP },
        // y=3 模块层
        { SP, ACOL, SP, SP, "  ACA   ACA   ACA   ABA   ACA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ABA   ABA   ABA   ACA   ABA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ABA   ACA   ACA   ACA   ABA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ABA   ACA   ACA   ACA   ACA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ACA   ACA   ABA   ABA   ACA   ", CLUSTER_GAP, CLUSTER_A, SP },
        // y=4 平台顶
        { SP, SP, ACOL, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A,
            SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP,
            SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP },
        // y=5 全地板
        { FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A,
            FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A,
            FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A }
    };

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
                             List<StructureError> errors) {
        moduleHatches.clear();
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
    public IStructureDefinition<DysonCore> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            IStructureElement<DysonCore> moduleSlot = buildHatchAdder(DysonCore.class)
                .atLeast(moduleElement.Module)
                .casingIndex(CASING_INDEX)
                .hint(2)
                .buildAndChain(ofBlock(sBlockCasings8, 7));
            STRUCTURE_DEFINITION = StructureDefinition.<DysonCore>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings8, 7))
                .addElement('B', moduleSlot)
                .addElement('C', moduleSlot)
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("戴森核心")
            .addInfo("每位玩家限一台的戴森球巨构枢纽")
            .addInfo("核心与模块必须在 Orundum 供电场内工作")
            .addInfo("最多挂载 32 个模块（占位结构，贴片数解锁槽位）")
            .addInfo("核心消耗 1,000,000 算力")
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new DysonCore(this.mName);
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
