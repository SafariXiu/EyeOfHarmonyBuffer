package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.common.misc.LinkNodeEntry;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.misc.OrundumLinkNetworkData;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.IWirelessComputeConsumer;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.IWirelessComputeProvider;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessNodeRef;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizons.angelica.shadow.javax.annotation.Nonnull;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Waila_OrundumCost;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Waila_OrundumFarallel;
import static com.EyeOfHarmonyBuffer.utils.Utils.mergeArray;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;

public abstract class OrundumWirelessMultiMachineBase<T extends OrundumWirelessMultiMachineBase<T>>
    extends WirelessEnergyMultiMachineBase<T>
    implements IWirelessComputeConsumer, IWirelessComputeProvider {

    protected int lastUsedParallel = 0;

    protected WirelessNodeRef wirelessNodeRefCache;

    protected BigInteger lastOrundumCost = BigInteger.ZERO;

    protected UUID linkNetworkNodeId = null;
    protected boolean lastPhysicalOnlineForLink = false;

    public OrundumWirelessMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public OrundumWirelessMultiMachineBase(String aName) {
        super(aName);
    }

    @Override
    protected boolean shouldShowEuWirelessHud() {
        return false;
    }

    @Override
    @Nonnull
    public final CheckRecipeResult checkProcessing() {
        costingEU = BigInteger.ZERO;
        costingEUText = ZERO_STRING;
        prepareProcessing();

        if (!wirelessMode) {
            return doNormalModeCheck();
        }

        boolean succeeded = false;
        CheckRecipeResult finalResult = CheckRecipeResultRegistry.SUCCESSFUL;

        int cycles = getWirelessCycleNum();
        for (int i = 0; i < cycles; i++) {
            CheckRecipeResult r = wirelessModeProcessOnce();
            if (!r.wasSuccessful()) {
                finalResult = r;
                break;
            }
            succeeded = true;
        }

        updateSlots();
        if (!succeeded) return finalResult;

        costingEUText = NumberFormatUtil.formatNumber(costingEU);

        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = getWirelessModeProcessingTime();

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 普通（非无线）模式下的配方检查逻辑。
     * 默认：沿用原来 WirelessEnergyMultiMachineBase 的实现。
     *
     * 绝大多数 Orundum 机器都是全无线，可以不用覆写。
     * 如果以后有需要非无线的 Orundum 机子，再在子类里覆写这个方法。
     */
    protected CheckRecipeResult doNormalModeCheck() {
        return super.checkProcessing();
    }

    @Override
    public final CheckRecipeResult wirelessModeProcessOnce() {
        CheckRecipeResult pre = wirelessPreCheck();
        if (!pre.wasSuccessful()) {
            this.lastUsedParallel = 0;
            this.mOutputItems = null;
            this.mOutputFluids = null;
            this.mProgresstime = 0;
            this.mMaxProgresstime = 0;
            return pre;
        }

        CheckRecipeResult op = doWirelessBusinessOnce();
        if (!op.wasSuccessful()) {
            this.lastUsedParallel = 0;
            this.mOutputItems = null;
            this.mOutputFluids = null;
            this.mProgresstime = 0;
            this.mMaxProgresstime = 0;
            return op;
        }

        CheckRecipeResult post = wirelessPostProcess(op);
        if (!post.wasSuccessful()) {
            this.lastUsedParallel = 0;
            this.mOutputItems = null;
            this.mOutputFluids = null;
            this.mProgresstime = 0;
            this.mMaxProgresstime = 0;
            return post;
        }

        return post;
    }

    /**
     * 通用无线前置检查：
     * - 默认：需要 Orundum 场 && 不在场内 → 失败。
     * 子类如有特殊要求可以覆写，但请记得 super。
     */
    protected CheckRecipeResult wirelessPreCheck() {
        if (shouldRequireOrundumField() && !isInOrundumField()) {
            return SimpleCheckRecipeResult.ofFailure("NotInOrundumField");
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 无线模式下一次“业务周期”的逻辑。
     *
     * 默认实现：沿用你之前的无线 EU→Orundum 计算那套：
     * - 调用 startRecipeProcessing + setupProcessingLogic；
     * - 用 processingLogic 跑一次配方；
     * - 记录并行数 lastUsedParallel；
     * - 根据 eut * duration * extraMultiplier 算出 baseCost；
     * - 转换为 Orundum 成本写入 lastOrundumCost；
     * - 不做真正的扣费，扣费放到 wirelessPostProcess 里。
     *
     * 特殊机器（矿机、发电机）可以覆写这个方法，自己：
     * - 决定产物 / 时长 / EUt；
     * - 决定当次 Orundum 成本（直接写 lastOrundumCost）。
     */
    protected CheckRecipeResult doWirelessBusinessOnce() {
        if (!isRecipeProcessing) startRecipeProcessing();
        setupProcessingLogic(processingLogic);
        setupWirelessProcessingPowerLogic(processingLogic);

        CheckRecipeResult result = doCheckRecipe();
        if (!result.wasSuccessful()) {
            this.lastUsedParallel = 0;
            return result;
        }

        int parallels = 0;
        if (this.processingLogic != null) {
            parallels = this.processingLogic.getCurrentParallels();
        }
        this.lastUsedParallel = Math.max(0, parallels);

        prepareWirelessCostFromProcessingLogic();

        return result;
    }

    /** 这台机器是否需要按配方消耗 Orundum（以及在 costingEUText 里累计 Orundum 成本） */
    protected boolean usesOrundumCost() {
        return true;
    }

    /** 这台机器是否作为无线算力 Consumer 参与算力网络（需要 D） */
    protected boolean actsAsComputeConsumer() {
        return true;
    }

    /**
     * 默认根据 processingLogic 计算本次无线周期的 Orundum 成本，
     * 结果写入 lastOrundumCost。
     */
    protected void prepareWirelessCostFromProcessingLogic() {
        this.lastOrundumCost = BigInteger.ZERO;

        if (!usesOrundumCost()) {
            return;
        }

        if (processingLogic == null) {
            return;
        }

        long eut = processingLogic.getCalculatedEut();
        int duration = processingLogic.getDuration();
        if (duration <= 0) {
            return;
        }

        BigInteger baseCost = BigInteger
            .valueOf(eut)
            .multiply(BigInteger.valueOf(duration));

        int m = getExtraEUCostMultiplier();
        if (m > 1) {
            baseCost = baseCost.multiply(BigInteger.valueOf(m));
        }

        BigInteger orundumCost = convertEuCostToOrundum(baseCost);
        if (orundumCost == null) {
            orundumCost = BigInteger.ZERO;
        }

        this.lastOrundumCost = orundumCost.max(BigInteger.ZERO);
    }

    protected BigInteger getPreparedOrundumCost() {
        return lastOrundumCost == null ? BigInteger.ZERO : lastOrundumCost;
    }

    /**
     * 返回本次无线周期的额外 EU 成本（以无线 EU 的形式扣）。
     *
     * 默认：0（不额外消耗）。
     */
    protected BigInteger getExtraWirelessEuCostForCycle() {
        return BigInteger.ZERO;
    }

    /**
     * 通用后置处理：
     * - 按需检查算力并更新 Consumer；
     * - 按需扣除 Orundum；
     * - 按需扣除额外无线 EU；
     * - 合并输出、结束配方处理。
     *
     * 子类若有极特殊需求，可以覆写（一般不需要）。
     */
    protected CheckRecipeResult wirelessPostProcess(CheckRecipeResult opResult) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.isDead()) {
            endRecipeProcessing();
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (actsAsComputeConsumer() && ownerUUID != null) {
            BigInteger demand = getRequiredComputeForCurrentRecipe();
            if (demand != null && demand.signum() > 0) {
                WirelessComputeHelper.updateConsumer(this);
                boolean satisfied = WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
                if (!satisfied) {
                    endRecipeProcessing();
                    return SimpleCheckRecipeResult.ofFailure("InsufficientCompute");
                }
            }
        }

        BigInteger extraEu = getExtraWirelessEuCostForCycle();
        if (extraEu != null && extraEu.signum() > 0 && ownerUUID != null) {
            if (!hasEnoughWirelessEU(ownerUUID, extraEu)) {
                endRecipeProcessing();
                return CheckRecipeResultRegistry.insufficientPower(safeToLong(extraEu));
            }
        }

        if (usesOrundumCost()) {
            BigInteger orundumCost = getPreparedOrundumCost();
            if (!consumeOrundumForOwner(ownerUUID, orundumCost)) {
                endRecipeProcessing();
                return CheckRecipeResultRegistry.insufficientPower(safeToLong(orundumCost));
            }

            this.costingEU = this.costingEU.add(orundumCost);
            this.costingEUText = NumberFormatUtil.formatNumber(this.costingEU);
        }

        if (extraEu != null && extraEu.signum() > 0 && ownerUUID != null) {
            consumeWirelessEUForOwner(ownerUUID, extraEu);
            addExtraEUToCostingText(extraEu);
        }

        if (processingLogic != null) {
            mOutputItems = mergeArray(mOutputItems, processingLogic.getOutputItems());
            mOutputFluids = mergeArray(mOutputFluids, processingLogic.getOutputFluids());
        }

        endRecipeProcessing();
        return opResult;
    }

    /** EU 成本 → Orundum 成本的换算（默认 1:1） */
    protected BigInteger convertEuCostToOrundum(BigInteger euCost) {
        return euCost;
    }

    /** EU → Orundum 的换算（用于产出/退还场景，默认 1:1） */
    protected BigInteger convertEuToOrundum(BigInteger eu) {
        return eu;
    }

    /** 从 owner 扣除 Orundum，失败返回 false。 */
    protected boolean consumeOrundumForOwner(UUID owner, BigInteger orundumCost) {
        if (owner == null || orundumCost == null) return false;
        if (orundumCost.signum() <= 0) return true;
        return OrundumEnergyService.changeOrundumForUser(owner, orundumCost.negate());
    }

    protected boolean consumeOrundumForOwner(UUID owner, long orundumCost) {
        if (orundumCost <= 0) return true;
        return consumeOrundumForOwner(owner, BigInteger.valueOf(orundumCost));
    }

    /** 向 owner 注入 Orundum（产出 / 返还）。 */
    protected boolean produceOrundumForOwner(UUID owner, BigInteger orundumAmount) {
        if (owner == null || orundumAmount == null) return false;
        if (orundumAmount.signum() <= 0) return true;
        return OrundumEnergyService.changeOrundumForUser(owner, orundumAmount);
    }

    protected boolean produceOrundumForOwner(UUID owner, long orundumAmount) {
        if (orundumAmount <= 0) return true;
        return produceOrundumForOwner(owner, BigInteger.valueOf(orundumAmount));
    }

    /**
     * 把一笔 EU 等价值按 convertEuToOrundum 换算后施加到 owner 上：
     * 正数 → 产出 Orundum；负数 → 扣 Orundum。
     */
    protected void applyEuAsOrundum(BigInteger eu) {
        if (eu == null || eu.signum() == 0 || ownerUUID == null) return;

        BigInteger orundumDelta = convertEuToOrundum(eu);
        int sign = orundumDelta.signum();
        if (sign > 0) {
            produceOrundumForOwner(ownerUUID, orundumDelta);
        } else if (sign < 0) {
            consumeOrundumForOwner(ownerUUID, orundumDelta.negate());
        }
    }

    /** 尝试扣除 owner 的无线 EU；失败时不会扣任何资源。 */
    protected boolean consumeWirelessEUForOwner(UUID owner, BigInteger euCost) {
        if (owner == null || euCost == null) return false;
        if (euCost.signum() <= 0) return true;
        return addEUToGlobalEnergyMap(owner, euCost.negate());
    }

    protected boolean consumeWirelessEUForOwner(UUID owner, long euCost) {
        return consumeWirelessEUForOwner(owner, BigInteger.valueOf(euCost));
    }

    /** 仅查询 EU 是否够用，不扣费。 */
    protected boolean hasEnoughWirelessEU(UUID owner, BigInteger euCost) {
        if (owner == null || euCost == null) return false;
        if (euCost.signum() <= 0) return true;
        return getUserEU(owner).compareTo(euCost) >= 0;
    }

    /** 向 owner 注入 EU（发电产出）。 */
    protected void produceWirelessEUForOwner(UUID owner, BigInteger euAmount) {
        if (owner == null || euAmount == null || euAmount.signum() <= 0) return;
        addEUToGlobalEnergyMap(owner, euAmount);
    }

    protected void produceWirelessEUForOwner(UUID owner, long euAmount) {
        if (euAmount <= 0) return;
        produceWirelessEUForOwner(owner, BigInteger.valueOf(euAmount));
    }

    /**
     * 退还 EU（语义上区别于 produce，用于"扣多了/机制失败回滚"等场景）。
     * 实现复用 produce。
     */
    protected void refundWirelessEUToOwner(UUID owner, BigInteger euAmount) {
        produceWirelessEUForOwner(owner, euAmount);
    }

    /**
     * 把额外的一笔消耗/产出累加到 costingEU 显示文本里。
     * 不调用就不影响原显示。
     */
    protected void addExtraEUToCostingText(BigInteger extra) {
        if (extra == null || extra.signum() == 0) return;
        this.costingEU = this.costingEU.add(extra);
        this.costingEUText = NumberFormatUtil.formatNumber(this.costingEU);
    }

    /** BigInteger 安全转 long，超出范围则截断到 Long.MAX_VALUE。 */
    protected static long safeToLong(BigInteger value) {
        if (value == null) return 0L;
        if (value.bitLength() < 63) return value.longValue();
        return value.signum() < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    /** Waila 中"消耗/产出"那一行左边的标签文字。 */
    protected String getWailaCostLabel() {
        return EOHB_Waila_OrundumCost;
    }

    /** Waila 中“并行数”这一行的本地化 key，默认可返回一个通用 key。 */
    protected String getWailaParallelLabelKey() {
        return EOHB_Waila_OrundumFarallel;
    }

    /** 是否在 Waila 中显示“当前并行数”这一行，默认：NBT 里有这个字段就显示。 */
    protected boolean shouldShowWailaParallel(NBTTagCompound tag) {
        return tag.hasKey("wirelessParallel");
    }

    /** 读取用于 Waila 显示的并行数，子类可覆盖自定义逻辑。 */
    protected int getWailaParallelValue(NBTTagCompound tag) {
        return tag.getInteger("wirelessParallel");
    }

    /** Waila 中显示数值后的单位文字。 */
    protected String getWailaCostUnit() {
        return "Orundum";
    }

    /** 是否在 Waila 中显示无线模式那一段（发电机或特殊机制可重写为 false）。 */
    protected boolean shouldShowWirelessWaila(NBTTagCompound tag) {
        return tag.getBoolean("wirelessMode");
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        final NBTTagCompound tag = accessor.getNBTData();
        if (!shouldShowWirelessWaila(tag)) return;

        currentTip.add(EnumChatFormatting.LIGHT_PURPLE + TextLocalization.Waila_WirelessMode);
        currentTip.add(
            EnumChatFormatting.AQUA + getWailaCostLabel()
                + EnumChatFormatting.RESET
                + ": "
                + EnumChatFormatting.GOLD
                + tag.getString("costingEUText")
                + EnumChatFormatting.RESET
                + " "
                + getWailaCostUnit());

        if (shouldShowWailaParallel(tag)) {
            int parallels = getWailaParallelValue(tag);
            String label = StatCollector.translateToLocal(getWailaParallelLabelKey());
            currentTip.add(
                EnumChatFormatting.AQUA + label
                    + EnumChatFormatting.RESET
                    + ": "
                    + EnumChatFormatting.GOLD
                    + parallels
            );
        }
    }

    @Override
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    /** 构造 WirelessNodeRef（玩家 + 维度 + 坐标） */
    @Override
    public WirelessNodeRef getWirelessNodeRef() {
        if (wirelessNodeRefCache == null) {
            if (ownerUUID == null || getBaseMetaTileEntity() == null) {
                return null;
            }
            int dimId = getBaseMetaTileEntity().getWorld().provider.dimensionId;
            int x = getBaseMetaTileEntity().getXCoord();
            int y = getBaseMetaTileEntity().getYCoord();
            int z = getBaseMetaTileEntity().getZCoord();
            wirelessNodeRefCache = new WirelessNodeRef(dimId, x, y, z);
        }
        return wirelessNodeRefCache;
    }

    /** 这台机器在无线模式下是否必须处在 Orundum 场内 */
    protected boolean shouldRequireOrundumField() {
        return true;
    }

    protected boolean isInOrundumField() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return false;
        World world = base.getWorld();
        if (world == null) return false;
        if (ownerUUID == null) return false;

        return OrundumFieldHelper.isPositionCoveredForUser(
            world,
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            ownerUUID
        );
    }

    /**
     * 子类可以重写，决定“当前配方需要多少算力 D”。
     * 默认返回 0：代表这台机器不需要无线算力。
     */
    protected BigInteger getRequiredComputeForCurrentRecipe() {
        return BigInteger.ZERO;
    }

    /** 当前这次配方需要的算力需求 D，默认 0（不使用算力） */
    @Override
    public BigInteger getRequiredCompute() {
        BigInteger d = getRequiredComputeForCurrentRecipe();
        return d == null ? BigInteger.ZERO : d;
    }

    /**
     * 子类可以重写，决定“当前状态提供多少算力 S”。
     * 默认返回 0：代表这台机器不提供算力。
     */
    protected BigInteger getProvidedComputeForCurrentState() {
        return BigInteger.ZERO;
    }

    @Override
    public BigInteger getProvidedCompute() {
        BigInteger s = getProvidedComputeForCurrentState();
        return s == null ? BigInteger.ZERO : s;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            if (ownerUUID != null) {
                BigInteger supply = getProvidedComputeForCurrentState();
                if (supply != null && supply.signum() > 0) {
                    WirelessComputeHelper.updateProvider(this);
                } else {
                    WirelessComputeHelper.unregisterProvider(this);
                }
            } else {
                WirelessComputeHelper.unregisterProvider(this);
            }

            tickOrundumLinkNetwork(aBaseMetaTileEntity);
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        WirelessComputeHelper.unregisterConsumer(this);
        WirelessComputeHelper.unregisterProvider(this);

        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide() && linkNetworkNodeId != null) {
            World world = base.getWorld();
            if (world != null) {
                OrundumLinkNetworkData data = OrundumLinkNetworkData.get(world);
                if (data != null) {
                    data.removeNode(linkNetworkNodeId);
                }
            }
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        WirelessComputeHelper.unregisterConsumer(this);
        WirelessComputeHelper.unregisterProvider(this);

        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide() && linkNetworkNodeId != null) {
            World world = base.getWorld();
            if (world != null) {
                OrundumLinkNetworkData data = OrundumLinkNetworkData.get(world);
                if (data != null) {
                    data.setNodeOfflineOnUnload(linkNetworkNodeId);
                }
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        if (linkNetworkNodeId != null) {
            nbt.setString("EOHB_NetworkNodeId", linkNetworkNodeId.toString());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        if (nbt.hasKey("EOHB_NetworkNodeId")) {
            try {
                linkNetworkNodeId = UUID.fromString(nbt.getString("EOHB_NetworkNodeId"));
            } catch (IllegalArgumentException ignored) {
                linkNetworkNodeId = null;
            }
        }
    }

    /**
     * 这台机器在 Orundum 链路网络中的节点类型。
     * 默认 null：不参与 Orundum 链路。
     *
     * 子类：
     * - EOHB_ProtocolCore  -> PROTOCOL_CORE
     * - EOHB_RelayTower    -> REPEATER
     * - EOHB_ElectricPylon -> SUBSTATION
     */
    protected LinkNodeEntry.NodeType getOrundumLinkNodeType() {
        return null;
    }

    public LinkNodeEntry.NodeType getNodeTypeForConnector() {
        return getOrundumLinkNodeType();
    }

    /**
     * 在链路语义下的“物理在线”条件。
     * 默认：多方块成型 && 允许工作。
     * 子类如有特殊需求可以重写。
     */
    protected boolean isPhysicalOnlineForOrundumLink() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return mMachine && base != null && base.isAllowedToWork();
    }

    /**
     * 每个服务器 tick 更新本机在 Orundum 链路网络中的状态：
     * - 注册/更新节点信息；
     * - 更新 physicalOnline；
     */
    protected void tickOrundumLinkNetwork(IGregTechTileEntity base) {
        if (base == null || !base.isServerSide()) return;

        LinkNodeEntry.NodeType nodeType = getOrundumLinkNodeType();
        if (nodeType == null) return;

        World world = base.getWorld();
        if (world == null) return;

        OrundumLinkNetworkData data = OrundumLinkNetworkData.get(world);
        if (data == null) return;

        if (linkNetworkNodeId == null) {
            linkNetworkNodeId = UUID.randomUUID();
        }

        UUID owner = this.ownerUUID;
        UUID teamId = null;
        if (owner != null) {
            teamId = OrundumEnergyService.getTeamIdForUser(owner);
            if (teamId == null) {
                teamId = owner;
            }
        }
        if (teamId == null) return;

        int dimId = world.provider.dimensionId;
        int x = base.getXCoord();
        int y = base.getYCoord();
        int z = base.getZCoord();

        data.registerOrUpdateNode(
            linkNetworkNodeId,
            nodeType,
            teamId,
            dimId,
            x, y, z
        );

        boolean physicalOnlineNow = isPhysicalOnlineForOrundumLink();
        if (physicalOnlineNow != lastPhysicalOnlineForLink) {
            lastPhysicalOnlineForLink = physicalOnlineNow;
            data.updatePhysicalOnline(linkNetworkNodeId, physicalOnlineNow);
        }

        /*System.out.println("[EOHB][Link] nodeType=" + nodeType +
            " owner=" + owner +
            " team=" + teamId +
            " pos=(" + x + "," + y + "," + z + ")");*/
    }

    /**
     * 提供给物品 / 交互逻辑使用的节点 ID。
     * 仅用于 OrundumLinkNetwork 逻辑，不要乱改。
     */
    public UUID getOrundumLinkNodeId() {
        return linkNetworkNodeId;
    }
}
