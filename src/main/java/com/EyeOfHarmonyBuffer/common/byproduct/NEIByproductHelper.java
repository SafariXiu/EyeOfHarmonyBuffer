package com.EyeOfHarmonyBuffer.common.byproduct;

import codechicken.nei.PositionedStack;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.RecipeDisplayInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 副产物的 NEI 通用辅助：
 * <ul>
 * <li>在输出物品行末尾的空槽里追加一个滚动副产物格（多物品轮播）；</li>
 * <li>每帧驱动滚动切换（NEI 自身只对输入/催化剂循环，输出需要手动切换）；</li>
 * <li>悬停副产物格时补充总概率提示；</li>
 * <li>描述区只画一行总概率。</li>
 * </ul>
 * 继承 {@link ByproductFrontend} 即可自动获得全部能力；自定义前端也可以逐个调用这些方法。
 */
public final class NEIByproductHelper {

    /** 相邻物品输出槽的横向间距（与项目内 18px 网格布局一致）。 */
    private static final int SLOT_STEP_X = 18;

    /** 滚动切换间隔（tick）。 */
    private static final int CYCLE_INTERVAL_TICKS = 20;

    /** 记录每个 NEI 缓存配方对应的副产物滚动槽，用于每帧驱动循环。 */
    private static final Map<GTNEIDefaultHandler.CachedDefaultRecipe, GTNEIDefaultHandler.FixedPositionedStack>
        BYPRODUCT_STACKS = Collections.synchronizedMap(new WeakHashMap<>());

    private NEIByproductHelper() {}

    /**
     * 在 NEI 缓存配方中追加副产物滚动槽。
     * 仅当配方携带 {@link ByproductTable} 且输出物品行还有空槽时生效。
     *
     * @param maxItemOutputs 当前配方池的物品输出槽上限（NEI 模板会画出这么多槽位）
     */
    public static void addByproductOutput(GTNEIDefaultHandler.CachedDefaultRecipe neiCachedRecipe,
                                          int maxItemOutputs) {
        if (!(neiCachedRecipe.mRecipe.mSpecialItems instanceof ByproductTable table)) {
            return;
        }
        List<ByproductTable.Entry> entries = table.getEntries();
        if (entries.isEmpty()) return;
        if (neiCachedRecipe.mRecipe.mOutputs == null
            || neiCachedRecipe.mRecipe.mOutputs.length == 0
            || neiCachedRecipe.mRecipe.mOutputs.length >= maxItemOutputs) {
            return;
        }

        // 找到最后一个正常物品输出，副产物紧挨着它放进右侧的空槽。
        // 直接使用 NEI 缓存中的实际坐标，避免模板窗口偏移导致的错位。
        int remainingOutputs = neiCachedRecipe.mRecipe.mOutputs.length;
        GTNEIDefaultHandler.FixedPositionedStack lastItemOutput = null;
        for (PositionedStack output : neiCachedRecipe.mOutputs) {
            if (output instanceof GTNEIDefaultHandler.FixedPositionedStack fixed && !fixed.isFluid()) {
                lastItemOutput = fixed;
                if (--remainingOutputs == 0) break;
            }
        }
        if (lastItemOutput == null) return;

        ItemStack[] stacks = new ItemStack[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            stacks[i] = entries.get(i).stack.copy();
            stacks[i].stackSize = 1;
        }

        GTNEIDefaultHandler.FixedPositionedStack byproductStack =
            new GTNEIDefaultHandler.FixedPositionedStack(
                neiCachedRecipe,
                stacks,
                false,
                lastItemOutput.relx + SLOT_STEP_X,
                lastItemOutput.rely,
                PositionedStack.CHANCE_FULL,
                false,
                false);
        neiCachedRecipe.mOutputs.add(byproductStack);
        BYPRODUCT_STACKS.put(neiCachedRecipe, byproductStack);
    }

    /** 每帧调用，驱动副产物滚动格切换当前显示的物品。 */
    public static void cycleByproductOutput(GTNEIDefaultHandler.CachedDefaultRecipe neiCachedRecipe) {
        GTNEIDefaultHandler.FixedPositionedStack byproductStack = BYPRODUCT_STACKS.get(neiCachedRecipe);
        if (byproductStack == null || byproductStack.items == null || byproductStack.items.length <= 1) {
            return;
        }
        int index = (GTNEIDefaultHandler.getDrawTicks() / CYCLE_INTERVAL_TICKS) % byproductStack.items.length;
        byproductStack.setPermutationToRender(index);
    }

    /** 悬停物品时补充副产物总概率提示；未命中副产物池时原样返回。 */
    public static List<String> addByproductTooltip(ItemStack stack, List<String> currentTip,
                                                   GTNEIDefaultHandler.CachedDefaultRecipe neiCachedRecipe) {
        if (!(neiCachedRecipe.mRecipe.mSpecialItems instanceof ByproductTable table)) {
            return currentTip;
        }
        for (ByproductTable.Entry entry : table.getEntries()) {
            if (entry.stack != null && entry.stack.isItemEqual(stack)) {
                currentTip.add(
                    EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("EOHB.nei.byproduct.title")
                        + EnumChatFormatting.RESET
                        + ": "
                        + StatCollector.translateToLocal("EOHB.nei.byproduct.totalChance")
                        + " "
                        + formatPercent(table.getTotalChance()));
                break;
            }
        }
        return currentTip;
    }

    /**
     * 在描述区画一行总概率。
     *
     * @return 是否画了副产物信息（配方未携带副产物表时返回 false，调用方应回退到默认描述）
     */
    public static boolean drawTotalChance(RecipeDisplayInfo recipeInfo) {
        if (!(recipeInfo.recipe.mSpecialItems instanceof ByproductTable table)) {
            return false;
        }
        recipeInfo.drawText(
            EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("EOHB.nei.byproduct.title")
                + EnumChatFormatting.RESET
                + "  "
                + EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("EOHB.nei.byproduct.totalChance")
                + EnumChatFormatting.AQUA
                + formatPercent(table.getTotalChance()));
        return true;
    }

    private static String formatPercent(int basisPoints) {
        double percent = basisPoints / 100.0;
        if (percent == Math.floor(percent) && !Double.isInfinite(percent)) {
            return (long) percent + "%";
        }
        return String.format(Locale.ROOT, "%.2f", percent) + "%";
    }
}
