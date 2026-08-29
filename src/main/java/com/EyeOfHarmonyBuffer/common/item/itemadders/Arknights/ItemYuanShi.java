package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class ItemYuanShi extends ItemFood {

    public static Item instance;

    public ItemYuanShi() {
        super(20, 1F, false);

        this.setUnlocalizedName("YuanShi");
        //this.setTextureName(EyeOfHarmonyBuffer.MODID + ":YuanShi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
        this.setAlwaysEdible();

        instance = this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_YuanShi_Tooltip_00);
        list.add(EOHB_YuanShi_Tooltip_01);
        list.add(EOHB_YuanShi_Tooltip_02);
    }

    @Override
    protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
        super.onFoodEaten(stack, world, player);

        if (!world.isRemote) {
            player.addPotionEffect(new PotionEffect(Potion.regeneration.id, 400, 1));
            player.addPotionEffect(new PotionEffect(Potion.damageBoost.id, 600, 1));
            player.addPotionEffect(new PotionEffect(Potion.resistance.id, 600, 0));
            player.addPotionEffect(new PotionEffect(Potion.fireResistance.id, 1200, 0));
        }
    }
}
