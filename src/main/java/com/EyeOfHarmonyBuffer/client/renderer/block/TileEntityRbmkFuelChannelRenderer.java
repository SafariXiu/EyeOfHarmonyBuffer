package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

/**
 * RBMK 通道 TESR（保留为空壳）。
 * <p>
 * 模型绘制已由 {@link RbmkChannelBatchRenderer} 在 {@code RenderWorldLastEvent} 中直接遍历
 * TE 列表完成，不再依赖本 TESR 的逐格调用（Angelica/Sodium 对远处 TE 所在区块剔除会跳过
 * TESR，导致整根通道消失）。本类保留绑定以避免解绑带来的注册链改动，渲染体为空。
 */
@SideOnly(Side.CLIENT)
public class TileEntityRbmkFuelChannelRenderer extends TileEntitySpecialRenderer {

    public static final ResourceLocation TEX_FUEL_TUBE =
        new ResourceLocation("eyeofharmonybuffer:textures/models/FuelTube.png");

    @Override
    public void renderTileEntityAt(TileEntity te, double px, double py, double pz, float partialTicks) {
        // 空：绘制由 RbmkChannelBatchRenderer 统一完成
    }
}
