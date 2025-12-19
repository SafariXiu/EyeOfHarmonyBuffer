package com.EyeOfHarmonyBuffer.space.talos;

import java.util.Random;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import com.EyeOfHarmonyBuffer.space.talos.client.render.HugePlanetSkyRenderer;
import galaxyspace.core.dimension.WorldProviderSpaceGS;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;

import galaxyspace.core.handler.api.IHostileBody;
import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.vector.Vector3;
import micdoodle8.mods.galacticraft.api.world.IExitHeight;
import micdoodle8.mods.galacticraft.api.world.ISolarLevel;
import micdoodle8.mods.galacticraft.api.world.ITeleportType;
import net.minecraftforge.client.IRenderHandler;

public class WorldProviderTalos2 extends WorldProviderSpaceGS
    implements IExitHeight, ISolarLevel, ITeleportType, IHostileBody {

    private IRenderHandler skyProvider;

    @Override
    public void registerWorldChunkManager() {
        this.worldChunkMgr = new WorldChunkManagerTalos2(this.worldObj);
        this.hasNoSky = false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public CelestialBody getCelestialBody() {
        return RegisterDimensions.talos2;
    }

    @Override
    public Class<? extends IChunkProvider> getChunkProviderClass() {
        return ChunkProviderTalos2.class;
    }

    @Override
    public Class<? extends net.minecraft.world.biome.WorldChunkManager> getWorldChunkManagerClass() {
        return WorldChunkManagerTalos2.class;
    }

    @Override public boolean hasBreathableAtmosphere() {
        return true;
    }

    @Override public boolean canRainOrSnow() {
        return true;
    }

    @Override
    public boolean canBlockFreeze(int x, int y, int z, boolean byWater) {
        return false;
    }

    @Override public boolean canDoRainSnowIce(net.minecraft.world.chunk.Chunk chunk) {
        return false;
    }

    @Override public float getGravity() {
        return 0.04F;
    }

    @Override public float getThermalLevelModifier() {
        return 0F;
    }

    @Override public int AtmosphericPressure() {
        return 1;
    }

    @Override public boolean SolarRadiation() {
        return false;
    }

    @Override public double getSolarEnergyMultiplier() {
        return 1.0;
    }

    @Override public long getDayLength() {
        return 24000L;
    }

    @Override public boolean hasSunset() {
        return true;
    }

    @Override public float getFallDamageModifier() {
        return 1.0F;
    }

    @Override public float getSoundVolReductionAmount() {
        return 1.0F;
    }

    @Override public float getWindLevel() {
        return 0;
    }

    @Override public Vector3 getFogColor() {
        return new Vector3(0.65, 0.75, 1.0);
    }

    @Override public Vector3 getSkyColor() {
        return new Vector3(0.25, 0.45, 0.95);
    }


    @Override public double getYCoordinateToTeleport() {
        return 1200.0;
    }

    @Override public Vector3 getEntitySpawnLocation(WorldServer world, Entity entity) {
        return new Vector3(entity.posX, 250.0, entity.posZ);
    }

    @Override public Vector3 getPlayerSpawnLocation(WorldServer world, EntityPlayerMP player) {
        return new Vector3(0.0, 250.0, 0.0);
    }

    @Override public Vector3 getParaChestSpawnLocation(WorldServer world, EntityPlayerMP player, Random rand) {
        double x = (rand.nextDouble() * 2.0 - 1.0) * 5.0;
        double z = (rand.nextDouble() * 2.0 - 1.0) * 5.0;
        return new Vector3(x, 220.0, z);
    }

    @Override
    public void onSpaceDimensionChanged(World newWorld, EntityPlayerMP player, boolean ridingAutoRocket) {

    }

    @Override
    public void setupAdventureSpawn(EntityPlayerMP player) {

    }

    @Override public boolean useParachute() {
        return true;
    }

    @Override public double getMeteorFrequency() {
        return 0;
    }

    @Override public double getFuelUsageMultiplier() {
        return 1.0;
    }

    @Override public boolean canSpaceshipTierPass(int tier) {
        return tier >= RegisterDimensions.tier_Talos2;
    }

    /*@Override
    public void updateWeather() {
        this.worldObj.updateWeatherBody();
    }*/

    /*@Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getWeatherRenderer() {
        return null;
    }*/

    @Override
    public IRenderHandler getSkyRenderer() {
        if (this.skyProvider == null) {
            this.skyProvider = new HugePlanetSkyRenderer();
        }
        return this.skyProvider;
    }
}
