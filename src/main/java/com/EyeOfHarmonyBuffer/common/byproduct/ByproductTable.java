package com.EyeOfHarmonyBuffer.common.byproduct;

import gregtech.api.objects.XSTR;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用副产物表。
 * <p>
 * 规则：每合成一份独立判定一次，先按总概率判定是否出副产物；
 * 判定成功后从表中按权重随机选一种。权重越接近平均，个体概率越接近 总概率 / 种类数。
 * <p>
 * 使用方式：配方构建时通过 {@code .special(byproductTable)} 挂到配方上即可。
 * 机器侧由 {@code GTCM_ParallelHelper} 自动按并行数逐份掷骰；
 * NEI 侧使用 {@link ByproductFrontend}（或调用 {@link NEIByproductHelper}）显示滚动副产物槽与总概率。
 */
public class ByproductTable {

    /** 概率基准：0 ~ 10000，10000 = 100% */
    public static final int CHANCE_BASE = 10000;

    /** 常用默认总概率：2000 = 20% */
    public static final int DEFAULT_TOTAL_CHANCE = 2000;

    private final int totalChance;
    private final List<Entry> entries = new ArrayList<>();
    private int totalWeight;

    public ByproductTable(int totalChance) {
        this.totalChance = totalChance;
    }

    /**
     * 添加一种副产物。
     *
     * @param stack  副产物物品（内部会复制一份，调用方可复用原堆）
     * @param weight 权重，越大个体概率越高
     */
    public ByproductTable add(ItemStack stack, int weight) {
        if (stack != null && weight > 0) {
            entries.add(new Entry(stack.copy(), weight));
            totalWeight += weight;
        }
        return this;
    }

    public int getTotalChance() {
        return totalChance;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    /** 某个副产物的有效概率，单位为 1/10000（basis points）。 */
    public int getChanceBasisPoints(Entry entry) {
        if (totalWeight <= 0) return 0;
        return totalChance * entry.weight / totalWeight;
    }

    /**
     * 掷一次副产物。
     *
     * @return 命中的副产物副本；未命中返回 null
     */
    public ItemStack roll() {
        XSTR rng = XSTR.XSTR_INSTANCE;
        if (rng.nextInt(CHANCE_BASE) >= totalChance) return null;

        int roll = rng.nextInt(totalWeight);
        for (Entry entry : entries) {
            roll -= entry.weight;
            if (roll < 0) {
                return entry.stack.copy();
            }
        }
        return entries.get(entries.size() - 1).stack.copy();
    }

    public static class Entry {

        public final ItemStack stack;
        public final int weight;

        private Entry(ItemStack stack, int weight) {
            this.stack = stack;
            this.weight = weight;
        }
    }
}
