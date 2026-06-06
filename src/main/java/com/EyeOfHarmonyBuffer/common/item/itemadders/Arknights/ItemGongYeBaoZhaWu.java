package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.entity.Arknights.EntityIndustrialExplosive;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemGongYeBaoZhaWu extends Item {

    public ItemGongYeBaoZhaWu() {
        this.setUnlocalizedName("GongYeBaoZhaWu");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/GongYeBaoZhaWu");
        this.setCreativeTab(tabMetaItem01);
        this.maxStackSize = 16;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            EntityIndustrialExplosive entity = new EntityIndustrialExplosive(world, player);
            entity.setThrowableHeading(
                player.getLookVec().xCoord,
                player.getLookVec().yCoord,
                player.getLookVec().zCoord,
                1.0F,
                1.0F
            );
            world.spawnEntityInWorld(entity);
        }

        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }

        return stack;
    }
}
