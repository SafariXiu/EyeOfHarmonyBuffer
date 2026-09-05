package com.EyeOfHarmonyBuffer.common.item;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import com.EyeOfHarmonyBuffer.common.item.itemadders.*;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.*;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.FuelRod.GTCMReactorFuelCells;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Map;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * EOHB 物品统一注册入口。
 * 字段全部声明不初始化，注册时 new 对应类，字段名对齐 {@link GTCMItemList} 条目名。
 * 分块：普通物品 / 中间产物（每物独立类）/ 流体瓶 / 燃料棒（{@link GTCMReactorFuelCells} 独立）。
 */
public final class EOHBItemRegistry {

    private EOHBItemRegistry() {
    }

    // 普通物品

    public static Item ChengDuHeart;
    public static Item Monkey;
    public static Item Shit;
    public static Item YuanShi;
    public static Item HeChengYu;
    public static Item LanTieKuang;
    public static Item YuanShiKuang;
    public static Item ZiJingKuang;
    public static Item DiRongGuDiDianChi;
    public static Item ZhongRongGuDiDianChi;
    public static Item GaoRongGuDiDianChi;
    public static Item DiRongXiRangDianChi;
    public static Item PoSuiYuanShi;
    public static Item JingTiWaiKeFenMo;
    public static Item LanTieFenMo;
    public static Item ShaYeFenMo;
    public static Item TanFenMo;
    public static Item TongHuaGuanMuFenMo;
    public static Item XiRang;
    public static Item YuanShiFenMo;
    public static Item ZiJingFenMo;
    public static Item TanKuai;
    public static Item JingTiWaiKe;
    public static Item LanTieKuai;
    public static Item WenDingTanKuai;
    public static Item ZiJingXianWei;
    public static Item MiZhiJingTi;
    public static Item GaoJingXianWei;
    public static Item GangKuai;
    public static Item UnactivatedYuanShi;
    public static Item UpgradeChipsMK1;
    public static Item UpgradeChipsMK2;
    public static Item UpgradeChipsMK3;
    public static Item MiGuardFrostShard;
    public static Item GongYeBaoZhaWu;
    public static Item YaZhenZhenJi;
    public static Item JinCaoRuanYin;
    public static Item YouZhiYaZhenZhenJi;
    public static Item YouZhiJinCaoRuanYin;
    public static Item GanShiGuanTou;
    public static Item YouZhiGanShiGuanTou;
    public static Item JingXuanGanShiGuanTou;
    public static Item QiaoYuJiaoNang;
    public static Item YouZhiQiaoYuJiaoNang;
    public static Item JingXuanQiaoYuJiaoNang;
    public static Item HeTongRongYe;
    public static Item QingShui;
    public static Item ChenJiSuan;
    public static Item DuoQi;
    public static Item ShuiZhengQi;
    public static Item SuanQi;
    public static Item XiRangQi;
    public static Item QiTaiZhuoTong;
    public static Item KuangMaiCaiJiZhe;
    public static Item EnergyConnector;
    public static Item OrbitalRailgun;
    public static Item DysonCloudComponent;
    public static Item DysonFrameComponent;

    // 流体瓶 / 气罐

    public static Item ChiTongFluidBottle;
    public static Item GangZhiFluidBottle;
    public static Item LanTieFluidBottle;
    public static Item ZiJingZhiFluidBottle;
    public static Item GaoJingFluidBottle;
    // HeTongFluidBottle：暂不注册（未设计完成）
    public static Item ChiTongGasTank;

    // 中间产物（每物独立类）

