package com.EyeOfHarmonyBuffer.common.worlddata;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.ComputeGroupService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeManager;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

public class WirelessAndGroupWorldData extends WorldSavedData {

    public static final String DATA_NAME = "EOH_WirelessAndGroup";

    public WirelessAndGroupWorldData() {
        super(DATA_NAME);
    }

    public WirelessAndGroupWorldData(String name) {
        super(name);
    }

    public static WirelessAndGroupWorldData get(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        // 组数据是全局的（跨维度共享），统一挂到主世界存储上，
        // 避免每个维度各存一份导致互相覆盖/数据丢失。
        World mainWorld = world;
        if (world.provider.dimensionId != 0) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null) {
                World world0 = server.worldServerForDimension(0);
                if (world0 != null) {
                    mainWorld = world0;
                }
            }
        }

        MapStorage storage = mainWorld.perWorldStorage;
        WirelessAndGroupWorldData data =
            (WirelessAndGroupWorldData) storage.loadData(WirelessAndGroupWorldData.class, DATA_NAME);

        if (data == null) {
            data = new WirelessAndGroupWorldData();
            storage.setData(DATA_NAME, data);
        }
        ComputeGroupService.INSTANCE.attachWorldData(data);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagCompound wirelessTag = nbt.getCompoundTag("WirelessCompute");
        WirelessComputeManager.getInstance().readFromNBT(wirelessTag);

        NBTTagCompound groupTag = nbt.getCompoundTag("ComputeGroup");
        ComputeGroupService.INSTANCE.readFromNBT(groupTag);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagCompound wirelessTag = new NBTTagCompound();
        WirelessComputeManager.getInstance().writeToNBT(wirelessTag);
        nbt.setTag("WirelessCompute", wirelessTag);

        NBTTagCompound groupTag = new NBTTagCompound();
        ComputeGroupService.INSTANCE.writeToNBT(groupTag);
        nbt.setTag("ComputeGroup", groupTag);
    }
}
