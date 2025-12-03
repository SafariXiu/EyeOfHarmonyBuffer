package com.EyeOfHarmonyBuffer.server;

import com.EyeOfHarmonyBuffer.Config.ItemConfig;
import com.EyeOfHarmonyBuffer.network.EOHNetwork;
import com.EyeOfHarmonyBuffer.network.packet.PacketSyncItemTableChunkToClient;
import com.EyeOfHarmonyBuffer.utils.ItemInfo;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.EyeOfHarmonyBuffer.client.ClientItemTableUploader.encodeNbtToBase64;

public class EOHItemTableManager {

    private static final int SYNC_CHUNK_SIZE = 32 * 1024;

    @Getter
    private static boolean initialized = false;
    private static boolean allowClientInit = true;

    private static List<ItemInfo> SERVER_ITEM_TABLE = new ArrayList<>();

    private static boolean SERVER_TABLE_INITIALIZED = false;

    private static List<ItemInfo> CLIENT_ITEM_TABLE = new ArrayList<>();

    private static final Map<Integer, UploadBuffer> ITEM_TABLE_UPLOADS = new HashMap<Integer, UploadBuffer>();

    private static class UploadBuffer {
        final int totalChunks;
        final byte[][] chunks;
        int received = 0;

        UploadBuffer(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }
    }

    public static void acceptClientTable(List<ItemInfo> table, EntityPlayerMP fromPlayer) {
        if (SERVER_TABLE_INITIALIZED) {
            System.out.println("[EOH] acceptClientTable: already initialized, ignore table from "
                + fromPlayer.getCommandSenderName() + ", size=" + table.size());
            return;
        }

        if (table == null || table.isEmpty()) {
            System.out.println("[EOH] acceptClientTable: received empty table from "
                + fromPlayer.getCommandSenderName());
            return;
        }

        SERVER_ITEM_TABLE = table;
        SERVER_TABLE_INITIALIZED = true;
        initialized = true;
        allowClientInit = false;

        System.out.println("[EOH] Accepted FIRST client item table from " + fromPlayer.getCommandSenderName()
            + ", size = " + table.size());

        ItemConfig.reloadConfig();

        broadcastServerItemTableCompressed(fromPlayer);
    }

    public static List<ItemInfo> getServerItemTable() {
        return SERVER_ITEM_TABLE;
    }

    public static void setClientItemTable(List<ItemInfo> table) {
        CLIENT_ITEM_TABLE = table;
        System.out.println("[EOH] Client item table updated, size = " + table.size());
    }

    public static List<ItemInfo> getItemTable() {
        Side side = FMLCommonHandler.instance().getEffectiveSide();
        if (side.isServer()) {
            return SERVER_ITEM_TABLE;
        } else {
            return CLIENT_ITEM_TABLE;
        }
    }

    public static boolean isServerTableInitialized() {
        return SERVER_TABLE_INITIALIZED;
    }

    public static void resetOnServerStart() {
        initialized = false;
        allowClientInit = true;
        ITEM_TABLE_UPLOADS.clear();
        SERVER_ITEM_TABLE.clear();
        SERVER_TABLE_INITIALIZED = false;
        System.out.println("[EOH] Item table reset, waiting for first client init.");
    }

    public static boolean isClientInitAllowed() {
        return allowClientInit && !SERVER_TABLE_INITIALIZED;
    }

