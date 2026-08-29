package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_UpgradeChipMK1_Tooltip_00;

public class itemUpgradeChipMK1 extends Item {

    public itemUpgradeChipMK1() {
        super();

        this.setUnlocalizedName("UpgradeChipMK1");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/UpgradeChipMK1");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_UpgradeChipMK1_Tooltip_00);
    }
}
