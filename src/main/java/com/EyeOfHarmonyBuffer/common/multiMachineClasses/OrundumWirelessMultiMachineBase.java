package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
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

public abstract class OrundumWirelessMultiMachineBase<T extends OrundumWirelessMultiMachineBase<T>>
    extends WirelessEnergyMultiMachineBase<T>{

    public OrundumWirelessMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public OrundumWirelessMultiMachineBase(String aName) {
        super(aName);
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
            return CheckRecipeResultRegistry.insufficientPower(orundumCost.longValue());
        }

        this.costingEU = this.costingEU.add(orundumCost);
        this.costingEUText = GTUtility.formatNumbers(this.costingEU);

        mOutputItems = mergeArray(mOutputItems, processingLogic.getOutputItems());
        mOutputFluids = mergeArray(mOutputFluids, processingLogic.getOutputFluids());

        endRecipeProcessing();
        return result;
    }

    protected BigInteger convertEuCostToOrundum(BigInteger euCost) {
        return euCost;
    }

    protected boolean consumeOrundumForOwner(UUID owner, BigInteger orundumCost) {
        if (owner == null || orundumCost == null) return false;

        return OrundumEnergyService.changeOrundumForUser(owner, orundumCost.negate());
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("wirelessMode")) {
            currentTip.add(EnumChatFormatting.LIGHT_PURPLE + TextLocalization.Waila_WirelessMode);
            currentTip.add(
                EnumChatFormatting.AQUA + "Current Orundum Cost"
                    + EnumChatFormatting.RESET
                    + ": "
                    + EnumChatFormatting.GOLD
                    + tag.getString("costingEUText")
                    + EnumChatFormatting.RESET
                    + " Orundum");
        }
    }

    @Override
    public boolean getDefaultWirelessMode() {
        return true;
    }
}
