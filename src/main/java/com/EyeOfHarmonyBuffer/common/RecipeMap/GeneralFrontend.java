package com.EyeOfHarmonyBuffer.common.RecipeMap;

import codechicken.nei.PositionedStack;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.api.util.GTRecipe;
import gregtech.common.gui.modularui.UIHelper;
import gregtech.nei.GTNEIDefaultHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeKey.GTExtendedRecipeKeys.NEI_OUTPUT_MAX;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeKey.GTExtendedRecipeKeys.NEI_OUTPUT_MIN;

public class GeneralFrontend extends RecipeMapFrontend {

    private static final int xDirMaxCount = 4;
    private static final int yOrigin = 20;
    private static final int rowStepY = 18;

    private final int maxItemInputs;
    private final int maxItemOutputs;

    public GeneralFrontend(BasicUIPropertiesBuilder uiProps,
                           NEIRecipePropertiesBuilder neiProps,
                           int maxItemInputs,
                           int maxItemOutputs) {
        super(uiProps, neiProps);
        this.maxItemInputs = maxItemInputs;
        this.maxItemOutputs = maxItemOutputs;
        neiProps.disableRenderRealStackSizes();
    }

    private int getInputRows() {
        return (maxItemInputs + xDirMaxCount - 1) / xDirMaxCount;
    }

    private int getOutputRows() {
        return (maxItemOutputs + xDirMaxCount - 1) / xDirMaxCount;
    }

    private int getItemRows() {
        return Math.max(getInputRows(), getOutputRows());
    }

    private int getFluidBaseY() {
        int itemRows = getItemRows();
        return yOrigin + itemRows * rowStepY;
    }

    @Override
    public List<Pos2d> getItemInputPositions(int itemInputCount) {
        return UIHelper.getGridPositions(itemInputCount, 5, yOrigin, xDirMaxCount);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int itemOutputCount) {
        return UIHelper.getGridPositions(itemOutputCount, 100, yOrigin, xDirMaxCount);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int fluidInputCount) {
        int fluidBaseY = getFluidBaseY();
        return UIHelper.getGridPositions(fluidInputCount, 5, fluidBaseY, xDirMaxCount);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        int fluidBaseY = getFluidBaseY();
        return UIHelper.getGridPositions(fluidOutputCount, 100, fluidBaseY, xDirMaxCount);
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

    @Override
    public void drawNEIOverlays(GTNEIDefaultHandler.CachedDefaultRecipe neiCachedRecipe) {
        List<PositionedStack> outs = neiCachedRecipe.mOutputs;
        int[] oldSizes = new int[outs.size()];

        for (int i = 0; i < outs.size(); i++) {
            PositionedStack ps = outs.get(i);
            if (ps == null || ps.item == null) continue;
            oldSizes[i] = ps.item.stackSize;
            ps.item.stackSize = 1;
        }

        super.drawNEIOverlays(neiCachedRecipe);

        for (int i = 0; i < outs.size(); i++) {
            PositionedStack ps = outs.get(i);
            if (ps == null || ps.item == null) continue;
            ps.item.stackSize = oldSizes[i];
        }

        GTRecipe recipe = neiCachedRecipe.mRecipe;
        if (recipe == null) return;

        Integer min = recipe.getMetadata(NEI_OUTPUT_MIN);
        Integer max = recipe.getMetadata(NEI_OUTPUT_MAX);
        if (min == null || max == null || min.equals(max)) return;

        String rangeText = min + "-" + max;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        GL11.glPushMatrix();
        GL11.glScalef(0.5F, 0.5F, 1F);
        for (PositionedStack out : outs) {
            int drawX = (out.relx + 2) * 2;
            int drawY = (out.rely + 2) * 2;
            fr.drawString(rangeText, drawX, drawY, 0xFFD54F, false);
        }
        GL11.glPopMatrix();
    }
}