    public static Item ChunJingYuanShiFenMo;
    public static Item DiChunYuanShiFenMo;
    public static Item YuanShiJingHe;
    public static Item HanZaYuanShiFenMo;
    public static Item ChiTongKuang;
    public static Item GanShiZhongZi;
    public static Item JinCaoZhongZi;
    public static Item QiaoHuaZhongZi;
    public static Item ShaYeZhongZi;
    public static Item TongHuaShuZhong;
    public static Item YaZhenZhongZi;
    public static Item ZhiMiJingTiFenMo;
    public static Item ZhiMiYuanShiFenMo;
    public static Item ZhiMiTanFenMo;
    public static Item ZhiMiLanTieFenMo;
    public static Item GaoJingFenMo;
    public static Item GanShiFenMo;
    public static Item YaZhenFenMo;
    public static Item QiaoHuaFenMo;
    public static Item JinCaoFenMo;
    public static Item GangZhiLingJian;
    public static Item GaoJingLingJian;
    public static Item ZiJingLingJian;
    public static Item TieZhiLingJian;
    public static Item GangZhiPing;
    public static Item GaoJingZhiPing;
    public static Item ZiJingZhiPing;
    public static Item LanTiePing;
    public static Item XiMoGanShiFenMo;
    public static Item XiMoQiaoHuaFenMo;
    public static Item XieYiYuanShi;
    public static Item ChiTongFenMo;
    public static Item ChiTongKuai;
    public static Item ChiTongLingJian;
    public static Item ChiTongPing;
    public static Item HeTongKuai;
    public static Item HeTongLingJian;
    public static Item HeTongPing;
    public static Item RangJing;
    public static Item ZhongRongWuLingDianChi;
    public static Item ZhongXiRang;
    public static Item FuelRod_empty1;
    public static Item FuelRod_empty2;
    public static Item FuelRod_empty4;
    public static Item OrundumPowder;
    public static Item OrundumSlag;
    public static Item YuanShiTongWeiSu_Alpha;
    public static Item ZhuoTongKuai;
    public static Item ZhuoTongLingJian;
    public static Item ChiTongNaiYaPing;
    public static Item ShuRangYi;
    public static Item ShuRangYi_KuoRongYiXing;
    public static Item ShuRangYi_KuoRongErXing;
    public static Item FenLiXin;
    public static Item ZiJingZhuangBeiYuanJian;
    public static Item LanTieZhuangBeiYuanJian;
    public static Item GaoJingZhuangBeiYuanJian;
    public static Item XiRangZhuangBeiYuanJian;
    public static Item ChiTongZhuangBeiYuanJian;
    public static Item HeTongZhuangBeiYuanJian;
    public static Item ZhuoTongZhuangBeiYuanJian;
    public static Item LongGu;
    public static Item Tan;
    public static Item TanSu;
    public static Item TanSuZu;
    public static Item JiChuJiaGuJianCai;
    public static Item JinJieJiaGuJianCai;
    public static Item GaoJiJiaGuJianCai;
    public static Item YuanShiSuiPian;
    public static Item ChiJin;
    public static Item JiQiaoGaiYao_Juan1;
    public static Item JiQiaoGaiYao_Juan2;
    public static Item JiQiaoGaiYao_Juan3;
    public static Item NiuZhuanChun;
    public static Item BaiMaChun;
    public static Item ShuangJiNaMiPian;
    public static Item PoSunZhuangZhi;
    public static Item ZhuangZhi;
    public static Item QuanXinZhuangZhi;
    public static Item GaiLiangZhuangZhi;
    public static Item HuaHeQieXiaoYe;
    public static Item DianJiDanYuan;
    public static Item JuNengDongLiDanYuan;
    public static Item D32Gang;
    public static Item ZhongXiangWeiDuiYingTi;
    public static Item YuanYan;
    public static Item GuYuanYan;
    public static Item GuYuanYanZu;
    public static Item TiChunYuanYan;
    public static Item HuanTingJuZhi;
    public static Item HuanTingYuZhiTi;
    public static Item ChiHeJin;
    public static Item ChiHeJinKuai;
    public static Item YiTieSuiPian;
    public static Item YiTie;
    public static Item YiTieZu;
    public static Item YiTieKuai;
    public static Item LeiNingJieHe;
    public static Item ShuangTong;
    public static Item TongNingJi;
    public static Item TongNingJiZu;
    public static Item TongZhenLie;
    public static Item QingMengKuang;
    public static Item SanShuiMengKuang;
    public static Item JingTiYuanJian;
    public static Item JingTiDianLu;
    public static Item JingTiDianZiDanYuan;
    public static Item YanMoShi;
    public static Item WuShuiYanMoShi;
    public static Item NingJiao;
    public static Item JuHeNingJiao;
    public static Item QieXiaoYuanYe;
    public static Item JuHeJi;
    public static Item ShouXingQuGuangTi;
    public static Item RMA70_12;
    public static Item RMA70_24;
    public static Item JingLianRongJi;
    public static Item ZhiYuanLiao;
    public static Item JuSuanZhi;
    public static Item JuSuanZhiZu;
    public static Item JuSuanZhiKuai;
    public static Item ShaoJieHeNingJing;
    public static Item BanZiRanRongJi;
    public static Item DaiTang;
    public static Item Tang;
    public static Item TangZu;
    public static Item TangJuKuai;
    public static Item HeSuXianWei;
    public static Item GuHuaXianWeiBan;
    public static Item YeHuaGaoNengQiTi;
    public static Item YeHuaMiXiJuTi;
    public static Item ZhuanZhiYanZu;
    public static Item ZhuanZhiYanJuKuai;
    public static Item QiYiWuZhi;
    public static Item D96GangYangPin4;
    public static Item SanXiangNaMiPian;
    public static Item KuaiZiLinJianJingGe;
    public static Item XiangXianNiHeYe;
    public static Item ChaoJuHuiYingGuan;

