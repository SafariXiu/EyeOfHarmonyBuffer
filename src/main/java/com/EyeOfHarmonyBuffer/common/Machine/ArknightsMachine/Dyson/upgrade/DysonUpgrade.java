package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.google.common.collect.ImmutableSet;

/**
 * 戴森升级树节点（声明式）。
 * <p>
 * - 效率节点：缩短一轮时间（并行是独立数值，不在此列）；
 * - 大节点：解锁型收益（双轨发射、能量分配）。
 * <p>
 * 材料成本为圆石占位（{@link #getExtraCost()}），后续替换为专属货币，最多 12 种材料。
 */
public enum DysonUpgrade {

    START,
    MANUFACTURING_EFFICIENCY_I,
    MANUFACTURING_EFFICIENCY_II,
    MANUFACTURING_EFFICIENCY_III,
    MANUFACTURING_PARALLEL_II,
    MANUFACTURING_PARALLEL_III,
    LAUNCH_EFFICIENCY_I,
    LAUNCH_EFFICIENCY_II,
    LAUNCH_BATCH_I,
    LAUNCH_BATCH_II,
    DUAL_LAUNCH,
    PASTE_CONVERSION,
    DROP_REDUCTION,
    RECEIVER_BOOST,
    SPLIT_UNLOCK,
    MASS_LAUNCH,
    ;

    public static final DysonUpgrade[] VALUES = values();

    /** 贴片转化 / 掉落减免二选一。 */
    public static final Set<DysonUpgrade> SPLIT_UPGRADES;

    static {
        START.build(b -> b.treePos(140, 30));

        MANUFACTURING_EFFICIENCY_I.build(b -> b.prereqs(START).cost(1).treePos(28, 85));
        MANUFACTURING_EFFICIENCY_II.build(b -> b.prereqs(MANUFACTURING_EFFICIENCY_I).cost(1).treePos(28, 135));
        MANUFACTURING_EFFICIENCY_III.build(b -> b.prereqs(MANUFACTURING_EFFICIENCY_II).cost(1).treePos(28, 185));

        MANUFACTURING_PARALLEL_II.build(b -> b.prereqs(START).cost(1).treePos(76, 125));
        MANUFACTURING_PARALLEL_III.build(b -> b.prereqs(MANUFACTURING_PARALLEL_II).cost(1).treePos(76, 175));

        LAUNCH_EFFICIENCY_I.build(b -> b.prereqs(START).cost(1).treePos(252, 85));
        LAUNCH_EFFICIENCY_II.build(b -> b.prereqs(LAUNCH_EFFICIENCY_I).cost(1).treePos(252, 135));

        LAUNCH_BATCH_I.build(b -> b.prereqs(START).cost(1).treePos(210, 125));
        LAUNCH_BATCH_II.build(b -> b.prereqs(LAUNCH_BATCH_I).cost(1).treePos(210, 175));

        DUAL_LAUNCH.build(b -> b.prereqs(LAUNCH_EFFICIENCY_II, LAUNCH_BATCH_II).cost(3).treePos(252, 185).major());

        PASTE_CONVERSION.build(b -> b.prereqs(START).cost(1).treePos(118, 165));
        DROP_REDUCTION.build(b -> b.prereqs(START).cost(1).treePos(168, 165));
        RECEIVER_BOOST.build(b -> b.prereqs(PASTE_CONVERSION, DROP_REDUCTION).anyPrereq().cost(1).treePos(140, 215));
        RECEIVER_BOOST.addExtraCost(
            GTCMItemList.YuanShi.get(1),
            GTCMItemList.XiRang.get(2));
        SPLIT_UNLOCK.build(b -> b.prereqs(RECEIVER_BOOST).cost(3).treePos(140, 255).major());
        MASS_LAUNCH.build(b -> b.prereqs(DUAL_LAUNCH).cost(3).treePos(252, 225).major());

        SPLIT_UPGRADES = ImmutableSet.of(PASTE_CONVERSION, DROP_REDUCTION);

        // 反向依赖表
        EnumMap<DysonUpgrade, List<DysonUpgrade>> dependencies = new EnumMap<>(DysonUpgrade.class);
        for (DysonUpgrade upgrade : VALUES) {
            for (DysonUpgrade prerequisite : upgrade.prerequisites) {
                dependencies.computeIfAbsent(prerequisite, k -> new ArrayList<>())
                    .add(upgrade);
            }
        }
        for (var entry : dependencies.entrySet()) {
            entry.getKey().dependents = entry.getValue().toArray(new DysonUpgrade[0]);
        }
    }

