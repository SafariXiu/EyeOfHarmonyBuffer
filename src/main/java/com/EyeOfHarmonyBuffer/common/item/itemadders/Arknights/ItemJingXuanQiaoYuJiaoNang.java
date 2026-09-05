package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_JinCaoRuanYin_00;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_JinCaoRuanYin_01;

public class ItemJingXuanQiaoYuJiaoNang extends Item {

    public ItemJingXuanQiaoYuJiaoNang(){
        this.setUnlocalizedName("JingXuanQiaoYuJiaoNang");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/JingXuanQiaoYuJiaoNang");
        this.setMaxStackSize(16);
        this.setCreativeTab(tabMetaItem01);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_JinCaoRuanYin_00);
        list.add(EOHB_JinCaoRuanYin_01);
    }
}
