package com.EyeOfHarmonyBuffer.common.misc;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumFieldHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.event.world.WorldEvent;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlobalOrundumWorldSavedData extends WorldSavedData {

    public static GlobalOrundumWorldSavedData INSTANCE;

    private static final String DATA_NAME = "EOHB_OrundumWorldSavedData";
    /** NBTTagList 存 {UUID, Value}。 */
    private static final String ORUNDUM_NBT_LIST_TAG = "EOHB_GlobalOrundum_List";

    private static void loadInstance(World world) {

        GlobalOrundumStorage.clear();
        OrundumFieldHelper.clearAll();

        MapStorage storage = world.mapStorage;
        INSTANCE = (GlobalOrundumWorldSavedData) storage.loadData(GlobalOrundumWorldSavedData.class, DATA_NAME);
        if (INSTANCE == null) {
            INSTANCE = new GlobalOrundumWorldSavedData();
            storage.setData(DATA_NAME, INSTANCE);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) {
            loadInstance(event.world);
        }
    }

    public GlobalOrundumWorldSavedData() {
        super(DATA_NAME);
    }

    public GlobalOrundumWorldSavedData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {

        HashMap<UUID, BigInteger> targetMap = GlobalOrundumStorage.getInternalMap();
        targetMap.clear();

        if (nbt.hasKey(ORUNDUM_NBT_LIST_TAG, 9)) {
            NBTTagList list = nbt.getTagList(ORUNDUM_NBT_LIST_TAG, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                try {
                    UUID teamId = UUID.fromString(entry.getString("UUID"));
                    BigInteger value = new BigInteger(entry.getString("Value"));
                    if (value != null) {
                        targetMap.put(teamId, value);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            System.out.println("[EOHB] Loaded Orundum entries (NBT list): " + targetMap.size());
        } else {
            System.out.println("[EOHB] No Orundum NBT tag found, starting empty.");
        }

        // 场计数不持久化：每次加载后清空，由各协议核心/供电塔/中继塔在成型后重新注册。
        // 这样能避免旧的“内存标记 + 持久化计数”在重进存档时重复累加，且能自愈已污染的存档。
        OrundumFieldHelper.clearAll();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {

        HashMap<UUID, BigInteger> map = GlobalOrundumStorage.getInternalMap();
        System.out.println("[EOHB] Saving Orundum entries: " + map.size());

        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, BigInteger> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            NBTTagCompound compound = new NBTTagCompound();
            compound.setString("UUID", entry.getKey().toString());
            compound.setString("Value", entry.getValue().toString());
            list.appendTag(compound);
        }
        nbt.setTag(ORUNDUM_NBT_LIST_TAG, list);

        // 场计数由机器在运行时重建，不再写入存档
        nbt.removeTag("EOHB_OrundumField_MapNBTTag");
    }
}
