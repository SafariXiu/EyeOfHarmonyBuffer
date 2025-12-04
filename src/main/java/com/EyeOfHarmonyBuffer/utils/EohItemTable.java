package com.EyeOfHarmonyBuffer.utils;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.util.GTUtility;
import lombok.Getter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EohItemTable {

    public static class Entry {
        public final String modid;
        public final String itemName;
        public final int meta;
        public final String nbtBase64;

        public transient boolean resolved = false;
        public transient ItemStack cached = null;

        public Entry(String modid, String itemName, int meta, String nbtBase64) {
            this.modid = modid;
            this.itemName = itemName;
            this.meta = meta;
            this.nbtBase64 = nbtBase64;
        }
    }

    @Getter
    private static List<Entry> entries = Collections.emptyList();

    public static void loadFromCsv(File configDir, boolean isServerSide) {
        File eohDir = new File(configDir, "EyeOfHarmonyBuffer");
        File csvFile = new File(eohDir, "eoh_nei_items.csv");

        try {
            if (!csvFile.exists()) {
                copyDefaultCsvTo(csvFile);
            }

            entries = readCsv(csvFile);
            System.out.println("[EOH] Loaded " + entries.size() + " item entries from " + csvFile.getAbsolutePath()
                + " (side=" + (isServerSide ? "server" : "client") + ")");

        } catch (Throwable t) {
            System.out.println("[EOH] Failed to load eoh_nei_items.csv: " + t);
            t.printStackTrace();
            entries = Collections.emptyList();
        }
    }

    private static void copyDefaultCsvTo(File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream in = EohItemTable.class.getResourceAsStream("/ItemList/eoh_nei_items.csv")) {
            if (in == null) {
                System.out.println("[EOH] WARNING: default eoh_nei_items.csv not found at /ItemList/eoh_nei_items.csv in mod jar.");
                return;
            }
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    private static List<Entry> readCsv(File csvFile) throws IOException {
        List<Entry> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {

            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (first) {
                    first = false;
                    if (line.toLowerCase(Locale.ROOT).startsWith("modid,")) {
                        continue;
                    }
                }

                String[] fields = parseCsvLine(line, 4);
                if (fields.length < 3) continue;

                String modid = fields[0];
                String itemName = fields[1];
                int meta;
                try {
                    meta = Integer.parseInt(fields[2]);
                } catch (NumberFormatException e) {
                    meta = 0;
                }
                String nbt = fields.length >= 4 ? fields[3] : "";
                result.add(new Entry(modid, itemName, meta, nbt));
            }
        }
        return result;
    }

    private static String[] parseCsvLine(String line, int expectedFields) {
        List<String> list = new ArrayList<>(expectedFields);
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    list.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        list.add(sb.toString());

        return list.toArray(new String[0]);
    }

    @Nullable
    private static NBTTagCompound decodeNbtFromBase64(String base64) throws IOException {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            System.out.println("[EOH] Invalid Base64 in nbt_base64: " + base64);
            throw ex;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             DataInputStream in = new DataInputStream(bais)) {
            return CompressedStreamTools.readCompressed(in);
        }
    }

    @Nullable
    public static ItemStack toItemStack(Entry e) {
        if (e == null) return null;

        if (e.resolved) {
            return e.cached;
        }
        e.resolved = true;

        if (e.itemName == null || e.itemName.isEmpty()) {
            System.out.println("[EOH] Invalid entry: empty itemName, modid=" + e.modid);
            e.cached = null;
            return null;
        }

        Item item = GameRegistry.findItem(e.modid, e.itemName);
        if (item == null) {
            System.out.println("[EOH] Missing item: " + e.modid + ":" + e.itemName + "@" + e.meta);
            e.cached = null;
            return null;
        }

        ItemStack stack = new ItemStack(item, 1, e.meta);

        if (e.nbtBase64 != null && !e.nbtBase64.isEmpty()) {
            try {
                NBTTagCompound tag = decodeNbtFromBase64(e.nbtBase64);
                if (tag != null) {
                    stack.setTagCompound(tag);
                } else {
                    System.out.println(
                        "[EOH] Decoded NBT is null for " + e.modid + ":" + e.itemName + "@" + e.meta);
                }
            } catch (Exception ex) {
                System.out.println(
                    "[EOH] Failed to decode NBT for " + e.modid + ":" + e.itemName + "@" + e.meta);
                ex.printStackTrace();
                e.cached = null;
                return null;
            }
        }

        if (!GTUtility.isStackValid(stack)) {
            e.cached = null;
            return null;
        }

        ItemStack sanitized = sanitizeForEoh(stack);
        if (sanitized == null) {
            e.cached = null;
            return null;
        }

        e.cached = sanitized;
        return sanitized;
    }

    private static boolean isForestryOrGendustryBee(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item == null) return false;

        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item);
        if (id == null) return false;

        String modId = id.modId.toLowerCase(Locale.ENGLISH);
        String name = id.name.toLowerCase(Locale.ENGLISH);

        if (modId.contains("forestry") && name.contains("bee")) return true;
        if (modId.contains("gendustry") && name.contains("bee")) return true;

        String className = item.getClass().getName().toLowerCase(Locale.ENGLISH);
        if (className.contains("beege")) return true;

        return false;
    }

    private static boolean hasTooLargeNbt(ItemStack stack, int maxLength) {
        if (stack == null || !stack.hasTagCompound()) return false;
        String nbtStr = stack.getTagCompound().toString();
        return nbtStr.length() > maxLength;
    }

    private static boolean hasBrokenGendustryPurpleGene(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return false;
        String nbtStr = stack.getTagCompound().toString();
        return nbtStr.contains("gendustry.bee.purple");
    }

    @Nullable
    private static ItemStack sanitizeForEoh(ItemStack original) {
        if (original == null) return null;

        ItemStack copy = original.copy();

        if (isForestryOrGendustryBee(copy)) {
            if (copy.hasTagCompound()) {
                System.out.println("[EOH] Sanitizing bee stack: clearing NBT for " + copy);
            }
            copy.setTagCompound(null);
            return copy;
        }

        if (hasBrokenGendustryPurpleGene(copy)) {
            System.out.println("[EOH] Found broken gendustry purple gene, clearing NBT for " + copy);
            copy.setTagCompound(null);
            return copy;
        }

        return copy;
    }

    public static List<ItemStack> getAllStacks() {
        List<ItemStack> list = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            ItemStack stack = toItemStack(e);
            if (stack != null) {
                list.add(stack);
            }
        }
        return list;
    }

    public static ItemStack[] getAllStacksArray() {
        System.out.println("[EOH] getAllStacksArray: entries size = " + entries.size());
        List<ItemStack> list = getAllStacks();
        System.out.println("[EOH] getAllStacksArray: valid stacks = " + list.size());
        return list.isEmpty() ? new ItemStack[0] : list.toArray(new ItemStack[0]);
    }
}
