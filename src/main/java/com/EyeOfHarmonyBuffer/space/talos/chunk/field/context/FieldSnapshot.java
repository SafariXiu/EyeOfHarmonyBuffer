package com.EyeOfHarmonyBuffer.space.talos.chunk.field.context;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import net.minecraft.util.ChunkCoordinates;

import java.util.EnumSet;
import java.util.Objects;

public final class FieldSnapshot {

    private final int chunkX;
    private final int chunkZ;
    private final int blockX;
    private final int blockZ;
    private final EnumSet<FieldDomain> domains;

    private final ChunkProviderTalos2.ChunkShoreCache macro;
    private final TerrainSample terrain;
    private final ClimateSample climate;
    private final HydroSample hydro;

    private FieldSnapshot(Builder builder) {
        this.chunkX = builder.chunkX;
        this.chunkZ = builder.chunkZ;
        this.blockX = builder.blockX;
        this.blockZ = builder.blockZ;
        this.domains = builder.domains.clone();
        this.macro = builder.macro;
        this.terrain = builder.terrain;
        this.climate = builder.climate;
        this.hydro = builder.hydro;
    }

    public static Builder builder(FieldSampleRequest request) {
        Objects.requireNonNull(request, "request");
        return new Builder(request.getChunkX(),
            request.getChunkZ(),
            request.getBlockX(),
            request.getBlockZ(),
            request.getDomains());
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockZ() {
        return blockZ;
    }

    public EnumSet<FieldDomain> getDomains() {
        return domains.clone();
    }

    public ChunkProviderTalos2.ChunkShoreCache getMacro() {
        return macro;
    }

    public TerrainSample getTerrain() {
        return terrain;
    }

    public ClimateSample getClimate() {
        return climate;
    }

    public HydroSample getHydro() {
        return hydro;
    }

    public ChunkCoordinates asChunkCoords() {
        return new ChunkCoordinates(chunkX, 0, chunkZ);
    }

    public static final class Builder {

        private final int chunkX;
        private final int chunkZ;
        private final int blockX;
        private final int blockZ;
        private final EnumSet<FieldDomain> domains;

        private ChunkProviderTalos2.ChunkShoreCache macro;
        private TerrainSample terrain;
        private ClimateSample climate;
        private HydroSample hydro;

        private Builder(int chunkX,
                        int chunkZ,
                        int blockX,
                        int blockZ,
                        EnumSet<FieldDomain> domains) {

            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.domains = domains;
        }

        public Builder macro(ChunkProviderTalos2.ChunkShoreCache macro) {
            this.macro = macro;
            return this;
        }

        public Builder terrain(TerrainSample terrain) {
            this.terrain = terrain;
            return this;
        }

        public Builder climate(ClimateSample climate) {
            this.climate = climate;
            return this;
        }

        public Builder hydro(HydroSample hydro) {
            this.hydro = hydro;
            return this;
        }

        public FieldSnapshot build() {
            return new FieldSnapshot(this);
        }
    }
}
