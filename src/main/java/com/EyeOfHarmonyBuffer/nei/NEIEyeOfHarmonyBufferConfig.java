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

        ItemStack QingShuiStack = GTCMItemList.QingShui.get(1);
        API.hideItem(QingShuiStack);

        ItemStack ChenJiSuanStack = GTCMItemList.ChenJiSuan.get(1);
        API.hideItem(ChenJiSuanStack);

        ItemStack DuoQi = GTCMItemList.DuoQi.get(1);
        API.hideItem(DuoQi);

        ItemStack XiRangQi = GTCMItemList.XiRangQi.get(1);
        API.hideItem(XiRangQi);

        ItemStack SuanQi = GTCMItemList.SuanQi.get(1);
        API.hideItem(SuanQi);

        ItemStack ShuiZhengQi = GTCMItemList.ShuiZhengQi.get(1);
        API.hideItem(ShuiZhengQi);

        ItemStack QiTaiZhuoTong = GTCMItemList.QiTaiZhuoTong.get(1);
        API.hideItem(QiTaiZhuoTong);

        /*if (EOHBFluidBlockRegistry.precipitationAcidBlock != null) {
            API.hideItem(new ItemStack(EOHBFluidBlockRegistry.precipitationAcidBlock));
        }*/
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
