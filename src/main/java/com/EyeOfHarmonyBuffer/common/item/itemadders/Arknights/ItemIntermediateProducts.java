package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;

public class ItemIntermediateProducts {

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

    public static void initAndRegister(String modid) {
        String textureFolder = "Arknights/";

        try {
            for (Field field : ItemIntermediateProducts.class.getDeclaredFields()) {
                if (Item.class.isAssignableFrom(field.getType())) {
                    String name = field.getName();
                    Item item = (Item) field.getType().newInstance();

                    item.setUnlocalizedName(name)
                        .setTextureName(modid + ":" + textureFolder + name);

                    GameRegistry.registerItem(item, name);
                    field.set(null, item);

                    try {
                        GTCMItemList enumEntry = GTCMItemList.valueOf(name);
                        enumEntry.set(new ItemStack(item));
                        System.out.println("[ItemIntermediateProducts] 已注册到 GTCMItemList: " + name);
                    } catch (IllegalArgumentException ignore) {
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