    // 注册入口

    /** main preInit 调用（须在方块注册之后、植物注册之前）。 */
    public static void registryItems() {
        registerNormalItems();
        registerIntermediateItems();
        registerFluidBottles();
        GTCMReactorFuelCells.init(); // 燃料棒保持独立体系
    }

    /** 普通物品：字段声明不初始化，注册时 new 对应类。字段名已对齐 GTCMItemList 条目名。 */
    private static void registerNormalItems() {
        ChengDuHeart = regItem(new ItemChengDuHeart(), "chengdu_heart", GTCMItemList.ChengDuHeart);
        Monkey = regItem(new ItemMonkey(), "Monkey", GTCMItemList.Monkey);
        Shit = regItem(new ItemShit(), "Shit", GTCMItemList.Shit);
        YuanShi = regItem(new ItemYuanShi(), "YuanShi", GTCMItemList.YuanShi);
        HeChengYu = regItem(new ItemHeChengYu(), "HeChengYu", GTCMItemList.HeChengYu);
        LanTieKuang = regItem(new ItemLanTieKuang(), "LanTieKuang", GTCMItemList.LanTieKuang);
        YuanShiKuang = regItem(new ItemYuanShiKuang(), "YuanShiKuang", GTCMItemList.YuanShiKuang);
        ZiJingKuang = regItem(new ItemZiJingKuang(), "ZiJingKuang", GTCMItemList.ZiJingKuang);
        DiRongGuDiDianChi = regItem(new ItemDiRongLiangDianChi(), "DiRongLiangDianChi", GTCMItemList.DiRongGuDiDianChi);
        ZhongRongGuDiDianChi = regItem(new ItemZhongRongLiangDianChi(), "ZhongRongLiangDianChi", GTCMItemList.ZhongRongGuDiDianChi);
        GaoRongGuDiDianChi = regItem(new ItemGaoRongLiangDianChi(), "GaoRongLiangDianChi", GTCMItemList.GaoRongGuDiDianChi);
        DiRongXiRangDianChi = regItem(new ItemXiRangDiRongLiangDianChi(), "XiRangDiRongLiangDianChi", GTCMItemList.DiRongXiRangDianChi);
        PoSuiYuanShi = regItem(new ItemPoSuiYuanShi(), "PoSuiYuanShi", GTCMItemList.PoSuiYuanShi);
        JingTiWaiKeFenMo = regItem(new ItemJingTiWaiKeFenMo(), "JingTiWaiKeFenMo", GTCMItemList.JingTiWaiKeFenMo);
        LanTieFenMo = regItem(new ItemLanTieFenMo(), "LanTieFenMo", GTCMItemList.LanTieFenMo);
        ShaYeFenMo = regItem(new ItemShaYeFenMo(), "ShaYeFenMo", GTCMItemList.ShaYeFenMo);
        TanFenMo = regItem(new ItemTanFenMo(), "TanFenMo", GTCMItemList.TanFenMo);
        TongHuaGuanMuFenMo = regItem(new ItemTongHuaGuanMuFenMo(), "TongHuaGuanMuFenMo", GTCMItemList.TongHuaGuanMuFenMo);
        XiRang = regItem(new ItemXiRang(), "XiRang", GTCMItemList.XiRang);
        YuanShiFenMo = regItem(new ItemYuanShiFenMo(), "YuanShiFenMo", GTCMItemList.YuanShiFenMo);
        ZiJingFenMo = regItem(new ItemZiJingFenMo(), "ZiJingFenMo", GTCMItemList.ZiJingFenMo);
        TanKuai = regItem(new ItemTanKuai(), "TanKuai", GTCMItemList.TanKuai);
        JingTiWaiKe = regItem(new ItemJingTiWaiKe(), "JingTiWaiKe", GTCMItemList.JingTiWaiKe);
        LanTieKuai = regItem(new ItemLanTieKuai(), "LanTieKuai", GTCMItemList.LanTieKuai);
        WenDingTanKuai = regItem(new ItemWenDingTanKuai(), "WenDingTanKuai", GTCMItemList.WenDingTanKuai);
        ZiJingXianWei = regItem(new ItemZiJingXianWei(), "ZiJingXianWei", GTCMItemList.ZiJingXianWei);
        MiZhiJingTi = regItem(new ItemMiZhiJingTi(), "MiZhiJingTi", GTCMItemList.MiZhiJingTi);
        GaoJingXianWei = regItem(new ItemGaoJingXianWei(), "GaoJingXianWei", GTCMItemList.GaoJingXianWei);
        GangKuai = regItem(new ItemGangKuai(), "GangKuai", GTCMItemList.GangKuai);
        UnactivatedYuanShi = regItem(new ItemUnactivatedYuanShi(), "UnactivatedYuanShi", GTCMItemList.UnactivatedYuanShi);
        UpgradeChipsMK1 = regItem(new itemUpgradeChipMK1(), "UpgradeChipMK1", GTCMItemList.UpgradeChipsMK1);
        UpgradeChipsMK2 = regItem(new itemUpgradeChipMK2(), "UpgradeChipMK2", GTCMItemList.UpgradeChipsMK2);
        UpgradeChipsMK3 = regItem(new itemUpgradeChipMK3(), "UpgradeChipMK3", GTCMItemList.UpgradeChipsMK3);
        MiGuardFrostShard = regItem(new itemMiGuardFrostShard(), "MiGuardFrostShard", GTCMItemList.MiGuardFrostShard);
        GongYeBaoZhaWu = regItem(new ItemGongYeBaoZhaWu(), "GongYeBaoZhaWu", GTCMItemList.GongYeBaoZhaWu);
        YaZhenZhenJi = regItem(new ItemYaZhenZhenJi(), "YaZhenZhenJi", GTCMItemList.YaZhenZhenJi);
        JinCaoRuanYin = regItem(new ItemJinCaoRuanYin(), "JinCaoRuanYin", GTCMItemList.JinCaoRuanYin);
        YouZhiYaZhenZhenJi = regItem(new ItemYouZhiYaZhenZhenJi(), "YouZhiYaZhenZhenJi", GTCMItemList.YouZhiYaZhenZhenJi);
        YouZhiJinCaoRuanYin = regItem(new ItemYouZhiJinCaoRuanYin(), "YouZhiJinCaoRuanYin", GTCMItemList.YouZhiJinCaoRuanYin);
        GanShiGuanTou = regItem(new ItemGanShiGuanTou(), "GanShiGuanTou", GTCMItemList.GanShiGuanTou);
        YouZhiGanShiGuanTou = regItem(new ItemYouZhiGanShiGuanTou(), "YouZhiGanShiGuanTou", GTCMItemList.YouZhiGanShiGuanTou);
        JingXuanGanShiGuanTou = regItem(new ItemJingXuanGanShiGuanTou(), "JingXuanGanShiGuanTou", GTCMItemList.JingXuanGanShiGuanTou);
        QiaoYuJiaoNang = regItem(new ItemQiaoYuJiaoNang(), "QiaoYuJiaoNang", GTCMItemList.QiaoYuJiaoNang);
        YouZhiQiaoYuJiaoNang = regItem(new ItemYouZhiQiaoYuJiaoNang(), "YouZhiQiaoYuJiaoNang", GTCMItemList.YouZhiQiaoYuJiaoNang);
        JingXuanQiaoYuJiaoNang = regItem(new ItemJingXuanQiaoYuJiaoNang(), "JingXuanQiaoYuJiaoNang", GTCMItemList.JingXuanQiaoYuJiaoNang);
        HeTongRongYe = regItem(new ItemHeTongRongYe(), "HeTongRongYe", GTCMItemList.HeTongRongYe);
        QingShui = regItem(new itemQingShui(), "QingShui", GTCMItemList.QingShui);
        ChenJiSuan = regItem(new ItemChenJiSuan(), "ChenJiSuan", GTCMItemList.ChenJiSuan);
        DuoQi = regItem(new ItemDuoQi(), "DuoQi", GTCMItemList.DuoQi);
        ShuiZhengQi = regItem(new ItemShuiZhengQi(), "ShuiZhengQi", GTCMItemList.ShuiZhengQi);
        SuanQi = regItem(new ItemSuanQi(), "SuanQi", GTCMItemList.SuanQi);
        XiRangQi = regItem(new ItemXiRangQi(), "XiRangQi", GTCMItemList.XiRangQi);
        QiTaiZhuoTong = regItem(new ItemQiTaiZhuoTong(), "QiTaiZhuoTong", GTCMItemList.QiTaiZhuoTong);
        KuangMaiCaiJiZhe = regItem(new ItemKuangMaiCaiJiZhe(), "KuangMaiCaiJiZhe", GTCMItemList.KuangMaiCaiJiZhe);
        EnergyConnector = regItem(new ItemEnergyConnector(), "EnergyConnector", GTCMItemList.EnergyConnector);
        OrbitalRailgun = regItem(new ItemOrbitalRailgun(), "OrbitalRailgun", GTCMItemList.OrbitalRailgun);
        DysonCloudComponent = regItem(new ItemDysonCloudComponent(), "DysonCloudComponent", GTCMItemList.DysonCloudComponent);
        DysonFrameComponent = regItem(new ItemDysonFrameComponent(), "DysonFrameComponent", GTCMItemList.DysonFrameComponent);
    }

