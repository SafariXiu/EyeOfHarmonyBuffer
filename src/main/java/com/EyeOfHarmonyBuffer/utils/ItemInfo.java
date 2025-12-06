package com.EyeOfHarmonyBuffer.utils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

public class ItemInfo {

    public String modid; // 模组 ID
    public String itemName; // 物品的注册名
    public long quantity; // 输出数量
    public int meta; // 物品的元数据值
    public String oreDictName; // 矿物词典
    public NBTTagCompound nbt;

    public ItemInfo(String modid, String itemName, long quantity, int meta) {
        this.modid = modid;
        this.itemName = itemName;
        this.quantity = quantity;
        this.meta = meta;
        this.oreDictName = null;
    }

    public ItemInfo(String oreDictName, long quantity) {
        this.modid = null;
        this.itemName = null;
        this.quantity = quantity;
        this.meta = 0;
        this.oreDictName = oreDictName;
    }

    public ItemInfo(String modid, String itemName, long quantity, int meta, NBTTagCompound nbt) {
        this.modid = modid;
        this.itemName = itemName;
        this.quantity = quantity;
        this.meta = meta;
        this.oreDictName = null;
        this.nbt = nbt;
    }

    public void writeToBuf(ByteBuf buf) {
        if (oreDictName != null) {
            buf.writeByte(1);
        } else {
            buf.writeByte(0);
        }

        buf.writeLong(quantity);

        if (oreDictName != null) {
            ByteBufUtils.writeUTF8String(buf, oreDictName);
        } else {
            ByteBufUtils.writeUTF8String(buf, modid == null ? "" : modid);
            ByteBufUtils.writeUTF8String(buf, itemName == null ? "" : itemName);
            buf.writeInt(meta);

            buf.writeBoolean(nbt != null);
            if (nbt != null) {
                ByteBufUtils.writeTag(buf, nbt);
            }
        }
    }

    public static ItemInfo readFromBuf(ByteBuf buf) {
        byte type = buf.readByte();
        long quantity = buf.readLong();

        if (type == 1) {
            String oreDictName = ByteBufUtils.readUTF8String(buf);
            return new ItemInfo(oreDictName, quantity);
        } else {
            String modid = ByteBufUtils.readUTF8String(buf);
            String itemName = ByteBufUtils.readUTF8String(buf);
            int meta = buf.readInt();

            boolean hasNbt = buf.readBoolean();
            NBTTagCompound nbt = hasNbt ? ByteBufUtils.readTag(buf) : null;

            return new ItemInfo(modid, itemName, quantity, meta, nbt);
        }
    }
}
