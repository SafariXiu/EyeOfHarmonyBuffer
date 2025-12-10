package com.EyeOfHarmonyBuffer.common.misc;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.event.world.WorldEvent;

import java.io.*;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlobalOrundumWorldSavedData extends WorldSavedData {

    public static GlobalOrundumWorldSavedData INSTANCE;

    private static final String DATA_NAME = "EOHB_OrundumWorldSavedData";
    private static final String ORUNDUM_NBT_TAG = "EOHB_GlobalOrundum_MapNBTTag";

    private static void loadInstance(World world) {

        GlobalOrundumStorage.clear();

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
    @SuppressWarnings("unchecked")
    public void readFromNBT(NBTTagCompound nbt) {

        try {
            if (!nbt.hasKey(ORUNDUM_NBT_TAG)) {
                System.out.println("[EOHB] No Orundum NBT tag found, starting empty.");
                return;
            }

            byte[] ba = nbt.getByteArray(ORUNDUM_NBT_TAG);
            InputStream byteArrayInputStream = new ByteArrayInputStream(ba);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            Object data = objectInputStream.readObject();

            HashMap<Object, BigInteger> hashData = (HashMap<Object, BigInteger>) data;

            HashMap<UUID, BigInteger> targetMap = GlobalOrundumStorage.getInternalMap();
            targetMap.clear();

            for (Map.Entry<Object, BigInteger> entry : hashData.entrySet()) {
                try {
                    UUID teamId = UUID.fromString(entry.getKey().toString());
                    BigInteger value = entry.getValue();
                    if (value != null) {
                        targetMap.put(teamId, value);
                    }
                } catch (RuntimeException ignored) {
                }
            }

            System.out.println("[EOHB] Loaded Orundum entries: " + targetMap.size());
        } catch (IOException | ClassNotFoundException exception) {
            System.out.println(ORUNDUM_NBT_TAG + " LOAD FAILED");
            exception.printStackTrace();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {

        try {
            HashMap<UUID, BigInteger> map = GlobalOrundumStorage.getInternalMap();
            System.out.println("[EOHB] Saving Orundum entries: " + map.size());

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(map);
            objectOutputStream.flush();

            byte[] data = byteArrayOutputStream.toByteArray();
            nbt.setByteArray(ORUNDUM_NBT_TAG, data);
        } catch (IOException exception) {
            System.out.println(ORUNDUM_NBT_TAG + " SAVE FAILED");
            exception.printStackTrace();
        }
    }
}
