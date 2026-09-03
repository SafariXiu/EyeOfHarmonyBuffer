package com.EyeOfHarmonyBuffer.common.Block.Arknights.botany;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.ArknightsProject_Block;

public final class BlockIntermediateResources {

    private BlockIntermediateResources() {}

    public static void registerAll(String modid) {
        for (ResourceClusterDef def : ResourceClusterDef.values()) {
            Item dropItem = resolveDropItem(def.dropItemField);
            Block block = new BlockResourceCluster(modid, def, dropItem);

            block.setBlockName(def.blockName);
            block.setBlockTextureName(modid + ":Arknights/" + def.blockName);
            block.setCreativeTab(ArknightsProject_Block);
            GameRegistry.registerBlock(block, def.blockName);

            try {
                GTCMItemList entry = GTCMItemList.valueOf(def.blockName);
                entry.set(new ItemStack(block));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static Item resolveDropItem(String fieldName) {
        return GTCMItemList.valueOf(fieldName).getItem();
    }
}
