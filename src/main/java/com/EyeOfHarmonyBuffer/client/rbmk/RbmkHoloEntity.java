package com.EyeOfHarmonyBuffer.client.rbmk;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端全息面板实体（世界面板的载体）。纯客户端，不参与服务端同步。
 */
@SideOnly(Side.CLIENT)
public class RbmkHoloEntity extends Entity {

    /** 视口类型：0 = 控制面板（现有），1 = 堆芯俯瞰大屏。 */
    public int viewType = 0;

    public RbmkHoloEntity(World world) {
        super(world);
        this.setSize(0.01f, 0.01f);
        this.ignoreFrustumCheck = true;
    }

    @Override
    protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {}

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return true;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }
}
