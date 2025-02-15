package com.EyeOfHarmonyBuffer.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class LanguageUtil0 {
    private static Map<String, String> zhCN;
    private static Map<String, String> currentLang;
    private static List<String> itemNameKey;
    private static List<String> blockNameKey;
    private static Map<String, String> nameKey2DescriptionKey;

    public LanguageUtil0() {
    }

    public static Map<String, String> getZhCN() {
        return zhCN;
    }

    public static Map<String, String> getCurrentLang() {
        return currentLang;
    }

    public static List<String> getItemNameKey() {
        return itemNameKey;
    }

    public static List<String> getBlockNameKey() {
        return blockNameKey;
    }

    public static String getCurLangDescription(String nameKey) {
        String descriptionKey = (String)nameKey2DescriptionKey.get(nameKey);
        String description = (String)currentLang.get(descriptionKey);
        return description == null ? (String)zhCN.get(descriptionKey) : description;
    }

    public static String getCurLangItemName(String nameKey) {
        String name = (String)currentLang.get(nameKey);
        return name == null ? (String)zhCN.get(nameKey) : name;
    }

    private static Map<String, String> parseLangFile() {
        return parseLangFile("zh_CN");
    }

    public static Map<String, String> parseLangFile(String currentLangCode) {
        return parseLangFile("/assets/gtnhcommunitymod/lang/", currentLangCode);
    }

    public static Map<String, String> parseLangFile(String langPath, String currentLangCode) {
        String fullLangPath = langPath + currentLangCode + ".lang";
        List<String> langList = getLangList(fullLangPath);
        if (langList == null) {
            return null;
        } else {
            Map<String, String> map = new HashMap();
            Splitter equalSignSplitter = Splitter.on('=').limit(2);
            Iterator var6 = langList.iterator();

            while(var6.hasNext()) {
                String s = (String)var6.next();
                if (!s.isEmpty() && s.charAt(0) != '#') {
                    String[] sArr = (String[]) Iterables.toArray(equalSignSplitter.split(s), String.class);
                    if (sArr != null && sArr.length == 2 && !"".equals(sArr[0]) && !"".equals(sArr[1])) {
                        map.put(sArr[0], sArr[1]);
                    }
                }
            }

            return map;
        }
    }

    private static List<String> getLangList(String fullLangPath) {
        InputStream langIS = com.EyeOfHarmonyBuffer.utils.LanguageUtil0.class.getResourceAsStream(fullLangPath);
        if (langIS == null) {
            return null;
        } else {
            List<String> list = null;

            try {
                list = IOUtils.readLines(langIS, Charsets.UTF_8);
            } catch (IOException var12) {
                IOException e = var12;
                e.printStackTrace();
            } finally {
                try {
                    langIS.close();
                } catch (IOException var11) {
                    IOException e = var11;
                    e.printStackTrace();
                }

            }

            return list;
        }
    }

    private static void init() {
        zhCN = parseLangFile();
        String currentLangCode = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        currentLang = parseLangFile(currentLangCode);
        itemNameKey = new ArrayList();
        blockNameKey = new ArrayList();
        nameKey2DescriptionKey = new HashMap();
        Iterator var1 = zhCN.keySet().iterator();

        while(var1.hasNext()) {
            String s = (String)var1.next();
            if (s.startsWith("item.")) {
                itemNameKey.add(s);
            } else if (s.startsWith("tile.")) {
                blockNameKey.add(s);
            } else if (s.startsWith("noteBook.") && s.endsWith(".description")) {
                String name = s.substring(s.indexOf(".") + 1, s.lastIndexOf(".")) + ".name";
                nameKey2DescriptionKey.put(name, s);
            }
        }

    }

    static {
        init();
        System.out.println("abcAFCR:LanguageUtil0 init completed");
    }
}
