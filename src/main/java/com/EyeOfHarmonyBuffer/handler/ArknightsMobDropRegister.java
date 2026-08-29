package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import gtPlusPlus.core.util.minecraft.EntityUtils;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;

/**
 * 通过 GT++ 的怪物击杀掉落表注册粥本家物品掉落。
 * 概率单位为万分之几：1000 = 10%。
 */
public final class ArknightsMobDropRegister {

    private ArknightsMobDropRegister() {
    }

    public static void registerDrops() {
        EntityUtils.registerDropsForMob(EntityZombie.class, GTCMItemList.YuanYan.get(1), 1, 1000);
        EntityUtils.registerDropsForMob(EntitySkeleton.class, GTCMItemList.YiTieSuiPian.get(1), 1, 1000);
        EntityUtils.registerDropsForMob(EntityCreeper.class, GTCMItemList.PoSunZhuangZhi.get(1), 1, 1000);
    }
}
