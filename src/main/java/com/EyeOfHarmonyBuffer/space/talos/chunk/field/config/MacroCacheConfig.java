package com.EyeOfHarmonyBuffer.space.talos.chunk.field.config;

public final class MacroCacheConfig {

    public static MacroCacheConfig defaults() {
        return builder().build();
    }

    private final boolean enabled;
    private final int maxEntries;
    private final boolean diagnosticsEnabled;

    private MacroCacheConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.maxEntries = builder.maxEntries;
        this.diagnosticsEnabled = builder.diagnosticsEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public boolean isDiagnosticsEnabled() {
        return diagnosticsEnabled;
    }

    public Builder toBuilder() {
        return builder()
            .enabled(enabled)
            .maxEntries(maxEntries)
            .diagnosticsEnabled(diagnosticsEnabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = true;
        private int maxEntries = 1024;
        private boolean diagnosticsEnabled = true;

        private Builder() {
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxEntries(int maxEntries) {
            this.maxEntries = Math.max(32, maxEntries);
            return this;
        }

        public Builder diagnosticsEnabled(boolean diagnosticsEnabled) {
            this.diagnosticsEnabled = diagnosticsEnabled;
            return this;
        }

        public MacroCacheConfig build() {
            return new MacroCacheConfig(this);
        }
    }
}

