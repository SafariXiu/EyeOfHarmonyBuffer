package com.EyeOfHarmonyBuffer.common.byproduct;

import com.EyeOfHarmonyBuffer.common.RecipeMap.GeneralFrontend;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.RecipeDisplayInfo;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * 支持副产物展示的通用前端，基于 {@link com.EyeOfHarmonyBuffer.common.RecipeMap.GeneralFrontend}。
 * <p>
 * 使用方式：配方池注册时
 * {@code .frontend((uiProps, neiProps) -> new ByproductFrontend(uiProps, neiProps, 输入槽数, 输出槽数))}，
 * 配方构建时 {@code .special(byproductTable)}，即可获得：
 * <ul>
 * <li>NEI 输出物品行末尾的滚动副产物槽；</li>
 * <li>描述区总概率一行；</li>
 * <li>悬停副产物格的总概率提示。</li>
 * </ul>
 * 自定义前端（不继承 GeneralFrontend 的布局）可逐个调用 {@link NEIByproductHelper} 的方法接入。
 */
public class ByproductFrontend extends GeneralFrontend {

    private final int maxItemOutputs;

    public ByproductFrontend(BasicUIPropertiesBuilder uiProps,
                             NEIRecipePropertiesBuilder neiProps,
                             int maxItemInputs,
                             int maxItemOutputs) {
        super(uiProps, neiProps, maxItemInputs, maxItemOutputs);
        this.maxItemOutputs = maxItemOutputs;
    }

    @Override
    public void prepareRecipe(GTNEIDefaultHandler.CachedDefaultRecipe neiCachedRecipe) {
        super.prepareRecipe(neiCachedRecipe);
        NEIByproductHelper.addByproductOutput(neiCachedRecipe, maxItemOutputs);
    }

    @Override
    public void drawNEIOverlays(GTNEIDefaultHandler.CachedDefaultRecipe neiCachedRecipe) {
        super.drawNEIOverlays(neiCachedRecipe);
        NEIByproductHelper.cycleByproductOutput(neiCachedRecipe);
    }

    @Override
    public List<String> handleNEIItemTooltip(ItemStack stack, List<String> currentTip,
                                             GTNEIDefaultHandler.CachedDefaultRecipe neiCachedRecipe) {
        super.handleNEIItemTooltip(stack, currentTip, neiCachedRecipe);
        return NEIByproductHelper.addByproductTooltip(stack, currentTip, neiCachedRecipe);
    }

    @Override
    protected void drawSpecialInfo(RecipeDisplayInfo recipeInfo) {
        if (!NEIByproductHelper.drawTotalChance(recipeInfo)) {
            super.drawSpecialInfo(recipeInfo);
        }
    }
}
