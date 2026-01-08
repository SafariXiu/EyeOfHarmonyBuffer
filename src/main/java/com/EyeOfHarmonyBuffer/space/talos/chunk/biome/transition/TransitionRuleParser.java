package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TransitionRuleParser {

    private TransitionRuleParser() {
    }

    public static List<TransitionRule> parse(Object transitionSettings) {
        if (transitionSettings == null) {
            return Collections.emptyList();
        }

        boolean enabled = readBoolean(transitionSettings, "enabled", true);
        if (!enabled) {
            return Collections.emptyList();
        }

        Collection<?> rawRules = readCollection(transitionSettings, "rules");
        if (rawRules.isEmpty()) {
            rawRules = readCollection(transitionSettings, "entries");
        }
        if (rawRules.isEmpty()) {
            return Collections.emptyList();
        }

        List<TransitionRule> parsed = new ArrayList<>();
        for (Object rawRule : rawRules) {
            if (rawRule == null) {
                continue;
            }

            int macroBiomeId = (int) readNumber(rawRule, new String[]{"macroBiomeId", "macroId", "biomeId"}, -1);
            if (macroBiomeId < 0) {
                continue;
            }

            double coastWidth = readDouble(rawRule, new String[]{"coastWidth", "width"}, 0.0d);
            Collection<String> allowed = readStrings(rawRule, new String[]{"allowedVariants", "variants", "whitelist"});
            String descriptor = readString(rawRule, new String[]{"descriptor", "name", "id"}, "macro-" + macroBiomeId);

            parsed.add(TransitionRule.create(macroBiomeId, coastWidth, allowed, descriptor));
        }

        return Collections.unmodifiableList(parsed);
    }

    private static boolean readBoolean(Object target, String methodName, boolean fallback) {
        Object value = readProperty(target, methodName);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return fallback;
    }

    private static Collection<?> readCollection(Object target, String methodName) {
        Object value = readProperty(target, methodName);
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value instanceof Object[] array) {
            List<Object> list = new ArrayList<>(array.length);
            Collections.addAll(list, array);
            return list;
        }
        return Collections.emptyList();
    }

    private static double readDouble(Object target, String[] names, double fallback) {
        double value = fallback;
        for (String name : names) {
            Object property = readProperty(target, name);
            if (property instanceof Number number) {
                value = number.doubleValue();
                break;
            }
            if (property instanceof String string) {
                try {
                    value = Double.parseDouble(string.trim());
                    break;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return value;
    }

    private static Number readNumber(Object target, String[] names, Number fallback) {
        for (String name : names) {
            Object property = readProperty(target, name);
            if (property instanceof Number number) {
                return number;
            }
            if (property instanceof String string) {
                try {
                    return Integer.parseInt(string.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }

    private static String readString(Object target, String[] names, String fallback) {
        for (String name : names) {
            Object property = readProperty(target, name);
            if (property instanceof String string && !string.isEmpty()) {
                return string;
            }
        }
        return fallback;
    }

    private static Collection<String> readStrings(Object target, String[] names) {
        for (String name : names) {
            Object property = readProperty(target, name);
            if (property instanceof Collection<?> collection) {
                List<String> tokens = new ArrayList<>(collection.size());
                for (Object entry : collection) {
                    if (entry == null) {
                        continue;
                    }
                    String token = entry.toString().trim();
                    if (!token.isEmpty()) {
                        tokens.add(token);
                    }
                }
                return tokens;
            }
            if (property instanceof String string) {
                String trimmed = string.trim();
                if (!trimmed.isEmpty()) {
                    String[] split = trimmed.split(",");
                    List<String> tokens = new ArrayList<>(split.length);
                    for (String token : split) {
                        String t = token.trim();
                        if (!t.isEmpty()) {
                            tokens.add(t);
                        }
                    }
                    return tokens;
                }
            }
        }
        return Collections.emptyList();
    }

    private static Object readProperty(Object target, String name) {
        if (target == null || name == null || name.isEmpty()) {
            return null;
        }

        Object viaMethod = invoke(target, name);
        if (viaMethod != null) {
            return viaMethod;
        }

        return readField(target, name);
    }

    private static Object invoke(Object target, String name) {
        Class<?> type = target.getClass();
        try {
            Method method = type.getMethod(name);
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

        return null;
    }

    private static Object readField(Object target, String name) {
        Class<?> type = target.getClass();
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
