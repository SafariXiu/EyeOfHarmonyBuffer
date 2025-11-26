package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import cpw.mods.fml.common.registry.GameRegistry;
import fox.spiteful.avaritia.crafting.ExtremeCraftingManager;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import static gregtech.api.enums.Mods.*;
import static gregtech.api.util.GTModHandler.getModItem;

public class RecipeLoader {

    public static void loadRecipes() {

        IRecipePool[] recipePools = new IRecipePool[] {
            new AssemblerRecipes(),
            new SpaceAssemblerRecipes(),
            new MachineBlockRecipes(),
            new SubstanceReshapingDeviceRecipes(),
            new BlueDogMachineRecipes(),
            new MaterialsRecipes(),
        };
        for (IRecipePool recipePool : recipePools) {
            recipePool.loadRecipes();
        }
    }

    public static void registerRecipes(){

        /*GameRegistry.addRecipe(
            new ItemStack(GTCMItemList.ChengDuHeart.getItem(),1),
            "DDD",
            ".D.",
            ".D.",
            'D', Items.diamond
        );*/

        ExtremeCraftingManager.getInstance().addExtremeShapedOreRecipe(
            GTCMItemList.ChengDuHeart.get(1),
            "..AAAAA..",
            "..BBBBB..",
            "...CCC...",
            "...DDD...",
            "...CCC...",
            "..EEEEE..",
            "..FFFFF..",
            "...GGG...",
            "....H....",
            'A', getModItem(Botania.ID,"storage",1,3),
            'B', Blocks.diamond_block,
            'C', getModItem(UniversalSingularities.ID, "universal.vanilla.singularity",1,2),
            'D', getModItem(TinkerConstruct.ID,"diamondApple",1),
            'E', Items.diamond,
            'F', getModItem(IndustrialCraft2.ID, "itemPartIndustrialDiamond",1),
            'G', getModItem(Botania.ID,"manaResource",1,2),
            'H', getModItem(ExtraUtilities.ID, "spike_base_diamond",1)
        );

        GameRegistry.addRecipe(
            GTCMItemList.BlueDogMachines.get(1),
            "AAA",
            "BCB",
            "DED",
            'A', getModItem(Chisel.ID, "hempcrete",1,0),
            'B', Materials.Water.getCells(1),
            'C', ItemList.Hull_LV.get(1),
            'D', getModItem(Chisel.ID, "hempcrete",1,11),
            'E', GTCMItemList.ChengDuHeart.get(1)
        );
    }
}
