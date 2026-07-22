package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.IWirelessComputeConsumer;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.IWirelessComputeProvider;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessNodeRef;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
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
    public CheckRecipeResult wirelessModeProcessOnce() {
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

        BigInteger baseCost = BigInteger.valueOf(processingLogic.getCalculatedEut())
            .multiply(BigInteger.valueOf(processingLogic.getDuration()));

        int m = getExtraEUCostMultiplier();
        if (m > 1) {
            baseCost = baseCost.multiply(BigInteger.valueOf(m));
        }

        BigInteger orundumCost = convertEuCostToOrundum(baseCost);

        BigInteger demand = getRequiredComputeForCurrentRecipe();

        if (demand != null && demand.signum() > 0 && ownerUUID != null) {
            WirelessComputeHelper.updateConsumer(this);

            boolean satisfied = WirelessComputeHelper.isConsumerSatisfiedInGroup(this);

            if (!satisfied) {
                endRecipeProcessing();
                return SimpleCheckRecipeResult.ofFailure("InsufficientCompute");
            }
        }

        if (!consumeOrundumForOwner(ownerUUID, orundumCost)) {
            endRecipeProcessing();
            return CheckRecipeResultRegistry.insufficientPower(safeToLong(orundumCost));
        }

        this.costingEU = this.costingEU.add(orundumCost);
        this.costingEUText = NumberFormatUtil.formatNumber(this.costingEU);

        mOutputItems = mergeArray(mOutputItems, processingLogic.getOutputItems());
        mOutputFluids = mergeArray(mOutputFluids, processingLogic.getOutputFluids());

        endRecipeProcessing();
        return result;
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
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        WirelessComputeHelper.unregisterConsumer(this);
        WirelessComputeHelper.unregisterProvider(this);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        WirelessComputeHelper.unregisterConsumer(this);
        WirelessComputeHelper.unregisterProvider(this);
    }
}
