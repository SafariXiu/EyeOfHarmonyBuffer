package com.EyeOfHarmonyBuffer.space.talos.chunk.field.context;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

public final class FieldSampleRequest {

    private final int chunkX;
    private final int chunkZ;
    private final int blockX;
    private final int blockZ;
    private final EnumSet<FieldDomain> domains;

    private FieldSampleRequest(int chunkX,
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

    public static Builder builder() {
        return new Builder();
    }

    public static FieldSampleRequest forChunk(int chunkX, int chunkZ, FieldDomain... domains) {
        Objects.requireNonNull(domains, "domains");
        EnumSet<FieldDomain> requested = domains.length == 0
            ? EnumSet.allOf(FieldDomain.class)
            : EnumSet.copyOf(Arrays.asList(domains));
        return new FieldSampleRequest(
            chunkX,
            chunkZ,
            chunkX << 4,
            chunkZ << 4,
            requested
        );
    }

    public static FieldSampleRequest forBlock(int blockX, int blockZ, FieldDomain... domains) {
        Objects.requireNonNull(domains, "domains");
        EnumSet<FieldDomain> requested = domains.length == 0
            ? EnumSet.allOf(FieldDomain.class)
            : EnumSet.copyOf(Arrays.asList(domains));
        return new FieldSampleRequest(
            blockX >> 4,
            blockZ >> 4,
            blockX,
            blockZ,
            requested
        );
    }

    public boolean includes(FieldDomain domain) {
        return domains.contains(domain);
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

    @Override
    public String toString() {
        return "FieldSampleRequest{" +
            "chunk=(" + chunkX + "," + chunkZ + ")" +
            ", block=(" + blockX + "," + blockZ + ")" +
            ", domains=" + domains +
            '}';
    }

    public static final class Builder {
        private int chunkX;
        private int chunkZ;
        private int blockX;
        private int blockZ;
        private EnumSet<FieldDomain> domains = EnumSet.noneOf(FieldDomain.class);

        public Builder chunk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockX = chunkX << 4;
            this.blockZ = chunkZ << 4;
            return this;
        }

        public Builder block(int blockX, int blockZ) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.chunkX = blockX >> 4;
            this.chunkZ = blockZ >> 4;
            return this;
        }

        public Builder domains(FieldDomain... domains) {
            if (domains == null || domains.length == 0) {
                this.domains = EnumSet.allOf(FieldDomain.class);
            } else {
                this.domains = EnumSet.copyOf(Arrays.asList(domains));
            }
            return this;
        }

        public Builder addDomain(FieldDomain domain) {
            if (domains.isEmpty()) {
                domains = EnumSet.of(domain);
            } else {
                domains.add(domain);
            }
            return this;
        }

        public FieldSampleRequest build() {
            if (domains.isEmpty()) {
                domains = EnumSet.allOf(FieldDomain.class);
            }
            return new FieldSampleRequest(chunkX, chunkZ, blockX, blockZ, domains);
        }
    }
}