    /** 中间产物：每物独立类（ItemEOHBBatch 子类），注册时 new，tooltip 已内置于类。 */
    private static void registerIntermediateItems() {
        ChunJingYuanShiFenMo = regIntermediate(new ItemChunJingYuanShiFenMo());
        DiChunYuanShiFenMo = regIntermediate(new ItemDiChunYuanShiFenMo());
        YuanShiJingHe = regIntermediate(new ItemYuanShiJingHe());
        HanZaYuanShiFenMo = regIntermediate(new ItemHanZaYuanShiFenMo());
        ChiTongKuang = regIntermediate(new ItemChiTongKuang());
        GanShiZhongZi = regIntermediate(new ItemGanShiZhongZi());
        JinCaoZhongZi = regIntermediate(new ItemJinCaoZhongZi());
        QiaoHuaZhongZi = regIntermediate(new ItemQiaoHuaZhongZi());
        ShaYeZhongZi = regIntermediate(new ItemShaYeZhongZi());
        TongHuaShuZhong = regIntermediate(new ItemTongHuaShuZhong());
        YaZhenZhongZi = regIntermediate(new ItemYaZhenZhongZi());
        ZhiMiJingTiFenMo = regIntermediate(new ItemZhiMiJingTiFenMo());
        ZhiMiYuanShiFenMo = regIntermediate(new ItemZhiMiYuanShiFenMo());
        ZhiMiTanFenMo = regIntermediate(new ItemZhiMiTanFenMo());
        ZhiMiLanTieFenMo = regIntermediate(new ItemZhiMiLanTieFenMo());
        GaoJingFenMo = regIntermediate(new ItemGaoJingFenMo());
        GanShiFenMo = regIntermediate(new ItemGanShiFenMo());
        YaZhenFenMo = regIntermediate(new ItemYaZhenFenMo());
        QiaoHuaFenMo = regIntermediate(new ItemQiaoHuaFenMo());
        JinCaoFenMo = regIntermediate(new ItemJinCaoFenMo());
        GangZhiLingJian = regIntermediate(new ItemGangZhiLingJian());
        GaoJingLingJian = regIntermediate(new ItemGaoJingLingJian());
        ZiJingLingJian = regIntermediate(new ItemZiJingLingJian());
        TieZhiLingJian = regIntermediate(new ItemTieZhiLingJian());
        GangZhiPing = regIntermediate(new ItemGangZhiPing());
        GaoJingZhiPing = regIntermediate(new ItemGaoJingZhiPing());
        ZiJingZhiPing = regIntermediate(new ItemZiJingZhiPing());
        LanTiePing = regIntermediate(new ItemLanTiePing());
        XiMoGanShiFenMo = regIntermediate(new ItemXiMoGanShiFenMo());
        XiMoQiaoHuaFenMo = regIntermediate(new ItemXiMoQiaoHuaFenMo());
        XieYiYuanShi = regIntermediate(new ItemXieYiYuanShi());
        ChiTongFenMo = regIntermediate(new ItemChiTongFenMo());
        ChiTongKuai = regIntermediate(new ItemChiTongKuai());
        ChiTongLingJian = regIntermediate(new ItemChiTongLingJian());
        ChiTongPing = regIntermediate(new ItemChiTongPing());
        HeTongKuai = regIntermediate(new ItemHeTongKuai());
        HeTongLingJian = regIntermediate(new ItemHeTongLingJian());
        HeTongPing = regIntermediate(new ItemHeTongPing());
        RangJing = regIntermediate(new ItemRangJing());
        ZhongRongWuLingDianChi = regIntermediate(new ItemZhongRongWuLingDianChi());
        ZhongXiRang = regIntermediate(new ItemZhongXiRang());
        FuelRod_empty1 = regIntermediate(new ItemFuelRod_empty1());
        FuelRod_empty2 = regIntermediate(new ItemFuelRod_empty2());
        FuelRod_empty4 = regIntermediate(new ItemFuelRod_empty4());
        OrundumPowder = regIntermediate(new ItemOrundumPowder());
        OrundumSlag = regIntermediate(new ItemOrundumSlag());
        YuanShiTongWeiSu_Alpha = regIntermediate(new ItemYuanShiTongWeiSu_Alpha());
        ZhuoTongKuai = regIntermediate(new ItemZhuoTongKuai());
        ZhuoTongLingJian = regIntermediate(new ItemZhuoTongLingJian());
        ChiTongNaiYaPing = regIntermediate(new ItemChiTongNaiYaPing());
        ShuRangYi = regIntermediate(new ItemShuRangYi());
        ShuRangYi_KuoRongYiXing = regIntermediate(new ItemShuRangYi_KuoRongYiXing());
        ShuRangYi_KuoRongErXing = regIntermediate(new ItemShuRangYi_KuoRongErXing());
        FenLiXin = regIntermediate(new ItemFenLiXin());
        ZiJingZhuangBeiYuanJian = regIntermediate(new ItemZiJingZhuangBeiYuanJian());
        LanTieZhuangBeiYuanJian = regIntermediate(new ItemLanTieZhuangBeiYuanJian());
        GaoJingZhuangBeiYuanJian = regIntermediate(new ItemGaoJingZhuangBeiYuanJian());
        XiRangZhuangBeiYuanJian = regIntermediate(new ItemXiRangZhuangBeiYuanJian());
        ChiTongZhuangBeiYuanJian = regIntermediate(new ItemChiTongZhuangBeiYuanJian());
        HeTongZhuangBeiYuanJian = regIntermediate(new ItemHeTongZhuangBeiYuanJian());
        ZhuoTongZhuangBeiYuanJian = regIntermediate(new ItemZhuoTongZhuangBeiYuanJian());
        LongGu = regIntermediate(new ItemLongGu());
        Tan = regIntermediate(new ItemTan());
        TanSu = regIntermediate(new ItemTanSu());
        TanSuZu = regIntermediate(new ItemTanSuZu());
        JiChuJiaGuJianCai = regIntermediate(new ItemJiChuJiaGuJianCai());
        JinJieJiaGuJianCai = regIntermediate(new ItemJinJieJiaGuJianCai());
        GaoJiJiaGuJianCai = regIntermediate(new ItemGaoJiJiaGuJianCai());
        YuanShiSuiPian = regIntermediate(new ItemYuanShiSuiPian());
        ChiJin = regIntermediate(new ItemChiJin());
        JiQiaoGaiYao_Juan1 = regIntermediate(new ItemJiQiaoGaiYao_Juan1());
        JiQiaoGaiYao_Juan2 = regIntermediate(new ItemJiQiaoGaiYao_Juan2());
        JiQiaoGaiYao_Juan3 = regIntermediate(new ItemJiQiaoGaiYao_Juan3());
        NiuZhuanChun = regIntermediate(new ItemNiuZhuanChun());
        BaiMaChun = regIntermediate(new ItemBaiMaChun());
        ShuangJiNaMiPian = regIntermediate(new ItemShuangJiNaMiPian());
        PoSunZhuangZhi = regIntermediate(new ItemPoSunZhuangZhi());
        ZhuangZhi = regIntermediate(new ItemZhuangZhi());
        QuanXinZhuangZhi = regIntermediate(new ItemQuanXinZhuangZhi());
        GaiLiangZhuangZhi = regIntermediate(new ItemGaiLiangZhuangZhi());
        HuaHeQieXiaoYe = regIntermediate(new ItemHuaHeQieXiaoYe());
        DianJiDanYuan = regIntermediate(new ItemDianJiDanYuan());
        JuNengDongLiDanYuan = regIntermediate(new ItemJuNengDongLiDanYuan());
        D32Gang = regIntermediate(new ItemD32Gang());
        ZhongXiangWeiDuiYingTi = regIntermediate(new ItemZhongXiangWeiDuiYingTi());
        YuanYan = regIntermediate(new ItemYuanYan());
        GuYuanYan = regIntermediate(new ItemGuYuanYan());
        GuYuanYanZu = regIntermediate(new ItemGuYuanYanZu());
        TiChunYuanYan = regIntermediate(new ItemTiChunYuanYan());
        HuanTingJuZhi = regIntermediate(new ItemHuanTingJuZhi());
        HuanTingYuZhiTi = regIntermediate(new ItemHuanTingYuZhiTi());
        ChiHeJin = regIntermediate(new ItemChiHeJin());
        ChiHeJinKuai = regIntermediate(new ItemChiHeJinKuai());
        YiTieSuiPian = regIntermediate(new ItemYiTieSuiPian());
        YiTie = regIntermediate(new ItemYiTie());
        YiTieZu = regIntermediate(new ItemYiTieZu());
        YiTieKuai = regIntermediate(new ItemYiTieKuai());
        LeiNingJieHe = regIntermediate(new ItemLeiNingJieHe());
        ShuangTong = regIntermediate(new ItemShuangTong());
        TongNingJi = regIntermediate(new ItemTongNingJi());
        TongNingJiZu = regIntermediate(new ItemTongNingJiZu());
        TongZhenLie = regIntermediate(new ItemTongZhenLie());
        QingMengKuang = regIntermediate(new ItemQingMengKuang());
        SanShuiMengKuang = regIntermediate(new ItemSanShuiMengKuang());
        JingTiYuanJian = regIntermediate(new ItemJingTiYuanJian());
        JingTiDianLu = regIntermediate(new ItemJingTiDianLu());
        JingTiDianZiDanYuan = regIntermediate(new ItemJingTiDianZiDanYuan());
        YanMoShi = regIntermediate(new ItemYanMoShi());
        WuShuiYanMoShi = regIntermediate(new ItemWuShuiYanMoShi());
        NingJiao = regIntermediate(new ItemNingJiao());
        JuHeNingJiao = regIntermediate(new ItemJuHeNingJiao());
        QieXiaoYuanYe = regIntermediate(new ItemQieXiaoYuanYe());
        JuHeJi = regIntermediate(new ItemJuHeJi());
        ShouXingQuGuangTi = regIntermediate(new ItemShouXingQuGuangTi());
        RMA70_12 = regIntermediate(new ItemRMA70_12());
        RMA70_24 = regIntermediate(new ItemRMA70_24());
        JingLianRongJi = regIntermediate(new ItemJingLianRongJi());
        ZhiYuanLiao = regIntermediate(new ItemZhiYuanLiao());
        JuSuanZhi = regIntermediate(new ItemJuSuanZhi());
        JuSuanZhiZu = regIntermediate(new ItemJuSuanZhiZu());
        JuSuanZhiKuai = regIntermediate(new ItemJuSuanZhiKuai());
        ShaoJieHeNingJing = regIntermediate(new ItemShaoJieHeNingJing());
        BanZiRanRongJi = regIntermediate(new ItemBanZiRanRongJi());
        DaiTang = regIntermediate(new ItemDaiTang());
        Tang = regIntermediate(new ItemTang());
        TangZu = regIntermediate(new ItemTangZu());
        TangJuKuai = regIntermediate(new ItemTangJuKuai());
        HeSuXianWei = regIntermediate(new ItemHeSuXianWei());
        GuHuaXianWeiBan = regIntermediate(new ItemGuHuaXianWeiBan());
        YeHuaGaoNengQiTi = regIntermediate(new ItemYeHuaGaoNengQiTi());
        YeHuaMiXiJuTi = regIntermediate(new ItemYeHuaMiXiJuTi());
        ZhuanZhiYanZu = regIntermediate(new ItemZhuanZhiYanZu());
        ZhuanZhiYanJuKuai = regIntermediate(new ItemZhuanZhiYanJuKuai());
        QiYiWuZhi = regIntermediate(new ItemQiYiWuZhi());
        D96GangYangPin4 = regIntermediate(new ItemD96GangYangPin4());
        SanXiangNaMiPian = regIntermediate(new ItemSanXiangNaMiPian());
        KuaiZiLinJianJingGe = regIntermediate(new ItemKuaiZiLinJianJingGe());
        XiangXianNiHeYe = regIntermediate(new ItemXiangXianNiHeYe());
        ChaoJuHuiYingGuan = regIntermediate(new ItemChaoJuHuiYingGuan());
    }

