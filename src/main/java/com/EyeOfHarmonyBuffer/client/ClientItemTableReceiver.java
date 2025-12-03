package com.EyeOfHarmonyBuffer.client;

import com.EyeOfHarmonyBuffer.Config.ItemConfig;
import com.EyeOfHarmonyBuffer.server.EOHItemTableManager;
import com.EyeOfHarmonyBuffer.utils.ItemInfo;
import net.minecraft.nbt.NBTTagCompound;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ClientItemTableReceiver {

    private static final Map<Integer, UploadBuffer> SERVER_UPLOADS = new HashMap<Integer, UploadBuffer>();

    private static class UploadBuffer {
        final int totalChunks;
        final byte[][] chunks;
        int received = 0;

        UploadBuffer(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }
    }

    public static synchronized void receiveServerItemTableChunk(
        int uploadId, int index, int total, byte[] data) {

        UploadBuffer buf = SERVER_UPLOADS.get(uploadId);
        if (buf == null) {
            buf = new UploadBuffer(total);
            SERVER_UPLOADS.put(uploadId, buf);
        }

        if (index < 0 || index >= buf.totalChunks) {
            System.out.println("[EOH] Client receiveServerItemTableChunk: invalid index " + index
                + " for uploadId=" + uploadId + ", total=" + buf.totalChunks);
            return;
        }

        if (buf.chunks[index] == null) {
            buf.chunks[index] = data;
            buf.received++;
        }

        System.out.println("[EOH] Client receiveServerItemTableChunk: uploadId=" + uploadId
            + ", index=" + index + "/" + (buf.totalChunks - 1)
            + ", received=" + buf.received + "/" + buf.totalChunks
            + ", size=" + (data == null ? 0 : data.length) + ")");

        if (buf.received == buf.totalChunks) {
            int totalLen = 0;
            for (byte[] c : buf.chunks) {
                if (c != null) totalLen += c.length;
            }
            byte[] fullCompressed = new byte[totalLen];
            int pos = 0;
            for (byte[] c : buf.chunks) {
                if (c != null) {
                    System.arraycopy(c, 0, fullCompressed, pos, c.length);
                    pos += c.length;
                }
            }

            SERVER_UPLOADS.remove(uploadId);

            System.out.println("[EOH] Client: all server item table chunks received, total bytes=" + totalLen);

            handleFullCompressedItemTableOnClient(fullCompressed);
        }
    }

    private static void handleFullCompressedItemTableOnClient(byte[] compressed) {
        System.out.println("[EOH] Client handleFullCompressedItemTableOnClient called, data length = "
            + (compressed == null ? -1 : compressed.length));

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
                    nbt = EOHItemTableManager.decodeNbtFromBase64(nbtPart); // 或者复制同样的实现
                }

                long qty = 10000L;

                result.add(new ItemInfo(modid, itemName, qty, meta, nbt));
            }

            br.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        EOHItemTableManager.setClientItemTable(result);
        ItemConfig.reloadConfig();

        System.out.println("[EOH] Client: full item table applied, size=" + result.size());
    }
}
