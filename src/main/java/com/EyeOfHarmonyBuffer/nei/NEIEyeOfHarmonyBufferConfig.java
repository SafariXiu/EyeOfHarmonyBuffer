package com.EyeOfHarmonyBuffer.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import net.minecraft.item.ItemStack;

public class NEIEyeOfHarmonyBufferConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        ItemStack heTongRongYeStack = GTCMItemList.HeTongRongYe.get(1);
        API.hideItem(heTongRongYeStack);
    }

    @Override
    public String getName() {
        return "EyeOfHarmonyBuffer NEI Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }
}