    /** 流体瓶 / 气罐。 */
    private static void registerFluidBottles() {
        ChiTongFluidBottle = regFluidBottle("ChiTongFluidBottle", "ChiTongPing_empty",
            EnumBottleFluid.YA_ZHEN_RONG_YE, EnumBottleFluid.JIN_CAO_RONG_YE);
        GangZhiFluidBottle = regFluidBottle("GangZhiFluidBottle", "GangZhiPing_empty",
            EnumBottleFluid.QING_SHUI, EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE, EnumBottleFluid.YE_HUA_XI_RANG);
        LanTieFluidBottle = regFluidBottle("LanTieFluidBottle", "LanTiePing_empty",
            EnumBottleFluid.YA_ZHEN_RONG_YE, EnumBottleFluid.JIN_CAO_RONG_YE,
            EnumBottleFluid.QING_SHUI, EnumBottleFluid.YE_HUA_XI_RANG,
            EnumBottleFluid.YE_HUA_ZHONG_XI_RANG);
        ZiJingZhiFluidBottle = regFluidBottle("ZiJingZhiFluidBottle", "ZiJingZhiPing_empty",
            EnumBottleFluid.QING_SHUI, EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE, EnumBottleFluid.YE_HUA_XI_RANG);
        GaoJingFluidBottle = regFluidBottle("GaoJingFluidBottle", "GaoJingZhiPing_empty",
            EnumBottleFluid.QING_SHUI, EnumBottleFluid.YA_ZHEN_RONG_YE,
            EnumBottleFluid.JIN_CAO_RONG_YE, EnumBottleFluid.YE_HUA_XI_RANG);
        // HeTongFluidBottle：暂不注册（未设计完成）
        ChiTongGasTank = regFluidBottle("ChiTongGasTank", "ChiTongGasTank_empty",
            EnumBottleFluid.SUAN_QI, EnumBottleFluid.QI_TAI_CHI_TONG,
            EnumBottleFluid.QI_TAI_HE_TONG, EnumBottleFluid.QI_TAI_ZHUO_TONG,
            EnumBottleFluid.DUO_QI, EnumBottleFluid.SHUI_ZHENG_QI,
            EnumBottleFluid.XI_RANG_QI, EnumBottleFluid.ZHONG_XI_RANG_QI);
    }

