package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.RiverRegistry;

public final class RiverTemplatePicker {

    private RiverTemplatePicker() {}

    public static String pickTemplateIdForSupercontinent(int worldSeedInt, int superId) {
        java.util.Collection<String> ids = RiverRegistry.getAllTemplateIds();
        if (ids.isEmpty()) {
            return null;
        }

        java.util.List<String> list = new java.util.ArrayList<String>(ids);
        java.util.Collections.sort(list);

        int h = java.util.Objects.hash(worldSeedInt, superId);
        int idx = Math.floorMod(h, list.size());

        return list.get(idx);
    }
}
