package com.EyeOfHarmonyBuffer.utils;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;

public class GemErgodic {

    private final List<String> gemOreNames = new ArrayList<>();

    /**
     * 从矿物词典中筛选出所有以 "gem" 开头的条目。
     */
    public void init(FMLInitializationEvent event) {
        String[] oreNames = OreDictionary.getOreNames();

        for (String oreName : oreNames) {
            if (oreName.startsWith("gem")) {
                gemOreNames.add(oreName);
            }
        }
    }
    /**
     * 获取所有以 "gem" 开头的矿物词典名称。
     *
     * @return 存储的矿物词典名称列表
     */
    public List<String> getGemOreNames() {
        return gemOreNames;
    }
}
