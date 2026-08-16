package com.EyeOfHarmonyBuffer.space.talos.station;

import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.IRenderHandler;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;

import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.core.client.CloudRenderer;
import galaxyspace.core.dimension.WorldProviderSpaceStationGS;

/**
 * 塔罗斯-1 空间站世界提供器（T5 级，参考 GS 土星空间站结构）。
 *
 * <p>注意：getSkyRenderer()/getCloudRenderer() 会被渲染循环每帧调用，且 SkyProviderBase
 * 的构造会分配 GL 显示列表（generateDisplayLists + renderStars 烘焙），因此天空/云渲染器
 * 必须懒创建并缓存（只 new 一次），否则每帧创建/泄漏 GL 资源会拖死客户端。
 */
public class WorldProviderTalos2Station extends WorldProviderSpaceStationGS {

    private IRenderHandler skyProvider;

    private IRenderHandler cloudRenderer;

    @Override
    public String getPlanetToOrbit() {
        return RegisterDimensions.talosGasGiant.getUnlocalizedName();
    }

    @Override
    public float getThermalLevelModifier() {
        return 0.0F;
    }

    @Override
    public CelestialBody getCelestialBody() {
        return RegisterDimensions.talos1Station;
    }

    @Override
    public double getSolarEnergyMultiplier() {
        return 1.0;
    }

    @Override
    public Class<? extends IChunkProvider> getChunkProviderClass() {
        return ChunkProviderTalos2Station.class;
    }

    @Override
    public IRenderHandler getSkyRenderer() {
        if (this.skyProvider == null) {
            this.skyProvider = new SkyProviderTalos2Station();
        }
        return this.skyProvider;
    }

    /**
     * 用 GC 的 CloudRenderer（空实现）占位：GC 空间站正是靠它让 Minecraft 跳过
     * 默认云渲染（getCloudRenderer()!=null 优先于默认云），否则太空站会画出主世界云。
     */
    @Override
    public IRenderHandler getCloudRenderer() {
        if (this.cloudRenderer == null) {
            this.cloudRenderer = new CloudRenderer();
        }
        return this.cloudRenderer;
    }
}