    public static synchronized void receiveItemTableChunk(
        int uploadId, int index, int total,
        byte[] data, EntityPlayerMP sender) {

        if (!isClientInitAllowed()) {
            System.out.println("[EOH] receiveItemTableChunk: client init not allowed, ignoring. player="
                + sender.getCommandSenderName());
            return;
        }

        UploadBuffer buf = ITEM_TABLE_UPLOADS.get(uploadId);
        if (buf == null) {
            buf = new UploadBuffer(total);
            ITEM_TABLE_UPLOADS.put(uploadId, buf);
        }

        if (index < 0 || index >= buf.totalChunks) {
            System.out.println("[EOH] receiveItemTableChunk: invalid index " + index
                + " for uploadId=" + uploadId + ", total=" + buf.totalChunks);
            return;
        }

        if (buf.chunks[index] == null) {
            buf.chunks[index] = data;
            buf.received++;
        }

        System.out.println("[EOH] receiveItemTableChunk: uploadId=" + uploadId
            + ", index=" + index + "/" + (buf.totalChunks - 1)
            + ", received=" + buf.received + "/" + buf.totalChunks
            + ", size=" + (data == null ? 0 : data.length)
            + ", from player=" + sender.getCommandSenderName());

        if (buf.received == buf.totalChunks) {
            int totalLen = 0;
            for (byte[] c : buf.chunks) {
                if (c != null) {
                    totalLen += c.length;
                }
            }

            byte[] fullCompressed = new byte[totalLen];
            int pos = 0;
            for (byte[] c : buf.chunks) {
                if (c != null) {
                    System.arraycopy(c, 0, fullCompressed, pos, c.length);
                    pos += c.length;
                }
            }

            ITEM_TABLE_UPLOADS.remove(uploadId);

            System.out.println("[EOH] All item table chunks received for uploadId="
                + uploadId + ", total bytes=" + totalLen
                + ", from player=" + sender.getCommandSenderName());

            handleFullCompressedItemTable(fullCompressed, sender);
        }
    }

    private static void handleFullCompressedItemTable(byte[] compressed, EntityPlayerMP sender) {
        System.out.println("[EOH] Server handleFullCompressedItemTable called, data length = "
            + (compressed == null ? -1 : compressed.length));

        if (!isClientInitAllowed()) {
            System.out.println("[EOH] Received client item table but not allowed, ignoring.");
            return;
        }

        List<ItemInfo> result = new ArrayList<ItemInfo>();

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            GZIPInputStream gzip = new GZIPInputStream(bis);
            BufferedReader br = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8));

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String mainPart = line;
                String nbtPart = "";
                int barIdx = line.indexOf('|');
                if (barIdx >= 0) {
                    mainPart = line.substring(0, barIdx);
                    nbtPart = line.substring(barIdx + 1);
                }

                String[] parts = mainPart.split(":");
                if (parts.length < 3) {
                    continue;
                }

                String modid = parts[0];
                String itemName = parts[1];

                int meta;
                try {
                    meta = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }

                NBTTagCompound nbt = null;
                if (!nbtPart.isEmpty()) {
                    nbt = decodeNbtFromBase64(nbtPart);
                }

                long qty = 10000L;

                ItemInfo info = new ItemInfo(modid, itemName, qty, meta, nbt);
                result.add(info);
            }

            br.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        acceptClientTable(result, sender);
    }

    private static void broadcastServerItemTableCompressed(EntityPlayerMP fromPlayer) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8));

            for (ItemInfo info : SERVER_ITEM_TABLE) {
                if (info.oreDictName != null) {
                    continue;
                }

                StringBuilder sb = new StringBuilder();
                sb.append(info.modid).append(":")
                    .append(info.itemName).append(":")
                    .append(info.meta);

                if (info.nbt != null) {
                    String base64 = encodeNbtToBase64(info.nbt);
                    sb.append("|").append(base64);
                }

                bw.write(sb.toString());
                bw.newLine();
            }

            bw.flush();
            gzip.finish();
            gzip.close();

            byte[] compressed = bos.toByteArray();

            int totalChunks = (compressed.length + SYNC_CHUNK_SIZE - 1) / SYNC_CHUNK_SIZE;
            int uploadId = (int) (System.currentTimeMillis() & 0x7fffffff);

            for (Object obj : fromPlayer.mcServer.getConfigurationManager().playerEntityList) {
                if (!(obj instanceof EntityPlayerMP)) continue;
                EntityPlayerMP p = (EntityPlayerMP) obj;

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * SYNC_CHUNK_SIZE;
                    int end = Math.min(start + SYNC_CHUNK_SIZE, compressed.length);
                    byte[] part = Arrays.copyOfRange(compressed, start, end);

                    PacketSyncItemTableChunkToClient msg =
                        new PacketSyncItemTableChunkToClient(uploadId, i, totalChunks, part);
                    EOHNetwork.NETWORK.sendTo(msg, p);
                }
            }

            System.out.println("[EOH] broadcastServerItemTableCompressed: size=" + SERVER_ITEM_TABLE.size()
                + ", compressedBytes=" + compressed.length + ", chunks=" + totalChunks);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static NBTTagCompound decodeNbtFromBase64(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(s);
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            return CompressedStreamTools.readCompressed(bis);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
