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
 * RBMK 通道批量渲染器（方案 A：直接遍历，摆脱 Sodium 区块剔除）。
 * <p>
 * 在 {@link RenderWorldLastEvent} 中直接遍历 {@code world.loadedTileEntityList} 收集所有
 * {@link TileEntityRbmkFuelChannel}（每通道仅底座挂 1 个 TE，坐标即通道基座），按贴图分组绘制。
 * <p>
 * 不再依赖 TESR 是否被调用：Angelica（Embeddium/Sodium 内核）只在 TE 自身所在 16³ 区块
 * 可见时才调 TESR，远离后底座区块被剔除会让整根通道消失（即使模型跨到可见区域）。
 * 直接遍历后，只要区块加载（TE 在列表）就会绘制，远处通道到 chunk 卸载才消失。
 * <p>
 * 性能：遍历过滤近乎零成本；用 TE 坐标直接当通道基座（省去每帧 channelBottom 的方块读取）；
 * 绘制量 = 通道数（~2000），按贴图分组后纹理绑定仅贴图种类数。
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

    static {
        // 类加载即注册事件总线，不再依赖 TESR 惰性触碰（TESR 已空壳化）。
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    private final FuelTube model = new FuelTube();
    private final Map<String, ResourceLocation> texCache = new ConcurrentHashMap<String, ResourceLocation>();

    private final Map<ResourceLocation, List<Entry>> perTex = new HashMap<ResourceLocation, List<Entry>>();

    private RbmkChannelBatchRenderer() {
    }

    /** 供 ClientProxy 客户端启动时调用，确保事件注册时机确定。 */
    public static void init() {
        if (INSTANCE == null) {
            throw new IllegalStateException("unreachable");
        }
    }

    // ==================== 收集（RenderWorldLast 直接遍历 TE 列表） ====================

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        try {
            collectAll();
            renderAll(event.partialTicks);
        } finally {
            clear();
        }
    }

    /** 遍历已加载 TE，收集所有 RBMK 通道（TE 坐标即通道基座）。 */
    private void collectAll() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) {
            return;
        }
        for (Object o : world.loadedTileEntityList) {
            if (!(o instanceof TileEntityRbmkFuelChannel)) {
                continue;
            }
            TileEntityRbmkFuelChannel te = (TileEntityRbmkFuelChannel) o;
            int bx = te.xCoord;
            int bottom = te.yCoord; // TE 仅挂底座，Y 即基座
            int bz = te.zCoord;
            // 防残留：底座位置已不是本通道方块时跳过（结构被拆后 TE 尚未清除）
            Block baseBlock = world.getBlock(bx, bottom, bz);
            if (!(baseBlock instanceof BlockRBMKRod)
                || ((BlockRBMKRod) baseBlock).getRole() != BlockRBMKRod.Role.FUEL_CHANNEL_BASE) {
                continue;
            }
            ResourceLocation tex = resolveTubeTex(world, bx, bottom, bz);
            int light = world.getLightBrightnessForSkyBlocks(bx, bottom + 4, bz, 0);
            List<Entry> list = perTex.get(tex);
            if (list == null) {
                list = new ArrayList<Entry>();
                perTex.put(tex, list);
            }
            list.add(new Entry(bx, bottom, bz, te.getRenderYOffset(), light));
        }
    }

    // ==================== 绘制 ====================

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
    }

    // ==================== 工具 ====================

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
