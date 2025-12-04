package com.EyeOfHarmonyBuffer.client;

import com.EyeOfHarmonyBuffer.network.EOHNetwork;
import com.EyeOfHarmonyBuffer.network.packet.PacketUploadItemTableChunk;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static org.apache.commons.lang3.StringEscapeUtils.escapeCsv;

public class ClientItemTableUploader {

    private static final int MAX_CHUNK_SIZE = 28 * 1024;

    public static void scanAndUpload() {
        System.out.println("[EOH] Client scanning NEI display items (with NBT) for upload...");

        List<String> lines = scanNeiDisplayWithNbtToLines();

        byte[] compressed = compressLines(lines);
        System.out.println("[EOH] Client built compressed item table, lines = "
            + lines.size() + ", bytes = " + compressed.length);

        int totalLen = compressed.length;
        int totalChunks = (totalLen + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE;
        int uploadId = (int) (System.currentTimeMillis() & 0x7fffffff);

        System.out.println("[EOH] Client will upload item table in "
            + totalChunks + " chunks, total bytes = " + totalLen
            + ", uploadId = " + uploadId);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * MAX_CHUNK_SIZE;
            int end = Math.min(start + MAX_CHUNK_SIZE, totalLen);
            int len = end - start;

            byte[] chunk = new byte[len];
            System.arraycopy(compressed, start, chunk, 0, len);

            EOHNetwork.NETWORK.sendToServer(
                new PacketUploadItemTableChunk(uploadId, i, totalChunks, chunk)
            );
        }

        System.out.println("[EOH] Client started upload of item table chunks.");
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> getNeiDisplayedItems() {
        try {
            Class<?> itemPanelsClass = Class.forName("codechicken.nei.ItemPanels");
            java.lang.reflect.Field itemPanelField = itemPanelsClass.getField("itemPanel");
            Object itemPanel = itemPanelField.get(null);
            if (itemPanel == null) {
                System.out.println("[EOH] ItemPanels.itemPanel is null");
                return null;
            }

            java.lang.reflect.Field realItemsField = itemPanel.getClass().getField("realItems");
            Object value = realItemsField.get(itemPanel);
            if (value instanceof List) {
                List<ItemStack> list = (List<ItemStack>) value;
                System.out.println("[EOH] NEI ItemPanel.realItems size = " + (list == null ? -1 : list.size()));
                return list;
            } else {
                System.out.println("[EOH] ItemPanel.realItems is not a List");
                return null;
            }
        } catch (Throwable t) {
            System.out.println("[EOH] getNeiDisplayedItems error: " + t);
            t.printStackTrace();
            return null;
        }
    }

    private static void ensureNeiPanelItemsReady() {
        try {
            Class<?> itemListClass = Class.forName("codechicken.nei.ItemList");

            java.lang.reflect.Field loadFinishedField = itemListClass.getField("loadFinished");
            boolean loadFinished = loadFinishedField.getBoolean(null);

            if (!loadFinished) {
                java.lang.reflect.Field loadItemsField = itemListClass.getField("loadItems");
                Object loadItemsTask = loadItemsField.get(null);
                java.lang.reflect.Method restartMethod = loadItemsTask.getClass().getMethod("restart");
                System.out.println("[EOH] NEI loadFinished = false, calling loadItems.restart()");
                restartMethod.invoke(loadItemsTask);

                final int maxWaitMs = 10000;
                final int stepMs = 100;
                int waited = 0;
                while (waited < maxWaitMs) {
                    Thread.sleep(stepMs);
                    waited += stepMs;
                    loadFinished = loadFinishedField.getBoolean(null);
                    if (loadFinished) {
                        System.out.println("[EOH] NEI loadFinished became true after " + waited + "ms");
                        break;
                    }
                }
                if (!loadFinished) {
                    System.out.println("[EOH] WARNING: NEI loadFinished still false after " + maxWaitMs + "ms");
                }
            } else {
                System.out.println("[EOH] NEI loadFinished already true.");
            }

            java.lang.reflect.Field updateFilterField = itemListClass.getField("updateFilter");
            Object updateFilterTask = updateFilterField.get(null);
            java.lang.reflect.Method restartUF = updateFilterTask.getClass().getMethod("restart");
            System.out.println("[EOH] Calling ItemList.updateFilter.restart()");
            restartUF.invoke(updateFilterTask);

            Thread.sleep(500);

        } catch (Throwable t) {
            System.out.println("[EOH] ensureNeiPanelItemsReady error: " + t);
            t.printStackTrace();
        }
    }

    private static String encodeStackToLine(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;

        Item item = stack.getItem();
        String regName = GameData.getItemRegistry().getNameForObject(item);
        if (regName == null) return null;

        String modid = "minecraft";
        String itemName = regName;
        int idx = regName.indexOf(':');
        if (idx >= 0) {
            modid = regName.substring(0, idx);
            itemName = regName.substring(idx + 1);
        }

        int meta = stack.getItemDamage();

        String nbtPart = "";
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            nbtPart = encodeNbtToBase64(tag);
        }

        return modid + ":" + itemName + ":" + meta + "|" + nbtPart;
    }

