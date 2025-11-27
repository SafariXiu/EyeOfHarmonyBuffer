package com.EyeOfHarmonyBuffer.common.RecipeMap;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.common.gui.modularui.UIHelper;

import java.util.List;

public class MonkeyShitFrontend extends RecipeMapFrontend {

    private static final int xDirMaxCount = 4; // 每行最大槽位数
    private static final int yOrigin = 20; // Y 轴起始位置

    public MonkeyShitFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder, NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(uiPropertiesBuilder, neiPropertiesBuilder);
    }

    @Override
    public List<Pos2d> getItemInputPositions(int itemInputCount) {
        // 物品输入槽位：顶部并排两个
        return UIHelper.getGridPositions(itemInputCount, 5, yOrigin, xDirMaxCount);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int itemOutputCount) {
        // 物品输出槽位（4x1 布局）
        return UIHelper.getGridPositions(itemOutputCount, 100, yOrigin, xDirMaxCount);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int fluidInputCount) {
        // 流体输入槽位：物品输入槽下方
        return UIHelper.getGridPositions(fluidInputCount, 5, yOrigin + 18, xDirMaxCount);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return UIHelper.getGridPositions(fluidOutputCount, 100, yOrigin + 18, xDirMaxCount);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder, Pos2d windowOffset) {
        Pos2d adjustedOffset = windowOffset.add(0, 105);
        builder.widget(
            new DrawableWidget()
                .setDrawable(uiProperties.logo)
                .setSize(uiProperties.logoSize)
                .setPos(uiProperties.logoPos.add(adjustedOffset))
        );
    }

    /*@Override
    public void addGregTechLogo(ModularWindow.Builder builder, Pos2d windowOffset) {
        builder.widget(
            new DrawableWidget().setDrawable(UITexture.fullImage("modid", "textures/gui/logo.png"))
                .setSize(18, 18)
                .setPos(new Pos2d(150, 10).add(windowOffset)));
    }*/
}
