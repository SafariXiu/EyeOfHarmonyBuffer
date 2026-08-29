package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;

import galaxyspace.core.dimension.ChunkProviderGSSpace;
import galaxyspace.core.dimension.WorldChunkManagerSpaceGS;
import galaxyspace.core.handler.api.IHostileBody;
import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldProviderSpace;
import micdoodle8.mods.galacticraft.api.vector.Vector3;

import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * 塔罗斯-1（气态巨行星 Talos）—— 与 GS 木星/土星完全同模式：
 *
 * <ul>
 *   <li>有维度（14000）：参与空间站创建匹配，玩家在星系图选中塔罗斯-1 即可创建塔罗斯-1 空间站（-52）；</li>
 *   <li>无法登录：不注册传送类型（星系图 Travel 无反应）、{@link #canSpaceshipTierPass} 恒 false（任何等级火箭都
 *       无法降落）、世界为太空（WorldProviderSpace + ChunkProviderGSSpace，无地表）、IHostileBody 高压致死。</li>
 * </ul>
 *
 * 参数照抄 GS 木星（galaxyspace.SolarSystem.planets.jupiter.dimension.WorldProviderJupiter）。
 */
public class WorldProviderTalos1 extends WorldProviderSpace implements IHostileBody {

    @Override
    public CelestialBody getCelestialBody() {
        return RegisterDimensions.talosGasGiant;
    }

    /** 气态巨行星：任何等级火箭都无法降落（同 GS 木星）。 */
    @Override
    public boolean canSpaceshipTierPass(int tier) {
        return false;
    }

    @Override
    public float getGravity() {
        return 0F;
    }

    @Override
    public double getMeteorFrequency() {
        return 0;
    }

    @Override
    public double getFuelUsageMultiplier() {
        return 0;
    }

    @Override
    public float getFallDamageModifier() {
        return 0.675F;
    }

    @Override
    public float getSoundVolReductionAmount() {
        return Float.MAX_VALUE;
    }

    @Override
    public float getThermalLevelModifier() {
        return -2.0F;
    }

    @Override
    public float getWindLevel() {
        return 2.0F;
    }

    @Override
    public int AtmosphericPressure() {
        return 100;
    }

    @Override
    public boolean SolarRadiation() {
        return false;
    }

    @Override
    public Vector3 getFogColor() {
        return new Vector3();
    }

    @Override
    public Vector3 getSkyColor() {
        return new Vector3();
    }

    @Override
    public boolean canRainOrSnow() {
        return true;
    }

    @Override
    public boolean hasSunset() {
        return false;
    }

    @Override
    public long getDayLength() {
        return 9925L;
    }

    @Override
    public Class<? extends IChunkProvider> getChunkProviderClass() {
        return ChunkProviderGSSpace.class;
    }

    @Override
    public Class<? extends WorldChunkManager> getWorldChunkManagerClass() {
        return WorldChunkManagerSpaceGS.class;
    }
}
