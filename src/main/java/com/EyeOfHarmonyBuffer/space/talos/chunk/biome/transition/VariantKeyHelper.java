package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition;

import net.minecraft.world.biome.BiomeGenBase;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class VariantKeyHelper {

    private VariantKeyHelper() {}

    static Set<String> keysFor(BiomeGenBase biome) {
        Set<String> keys = new LinkedHashSet<>();
        if (biome == null) {
            return keys;
        }

        try {
            String registry = registryName(biome);
            if (!registry.isEmpty()) {
                keys.add(registry);
            }
        } catch (Throwable ignored) {}

        if (biome.biomeName != null && !biome.biomeName.trim().isEmpty()) {
            keys.add(sanitize(biome.biomeName));
        }

        try {
            keys.add("id:" + biome.biomeID);
        } catch (Throwable ignored) {}

        keys.add(biome.getClass().getSimpleName().toLowerCase(Locale.ROOT));

        return keys;
    }

    private static String registryName(BiomeGenBase biome) throws ReflectiveOperationException {
        Class<?> gameData = Class.forName("cpw.mods.fml.common.registry.GameData");
        Object registry = gameData.getMethod("getBiomeRegistry").invoke(null);
        if (registry == null) {
            return "";
        }
        Object resourceLocation = registry.getClass()
            .getMethod("getNameForObject", Object.class)
            .invoke(registry, biome);
        if (resourceLocation == null) {
            return "";
        }
        String name = resourceLocation.toString();
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    static String sanitize(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.replace(' ', '_');
    }

    static Collection<String> sanitizeAll(Collection<String> values) {
        Set<String> sanitized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            sanitized.add(sanitize(value));
        }
        return sanitized;
    }
}
