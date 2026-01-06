package com.EyeOfHarmonyBuffer.space.talos.chunk.biome;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.StrategyMode;
import net.minecraft.world.World;

import java.util.Objects;

public final class SimplifiedStrategy implements BiomeDecisionStrategy {

    private final String version;
    private final FieldManager fieldManager;
    private final World world;

    public SimplifiedStrategy(String version, FieldManager fieldManager, World world) {
        this.version = Objects.requireNonNull(version, "version");
        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public StrategyMode getMode() {
        return StrategyMode.SIMPLIFIED;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public FieldManager getFieldManager() {
        return fieldManager;
    }

    public World getWorld() {
        return world;
    }
}
