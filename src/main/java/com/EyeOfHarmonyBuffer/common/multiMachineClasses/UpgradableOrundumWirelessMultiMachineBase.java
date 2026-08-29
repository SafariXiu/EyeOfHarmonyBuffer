package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class UpgradableOrundumWirelessMultiMachineBase<T extends UpgradableOrundumWirelessMultiMachineBase<T>>
    extends OrundumWirelessMultiMachineBase<T> {

    public static class ChipConfig {
        public final int targetMaxParallel;
        public final int targetWirelessTime;

        public ChipConfig(int targetMaxParallel, int targetWirelessTime) {
            this.targetMaxParallel = targetMaxParallel;
            this.targetWirelessTime = targetWirelessTime;
        }
    }

    protected static final Map<Item, ChipConfig> CHIP_CONFIGS;

    static {
        Map<Item, ChipConfig> map = new HashMap<>();

        map.put(GTCMItemList.UpgradeChipsMK1.getItem(), new ChipConfig(
            16,
            100
        ));

        map.put(GTCMItemList.UpgradeChipsMK2.getItem(), new ChipConfig(
            64,
            50
        ));

        map.put(GTCMItemList.UpgradeChipsMK3.getItem(), new ChipConfig(
            512,
            20
        ));

        CHIP_CONFIGS = Collections.unmodifiableMap(map);
    }

    protected static final int DEFAULT_BASE_MAX_PARALLEL = 1;
    protected static final int DEFAULT_BASE_WIRELESS_TIME = 200;

    protected int targetWirelessTimeTicks = DEFAULT_BASE_WIRELESS_TIME;
    protected int targetMaxParallel = DEFAULT_BASE_MAX_PARALLEL;

    public UpgradableOrundumWirelessMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public UpgradableOrundumWirelessMultiMachineBase(String aName) {
        super(aName);
    }

    @Nullable
    protected ItemStack getUpgradeChipStack() {
        return getControllerSlot();
    }

    protected void recalcControllerUpgrades() {
        this.targetWirelessTimeTicks = DEFAULT_BASE_WIRELESS_TIME;
        this.targetMaxParallel = DEFAULT_BASE_MAX_PARALLEL;

        ItemStack stack = getUpgradeChipStack();
        if (stack == null) return;

        ChipConfig config = CHIP_CONFIGS.get(stack.getItem());
        if (config == null) return;

        if (config.targetMaxParallel > 0) {
            this.targetMaxParallel = config.targetMaxParallel;
        }
        if (config.targetWirelessTime > 0) {
            this.targetWirelessTimeTicks = config.targetWirelessTime;
        }
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag,
                                World world, int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);

        String displayName = getLocalName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = getInventoryName();
        }
        if (displayName == null) {
            displayName = this.mName;
        }

        int wirelessTime = getWirelessModeProcessingTime();
        int maxParallel = getMaxParallelRecipes();
        int cycles = getWirelessCycleNum();

        tag.setString("UOWM_MachineName", displayName);
        tag.setInteger("UOWM_WirelessTime", wirelessTime);
        tag.setInteger("UOWM_MaxParallel", maxParallel);
        tag.setInteger("UOWM_CycleNum", cycles);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);

        NBTTagCompound tag = accessor.getNBTData();
        if (tag == null) return;

        String machineName = tag.getString("UOWM_MachineName");
        int wirelessTime = tag.getInteger("UOWM_WirelessTime");
        int maxParallel = tag.getInteger("UOWM_MaxParallel");
        int cycleNum = tag.getInteger("UOWM_CycleNum");

        currentTip.add(EnumChatFormatting.DARK_AQUA + "【" +
            (machineName == null || machineName.isEmpty() ? "无线加工机" : machineName) + "】");
        currentTip.add(EnumChatFormatting.AQUA + "无线加工时间: "
            + EnumChatFormatting.GOLD + wirelessTime + " tick");
        currentTip.add(EnumChatFormatting.AQUA + "最大并行数量: "
            + EnumChatFormatting.GOLD + maxParallel + " 路");
        currentTip.add(EnumChatFormatting.AQUA + "跨配方并行数量: "
            + EnumChatFormatting.GOLD + cycleNum + " 次");
    }

    @Override
    protected void prepareProcessing() {
        super.prepareProcessing();
        recalcControllerUpgrades();
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return Math.max(1, targetWirelessTimeTicks);
    }

    @Override
    public int getMaxParallelRecipes() {
        return Math.max(1, targetMaxParallel);
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 0.0F;
    }
}
