package com.EyeOfHarmonyBuffer.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class GemErgodic {

    private final List<String> gemOreNames = new ArrayList<>();

    private final File cacheFile = new File("config/EyeOfHarmonyBuffer/gem_oredict_cache.json");

    /**
     * 初始化方法，在 FMLInitializationEvent 时调用。
     * 检查是否存在缓存文件，如果存在则加载缓存，
     * 否则遍历矿物词典并生成缓存文件。
     */
    public void init(FMLInitializationEvent event) {
        if (cacheFile.exists()) {
            loadFromCache();
        } else {
            generateAndSaveCache();
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

    /**
     * 从矿物词典中筛选以 "gem" 开头的条目，并保存到缓存文件。
     */
    private void generateAndSaveCache() {
        // 遍历矿物词典，筛选出以 "gem" 开头的条目
        String[] oreNames = OreDictionary.getOreNames();

        for (String oreName : oreNames) {
            if (oreName.startsWith("gem")) {
                gemOreNames.add(oreName);
            }
        }

        saveToCache();
    }

    /**
     * 将矿物词典条目保存到本地缓存文件。
     */
    private void saveToCache() {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            Gson gson = new Gson();
            gson.toJson(gemOreNames, writer);
        } catch (IOException e) {
            System.err.println("保存矿物词典缓存文件失败！错误：" + e.getMessage());
        }
    }

    /**
     * 从本地缓存文件加载矿物词典条目。
     */
    private void loadFromCache() {
        try (FileReader reader = new FileReader(cacheFile)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> cachedOreNames = gson.fromJson(reader, listType);

            gemOreNames.clear();
            gemOreNames.addAll(cachedOreNames);
        } catch (IOException e) {
            System.err.println("加载矿物词典缓存文件失败！错误：" + e.getMessage());
        }
    }
}
