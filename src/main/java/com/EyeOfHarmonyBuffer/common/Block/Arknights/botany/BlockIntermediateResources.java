package com.EyeOfHarmonyBuffer.common.Block.Arknights.botany;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemIntermediateProducts;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;

public final class BlockIntermediateResources {

    private BlockIntermediateResources() {}

    private static final Map<ResourceClusterDef, Block> REGISTERED =
        new EnumMap<>(ResourceClusterDef.class);

    public static void registerAll(String modid) {
        for (ResourceClusterDef def : ResourceClusterDef.values()) {
            Item dropItem = resolveDropItem(def.dropItemField);
            Block block = new BlockResourceCluster(modid, def, dropItem);

            GameRegistry.registerBlock(block, def.blockName);
            REGISTERED.put(def, block);

            try {
                GTCMItemList entry = GTCMItemList.valueOf(def.blockName);
                entry.set(new ItemStack(block));
            } catch (IllegalArgumentException ignored) {

            }
        }
    }

    public static Block getBlock(ResourceClusterDef def) {
        return REGISTERED.get(def);
    }

    private static Item resolveDropItem(String fieldName) {
        try {
            Field field = ItemIntermediateProducts.class.getDeclaredField(fieldName);
            return (Item) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("找不到掉落物字段: " + fieldName, e);
        }
    }
}
