package com.EyeOfHarmonyBuffer.common;

import com.EyeOfHarmonyBuffer.utils.Utils;
import gregtech.api.util.GTLog;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;

public enum GTCMItemList {

    TestItem0,

    //机器主方块
    VendingMachines,
    WindTurbines,
    SolarEnergyArray,
    SubstanceReshapingDevice,
    BlueDogMachines,
    MonkeyShitS,

    //物品
    ChengDuHeart,
    Monkey,
    Shit,
    MiGuardFrostShard,

    //方舟-物品
    YuanShi,
    HeChengYu,
    LanTieKuang,
    YuanShiKuang,
    ZiJingKuang,
    DiRongLiangDianChi,
    ZhongRongLiangDianChi,
    GaoRongLiangDianChi,
    XiRangDiRongLiangDianChi,
    PoSuiYuanShi,
    JingTiWaiKeFenMo,
    LanTieFenMo,
    ShaYeFenMo,
    TanFenMo,
    TongHuaGuanMuFenMo,
    XiRang,
    YuanShiFenMo,
    ZiJingFenMo,
    TanKuai,
    JingTiWaiKe,
    LanTieKuai,
    WenDingTanKuai,
    ZiJingXianWei,
    MiZhiJingTi,
    GaoJingXianWei,
    GangKuai,
    ChunJingYuanShiFenMo,
    DiChunYuanShiFenMo,
    YuanShiJingHe,
    UnactivatedYuanShi,
    HanZaYuanShiFenMo,
    ChiTongKuang,
    UpgradeChipsMK1,
    UpgradeChipsMK2,
    UpgradeChipsMK3,
    ZhiMiJingTiFenMo,
    ZhiMiYuanShiFenMo,
    ZhiMiTanFenMo,
    ZhiMiLanTieFenMo,
    GaoJingFenMo,
    GanShiFenMo,
    YaZhenFenMo,
    QiaoHuaFenMo,
    JinCaoFenMo,
    GangZhiLingJian,
    GaoJingLingJian,
    ZiJingLingJian,
    TieZhiLingJian,
    GangZhiPing,
    GaoJingZhiPing,
    ZiJingZhiPing,
    LanTiePing,
    XiMoGanShiFenMo,
    XiMoQiaoHuaFenMo,
    XiYiYuanShi,
    GongYeBaoZhaWu,
    YaZhenZhenJi,
    JinCaoRuanYin,
    ChiTongFenMo,
    ChiTongKuai,
    ChiTongLingJian,
    ChiTongPing,
    HeTongKuai,
    HeTongLingJian,
    HeTongPing,
    RangJing,
    ZhongRongWuLingDianChi,
    ZhongXiRang,
    ChiTongFluidBottle,
    GangZhiFluidBottle,
    LanTieFluidBottle,
    ZiJingZhiFluidBottle,
    GaoJingFluidBottle,
    HeTongFluidBottle,
    YouZhiYaZhenZhenJi,
    YouZhiJinCaoRuanYin,
    GanShiGuanTou,
    YouZhiGanShiGuanTou,
    JingXuanGanShiGuanTou,
    QiaoYuJiaoNang,
    YouZhiQiaoYuJiaoNang,
    JingXuanQiaoYuJiaoNang,
    HeTongRongYe,
    YuanShiFuelRod1,
    YuanShiFuelRod2,
    YuanShiFuelRod4,
    YuanShiDepletedFuelRod1,
    YuanShiDepletedFuelRod2,
    YuanShiDepletedFuelRod4,
    OrundumPowder,
    OrundumSlag,
    FuelRod_empty1,
    FuelRod_empty2,
    FuelRod_empty4,

    //方舟-植物
    GanShi,
    GanShiZhongZi,
    JinCao,
    JinCaoZhongZi,
    QiaoHua,
    QiaoHuaZhongZi,
    ShaYe,
    ShaYeZhongZi,
    TongHuaGuanMu,
    TongHuaShuZhong,
    YaZhen,
    YaZhenZhongZi,

    //方舟机器
    OrundumDynamos,
    ElectricTypeOneMiningMachines,
    Planters,
    SeedCollectingMachines,
    RefiningFurnaces,
    Pulverizers,
    AccessoriesMachines,
    ShapingMachines,
    Grinders,
    EncapsulationMachines,
    FillingUnits,
    ForgeOfTheSkys,
    PurificationUnits,
    ReactorCrucibles,
    ExpandedCrucibles,
    FluidPump,

    //机器结构方块
    SingularityStabilizationRingCasingsLV,
    SingularityStabilizationRingCasingsMV,
    SingularityStabilizationRingCasingsHV,
    SingularityStabilizationRingCasingsEV,
    SingularityStabilizationRingCasingsIV,
    SingularityStabilizationRingCasingsLuV,
    SingularityStabilizationRingCasingsZPM,
    SingularityStabilizationRingCasingsUV,
    SingularityStabilizationRingCasingsUHV,
    SingularityStabilizationRingCasingsUEV,
    SingularityStabilizationRingCasingsUIV,
    SingularityStabilizationRingCasingsUMV,
    SingularityStabilizationRingCasingsUXV,
    SingularityStabilizationRingCasingsMAX,
    ForgeOfTheSkyCore;


    private boolean mHasNotBeenSet;
    private boolean mDeprecated;
    private boolean mWarned;
    private ItemStack mStack;

    GTCMItemList() {
        mHasNotBeenSet = true;
    }

    GTCMItemList(boolean aDeprecated) {
        if (aDeprecated) {
            mDeprecated = true;
            mHasNotBeenSet = true;
        }
    }

    public GTCMItemList set(Item aItem) {
        mHasNotBeenSet = false;
        if (aItem == null) return this;
        ItemStack aStack = new ItemStack(aItem, 1, 0);
        mStack = Utils.copyAmount(1, aStack);
        return this;
    }

    public GTCMItemList set(ItemStack aStack) {
        if (aStack != null) {
            mHasNotBeenSet = false;
            mStack = Utils.copyAmount(1, aStack);
        }
        return this;
    }

    public ItemStack get(int aAmount, Object... aReplacements) {
        sanityCheck();
        if (Utils.isStackInvalid(mStack)) {
            GTLog.out.println("Object in the ItemList is null at:");
            new NullPointerException().printStackTrace(GTLog.out);
            return Utils.copyAmount(aAmount, TestItem0.get(1));
        }
        return Utils.copyAmount(aAmount, mStack);
    }

    public Item getItem() {
        sanityCheck();
        if (Utils.isStackInvalid(mStack)) return null;
        return mStack.getItem();
    }

    public Block getBlock() {
        sanityCheck();
        return Block.getBlockFromItem(getItem());
    }

    private void sanityCheck() {
        if (mHasNotBeenSet)
            throw new IllegalAccessError("The Enum '" + name() + "' has not been set to an Item at this time!");
        if (mDeprecated && !mWarned) {
            new Exception(this + " is now deprecated").printStackTrace(GTLog.err);
            mWarned = true;
        }
    }

    public int getMeta() {
        return mStack.getItemDamage();
    }

    public boolean hasBeenSet() {
        return !mHasNotBeenSet;
    }

    public ItemStack getInternalStack_unsafe() {
        return mStack;
    }
}
