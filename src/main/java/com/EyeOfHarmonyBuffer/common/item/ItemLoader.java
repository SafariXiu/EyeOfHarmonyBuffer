package com.EyeOfHarmonyBuffer.common.item;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import com.EyeOfHarmonyBuffer.common.item.itemadders.*;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.*;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.FuelRod.GTCMReactorFuelCells;
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
    public static Item PoSuiYuanShi =  new ItemPoSuiYuanShi();
    public static Item JingTiWaiKeFenMo = new ItemJingTiWaiKeFenMo();
    public static Item LanTieFenMo = new ItemLanTieFenMo();
    public static Item ShaYeFenMo = new ItemShaYeFenMo();
    public static Item TanFenMo = new ItemTanFenMo();
    public static Item TongHuaGuanMuFenMo = new ItemTongHuaGuanMuFenMo();
    public static Item XiRang = new ItemXiRang();
    public static Item YuanShiFenMo = new ItemYuanShiFenMo();
    public static Item ZiJingFenMo = new ItemZiJingFenMo();
    public static Item TanKuai = new ItemTanKuai();
    public static Item JingTiWaiKe =  new ItemJingTiWaiKe();
    public static Item LanTieKuai = new ItemLanTieKuai();
    public static Item WenDingTanKuai = new ItemWenDingTanKuai();
    public static Item ZiJingXianWei =  new ItemZiJingXianWei();
    public static Item MiZhiJingTi = new ItemMiZhiJingTi();
    public static Item GaoJingXianWei =  new ItemGaoJingXianWei();
    public static Item GangKuai = new ItemGangKuai();
    public static Item UnactivatedYuanShi = new ItemUnactivatedYuanShi();
    public static Item UpgradeChipMK1 = new itemUpgradeChipMK1();
    public static Item UpgradeChipMK2 = new itemUpgradeChipMK2();
    public static Item UpgradeChipMK3 = new itemUpgradeChipMK3();
    public static Item MiGuardFrostShard = new itemMiGuardFrostShard();
    public static Item GongYeBaoZhaWu = new ItemGongYeBaoZhaWu();
    public static Item YaZhenZhenJi = new ItemYaZhenZhenJi();
    public static Item JinCaoRuanYin = new ItemJinCaoRuanYin();
    public static Item YouZhiYaZhenZhenJi =  new ItemYouZhiYaZhenZhenJi();
    public static Item YouZhiJinCaoRuanYin = new ItemYouZhiJinCaoRuanYin();
    public static Item GanShiGuanTou = new ItemGanShiGuanTou();
    public static Item YouZhiGanShiGuanTou = new ItemYouZhiGanShiGuanTou();
    public static Item JingXuanGanShiGuanTou = new ItemJingXuanGanShiGuanTou();
    public static Item QiaoYuJiaoNang = new ItemQiaoYuJiaoNang();
    public static Item YouZhiQiaoYuJiaoNang = new ItemYouZhiQiaoYuJiaoNang();
    public static Item JingXuanQiaoYuJiaoNang = new ItemJingXuanQiaoYuJiaoNang();
    public static Item HeTongRongYe = new ItemHeTongRongYe();
    public static Item QingShui = new itemQingShui();
    public static Item ChenJiSuan = new ItemChenJiSuan();
    public static Item DuoQi = new ItemDuoQi();
    public static Item ShuiZhengQi = new ItemShuiZhengQi();
    public static Item SuanQi = new ItemSuanQi();
    public static Item XiRangQi = new ItemXiRangQi();
    public static Item QiTaiZhuoTong = new ItemQiTaiZhuoTong();
    public static Item KuangMaiCaiJiZhe = new ItemKuangMaiCaiJiZhe();
    public static Item EnergyConnector = new ItemEnergyConnector();;
    public static Item OrbitalRailgun = new ItemOrbitalRailgun();

    //瓶子
    public static Item ChiTongFluidBottle;
    public static Item GangZhiFluidBottle;
    public static Item LanTieFluidBottle;
    public static Item ZiJingZhiFluidBottle;
    public static Item GaoJingFluidBottle;
    public static Item HeTongFluidBottle;

    public static Item ChiTongGasTank;

    public ItemLoader(FMLPreInitializationEvent event){
        GTCMItemList.ChengDuHeart.set(registryAndCallback(ChengDuHeart,"chengdu_heart"));
        GTCMItemList.Monkey.set(registryAndCallback(Monkey,"Monkey"));
        GTCMItemList.Shit.set(registryAndCallback(Shit,"Shit"));
        GTCMItemList.YuanShi.set(registryAndCallback(YuanShi,"YuanShi"));
        GTCMItemList.HeChengYu.set(registryAndCallback(HeChengYu,"HeChengYu"));
        GTCMItemList.LanTieKuang.set(registryAndCallback(LanTieKuang,"LanTieKuang"));
        GTCMItemList.YuanShiKuang.set(registryAndCallback(YuanShiKuang,"YuanShiKuang"));
        GTCMItemList.ZiJingKuang.set(registryAndCallback(ZiJingKuang,"ZiJingKuang"));
        GTCMItemList.DiRongGuDiDianChi.set(registryAndCallback(DiRongLiangDianChi,"DiRongLiangDianChi"));
        GTCMItemList.ZhongRongGuDiDianChi.set(registryAndCallback(ZhongRongLiangDianChi,"ZhongRongLiangDianChi"));
        GTCMItemList.GaoRongGuDiDianChi.set(registryAndCallback(GaoRongLiangDianChi,"GaoRongLiangDianChi"));
        GTCMItemList.DiRongXiRangDianChi.set(registryAndCallback(XiRangDiRongLiangDianChi,"XiRangDiRongLiangDianChi"));
        GTCMItemList.PoSuiYuanShi.set(registryAndCallback(PoSuiYuanShi,"PoSuiYuanShi"));
        GTCMItemList.JingTiWaiKeFenMo.set(registryAndCallback(JingTiWaiKeFenMo,"JingTiWaiKeFenMo"));
        GTCMItemList.LanTieFenMo.set(registryAndCallback(LanTieFenMo,"LanTieFenMo"));
        GTCMItemList.ShaYeFenMo.set(registryAndCallback(ShaYeFenMo,"ShaYeFenMo"));
        GTCMItemList.TanFenMo.set(registryAndCallback(TanFenMo,"TanFenMo"));
        GTCMItemList.TongHuaGuanMuFenMo.set(registryAndCallback(TongHuaGuanMuFenMo,"TongHuaGuanMuFenMo"));
        GTCMItemList.XiRang.set(registryAndCallback(XiRang,"XiRang"));
        GTCMItemList.YuanShiFenMo.set(registryAndCallback(YuanShiFenMo,"YuanShiFenMo"));
        GTCMItemList.ZiJingFenMo.set(registryAndCallback(ZiJingFenMo,"ZiJingFenMo"));
        GTCMItemList.TanKuai.set(registryAndCallback(TanKuai,"TanKuai"));
        GTCMItemList.JingTiWaiKe.set(registryAndCallback(JingTiWaiKe,"JingTiWaiKe"));
        GTCMItemList.LanTieKuai.set(registryAndCallback(LanTieKuai,"LanTieKuai"));
        GTCMItemList.WenDingTanKuai.set(registryAndCallback(WenDingTanKuai,"WenDingTanKuai"));
        GTCMItemList.ZiJingXianWei.set(registryAndCallback(ZiJingXianWei,"ZiJingXianWei"));
        GTCMItemList.MiZhiJingTi.set(registryAndCallback(MiZhiJingTi,"MiZhiJingTi"));
        GTCMItemList.GaoJingXianWei.set(registryAndCallback(GaoJingXianWei,"GaoJingXianWei"));
        GTCMItemList.GangKuai.set(registryAndCallback(GangKuai,"GangKuai"));
        GTCMItemList.UnactivatedYuanShi.set(registryAndCallback(UnactivatedYuanShi,"UnactivatedYuanShi"));
        GTCMItemList.UpgradeChipsMK1.set(registryAndCallback(UpgradeChipMK1,"UpgradeChipMK1"));
        GTCMItemList.UpgradeChipsMK2.set(registryAndCallback(UpgradeChipMK2,"UpgradeChipMK2"));
        GTCMItemList.UpgradeChipsMK3.set(registryAndCallback(UpgradeChipMK3,"UpgradeChipMK3"));
        GTCMItemList.MiGuardFrostShard.set(registryAndCallback(MiGuardFrostShard,"MiGuardFrostShard"));
        GTCMItemList.GongYeBaoZhaWu.set(registryAndCallback(GongYeBaoZhaWu,"GongYeBaoZhaWu"));
        GTCMItemList.YaZhenZhenJi.set(registryAndCallback(YaZhenZhenJi,"YaZhenZhenJi"));
        GTCMItemList.JinCaoRuanYin.set(registryAndCallback(JinCaoRuanYin,"JinCaoRuanYin"));
        GTCMItemList.YouZhiYaZhenZhenJi.set(registryAndCallback(YouZhiYaZhenZhenJi,"YouZhiYaZhenZhenJi"));
        GTCMItemList.YouZhiJinCaoRuanYin.set(registryAndCallback(YouZhiJinCaoRuanYin,"YouZhiJinCaoRuanYin"));
        GTCMItemList.GanShiGuanTou.set(registryAndCallback(GanShiGuanTou,"GanShiGuanTou"));
        GTCMItemList.YouZhiGanShiGuanTou.set(registryAndCallback(YouZhiGanShiGuanTou,"YouZhiGanShiGuanTou"));
        GTCMItemList.JingXuanGanShiGuanTou.set(registryAndCallback(JingXuanGanShiGuanTou,"JingXuanGanShiGuanTou"));
        GTCMItemList.QiaoYuJiaoNang.set(registryAndCallback(QiaoYuJiaoNang,"QiaoYuJiaoNang"));
        GTCMItemList.YouZhiQiaoYuJiaoNang.set(registryAndCallback(YouZhiQiaoYuJiaoNang,"YouZhiQiaoYuJiaoNang"));
        GTCMItemList.JingXuanQiaoYuJiaoNang.set(registryAndCallback(JingXuanQiaoYuJiaoNang,"JingXuanQiaoYuJiaoNang"));
        GTCMItemList.HeTongRongYe.set(registryAndCallback(HeTongRongYe,"HeTongRongYe"));
        GTCMItemList.QingShui.set(registryAndCallback(QingShui,"QingShui"));
        GTCMItemList.ChenJiSuan.set(registryAndCallback(ChenJiSuan,"ChenJiSuan"));
        GTCMItemList.DuoQi.set(registryAndCallback(DuoQi,"DuoQi"));
        GTCMItemList.ShuiZhengQi.set(registryAndCallback(ShuiZhengQi,"ShuiZhengQi"));
        GTCMItemList.SuanQi.set(registryAndCallback(SuanQi,"SuanQi"));
        GTCMItemList.XiRangQi.set(registryAndCallback(XiRangQi,"XiRangQi"));
        GTCMItemList.QiTaiZhuoTong.set(registryAndCallback(QiTaiZhuoTong,"QiTaiZhuoTong"));
        GTCMItemList.KuangMaiCaiJiZhe.set(registryAndCallback(KuangMaiCaiJiZhe,"KuangMaiCaiJiZhe"));
        GTCMItemList.EnergyConnector.set(registryAndCallback(EnergyConnector,"EnergyConnector"));
        GTCMItemList.OrbitalRailgun.set(registryAndCallback(OrbitalRailgun,"OrbitalRailgun"));
        GTCMItemList.DysonCloudComponent.set(
            registryAndCallback(new ItemDysonCloudComponent(), "DysonCloudComponent"));
        GTCMItemList.DysonFrameComponent.set(
            registryAndCallback(new ItemDysonFrameComponent(), "DysonFrameComponent"));

        EnumBottleFluid[] allFluids = EnumBottleFluid.values();
        ChiTongFluidBottle = new ItemBottleBase(
            "ChiTongFluidBottle",
            "ChiTongPing_empty",
            EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE
        );
        GangZhiFluidBottle = new ItemBottleBase(
            "GangZhiFluidBottle",
            "GangZhiPing_empty",
            EnumBottleFluid.QING_SHUI,
            EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE,
            EnumBottleFluid.YE_HUA_XI_RANG
        );
        LanTieFluidBottle = new ItemBottleBase(
            "LanTieFluidBottle",
            "LanTiePing_empty",
            EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE,
            EnumBottleFluid.QING_SHUI,
            EnumBottleFluid.YE_HUA_XI_RANG,
            EnumBottleFluid.YE_HUA_ZHONG_XI_RANG
        );
        ZiJingZhiFluidBottle = new ItemBottleBase(
            "ZiJingZhiFluidBottle",
            "ZiJingZhiPing_empty",
            EnumBottleFluid.QING_SHUI,
            EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE,
            EnumBottleFluid.YE_HUA_XI_RANG
        );
        GaoJingFluidBottle = new ItemBottleBase(
            "GaoJingFluidBottle",
            "GaoJingZhiPing_empty",
            EnumBottleFluid.QING_SHUI,
            EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE,
            EnumBottleFluid.YE_HUA_XI_RANG
        );
        HeTongFluidBottle = new ItemBottleBase(
            "HeTongFluidBottle",
            "HeTongPing_empty"
        );
        ChiTongGasTank = new ItemBottleBase(
            "ChiTongGasTank",
            "ChiTongGasTank_empty",
            EnumBottleFluid.SUAN_QI,
            EnumBottleFluid.QI_TAI_CHI_TONG,
            EnumBottleFluid.QI_TAI_HE_TONG,
            EnumBottleFluid.QI_TAI_ZHUO_TONG,
            EnumBottleFluid.DUO_QI,
            EnumBottleFluid.SHUI_ZHENG_QI,
            EnumBottleFluid.XI_RANG_QI,
            EnumBottleFluid.ZHONG_XI_RANG_QI
        );

        GTCMItemList.ChiTongFluidBottle.set(registryAndCallback(ChiTongFluidBottle, "ChiTongFluidBottle", 0));
        GTCMItemList.GangZhiFluidBottle.set(registryAndCallback(GangZhiFluidBottle, "GangZhiFluidBottle", 0));
        GTCMItemList.LanTieFluidBottle.set(registryAndCallback(LanTieFluidBottle, "LanTieFluidBottle", 0));
        GTCMItemList.ZiJingZhiFluidBottle.set(registryAndCallback(ZiJingZhiFluidBottle, "ZiJingZhiFluidBottle", 0));
        GTCMItemList.GaoJingFluidBottle.set(registryAndCallback(GaoJingFluidBottle, "GaoJingFluidBottle", 0));
        GTCMItemList.HeTongFluidBottle.set(registryAndCallback(HeTongFluidBottle, "HeTongFluidBottle", 0));
        GTCMItemList.ChiTongGasTank.set(registryAndCallback(ChiTongGasTank, "ChiTongGasTank", 0));

        GTCMReactorFuelCells.init();
    }

    private static ItemStack registryAndCallback(Item item, String name) {
        return registryAndCallback(item, name, 0);
    }

    private static ItemStack registryAndCallback(Item item, String name, int aMeta) {
        GameRegistry.registerItem(item, name);
        return new ItemStack(item, 1, aMeta);
    }
}
