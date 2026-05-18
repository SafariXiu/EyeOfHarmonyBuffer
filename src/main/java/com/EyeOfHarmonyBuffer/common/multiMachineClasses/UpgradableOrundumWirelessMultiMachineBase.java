package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class UpgradableOrundumWirelessMultiMachineBase<T extends UpgradableOrundumWirelessMultiMachineBase<T>>
    extends OrundumWirelessMultiMachineBase<T> {

    public static class ChipConfig {
        public final int targetMaxParallel;
        public final int targetWirelessTime;
        public final int targetWirelessCycles;

        public ChipConfig(int targetMaxParallel, int targetWirelessTime, int targetWirelessCycles) {
            this.targetMaxParallel = targetMaxParallel;
            this.targetWirelessTime = targetWirelessTime;
            this.targetWirelessCycles = targetWirelessCycles;
        }
    }

    protected static final Map<Item, ChipConfig> CHIP_CONFIGS;

    static {
        Map<Item, ChipConfig> map = new HashMap<>();

        map.put(GTCMItemList.UpgradeChipsMK1.getItem(), new ChipConfig(
            16,
            100,
            1
        ));

        map.put(GTCMItemList.UpgradeChipsMK2.getItem(), new ChipConfig(
            64,
            50,
            1
        ));

        map.put(GTCMItemList.UpgradeChipsMK3.getItem(), new ChipConfig(
            512,
            20,
            1
        ));

        CHIP_CONFIGS = Collections.unmodifiableMap(map);
    }

    protected static final int DEFAULT_TARGET_MAX_PARALLEL = 1;
    protected static final int DEFAULT_TARGET_WIRELESS_TIME = 200;
    protected static final int DEFAULT_TARGET_WIRELESS_CYCLES = 1;

    protected Integer targetWirelessTimeTicks = DEFAULT_TARGET_WIRELESS_TIME;
    protected Integer targetMaxParallel = DEFAULT_TARGET_MAX_PARALLEL;
    protected Integer targetWirelessCycles = DEFAULT_TARGET_WIRELESS_CYCLES;

    public UpgradableOrundumWirelessMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public UpgradableOrundumWirelessMultiMachineBase(String aName) {
        super(aName);
    }

    protected ItemStack getUpgradeChipStack() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            return null;
        }
        if (base.getSizeInventory() <= 0) {
            return null;
        }
        return base.getStackInSlot(0);
    }

    protected void recalcControllerUpgrades() {
        this.targetWirelessTimeTicks = DEFAULT_TARGET_WIRELESS_TIME;
        this.targetMaxParallel = DEFAULT_TARGET_MAX_PARALLEL;
        this.targetWirelessCycles = DEFAULT_TARGET_WIRELESS_CYCLES;

        ItemStack stack = getUpgradeChipStack();
        if (stack == null) {
            return;
        }

        ChipConfig config = CHIP_CONFIGS.get(stack.getItem());
        if (config == null) {
            return;
        }

        if (config.targetMaxParallel > 0) {
            this.targetMaxParallel = config.targetMaxParallel;
        }
        if (config.targetWirelessTime > 0) {
            this.targetWirelessTimeTicks = config.targetWirelessTime;
        }
        if (config.targetWirelessCycles > 0) {
            this.targetWirelessCycles = config.targetWirelessCycles;
        }
    }

    protected int applyWirelessTimeUpgrades(int baseTime) {
        return Math.max(1, this.targetWirelessTimeTicks);
    }

    protected int applyParallelUpgrades(int baseParallel) {
        return Math.max(baseParallel, this.targetMaxParallel);
    }

    protected int applyCycleNumUpgrades(int baseCycle) {
        return Math.max(baseCycle, this.targetWirelessCycles);
    }

    @Override
    protected void prepareProcessing() {
        super.prepareProcessing();
        recalcControllerUpgrades();
    }

    @Override
    protected int getWirelessCycleNum() {
        int baseCycles = Math.max(1, this.cycleNum);
        return applyCycleNumUpgrades(baseCycles);
    }
}
