package com.EyeOfHarmonyBuffer.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.oredict.OreDictionary;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class GemErgodic {

    private static final List<String> gemOreNames = new ArrayList<>();
    private static final File cacheFile = new File("config/eyeofharmonybuffer/gem_oredict_cache.json");

    /**
     * 处理矿物词典，筛选 gem 开头的条目（在服务器启动时调用）
     */
    public static void processOreDictionary() {
        System.out.println("[GemErgodic] 服务器启动，开始处理矿物词典...");

        if (cacheFile.exists()) {
            loadFromCache();
        } else {
            generateAndSaveCache();
        }

        System.out.println("[GemErgodic] 处理完成，共找到 " + gemOreNames.size() + " 个 gem 开头的矿物。");
    }

    /**
     * 获取所有以 "gem" 开头的矿物词典名称。
     *
     * @return 存储的矿物词典名称列表
     */
    public static List<String> getGemOreNames() {
        return gemOreNames;
    }

    private static void generateAndSaveCache() {
        String[] oreNames = OreDictionary.getOreNames();
        System.out.println("[GemErgodic] 矿物词典总数：" + oreNames.length);

        for (String oreName : oreNames) {
            if (oreName.startsWith("gem")) {
                gemOreNames.add(oreName);
            }
        }

        System.out.println("[GemErgodic] 发现 " + gemOreNames.size() + " 个 gem 矿物，将其保存到缓存...");
        saveToCache();
    }

    private static void saveToCache() {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            Gson gson = new Gson();
            gson.toJson(gemOreNames, writer);
        } catch (IOException e) {
            System.err.println("[GemErgodic] 保存矿物词典缓存文件失败！错误：" + e.getMessage());
        }
    }

    private static void loadFromCache() {
        try (FileReader reader = new FileReader(cacheFile)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> cachedOreNames = gson.fromJson(reader, listType);

            gemOreNames.clear();
            gemOreNames.addAll(cachedOreNames);
            System.out.println("[GemErgodic] 成功从缓存加载 " + gemOreNames.size() + " 个矿物词典条目。");
        } catch (IOException e) {
            System.err.println("[GemErgodic] 加载矿物词典缓存文件失败！错误：" + e.getMessage());
        }
    }
}