    // 注册辅助

    /** 普通物品：注册 + 挂 GTCMItemList（注册名 = 条目枚举名）。 */
    private static Item regItem(Item item, String name, GTCMItemList entry) {
        GameRegistry.registerItem(item, name);
        entry.set(new ItemStack(item));
        return item;
    }

    /** 中间产物：独立类已自带 name/texture/tab/tooltip，只做注册 + GTCMItemList 挂接。 */
    private static Item regIntermediate(Item item) {
        String name = item.getUnlocalizedName().substring(5);
        GameRegistry.registerItem(item, name);
        if (!linkByName(name, item)) {
            throw new IllegalStateException("[EOHBItemRegistry] 中间产物未挂接 GTCMItemList: " + name);
        }
        return item;
    }

    /** 流体瓶 / 气罐。 */
    private static com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemBottleBase regFluidBottle(
        String name, String emptyTexture, EnumBottleFluid... allowed) {
        com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemBottleBase bottle =
            new com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemBottleBase(name, emptyTexture, allowed);
        GameRegistry.registerItem(bottle, name);
        if (!linkByName(name, bottle)) {
            throw new IllegalStateException("[EOHBItemRegistry] 流体瓶未挂接 GTCMItemList: " + name);
        }
        return bottle;
    }

    /** 按枚举名挂接 GTCMItemList；枚举不存在时返回 false（调用方据此快速失败而非静默吞错）。 */
    private static boolean linkByName(String name, Item item) {
        try {
            GTCMItemList.valueOf(name).set(new ItemStack(item));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
