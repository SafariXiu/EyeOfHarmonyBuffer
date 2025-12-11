package com.EyeOfHarmonyBuffer.common.item;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.item.itemadders.*;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.*;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

@SuppressWarnings("SameParameterValue")
public class ItemLoader {

    public static Item ChengDuHeart = new ItemChengDuHeart();
    public static Item Monkey = new ItemMonkey();
    public static Item Shit = new ItemShit();
    public static Item YuanShi = new ItemYuanShi();
    public static Item HeChengYu = new ItemHeChengYu();
    public static Item LanTieKuang = new ItemLanTieKuang();
    public static Item YuanShiKuang = new ItemYuanShiKuang();
    public static Item ZiJingKuang = new ItemZiJingKuang();
    public static Item DiRongLiangDianChi = new ItemDiRongLiangDianChi();
    public static Item ZhongRongLiangDianChi = new ItemZhongRongLiangDianChi();
    public static Item GaoRongLiangDianChi = new ItemGaoRongLiangDianChi();
    public static Item XiRangDiRongLiangDianChi = new ItemXiRangDiRongLiangDianChi();

    public ItemLoader(FMLPreInitializationEvent event){
        GTCMItemList.ChengDuHeart.set(registryAndCallback(ChengDuHeart,"chengdu_heart"));
        GTCMItemList.Monkey.set(registryAndCallback(Monkey,"Monkey"));
        GTCMItemList.Shit.set(registryAndCallback(Shit,"Shit"));
        GTCMItemList.YuanShi.set(registryAndCallback(YuanShi,"YuanShi"));
        GTCMItemList.HeChengYu.set(registryAndCallback(HeChengYu,"HeChengYu"));
        GTCMItemList.LanTieKuang.set(registryAndCallback(LanTieKuang,"LanTieKuang"));
        GTCMItemList.YuanShiKuang.set(registryAndCallback(YuanShiKuang,"YuanShiKuang"));
        GTCMItemList.ZiJingKuang.set(registryAndCallback(ZiJingKuang,"ZiJingKuang"));
        GTCMItemList.DiRongLiangDianChi.set(registryAndCallback(DiRongLiangDianChi,"DiRongLiangDianChi"));
        GTCMItemList.ZhongRongLiangDianChi.set(registryAndCallback(ZhongRongLiangDianChi,"ZhongRongLiangDianChi"));
        GTCMItemList.GaoRongLiangDianChi.set(registryAndCallback(GaoRongLiangDianChi,"GaoRongLiangDianChi"));
        GTCMItemList.XiRangDiRongLiangDianChi.set(registryAndCallback(XiRangDiRongLiangDianChi,"XiRangDiRongLiangDianChi"));
    }

    private static ItemStack registryAndCallback(Item item, String name) {
        return registryAndCallback(item, name, 0);
    }

    private static ItemStack registryAndCallback(Item item, String name, int aMeta) {
        GameRegistry.registerItem(item, name);
        return new ItemStack(item, 1, aMeta);
    }
}
