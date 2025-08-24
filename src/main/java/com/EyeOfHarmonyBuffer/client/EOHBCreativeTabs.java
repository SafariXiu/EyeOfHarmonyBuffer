package com.EyeOfHarmonyBuffer.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import com.EyeOfHarmonyBuffer.common.item.items.BasicItems;

import static com.EyeOfHarmonyBuffer.utils.TextHandler.texter;

public class EOHBCreativeTabs {

    /**
     * Creative Tab for MetaItem01
     */
    public static final CreativeTabs tabMetaItem01 = new CreativeTabs("EOHBMetaItems1") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return BasicItems.MetaItem01;
        }
    };

    public static final CreativeTabs tabGears = new CreativeTabs("EOHBGears") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return BasicItems.MetaItem01;
        }
    };

    /**
     * Creative Tab for MetaBlocks
     */
    public static final CreativeTabs TAB_META_BLOCKS = new CreativeTabs("EOHBMetaBlocks") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return BasicItems.MetaItem01;
        }
    };

    /**
     * Creative Tab for MetaBlock01
     */
    public static final CreativeTabs tabGTCMGeneralTab = new CreativeTabs("EOHB") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return BasicItems.MetaItem01;
        }
    };
}
