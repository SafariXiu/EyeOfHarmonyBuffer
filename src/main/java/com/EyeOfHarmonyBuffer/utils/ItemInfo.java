package com.EyeOfHarmonyBuffer.utils;

public class ItemInfo {

    public String modid; // 模组 ID
    public String itemName; // 物品的注册名
    public long quantity; // 输出数量
    public int meta; // 物品的元数据值
    public String oreDictName; // 矿物词典

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

}
