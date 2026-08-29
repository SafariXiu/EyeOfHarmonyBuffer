package com.EyeOfHarmonyBuffer.client.holo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * 客户端全息屏实体（世界屏的载体）。纯客户端，不参与服务端同步。
 * 组合持有 HoloScreen 实例：屏幕类型/尺寸/状态/逻辑全在屏对象上，实体只负责"放在世界里"。
 */
@SideOnly(Side.CLIENT)
public class HoloEntity extends Entity {

    private HoloScreen screen;

    public HoloEntity(World world) {
        super(world);
        this.setSize(0.01f, 0.01f);
        this.ignoreFrustumCheck = true;
    }

    /** 挂载屏幕，并把屏的"请求关闭"绑定到关闭本实体。 */
    public void setScreen(HoloScreen screen) {
        this.screen = screen;
        if (screen != null) {
            screen.setCloseRequest(() -> HoloInteraction.closeEntity(HoloEntity.this));
        }
    }

    public HoloScreen getScreen() {
        return screen;
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
