package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.client.model.FuelTube;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockRBMKRod;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RBMK 通道批量渲染器（方案 B+）。
 * <p>
 * 保留"8 格 TE 各自收集"的可见性语义（任意一格可见 -> 该通道必被收集），
 * 但把实际绘制挪到 {@link RenderWorldLastEvent} 一次性执行：
 * <ul>
 *   <li>去重：同一通道 8 格只收集一次，绘制调用降 8 倍（16000 -> ~2000）；</li>
 *   <li>按贴图分组：同种管子贴图连续画完，纹理绑定从 16000 降到贴图种类数；</li>
 *   <li>贴图 ResourceLocation 缓存，避免每帧 new；</li>
 *   <li>不依赖 shader/帧缓冲，无光影环境零兼容问题。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class RbmkChannelBatchRenderer {

    private static final class Entry {
        final int bx, bottom, bz;
        final double yOffset;
        final int light;
        Entry(int bx, int bottom, int bz, double yOffset, int light) {
            this.bx = bx; this.bottom = bottom; this.bz = bz;
            this.yOffset = yOffset; this.light = light;
        }
    }

    public static final RbmkChannelBatchRenderer INSTANCE = new RbmkChannelBatchRenderer();

    private final FuelTube model = new FuelTube();
    private final Map<String, ResourceLocation> texCache = new ConcurrentHashMap<String, ResourceLocation>();

    private final Map<ResourceLocation, List<Entry>> perTex = new HashMap<ResourceLocation, List<Entry>>();
    private final Map<Long, Entry> byKey = new HashMap<Long, Entry>();

    private RbmkChannelBatchRenderer() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ==================== 收集（TESR 调用） ====================

    public void collect(TileEntityRbmkFuelChannel te, World world, int bx, int bottom, int bz, ResourceLocation tex) {
        long key = pack(bx, bottom, bz);
        if (byKey.containsKey(key)) {
            return;
        }
        int light = world.getLightBrightnessForSkyBlocks(bx, bottom + 4, bz, 0);
        Entry entry = new Entry(bx, bottom, bz, te.getRenderYOffset(), light);
        byKey.put(key, entry);
        List<Entry> list = perTex.get(tex);
        if (list == null) {
            list = new ArrayList<Entry>();
            perTex.put(tex, list);
        }
        list.add(entry);
    }

    // ==================== 绘制（RenderWorldLastEvent） ====================

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        try {
            renderAll(event.partialTicks);
        } finally {
            clear();
        }
    }

    private void renderAll(float partialTicks) {
        if (perTex.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.entity.Entity p = mc.thePlayer;
        float px = (float) (p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks);
        float py = (float) (p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks);
        float pz = (float) (p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks);

        // RenderWorldLastEvent 阶段 lightmap 次级纹理单元已被 disableLightmap 禁用，
        // 手动重新启用（内部会设纹理矩阵 + 绑定动态 lightmap 纹理），否则模型全是默认亮度
        mc.entityRenderer.enableLightmap(partialTicks);
        try {
            for (Map.Entry<ResourceLocation, List<Entry>> group : perTex.entrySet()) {
                mc.getTextureManager().bindTexture(group.getKey());
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                for (Entry e : group.getValue()) {
                    GL11.glPushMatrix();
                    GL11.glTranslated(e.bx - px + 1.0, e.bottom - py + 6.5 + e.yOffset, e.bz - pz + 0.0);
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                        (float) (e.light & 0xFFFF), (float) (e.light >>> 16));
                    this.model.render(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);
                    GL11.glPopMatrix();
                }
            }
        } finally {
            mc.entityRenderer.disableLightmap(partialTicks);
        }
    }

    public void clear() {
        perTex.clear();
        byKey.clear();
    }

    // ==================== 工具 ====================

    private static long pack(int x, int y, int z) {
        return ((long) x & 0xFFFFF) << 42 | ((long) y & 0xFFFFF) << 21 | ((long) z & 0xFFFFF);
    }

    public ResourceLocation tex(String modelTexture) {
        if (modelTexture == null || modelTexture.isEmpty()) {
            return null;
        }
        ResourceLocation rl = texCache.get(modelTexture);
        if (rl == null) {
            rl = new ResourceLocation(modelTexture);
            texCache.put(modelTexture, rl);
        }
        return rl;
    }

    public ResourceLocation resolveTubeTex(World world, int bx, int bottom, int bz) {
        Block topBlock = world.getBlock(bx, bottom + 7, bz);
        if (topBlock instanceof BlockRBMKRod) {
            String modelTex = ((BlockRBMKRod) topBlock).getModelTexture();
            if (modelTex != null && !modelTex.isEmpty()) {
                ResourceLocation rl = tex(modelTex);
                if (rl != null) {
                    return rl;
                }
            }
        }
        return TileEntityRbmkFuelChannelRenderer.TEX_FUEL_TUBE;
    }
}
