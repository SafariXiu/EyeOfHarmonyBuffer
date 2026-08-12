package com.EyeOfHarmonyBuffer.common.dyson;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;

/**
 * 戴森球进度存档（服务端）。
 * <p>
 * 全局唯一，统一绑定塔罗斯 2（{@link RegisterDimensions#ID_TALOS2_DIM}）存档，
 * 无论在哪个维度调用都取同一份数据。
 */
public class DysonSphereWorldData extends WorldSavedData {

    private static final String DATA_NAME = "EOHB_DysonSphere";
    private static DysonSphereWorldData INSTANCE;

    private int stage = DysonSphereState.STAGE_NONE;
    private float progress = 0.0F;
    private int cloudCount = 0;
    private int frameCount = 0;
    private String ownerName = "";

    public DysonSphereWorldData() {
        super(DATA_NAME);
    }

    public DysonSphereWorldData(String name) {
        super(name);
    }

    public static DysonSphereWorldData get(World world) {
        if (world == null || world.isRemote) {
            return INSTANCE;
        }

        // 统一绑定塔罗斯 2：机器/指令无论从哪个维度调用，都读写同一份存档
        World target = world;
        if (world.provider.dimensionId != RegisterDimensions.ID_TALOS2_DIM) {
            WorldServer talos = MinecraftServer.getServer().worldServerForDimension(RegisterDimensions.ID_TALOS2_DIM);
            if (talos == null) {
                return INSTANCE;
            }
            target = talos;
        }

        MapStorage storage = target.mapStorage;
        if (storage == null) {
            return INSTANCE;
        }

        DysonSphereWorldData data =
            (DysonSphereWorldData) storage.loadData(DysonSphereWorldData.class, DATA_NAME);
        if (data == null) {
            data = new DysonSphereWorldData();
            storage.setData(DATA_NAME, data);
            data.markDirty();
        }
        INSTANCE = data;
        return data;
    }

    public int getStage() {
        return stage;
    }

    public float getProgress() {
        return progress;
    }

    public int getCloudCount() {
        return cloudCount;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setState(int newStage, float newProgress, int newCloudCount, int newFrameCount,
                         String newOwnerName) {
        this.stage = Math.max(DysonSphereState.STAGE_NONE, Math.min(DysonSphereState.STAGE_COMPLETE, newStage));
        this.progress = Math.max(0.0F, Math.min(1.0F, newProgress));
        this.cloudCount = Math.max(0, newCloudCount);
        this.frameCount = Math.max(0, newFrameCount);
        this.ownerName = newOwnerName == null ? "" : newOwnerName;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.stage = nbt.getInteger("Stage");
        this.progress = nbt.getFloat("Progress");
        this.cloudCount = nbt.getInteger("CloudCount");
        this.frameCount = nbt.getInteger("FrameCount");
        this.ownerName = nbt.getString("OwnerName");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("Stage", stage);
        nbt.setFloat("Progress", progress);
        nbt.setInteger("CloudCount", cloudCount);
        nbt.setInteger("FrameCount", frameCount);
        nbt.setString("OwnerName", ownerName);
    }
}
