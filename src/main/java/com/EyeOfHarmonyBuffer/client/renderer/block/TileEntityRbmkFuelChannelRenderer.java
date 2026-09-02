package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * RBMK 通道 TESR（方案 B+ 收集端）。
 * <p>
 * 本类不再直接绘制，只负责"收集"：8 格通道的任意一格 TE 被 Angelica 判定可见并调用本
 * TESR 时，把该通道（基座坐标 + 贴图 + yOffset）交给 {@link RbmkChannelBatchRenderer}，
 * 由它在 {@code RenderWorldLastEvent} 一次性去重 + 按贴图分组绘制。
 * <p>
 * 可见性语义不变：只要通道任意一格在视野内，必有 TE 被渲染器调用 -> 通道被收集 ->
 * 整根模型被画出来（不重现"底部被剔除导致整根消失"）。
 * <p>
 * 坐标参照系注意：本收集端不再需要 px/py/pz（批处理端按玩家位置自行换算）。
 */
@SideOnly(Side.CLIENT)
public class TileEntityRbmkFuelChannelRenderer extends TileEntitySpecialRenderer {

    public static final ResourceLocation TEX_FUEL_TUBE =
        new ResourceLocation("eyeofharmonybuffer:textures/models/FuelTube.png");

    @Override
    public void renderTileEntityAt(TileEntity te, double px, double py, double pz, float partialTicks) {
        if (!(te instanceof TileEntityRbmkFuelChannel)) {
            return;
        }
        World world = te.getWorldObj();
        if (world == null) {
            return;
        }
        int bx = te.xCoord;
        int by = te.yCoord;
        int bz = te.zCoord;
        int bottom = RbmkFuelChannelHelper.channelBottom(world, bx, by, bz);
        if (bottom < 0) {
            return; // 未成型不画（方块本体正常渲染）
        }
        // 收集：解析通道顶部贴图，交给批处理渲染器（内部去重）
        RbmkChannelBatchRenderer batch = RbmkChannelBatchRenderer.INSTANCE;
        batch.collect((TileEntityRbmkFuelChannel) te, world, bx, bottom, bz,
            batch.resolveTubeTex(world, bx, bottom, bz));
    }
}
