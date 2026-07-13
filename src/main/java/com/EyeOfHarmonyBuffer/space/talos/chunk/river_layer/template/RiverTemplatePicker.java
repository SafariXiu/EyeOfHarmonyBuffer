package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.RiverRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public final class RiverTemplatePicker {

    private RiverTemplatePicker() {}

    public static String pickTemplateIdForSupercontinent(int worldSeedInt, int superId) {
        Collection<String> ids = RiverRegistry.getAllTemplateIds();
        if (ids.isEmpty()) {
            return null;
        }

        List<String> list = new ArrayList<String>(ids);
        long seed = (((long) worldSeedInt) << 32) ^ (superId * 0x9E3779B97F4A7C15L);
        Random rng = new Random(seed);

        return list.get(rng.nextInt(list.size()));
    }
}