    public static String encodeNbtToBase64(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(tag, bos);
            byte[] bytes = bos.toByteArray();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static List<String> scanNeiDisplayWithNbtToLines() {
        Set<String> set = new LinkedHashSet<String>();

        try {
            ensureNeiPanelItemsReady();

            List<ItemStack> displayItems = getNeiDisplayedItems();
            if (displayItems == null) {
                System.out.println("[EOH] NEI display item list is null, fallback to empty list.");
                return new ArrayList<String>();
            }

            System.out.println("[EOH] NEI display item list size = " + displayItems.size());

            for (ItemStack stack : displayItems) {
                String line = encodeStackToLine(stack);
                if (line != null) {
                    set.add(line);
                }
            }

            System.out.println("[EOH] NEI scan display with NBT finished: unique lines = " + set.size());

        } catch (Throwable t) {
            System.out.println("[EOH] scanNeiDisplayWithNbtToLines error: " + t);
            t.printStackTrace();
            return new ArrayList<String>();
        }

        return new ArrayList<String>(set);
    }

    private static byte[] compressLines(List<String> lines) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            OutputStreamWriter osw = new OutputStreamWriter(gzip, "UTF-8");
            PrintWriter pw = new PrintWriter(osw);
            for (String line : lines) {
                pw.println(line);
            }
            pw.flush();
            gzip.finish();
            gzip.close();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public static void exportNeiItemsToCsv(File file) {
        System.out.println("[EOH] Exporting NEI display items to CSV: " + file.getAbsolutePath());
        try {
            List<String> lines = scanNeiDisplayWithNbtToLines();

            writeLinesToCsv(lines, file);

            System.out.println("[EOH] Export finished. Total unique items = " + lines.size());
        } catch (Throwable t) {
            System.out.println("[EOH] exportNeiItemsToCsv error: " + t);
            t.printStackTrace();
        }
    }

    private static void writeLinesToCsv(List<String> encodedLines, File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (Writer writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            writer.write("modid,item_name,meta,nbt_base64");
            writer.write("\n");

            for (String encoded : encodedLines) {
                if (encoded == null || encoded.isEmpty()) continue;

                String left = encoded;
                String nbtPart = "";

                int pipeIdx = encoded.indexOf('|');
                if (pipeIdx >= 0) {
                    left = encoded.substring(0, pipeIdx);
                    if (pipeIdx + 1 < encoded.length()) {
                        nbtPart = encoded.substring(pipeIdx + 1);
                    }
                }

                String modid = "";
                String itemName = "";
                String metaStr = "";

                String[] parts = left.split(":", 3);
                if (parts.length >= 1) modid = parts[0];
                if (parts.length >= 2) itemName = parts[1];
                if (parts.length >= 3) metaStr = parts[2];

                writer.write(escapeCsv(modid));
                writer.write(',');
                writer.write(escapeCsv(itemName));
                writer.write(',');
                writer.write(escapeCsv(metaStr));
                writer.write(',');
                writer.write(escapeCsv(nbtPart));
                writer.write('\n');
            }
        }
    }

    private static String escapeCsv(String field) {
        if (field == null) return "";
        boolean needQuote = field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
        String result = field.replace("\"", "\"\"");
        if (needQuote) {
            return "\"" + result + "\"";
        }
        return result;
    }
}