    private DysonUpgrade[] prerequisites = new DysonUpgrade[0];
    private boolean requireAllPrerequisites = true;
    private final List<ItemStack> extraCost = new ArrayList<>();
    private int treeX;
    private int treeY;
    private boolean major;
    private DysonUpgrade[] dependents = new DysonUpgrade[0];

    DysonUpgrade() {}

    private void build(UnaryOperator<Builder> function) {
        Builder builder = function.apply(new Builder());
        this.prerequisites = builder.prerequisites == null
            ? new DysonUpgrade[0]
            : builder.prerequisites.toArray(new DysonUpgrade[0]);
        this.requireAllPrerequisites = builder.requireAllPrerequisites;
        if (builder.itemCost > 0) {
            this.extraCost.add(new ItemStack(Blocks.cobblestone, builder.itemCost));
        }
        this.treeX = builder.treeX;
        this.treeY = builder.treeY;
        this.major = builder.major;
    }

    public DysonUpgrade[] getPrerequisites() {
        return prerequisites;
    }

    public boolean requiresAllPrerequisites() {
        return requireAllPrerequisites;
    }

    public DysonUpgrade[] getDependents() {
        return dependents;
    }

    public boolean hasExtraCost() {
        return !extraCost.isEmpty();
    }

    /** 材料成本（最多 12 种，当前为圆石占位）。 */
    public ItemStack[] getExtraCost() {
        return extraCost.toArray(new ItemStack[0]);
    }

    public int getTotalItemCost() {
        int total = 0;
        for (ItemStack stack : extraCost) {
            total += stack.stackSize;
        }
        return total;
    }

    public void addExtraCost(ItemStack... cost) {
        if (cost == null || extraCost.size() + cost.length > 12) {
            throw new IllegalArgumentException("升级材料成本最多 12 种");
        }
        extraCost.addAll(Arrays.asList(cost));
    }

    public int getTreeX() {
        return treeX;
    }

    public int getTreeY() {
        return treeY;
    }

    public boolean isMajor() {
        return major;
    }

    public String getNameKey() {
        return "eohb.dyson.upgrade.tt." + ordinal();
    }

    public String getShortNameKey() {
        return "eohb.dyson.upgrade.tt.short." + ordinal();
    }

    public String getEffectKey() {
        return "eohb.dyson.upgrade.text." + ordinal();
    }

    public String getLoreKey() {
        return "eohb.dyson.upgrade.lore." + ordinal();
    }

    public static final class Builder {

        private List<DysonUpgrade> prerequisites;
        private boolean requireAllPrerequisites = true;
        private int itemCost;
        private int treeX;
        private int treeY;
        private boolean major;

        private Builder() {}

        public Builder prereqs(DysonUpgrade... prerequisites) {
            this.prerequisites = new ArrayList<>(Arrays.asList(prerequisites));
            return this;
        }

        /** 多前置时满足任意一个即可。 */
        public Builder anyPrereq() {
            this.requireAllPrerequisites = false;
            return this;
        }

        public Builder cost(int itemCost) {
            this.itemCost = itemCost;
            return this;
        }

        public Builder treePos(int x, int y) {
            this.treeX = x;
            this.treeY = y;
            return this;
        }

        public Builder major() {
            this.major = true;
            return this;
        }
    }
}
