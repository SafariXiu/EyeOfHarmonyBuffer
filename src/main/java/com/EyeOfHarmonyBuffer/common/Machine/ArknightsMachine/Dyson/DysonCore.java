package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IPanelHandler;
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
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
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

    /** 开机中的核心按队伍注册（服务端运行时，供每日结算判定“核心是否开机”）。 */
    private static final Map<UUID, Set<DysonCore>> ACTIVE_CORES_BY_TEAM = new HashMap<>();

    /** 重复核心的停机原因（文案在语言文件 GT5U.gui.text.shutdown_reason.dyson_duplicate_core）。 */
    private static final ShutDownReason DUPLICATE_CORE_REASON =
        SimpleShutDownReason.ofNormal("dyson_duplicate_core");

    public final ArrayList<DysonModuleBase<?>> moduleHatches = new ArrayList<>();

    /** 本机因该玩家已存在另一台核心而被停机。 */
    private boolean duplicateRejected = false;

    /** 当前登记在“开机核心”表里的队伍 ID，换队时用于清理旧键。 */
    private UUID activeCoreTeamId = null;

    /** 储存在核心中的奇异物质（神锻引力碎片式货币），优先供本机升级使用。 */
    private long strangeMatter = 0L;

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

    /** 队伍是否有已开机（成型 + 算力满足 + 运行中）的核心，供每日结算查询。 */
    public static boolean isTeamCoreOnline(UUID teamId) {
        if (teamId == null) {
            return false;
        }
        synchronized (ACTIVE_CORES_BY_TEAM) {
            Set<DysonCore> cores = ACTIVE_CORES_BY_TEAM.get(teamId);
            return cores != null && !cores.isEmpty();
        }
    }

    public long getStrangeMatter() {
        return strangeMatter;
    }

    /** 把核心中储存的奇异物质喷出为物品：优先塞进玩家背包，放不下的丢在核心附近。 */
    public void ejectStrangeMatter(EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null || base.getWorld().isRemote || strangeMatter <= 0) {
            return;
        }
        Item item = GTCMItemList.QiYiWuZhi.getItem();
        if (item == null) {
            return;
        }
        World world = base.getWorld();
        long remaining = strangeMatter;
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, 64);
            ItemStack stack = new ItemStack(item, chunk);
            boolean stored = player != null && player.inventory.addItemStackToInventory(stack);
            if (!stored || stack.stackSize > 0) {
                world.spawnEntityInWorld(
                    new EntityItem(
                        world,
                        base.getXCoord() + 0.5D,
                        base.getYCoord() + 1.5D,
                        base.getZCoord() + 0.5D,
                        stack));
            }
            remaining -= chunk;
        }
        strangeMatter = 0L;
        base.markDirty();
    }

    private void updateTeamCoreOnline(boolean online) {
        synchronized (ACTIVE_CORES_BY_TEAM) {
            // 先清理旧队伍登记（队伍变更/停机/卸载都走这里）
            if (activeCoreTeamId != null) {
                Set<DysonCore> cores = ACTIVE_CORES_BY_TEAM.get(activeCoreTeamId);
                if (cores != null) {
                    cores.remove(this);
                    if (cores.isEmpty()) {
                        ACTIVE_CORES_BY_TEAM.remove(activeCoreTeamId);
                    }
                }
                activeCoreTeamId = null;
            }
            if (online) {
                UUID team = getTeamId();
                if (team != null) {
                    Set<DysonCore> cores = ACTIVE_CORES_BY_TEAM.computeIfAbsent(team, k -> new HashSet<>());
                    cores.add(this);
                    activeCoreTeamId = team;
                }
            }
        }
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

    public long getPersonalCloudComponents() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base == null || ownerUUID == null
            ? 0
            : DysonSphereSystem.getPlayerCloudComponents(base.getWorld(), ownerUUID);
    }

    public long getPersonalFrameComponents() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base == null || ownerUUID == null
            ? 0
            : DysonSphereSystem.getPlayerFrameComponents(base.getWorld(), ownerUUID);
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
        if (base == null || base.getWorld() == null || base.getWorld().isRemote) {
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
        // 奇异物质是独立判定：投料物品与货币两者都够才能点亮
        if (strangeMatter < upgrade.getShardCost()) {
            return false;
        }
        strangeMatter -= upgrade.getShardCost();
        base.markDirty();
        storage.unlockUpgrade(upgrade);
        markTeamDataDirty(base);
        return true;
    }

    public boolean tryRespecUpgrade(DysonUpgrade upgrade) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null || base.getWorld().isRemote) {
            return false;
        }
        DysonUpgradeStorage storage = DysonSphereSystem.getTeamUpgrades(base.getWorld(), getTeamId());
        if (storage == null || !storage.isUpgradeActive(upgrade)) {
            return false;
        }
        if (!storage.checkDependents(upgrade)) {
            return false;
        }
        // 洗点退还奇异物质
        strangeMatter += upgrade.getShardCost();
        base.markDirty();
        storage.respecUpgrade(upgrade);
        markTeamDataDirty(base);
        return true;
    }

    /** 从投料格的物品处理器向节点成本充入（神锻 payCost 模式）。 */
    public void payUpgradeFromHandler(DysonUpgrade upgrade, ItemStackHandler handler) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || handler == null || base.getWorld() == null || base.getWorld().isRemote) {
            return;
        }
        DysonUpgradeStorage storage = DysonSphereSystem.getTeamUpgrades(base.getWorld(), getTeamId());
        if (storage == null || !storage.checkPrerequisites(upgrade)) {
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
        private LongSyncValue strangeMatterSyncer;
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
            cloudComponentsSyncer = new LongSyncValue(multiblock::getPersonalCloudComponents);
            frameComponentsSyncer = new LongSyncValue(multiblock::getPersonalFrameComponents);
            strangeMatterSyncer = new LongSyncValue(multiblock::getStrangeMatter);
            syncManager.syncValue("dysonCloud", cloudSyncer);
            syncManager.syncValue("dysonFrame", frameSyncer);
            syncManager.syncValue("dysonPaste", pasteSyncer);
            syncManager.syncValue("dysonCloudComponents", cloudComponentsSyncer);
            syncManager.syncValue("dysonFrameComponents", frameComponentsSyncer);
            syncManager.syncValue("dysonStrangeMatter", strangeMatterSyncer);

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
                    createStrangeMatterButton(syncManager)
                        .marginTop(4))
                .child(createPowerSwitchButton())
                .child(createStructureUpdateButton(syncManager))
                .child(createUpgradeTreeButton(panel, syncManager));
        }

        /** 喷射奇异物质按钮：把核心中储存的货币喷出为物品。 */
        protected ToggleButton createStrangeMatterButton(PanelSyncManager syncManager) {
            BooleanSyncValue eject = new BooleanSyncValue(
                () -> false,
                val -> {
                    if (val) {
                        multiblock.ejectStrangeMatter(syncManager.getPlayer());
                    }
                }).allowC2S();
            return new ToggleButton()
                .size(18, 18)
                .value(eject)
                .overlay(UITexture.fullImage("eyeofharmonybuffer", "items/Arknights/QiYiWuZhi"))
                .tooltipBuilder(t -> t.addLine(IKey.str(Dyson_Gui_EjectMatter)))
                .tooltipShowUpTimer(TOOLTIP_DELAY);
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
                (panelSyncManager, handler) -> createUpgradeTreePanel(
                    panelSyncManager,
                    panel,
                    individualPanels,
                    handler));
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
                                                      IPanelHandler[] individualPanels, IPanelHandler selfPanel) {
            ModularPanel panel = new ModularPanel("dysonUpgradeTree")
                .size(300, 275);
            panel.background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);

            // 标题栏：深色顶条 + 居中标题
            panel.child(
                new Widget<>()
                    .pos(0, 0)
                    .size(300, 16)
                    .background(new Rectangle().setColor(0xFF202838)));
            panel.child(
                new TextWidget<>(IKey.lang("eohb.dyson.upgrade.tree"))
                    .pos(0, 0)
                    .size(300, 16)
                    .textAlign(Alignment.Center)
                    .color(Color.WHITE.main)
                    .shadow(true));
            panel.child(
                ButtonWidget.panelCloseButton());

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
                addLineSegment(panel, parentX, parentY, 2, childY - parentY);
            }
            if (childX != parentX) {
                int lineX = Math.min(parentX, childX);
                addLineSegment(panel, lineX, childY, Math.abs(childX - parentX) + 2, 2);
            }
        }

        /** 双色“轨道”连接线：暗色外轨 + 亮色内芯，作为背景绘制，节点盒子后添加会盖在线上面。 */
        private void addLineSegment(ModularPanel panel, int x, int y, int width, int height) {
            boolean vertical = height > width;
            int dx = vertical ? 1 : 0;
            int dy = vertical ? 0 : 1;
            panel.child(
                new Widget<>()
                    .pos(x - dx, y - dy)
                    .size(width + dx * 2, height + dy * 2)
                    .background(new Rectangle().setColor(0xFF1B2232)));
            panel.child(
                new Widget<>()
                    .pos(x, y)
                    .size(width, height)
                    .background(new Rectangle().setColor(0xFF5B79B5)));
        }

        protected IWidget createUpgradeNodeBox(DysonUpgrade upgrade, IPanelHandler individualPanel) {
            BooleanSyncValue active = activeSyncers[upgrade.ordinal()];
            return new ButtonWidget<>()
                .pos(upgrade.getTreeX(), upgrade.getTreeY())
                .size(36, 14)
                .background(GTGuiTextures.BUTTON_STANDARD)
                .hoverBackground(GTGuiTextures.BUTTON_STANDARD_PRESSED)
                .overlay(
                    new DynamicDrawable(
                        () -> new Rectangle()
                            .setColor(
                                active.getValue()
                                    ? 0x8024B05A
                                    : (upgrade.isMajor() ? 0x80D9A441 : 0x403A4D78))))
                .child(
                    new TextWidget<>(IKey.lang(upgrade.getShortNameKey()))
                        .size(36, 14)
                        .textAlign(Alignment.Center)
                        .color(Color.WHITE.main)
                        .shadow(true))
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
                .size(240, 150);
            panel.background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);

            // 标题栏：深色顶条 + 居中标题 + 标准关闭按钮
            panel.child(
                new Widget<>()
                    .pos(0, 0)
                    .size(240, 16)
                    .background(new Rectangle().setColor(0xFF202838)));
            panel.child(
                new TextWidget<>(StatCollector.translateToLocal(upgrade.getNameKey()))
                    .pos(0, 0)
                    .size(240, 16)
                    .textAlign(Alignment.Center)
                    .color(Color.WHITE.main)
                    .shadow(true));
            panel.child(
                ButtonWidget.panelCloseButton());
            panel.child(
                new TextWidget<>(StatCollector.translateToLocal(upgrade.getEffectKey()))
                    .pos(8, 24)
                    .size(224, 44));
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> costText(upgrade)))
                    .pos(8, 74)
                    .size(224, 12)
                    .textAlign(Alignment.Center));
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> prerequisiteText(upgrade)))
                    .pos(8, 88)
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
                    .background(false, GTGuiTextures.BUTTON_STANDARD)
                    .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> confirmLabel(upgrade)))
                            .size(100, 18)
                            .textAlign(Alignment.Center)
                            .color(Color.WHITE.main)
                            .shadow(true)));
            panel.child(
                new ButtonWidget<>()
                    .pos(175, 116)
                    .size(50, 18)
                    .background(GTGuiTextures.BUTTON_STANDARD)
                    .hoverBackground(GTGuiTextures.BUTTON_STANDARD_PRESSED)
                    .child(
                        new TextWidget<>(IKey.lang("eohb.dyson.upgrade.depositOpen"))
                            .size(50, 18)
                            .textAlign(Alignment.Center)
                            .color(Color.WHITE.main)
                            .shadow(true))
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
                .size(189, 190);
            panel.background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);

            // 标准关闭按钮（右上角）
            panel.child(
                ButtonWidget.panelCloseButton());

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
                        .background(new Rectangle().setColor(0xFF171D29))
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
                            && canDepositUpgrade(upgrade)
                            && stack != null
                            && GTUtility.areStacksEqual(stack, filterCost));
                } else {
                    slot.filter(stack -> selfPanel.isPanelOpen() && canDepositUpgrade(upgrade));
                }
                panel.child(
                    new ItemSlot()
                        .slot(slot)
                        // 左移一格，避开右上角关闭按钮
                        .pos(94 + (i % 4) * 18, 6 + (i / 4) * 18));
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
                    .background(
                        false,
                        new DynamicDrawable(
                            () -> canDepositUpgrade(upgrade)
                                ? GTGuiTextures.BUTTON_STANDARD
                                : GTGuiTextures.BUTTON_STANDARD_DISABLED))
                    .background(
                        true,
                        new DynamicDrawable(
                            () -> canDepositUpgrade(upgrade)
                                ? GTGuiTextures.BUTTON_STANDARD_PRESSED
                                : GTGuiTextures.BUTTON_STANDARD_DISABLED))
                    .child(
                        new TextWidget<>(
                            IKey.dynamic(
                                () -> canDepositUpgrade(upgrade)
                                    ? StatCollector.translateToLocal("eohb.dyson.upgrade.deposit")
                                    : EnumChatFormatting.DARK_RED
                                        + StatCollector.translateToLocal("eohb.dyson.upgrade.locked")))
                            .size(179, 18)
                            .textAlign(Alignment.Center)
                            .color(Color.WHITE.main)
                            .shadow(true)));

            // 玩家背包（下方 4 行），用于 Shift 点击批量填料到上方投料格
            panel.child(
                SlotGroupWidget.playerInventory(false)
                    .pos(5, 108));
            return panel;
        }

        /** 投料是否放行：无前置直接可投；有前置时按节点自身“全部/任一”语义检查前置是否已解锁。 */
        protected boolean canDepositUpgrade(DysonUpgrade upgrade) {
            DysonUpgrade[] prerequisites = upgrade.getPrerequisites();
            if (prerequisites.length == 0) {
                return true;
            }
            if (upgrade.requiresAllPrerequisites()) {
                for (DysonUpgrade prerequisite : prerequisites) {
                    if (!activeSyncers[prerequisite.ordinal()].getValue()) {
                        return false;
                    }
                }
                return true;
            }
            for (DysonUpgrade prerequisite : prerequisites) {
                if (activeSyncers[prerequisite.ordinal()].getValue()) {
                    return true;
                }
            }
            return false;
        }

        protected String confirmLabel(DysonUpgrade upgrade) {
            if (activeSyncers[upgrade.ordinal()].getValue()) {
                return StatCollector.translateToLocal("eohb.dyson.upgrade.respec");
            }
            boolean itemsPaid = !upgrade.hasExtraCost()
                || paidSyncers[upgrade.ordinal()].getValue() >= upgrade.getTotalItemCost();
            if (!itemsPaid) {
                return StatCollector.translateToLocal("eohb.dyson.upgrade.unpaid");
            }
            if (strangeMatterSyncer.getValue() < upgrade.getShardCost()) {
                return StatCollector.translateToLocal("eohb.dyson.upgrade.needMatter");
            }
            return StatCollector.translateToLocal("eohb.dyson.upgrade.unlock");
        }

        protected String costText(DysonUpgrade upgrade) {
            String matter = StatCollector.translateToLocalFormatted(
                "eohb.dyson.upgrade.matterCost",
                upgrade.getShardCost());
            if (!upgrade.hasExtraCost()) {
                return matter;
            }
            return matter
                + "  "
                + StatCollector.translateToLocalFormatted(
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
                    builder.append(upgrade.requiresAllPrerequisites() ? " + " : Dyson_Text_Or);
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
                .width(140)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .coverChildrenHeight(0)
                .child(makeStat(Dyson_Stat_Cloud, () -> String.valueOf(cloudSyncer.getValue())))
                .child(makeStat(Dyson_Stat_Frame, () -> String.valueOf(frameSyncer.getValue())))
                .child(makeStat(Dyson_Stat_Paste, () -> String.valueOf(pasteSyncer.getValue())))
                .child(makeStat(Dyson_Stat_StrangeMatter, () -> String.valueOf(strangeMatterSyncer.getValue())))
                .child(
                    makeStat(
                        Dyson_Stat_Components,
                        () -> Dyson_Stat_Cloud
                            + " "
                            + cloudComponentsSyncer.getValue()
                            + " / "
                            + Dyson_Stat_Frame
                            + " "
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

        // 奇异物质产出：按本队云与贴片数量决定，优先储存在核心中
        DysonSphereWorldData data = DysonSphereWorldData.get(base.getWorld());
        DysonTeamProgress team = data == null ? null : data.getTeam(getTeamId());
        if (team != null) {
            long gain = team.cloudCount / DysonMachineConfig.strangeMatterCloudDivisor
                + team.pasteCount / DysonMachineConfig.strangeMatterPasteDivisor;
            if (gain > 0) {
                strangeMatter += gain;
                base.markDirty();
            }
        }

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setLong("StrangeMatter", strangeMatter);
        super.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        strangeMatter = aNBT.hasKey("StrangeMatter") ? aNBT.getLong("StrangeMatter") : 0L;
        super.loadNBTData(aNBT);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) {
            return;
        }

        // 模块热插拔：模块槽位距离控制器 16+ 格，方块邻接更新传不到核心，
        // 而 GT 的初始结构检查跑完后就不再自动重查。这里每隔 ~2 秒强制一次结构重查，
        // 让玩家事后新放/替换的模块能被收集进 moduleHatches（不用拆核心重放）。
        if ((aTick % 40) == 0) {
            setStructureUpdateTime(5);
        }

        // 队伍归属上报：离队/被踢时由系统把升级树继承到个人
        if (ownerUUID != null) {
            DysonSphereSystem.trackPlayerTeam(
                aBaseMetaTileEntity.getWorld(),
                ownerUUID,
                aBaseMetaTileEntity.getOwnerName(),
                getTeamId());
        }

        // 维度强约束：只能在塔罗斯 2 运行，否则全部模块断开
        if (!DysonMachineConfig.isInTalos(aBaseMetaTileEntity.getWorld())) {
            disconnectAll();
            updateTeamCoreOnline(false);
            return;
        }

        // 完工锁：戴森球成型后，非胜利队伍的核心禁止开机
        DysonSphereWorldData sphereData = DysonSphereWorldData.get(aBaseMetaTileEntity.getWorld());
        if (sphereData != null && sphereData.isCompleted()
            && !(getTeamId() != null && getTeamId().equals(sphereData.getCompletedTeamId()))) {
            disableWorking();
            stopMachine(DysonModuleBase.DYSON_COMPLETED_REASON);
            disconnectAll();
            updateTeamCoreOnline(false);
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
                updateTeamCoreOnline(false);
                return;
            }
        } else if (owner != null && CORE_BY_OWNER.get(owner) == this) {
            CORE_BY_OWNER.remove(owner);
            updateTeamCoreOnline(false);
        }

        // 核心算力门控：不足则全部模块断开
        boolean computeOk = false;
        if (ownerUUID != null) {
            WirelessComputeHelper.updateConsumer(this);
            computeOk = WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
        }
        // 核心不在线（未成型 / 算力不足 / 被软锤停机）时全部模块断开
        // 注意：不能用 isActive()，无线模式下每轮 20 tick 之间 mMaxProgresstime 会短暂归 0，
        // 那会把模块每轮都断开重连，导致接收模块的每队唯一注册反复抖动。
        if (!mMachine || !computeOk || !aBaseMetaTileEntity.isAllowedToWork()) {
            disconnectAll();
            updateTeamCoreOnline(false);
            return;
        }
        updateTeamCoreOnline(true);

        World world = aBaseMetaTileEntity.getWorld();
        long worldTime = world.getTotalWorldTime();
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        DysonTeamProgress team = data == null ? null : data.getTeam(getTeamId());
        int paste = team == null ? 0 : team.pasteCount;
        int activeSlots = DysonMachineConfig.activeSlotsForPaste(paste);

        int connectedCount = 0;
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module == null) {
                continue;
            }
            // 被软锤关机的模块不允许占用连接名额；接收模块尤其不能占着“每队唯一”的名额
            if (!module.isFormed() || !module.isAllowedToWork()
                || connectedCount >= activeSlots || module.getRequiredPaste() > paste) {
                module.disconnect();
                continue;
            }
            // 接收模块每队至多 1 台：先注册成功的保持连接，后到的断开；失效后其他接收机可接管
            if (module.getModuleType() == DysonModuleBase.ModuleType.RECEIVER) {
                if (!DysonReceiverModule.tryRegisterTeamReceiver(getTeamId(), (DysonReceiverModule) module)) {
                    module.disconnect();
                    continue;
                }
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
        updateTeamCoreOnline(false);
        disconnectAll();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        updateTeamCoreOnline(false);
        disconnectAll();
        super.onUnload();
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
            EnumChatFormatting.AQUA + Dyson_Info_ConnectedModules
                + EnumChatFormatting.GOLD
                + connectedCount
                + EnumChatFormatting.AQUA
                + " / "
                + Dyson_Info_ActiveSlots
                + EnumChatFormatting.GOLD
                + DysonMachineConfig.activeSlotsForPaste(paste));
        lines.add(
            EnumChatFormatting.AQUA + Dyson_Info_TeamPaste
                + EnumChatFormatting.GOLD
                + paste);
        lines.add(
            EnumChatFormatting.AQUA + Dyson_Info_PersonalComponents
                + EnumChatFormatting.GOLD
                + getPersonalCloudComponents()
                + EnumChatFormatting.AQUA
                + " / "
                + Dyson_Stat_Frame
                + " "
                + EnumChatFormatting.GOLD
                + getPersonalFrameComponents());
        lines.add(
            EnumChatFormatting.AQUA + Dyson_Info_StrangeMatter
                + EnumChatFormatting.GOLD
                + strangeMatter);

        if (duplicateRejected) {
            lines.add(
                EnumChatFormatting.RED + Dyson_Info_DuplicateCore);
        }

        if (base != null && base.isServerSide()) {
            boolean computeOk = ownerUUID != null && WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
            lines.add(
                computeOk
                    ? EnumChatFormatting.GREEN + Dyson_Info_ComputeSatisfied
                    : EnumChatFormatting.RED + Dyson_Info_ComputeInsufficient);
        }
        return lines.toArray(new String[0]);
    }

    // ---- Waila：核心不接能量仓，显示队伍计数 / 槽位 / 个人资产 / 算力 ----

    @Override
    protected boolean shouldShowEuWirelessHud() {
        return false;
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        currentTip.add(
            EnumChatFormatting.AQUA + Dyson_Info_ConnectedModules
                + EnumChatFormatting.GOLD + tag.getInteger("dysonModules")
                + EnumChatFormatting.AQUA + " / " + Dyson_Info_ActiveSlots
                + EnumChatFormatting.GOLD + tag.getInteger("dysonSlots"));
        currentTip.add(
            EnumChatFormatting.AQUA + Dyson_Info_TeamPaste
                + EnumChatFormatting.GOLD + tag.getInteger("dysonPaste"));
        currentTip.add(
            EnumChatFormatting.AQUA + Dyson_Info_PersonalComponents
                + EnumChatFormatting.GOLD + tag.getLong("dysonCloud")
                + EnumChatFormatting.AQUA + " / " + Dyson_Stat_Frame + " "
                + EnumChatFormatting.GOLD + tag.getLong("dysonFrame"));
        currentTip.add(
            EnumChatFormatting.AQUA + Dyson_Info_StrangeMatter
                + EnumChatFormatting.GOLD + tag.getLong("dysonStrange"));
        if (tag.getBoolean("dysonDuplicate")) {
            currentTip.add(EnumChatFormatting.RED + Dyson_Info_DuplicateCore);
        }
        currentTip.add(
            tag.getBoolean("dysonComputeOk")
                ? EnumChatFormatting.GREEN + Dyson_Info_ComputeSatisfied
                : EnumChatFormatting.RED + Dyson_Info_ComputeInsufficient);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world,
                                int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        int connectedCount = 0;
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module != null && module.isConnected()) {
                connectedCount++;
            }
        }
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        DysonTeamProgress team = data == null ? null : data.getTeam(getTeamId());
        int paste = team == null ? 0 : team.pasteCount;
        tag.setInteger("dysonModules", connectedCount);
        tag.setInteger("dysonSlots", DysonMachineConfig.activeSlotsForPaste(paste));
        tag.setInteger("dysonPaste", paste);
        tag.setLong("dysonCloud", getPersonalCloudComponents());
        tag.setLong("dysonFrame", getPersonalFrameComponents());
        tag.setLong("dysonStrange", strangeMatter);
        tag.setBoolean("dysonDuplicate", duplicateRejected);
        tag.setBoolean(
            "dysonComputeOk",
            ownerUUID != null && WirelessComputeHelper.isConsumerSatisfiedInGroup(this));
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
        tt.addMachineType(Tooltip_DysonCore_MachineType)
            .addInfo(Tooltip_DysonCore_00)
            .addInfo(Tooltip_DysonCore_01)
            .addInfo(Tooltip_DysonCore_02)
            .addInfo(Tooltip_DysonCore_03)
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
