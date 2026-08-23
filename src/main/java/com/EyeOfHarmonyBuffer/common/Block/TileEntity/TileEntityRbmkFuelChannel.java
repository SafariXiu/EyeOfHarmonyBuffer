package com.EyeOfHarmonyBuffer.common.Block.TileEntity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * RBMK 燃料通道基座标记 TE。
 * <p>
 * 只作为渲染锚点存在：当 8 格燃料通道成型时，由对应的 TESR 绘制整根
 * {@code FuelTube} 模型。本 TE 不保存任何持久数据。
 * <p>
 * <b>必须覆写 {@link #getRenderBoundingBox()}</b>：Angelica 会按 TE 的渲染包围盒
 * 做视锥剔除，默认只覆盖本格 1x1x1，而模型贯穿 8 格——盒不扩大会导致贴近侧面
 * （基座格移出视野）时整根模型被剔除消失。这里恒定返回覆盖整条通道的 8 格盒
 * （Angelica 对结果有缓存，保持恒定最安全）。
 */
public class TileEntityRbmkFuelChannel extends TileEntity {

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(
            this.xCoord, this.yCoord, this.zCoord,
            this.xCoord + 1, this.yCoord + 8, this.zCoord + 1);
    }
}
