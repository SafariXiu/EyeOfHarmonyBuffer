package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;

import gregtech.api.util.GTUtility;

/**
 * 戴森升级树存储：解锁/洗点/前置/分叉/依赖校验与 NBT。
 * 材料成本最多 12 种，支持 {@link #payFromHandler} 从物品处理器批量充入（当前为圆石占位）。
 */
public class DysonUpgradeStorage {

    private final EnumMap<DysonUpgrade, UpgradeData> unlockedUpgrades = new EnumMap<>(DysonUpgrade.class);

    public DysonUpgradeStorage() {
        for (DysonUpgrade upgrade : DysonUpgrade.VALUES) {
            unlockedUpgrades.put(upgrade, new UpgradeData());
        }
        // START 核心激活默认已解锁
        unlockedUpgrades.get(DysonUpgrade.START).active = true;
    }

    public boolean isUpgradeActive(DysonUpgrade upgrade) {
        return getData(upgrade).active;
    }

    public boolean isCostPaid(DysonUpgrade upgrade) {
        return getData(upgrade).costPaid;
    }

    public short[] getPaidAmounts(DysonUpgrade upgrade) {
        return getData(upgrade).amountsPaid;
    }

    public int getTotalPaid(DysonUpgrade upgrade) {
        int total = 0;
        for (short paid : getData(upgrade).amountsPaid) {
            total += paid;
        }
        return total;
    }

    /**
     * 向某节点的材料成本充入一件物品，返回实际充入数量（0 = 不匹配或已满）。
     * 调用方负责按返回值扣减手中的物品。
     */
    public int deposit(DysonUpgrade upgrade, ItemStack stack) {
        if (upgrade == null || stack == null || !upgrade.hasExtraCost()) {
            return 0;
        }
        UpgradeData data = getData(upgrade);
        ItemStack[] costs = upgrade.getExtraCost();
        for (int j = 0; j < costs.length; j++) {
            ItemStack costStack = costs[j];
            if (!GTUtility.areStacksEqual(stack, costStack)) {
                continue;
            }
            int need = costStack.stackSize - data.amountsPaid[j];
            if (need <= 0) {
                return 0;
            }
            int accepted = Math.min(stack.stackSize, need);
            data.amountsPaid[j] += accepted;
            refreshCostPaid(upgrade, data);
            return accepted;
        }
        return 0;
    }

