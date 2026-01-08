package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;

import java.util.*;

public final class TransitionResolver {

    private final boolean enabled;
    private final Map<Integer, List<TransitionRule>> rulesByMacroId;

    public TransitionResolver(Object transitionSettings) {
        List<TransitionRule> parsed = TransitionRuleParser.parse(transitionSettings);
        this.enabled = transitionSettings != null && !parsed.isEmpty();
        this.rulesByMacroId = groupByMacroId(parsed);
    }

    public Result evaluate(MacroBiome macroBiome,
                           MacroBiome.MacroBiomeVariant variant,
                           double coastDistance) {
        if (!enabled || macroBiome == null || !Double.isFinite(coastDistance)) {
            return Result.none();
        }

        List<TransitionRule> rules = rulesByMacroId.get(macroBiome.id);
        if (rules == null || rules.isEmpty()) {
            return Result.none();
        }

        for (TransitionRule rule : rules) {
            if (!rule.matches(macroBiome.id, coastDistance)) {
                continue;
            }
            if (variant != null && rule.allowsVariant(variant)) {
                return Result.applied(rule);
            }
            return Result.override(rule);
        }

        return Result.none();
    }

    private static Map<Integer, List<TransitionRule>> groupByMacroId(List<TransitionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<TransitionRule>> map = new HashMap<>();
        for (TransitionRule rule : rules) {
            map.computeIfAbsent(rule.macroBiomeId(), ignored -> new ArrayList<>()).add(rule);
        }
        return map;
    }

    public static final class Result {

        private static final Result NONE = new Result(false, 0.0d, false, null);

        private final boolean active;
        private final double appliedCoastWidth;
        private final boolean requiresOverride;
        private final TransitionRule rule;

        private Result(boolean active,
                       double appliedCoastWidth,
                       boolean requiresOverride,
                       TransitionRule rule) {
            this.active = active;
            this.appliedCoastWidth = appliedCoastWidth;
            this.requiresOverride = requiresOverride;
            this.rule = rule;
        }

        public static Result none() {
            return NONE;
        }

        public static Result applied(TransitionRule rule) {
            if (rule == null) {
                return NONE;
            }
            return new Result(true, rule.coastWidth(), false, rule);
        }

        public static Result override(TransitionRule rule) {
            if (rule == null) {
                return NONE;
            }
            return new Result(true, rule.coastWidth(), true, rule);
        }

        public boolean active() {
            return active;
        }

        public double appliedCoastWidth() {
            return appliedCoastWidth;
        }

        public boolean requiresOverride() {
            return requiresOverride;
        }

        public TransitionRule rule() {
            return rule;
        }
    }
}
