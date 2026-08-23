package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockRBMKRod;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

/**
 * RBMK 燃料通道自定义块渲染器。
 * <ul>
 *   <li>未成型：按普通方块渲染（沿用各面 getIcon 分面贴图）；</li>
 *   <li>成型：块本体不画（返回 true、不产生几何），由基座 TileEntity 的
 *       {@link TileEntityRbmkFuelChannelRenderer} 绘制整根 FuelTube 模型。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class RbmkFuelChannelBlockRenderer implements ISimpleBlockRenderingHandler {

    public static final RbmkFuelChannelBlockRenderer INSTANCE = new RbmkFuelChannelBlockRenderer();

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        if (!(block instanceof BlockRBMKRod)) {
            return;
        }
        block.setBlockBoundsForItemRender();
        renderer.setRenderBoundsFromBlock(block);
        Tessellator tessellator = Tessellator.instance;
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(0, metadata));
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 1.0F, 0.0F);
        renderer.renderFaceYPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(1, metadata));
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(2, metadata));
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(3, metadata));
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(4, metadata));
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(5, metadata));
        tessellator.draw();
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
        if (!(block instanceof BlockRBMKRod)) {
            return false;
        }
        BlockRBMKRod rod = (BlockRBMKRod) block;
        if (rod.getRole() == BlockRBMKRod.Role.NORMAL) {
            return renderer.renderStandardBlock(block, x, y, z);
        }
        if (RbmkFuelChannelHelper.channelBottom(world, x, y, z) < 0) {
            // 未成型：按普通方块渲染
            return renderer.renderStandardBlock(block, x, y, z);
        }
        // 成型：本体不画，由基座 TE 的 TESR 绘制整根模型
        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return BlockRBMKRod.renderId;
    }
}