    /** 从物品处理器按材料成本逐个匹配扣除（神锻 payCost 模式）。 */
    public void payFromHandler(DysonUpgrade upgrade, ItemStackHandler handler) {
        if (upgrade == null || handler == null || !upgrade.hasExtraCost()) {
            return;
        }
        UpgradeData data = getData(upgrade);
        ItemStack[] costs = upgrade.getExtraCost();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack input = handler.getStackInSlot(slot);
            if (input == null) {
                continue;
            }
            for (int j = 0; j < costs.length; j++) {
                ItemStack costStack = costs[j];
                int alreadyPaid = data.amountsPaid[j];
                if (alreadyPaid >= costStack.stackSize) {
                    continue;
                }
                if (GTUtility.areStacksEqual(input, costStack)) {
                    int maxExtract = costStack.stackSize - alreadyPaid;
                    ItemStack extracted = handler.extractItem(slot, maxExtract, false);
                    if (extracted != null) {
                        data.amountsPaid[j] += extracted.stackSize;
                    }
                    break;
                }
            }
        }
        refreshCostPaid(upgrade, data);
    }

    private void refreshCostPaid(DysonUpgrade upgrade, UpgradeData data) {
        ItemStack[] costs = upgrade.getExtraCost();
        for (int j = 0; j < costs.length; j++) {
            if (data.amountsPaid[j] < costs[j].stackSize) {
                return;
            }
        }
        data.costPaid = true;
    }

    public void unlockUpgrade(DysonUpgrade upgrade) {
        getData(upgrade).active = true;
    }

    public void respecUpgrade(DysonUpgrade upgrade) {
        getData(upgrade).active = false;
    }

    public boolean checkPrerequisites(DysonUpgrade upgrade) {
        DysonUpgrade[] prereqs = upgrade.getPrerequisites();
        if (prereqs.length == 0) {
            return true;
        }
        Stream<UpgradeData> stream = Arrays.stream(prereqs).map(unlockedUpgrades::get);
        if (upgrade.requiresAllPrerequisites()) {
            return stream.allMatch(UpgradeData::isActive);
        }
        return stream.anyMatch(UpgradeData::isActive);
    }

    /** 分叉节点：同组内已激活数量必须小于 maxActive。 */
    public boolean checkSplit(DysonUpgrade upgrade, int maxActive) {
        if (!DysonUpgrade.SPLIT_UPGRADES.contains(upgrade)) {
            return true;
        }
        long active = DysonUpgrade.SPLIT_UPGRADES.stream()
            .map(unlockedUpgrades::get)
            .filter(UpgradeData::isActive)
            .count();
        return active < maxActive;
    }

    public boolean checkCost(DysonUpgrade upgrade) {
        return !upgrade.hasExtraCost() || isCostPaid(upgrade);
    }

    /** 洗点时校验：还有依赖它的已激活节点则不允许移除。 */
    public boolean checkDependents(DysonUpgrade upgrade) {
        for (DysonUpgrade dependent : upgrade.getDependents()) {
            if (!isUpgradeActive(dependent)) {
                continue;
            }
            if (dependent.requiresAllPrerequisites()) {
                return false;
            }
            if (Arrays.stream(dependent.getPrerequisites())
                .map(unlockedUpgrades::get)
                .filter(UpgradeData::isActive)
                .count() <= 1) {
                return false;
            }
        }
        return true;
    }

    public int getTotalActiveUpgrades() {
        return (int) unlockedUpgrades.values().stream().filter(UpgradeData::isActive).count();
    }

    public void resetAll() {
        for (UpgradeData data : unlockedUpgrades.values()) {
            data.active = false;
            data.costPaid = false;
            Arrays.fill(data.amountsPaid, (short) 0);
        }
    }

    public void unlockAll() {
        for (var entry : unlockedUpgrades.entrySet()) {
            DysonUpgrade upgrade = entry.getKey();
            UpgradeData data = entry.getValue();
            data.active = true;
            data.costPaid = true;
            if (upgrade.hasExtraCost()) {
                ItemStack[] costs = upgrade.getExtraCost();
                for (int j = 0; j < costs.length; j++) {
                    data.amountsPaid[j] = (short) costs[j].stackSize;
                }
            }
        }
    }

    /** 把另一份升级进度合并进来（逐节点取并集 / 已付数量取最大），用于离队继承。 */
    public void copyFrom(DysonUpgradeStorage other) {
        if (other == null) {
            return;
        }
        for (DysonUpgrade upgrade : DysonUpgrade.VALUES) {
            UpgradeData target = unlockedUpgrades.get(upgrade);
            UpgradeData source = other.unlockedUpgrades.get(upgrade);
            if (source == null) {
                continue;
            }
            target.active |= source.active;
            target.costPaid |= source.costPaid;
            for (int i = 0; i < target.amountsPaid.length; i++) {
                target.amountsPaid[i] = (short) Math.max(target.amountsPaid[i], source.amountsPaid[i]);
            }
        }
    }

    public void serializeToNBT(NBTTagCompound nbt) {
        if (!hasAnyProgress()) {
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        for (DysonUpgrade upgrade : DysonUpgrade.VALUES) {
            UpgradeData data = unlockedUpgrades.get(upgrade);
            tag.setBoolean("upgrade" + upgrade.ordinal(), data.active);
            if (upgrade.hasExtraCost()) {
                NBTTagCompound costTag = new NBTTagCompound();
                costTag.setBoolean("paid", data.costPaid);
                for (int i = 0; i < data.amountsPaid.length; i++) {
                    costTag.setShort("costPaid" + i, data.amountsPaid[i]);
                }
                tag.setTag("extraCost" + upgrade.ordinal(), costTag);
            }
        }
        nbt.setTag("dysonUpgrades", tag);
    }

    public void rebuildFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("dysonUpgrades")) {
            return;
        }
        NBTTagCompound tag = nbt.getCompoundTag("dysonUpgrades");
        for (DysonUpgrade upgrade : DysonUpgrade.VALUES) {
            UpgradeData data = unlockedUpgrades.get(upgrade);
            data.active = tag.getBoolean("upgrade" + upgrade.ordinal());
            if (upgrade.hasExtraCost() && tag.hasKey("extraCost" + upgrade.ordinal())) {
                NBTTagCompound costTag = tag.getCompoundTag("extraCost" + upgrade.ordinal());
                data.costPaid = costTag.getBoolean("paid");
                for (int i = 0; i < data.amountsPaid.length; i++) {
                    data.amountsPaid[i] = costTag.getShort("costPaid" + i);
                }
            }
        }
    }

    private boolean hasAnyProgress() {
        for (UpgradeData data : unlockedUpgrades.values()) {
            if (data.active || data.costPaid) {
                return true;
            }
            for (short paid : data.amountsPaid) {
                if (paid != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private UpgradeData getData(DysonUpgrade upgrade) {
        return unlockedUpgrades.computeIfAbsent(upgrade, k -> new UpgradeData());
    }

    private static class UpgradeData {

        private boolean active;
        private boolean costPaid;
        private final short[] amountsPaid = new short[12];

        private boolean isActive() {
            return active;
        }
    }
}
