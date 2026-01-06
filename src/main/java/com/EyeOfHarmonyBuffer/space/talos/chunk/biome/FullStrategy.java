package com.EyeOfHarmonyBuffer.space.talos.chunk.biome;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.StrategyMode;
import net.minecraft.world.World;

public final class FullStrategy implements BiomeDecisionStrategy {

    private final SimplifiedStrategy delegate;

    public FullStrategy(String version, FieldManager manager, World world) {
        this.delegate = new SimplifiedStrategy(version, manager, world);
    }

    @Override
    public StrategyMode getMode() {
        return StrategyMode.FULL;
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
