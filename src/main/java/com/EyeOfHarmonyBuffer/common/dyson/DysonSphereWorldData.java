package com.EyeOfHarmonyBuffer.common.dyson;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

/**
 * 戴森球进度存档（服务端）。
 * <p>
 * 全局唯一，绑定主世界存储。当前先存阶段/进度/归属，
 * 后续接入发射机系统时再扩展组件数量、建造者队伍等字段。
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

        MapStorage storage = world.mapStorage;
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
