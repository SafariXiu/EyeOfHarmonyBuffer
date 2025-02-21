package com.EyeOfHarmonyBuffer.utils;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.util.StatCollector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TextHandler {
    public static Map<String, String> LangMap;
    public static Map<String, String> LangMapNeedToWrite = new HashMap();

    public TextHandler() {
    }

    public static String texter(String aTextLine, String aKey) {
        return null != StatCollector.translateToLocalFormatted(aKey, new Object[0]) ? StatCollector.translateToLocalFormatted(aKey, new Object[0]) : "texterError: " + aTextLine;
    }

    public static String texterButKey(String aTextLine, String aKey) {
        return aKey;
    }

    public static String texter(String aTextLine) {
        String aKey = "Auto." + aTextLine + ".text";
        return texter(aTextLine, aKey);
    }

    public static void initLangMap(Boolean isInDevMode) {
        if (isInDevMode) {
            LangMap = LanguageUtil0.parseLangFile("en_US");
        }

    }

    public static void serializeLangMap(boolean isInDevMode) {
        if (isInDevMode) {
            if (LangMapNeedToWrite.isEmpty()) {
                return;
            }

            File en_US_lang = new File(EyeOfHarmonyBuffer.DevResource + "\\assets\\eyeofharmonybuffer\\lang\\en_US.lang");
            File zh_CN_lang = new File(EyeOfHarmonyBuffer.DevResource + "\\assets\\eyeofharmonybuffer\\lang\\zh_CN.lang");
            EyeOfHarmonyBuffer.LOG.info("File finder with en_US.lang catch a file absolutePath: " + en_US_lang.getAbsolutePath());
            EyeOfHarmonyBuffer.LOG.info("File finder with en_US.lang catch a file named: " + en_US_lang.getName());
            EyeOfHarmonyBuffer.LOG.info("Start write new text: " + en_US_lang.getAbsolutePath());

            try {
                FileWriter en_Us = new FileWriter(en_US_lang, true);
                FileWriter zh_CN = new FileWriter(zh_CN_lang, true);
                Iterator var5 = LangMapNeedToWrite.keySet().iterator();

                while(true) {
                    if (!var5.hasNext()) {
                        EyeOfHarmonyBuffer.LOG.info("Finish to write new text: " + en_US_lang.getAbsolutePath());
                        en_Us.close();
                        zh_CN.close();
                        break;
                    }

                    String key = (String)var5.next();
                    EyeOfHarmonyBuffer.LOG.info("en_US write a Line START: " + key + "===>" + (String)LangMapNeedToWrite.get(key));
                    en_Us.write(key);
                    en_Us.write("=");
                    en_Us.write((String)LangMapNeedToWrite.get(key));
                    en_Us.write("\n");
                    EyeOfHarmonyBuffer.LOG.info("en_US write a Line COMPLETE.");
                    EyeOfHarmonyBuffer.LOG.info("zh_CN write a Line START: " + key + "===>" + (String)LangMapNeedToWrite.get(key));
                    zh_CN.write(key);
                    zh_CN.write("=");
                    zh_CN.write((String)LangMapNeedToWrite.get(key));
                    zh_CN.write("\n");
                    EyeOfHarmonyBuffer.LOG.info("zh_CN write a Line COMPLETE.");
                }
            } catch (IOException var7) {
                IOException e = var7;
                EyeOfHarmonyBuffer.LOG.info("Error in serializeLangMap() File Writer en_US");
                throw new RuntimeException(e);
            }

            LangMapNeedToWrite.clear();
        }
    }
}
