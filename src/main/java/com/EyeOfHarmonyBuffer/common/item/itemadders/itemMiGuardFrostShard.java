package com.EyeOfHarmonyBuffer.common.item.itemadders;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class itemMiGuardFrostShard extends Item {

    public itemMiGuardFrostShard() {
        super();

        this.setUnlocalizedName("MiGuardFrostShard");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":MiXiaoZi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
