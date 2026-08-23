package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.client.model.FuelTube;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockRBMKRod;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * RBMK 燃料通道 TESR：当 8 格通道成型时，在<b>通道基座</b>处绘制整根
 * {@code FuelTube} 模型（1x8x1 竖直柱体）。
 * <p>
 * 仅基座持有 {@link TileEntityRbmkFuelChannel}。Angelica 按 TE 的渲染包围盒做视锥剔除，
 * 该 TE 已通过 {@code getRenderBoundingBox()} 把盒扩成整条 8 格通道，因此只要柱子的
 * 任意部分在视野内（贴近侧面、只看上半/下半），模型都会被绘制，不会整根消失。
 * <p>
 * <b>坐标参照系</b>：{@code renderTileEntityAt} 传入的 px/py/pz 是<b>相对摄像机</b>的坐标
 * （见 TileEntityRendererDispatcher.renderTileEntity：te.xCoord - staticPlayerX），
 * 位移必须用它们，绝不能拿 te.xCoord 的绝对值去平移。
 */
@SideOnly(Side.CLIENT)
public class TileEntityRbmkFuelChannelRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEX_FUEL_TUBE =
        new ResourceLocation("eyeofharmonybuffer:textures/models/FuelTube.png");
    private final FuelTube model = new FuelTube();

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
            return; // 未成型不画（此时方块本体正常渲染）
        }

        // 按通道顶部的方块选择整根管子的贴图（燃料管 -> FuelTube.png；控制棒 -> FuelTube_<棒名>.png）
        ResourceLocation tubeTex = TEX_FUEL_TUBE;
        Block topBlock = world.getBlock(bx, bottom + 7, bz);
        if (topBlock instanceof BlockRBMKRod) {
            String modelTex = ((BlockRBMKRod) topBlock).getModelTexture();
            if (modelTex != null && !modelTex.isEmpty()) {
                tubeTex = new ResourceLocation(modelTex);
            }
        }

        GL11.glPushMatrix();
        // 底对齐基座、X/Z 居中、贯穿 8 格。位移必须用相对摄像机的 px/py/pz。
        // 叠加 TE 的渲染偏移（跳舞/插值）：正数向上。
        GL11.glTranslated(px + 1.0, py + 6.5 + ((TileEntityRbmkFuelChannel) te).getRenderYOffset(), pz + 0.0);

        int light = world.getLightBrightnessForSkyBlocks(bx, by + 4, bz, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
            (float) (light % 65536), (float) (light / 65536));

        this.bindTexture(tubeTex);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.model.render(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);
        GL11.glPopMatrix();
    }
}
