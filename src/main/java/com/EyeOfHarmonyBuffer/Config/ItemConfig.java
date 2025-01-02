package com.EyeOfHarmonyBuffer.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.EyeOfHarmonyBuffer.utils.GemErgodic;
import net.minecraftforge.common.config.Configuration;

import com.EyeOfHarmonyBuffer.utils.ItemInfo;
import com.EyeOfHarmonyBuffer.utils.UnitParser;
import net.minecraftforge.oredict.OreDictionary;

public class ItemConfig {

    public static List<ItemInfo> outputItems = new ArrayList<>();
    private static Configuration config;
    private static GemErgodic gemErgodic;

    public static void setGemErgodic(GemErgodic instance) {
        gemErgodic = instance;
    }

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    public static void reloadConfig() {
        if (config != null) {
            config.load();
            loadConfig();
        }
    }

    public static void loadConfig() {
        String[] itemsConfig = config.get(
                "鸿蒙之眼功能",
                "物品列表",
                new String[] { "miscutils:MU-metaitem.01:2G:32105", "oreDict:dustSteeleaf:2G" },
                "要输出的物品列表，每个条目格式为 modid:itemname:quantity:meta 或者使用矿物词典 oreDict:quantity 来指定，支持long级别的物品输出，大于int数量的物品请使用字符来表示，例如100T，100G等方式"
                    + "目前支持K(千)，M(百万，B、G(10亿),T(万亿),P(千万亿),E(百亿亿))")
            .getStringList();
        outputItems.clear();

        for (String itemConfig : itemsConfig) {
            itemConfig = itemConfig.trim();

            if (itemConfig.startsWith("oreDict:")) {
                String[] parts = itemConfig.split(":");
                if (parts.length >= 2) {
                    String oreDictName = parts[1];
                    long quantity = UnitParser.parseQuantityWithUnits(parts[2]);
                    outputItems.add(new ItemInfo(oreDictName, quantity));
                }
            } else {
                String[] parts = itemConfig.split(":");
                if (parts.length == 4) {
                    String modid = parts[0];
                    String itemName = parts[1];
                    long quantity = UnitParser.parseQuantityWithUnits(parts[2]);
                    int meta = Integer.parseInt(parts[3]);
                    outputItems.add(new ItemInfo(modid, itemName, quantity, meta));
                }
            }
        }
        addOreDictionaryItems();

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static void addOreDictionaryItems() {
        if(!MainConfig.EOHGemEnable){
            return;
        }

        if (gemErgodic == null) {
            System.err.println("GemErgodic 实例未初始化，无法添加矿物词典条目！");
            return;
        }

        List<String> gemOreNames = gemErgodic.getGemOreNames();

        for (String oreDictName : gemOreNames) {
            if (OreDictionary.getOres(oreDictName).isEmpty()) {
                System.err.println("矿物词典条目不存在: " + oreDictName);
                continue;
            }

            long quantity = UnitParser.parseQuantityWithUnits(MainConfig.EOHGem);

            outputItems.add(new ItemInfo(oreDictName, quantity));
        }

        System.out.println("已从矿物词典中添加所有 gem 条目，共计: " + gemOreNames.size());
    }
}
