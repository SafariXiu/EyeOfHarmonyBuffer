package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_UpgradeChipMK2_Tooltip_00;

public class itemUpgradeChipMK2 extends Item {

    public itemUpgradeChipMK2() {
        super();

        this.setUnlocalizedName("UpgradeChipMK2");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/UpgradeChipMK2");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_UpgradeChipMK2_Tooltip_00);
    }
}
