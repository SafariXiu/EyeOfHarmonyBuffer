package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTUtility;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static com.EyeOfHarmonyBuffer.utils.Utils.mergeArray;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;

public abstract class OrundumWirelessMultiMachineBase<T extends OrundumWirelessMultiMachineBase<T>>
    extends WirelessEnergyMultiMachineBase<T> {

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
            return result;
        }

        BigInteger baseCost = BigInteger.valueOf(processingLogic.getCalculatedEut())
            .multiply(BigInteger.valueOf(processingLogic.getDuration()));

        int m = getExtraEUCostMultiplier();
        if (m > 1) {
            baseCost = baseCost.multiply(BigInteger.valueOf(m));
        }

        BigInteger orundumCost = convertEuCostToOrundum(baseCost);

        if (!consumeOrundumForOwner(ownerUUID, orundumCost)) {
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
        return "Current Orundum Cost";
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
    }
}
