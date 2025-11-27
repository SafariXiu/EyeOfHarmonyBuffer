package com.EyeOfHarmonyBuffer.client;

import com.EyeOfHarmonyBuffer.Loader.MachineLoader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import com.EyeOfHarmonyBuffer.common.item.items.BasicItems;

import java.util.List;

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

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("unchecked")
        public void displayAllReleventItems(List itemList) {
            super.displayAllReleventItems(itemList);

            if (MachineLoader.VendingMachines != null) {
                itemList.add(MachineLoader.VendingMachines);
            }
            if (MachineLoader.WindTurbine != null) {
                itemList.add(MachineLoader.WindTurbine);
            }
            if (MachineLoader.SolarEnergyArrays != null) {
                itemList.add(MachineLoader.SolarEnergyArrays);
            }
            if (MachineLoader.SubstanceReshapingDevices != null) {
                itemList.add(MachineLoader.SubstanceReshapingDevices);
            }
            if (MachineLoader.BlueDogMachine != null) {
                itemList.add(MachineLoader.BlueDogMachine);
            }
            if (MachineLoader.MonkeyShit != null) {
                itemList.add(MachineLoader.MonkeyShit);
            }
        }
    };
}
