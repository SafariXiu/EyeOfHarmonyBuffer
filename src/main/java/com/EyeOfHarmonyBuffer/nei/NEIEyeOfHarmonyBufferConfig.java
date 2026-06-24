package com.EyeOfHarmonyBuffer.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.fluids.EOHBFluidBlockRegistry;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import net.minecraft.item.ItemStack;

public class NEIEyeOfHarmonyBufferConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        ItemStack heTongRongYeStack = GTCMItemList.HeTongRongYe.get(1);
        API.hideItem(heTongRongYeStack);

        if (EOHBFluidBlockRegistry.precipitationAcidBlock != null) {
            API.hideItem(new ItemStack(EOHBFluidBlockRegistry.precipitationAcidBlock));
        }
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
