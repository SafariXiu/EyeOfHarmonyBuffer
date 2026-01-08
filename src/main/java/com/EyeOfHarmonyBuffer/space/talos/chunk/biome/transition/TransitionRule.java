package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import net.minecraft.world.biome.BiomeGenBase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public final class TransitionRule {

    private static final String[] VARIANT_KEY_METHODS = {"key", "name", "getKey", "getName", "id", "getId"};
    private static final String[] VARIANT_KEY_FIELDS = {"key", "name", "id"};
    private static final String[] VARIANT_BIOME_ID_METHODS = {"biomeId", "getBiomeId", "biomeID", "getBiomeID"};
    private static final String[] VARIANT_BIOME_ID_FIELDS = {"biomeId", "biomeID"};
    private static final String[] VARIANT_BIOME_METHODS = {"biome", "getBiome"};
    private static final String[] VARIANT_BIOME_FIELDS = {"biome"};

    private final int macroBiomeId;
    private final double coastWidth;
    private final Set<String> allowedVariantKeys;
    private final Set<Integer> allowedBiomeIds;
    private final String descriptor;

    private TransitionRule(int macroBiomeId,
                           double coastWidth,
                           Set<String> allowedVariantKeys,
                           Set<Integer> allowedBiomeIds,
                           String descriptor) {
        if (macroBiomeId < 0) {
            throw new IllegalArgumentException("macroBiomeId must be >= 0");
        }
        this.macroBiomeId = macroBiomeId;
        this.coastWidth = Math.max(0.0d, coastWidth);
        this.allowedVariantKeys = Collections.unmodifiableSet(allowedVariantKeys);
        this.allowedBiomeIds = Collections.unmodifiableSet(allowedBiomeIds);
        this.descriptor = descriptor != null ? descriptor : "macro-" + macroBiomeId;
    }

    public static TransitionRule create(int macroBiomeId,
                                        double coastWidth,
                                        Collection<String> tokens,
                                        String descriptor) {
        Set<String> names = new LinkedHashSet<>();
        Set<Integer> ids = new LinkedHashSet<>();

        if (tokens != null) {
            for (String token : tokens) {
                if (token == null) {
                    continue;
                }
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    ids.add(Integer.parseInt(trimmed));
                    continue;
                } catch (NumberFormatException ignored) {
                }
                names.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }

        return new TransitionRule(
            macroBiomeId,
            coastWidth,
            names,
            ids,
            descriptor
        );
    }

    public int macroBiomeId() {
        return macroBiomeId;
    }

    public double coastWidth() {
        return coastWidth;
    }

    public String descriptor() {
        return descriptor;
    }

    public boolean matches(int macroBiomeId, double coastDistance) {
        return this.macroBiomeId == macroBiomeId
            && Double.isFinite(coastDistance)
            && coastDistance <= coastWidth;
    }

    public boolean allowsVariant(MacroBiome.MacroBiomeVariant variant) {
        if (variant == null) {
            return allowedVariantKeys.isEmpty() && allowedBiomeIds.isEmpty();
        }

        if (allowedVariantKeys.isEmpty() && allowedBiomeIds.isEmpty()) {
            return true;
        }

        String key = extractVariantKey(variant);
        if (key != null && allowedVariantKeys.contains(key)) {
            return true;
        }

        Integer variantBiomeId = extractVariantBiomeId(variant);
        if (variantBiomeId != null && allowedBiomeIds.contains(variantBiomeId)) {
            return true;
        }

        BiomeGenBase biome = extractVariantBiome(variant);
        if (biome != null && allowedBiomeIds.contains(biome.biomeID)) {
            return true;
        }

        return false;
    }

    private static String extractVariantKey(MacroBiome.MacroBiomeVariant variant) {
        String fromMethod = invokeString(variant, VARIANT_KEY_METHODS);
        if (fromMethod != null) {
            return fromMethod.toLowerCase(Locale.ROOT);
        }
        String fromField = readStringField(variant, VARIANT_KEY_FIELDS);
        if (fromField != null) {
            return fromField.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static Integer extractVariantBiomeId(MacroBiome.MacroBiomeVariant variant) {
        Number number = invokeNumber(variant, VARIANT_BIOME_ID_METHODS);
        if (number != null) {
            return number.intValue();
        }
        Number fieldNumber = readNumberField(variant, VARIANT_BIOME_ID_FIELDS);
        if (fieldNumber != null) {
            return fieldNumber.intValue();
        }
        BiomeGenBase biome = extractVariantBiome(variant);
        return biome != null ? biome.biomeID : null;
    }

    private static BiomeGenBase extractVariantBiome(MacroBiome.MacroBiomeVariant variant) {
        Object viaMethod = invoke(variant, VARIANT_BIOME_METHODS);
        if (viaMethod instanceof BiomeGenBase biomeGenBase) {
            return biomeGenBase;
        }
        Object viaField = readField(variant, VARIANT_BIOME_FIELDS);
        if (viaField instanceof BiomeGenBase biomeGenBase) {
            return biomeGenBase;
        }
        return null;
    }

    private static Object invoke(Object target, String... candidates) {
        if (target == null || candidates == null) {
            return null;
        }
        Class<?> type = target.getClass();
        for (String name : candidates) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                Method method = type.getMethod(name, new Class<?>[0]);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
            String getterName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            try {
                Method method = type.getMethod(getterName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static String invokeString(Object target, String... candidates) {
        Object value = invoke(target, candidates);
        return value != null ? String.valueOf(value) : null;
    }

    private static Number invokeNumber(Object target, String... candidates) {
        Object value = invoke(target, candidates);
        return value instanceof Number number ? number : null;
    }

    private static Object readField(Object target, String... candidates) {
        if (target == null || candidates == null) {
            return null;
        }
        Class<?> type = target.getClass();
        for (String name : candidates) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static String readStringField(Object target, String... candidates) {
        Object value = readField(target, candidates);
        return value != null ? String.valueOf(value) : null;
    }

    private static Number readNumberField(Object target, String... candidates) {
        Object value = readField(target, candidates);
        return value instanceof Number number ? number : null;
    }
}
