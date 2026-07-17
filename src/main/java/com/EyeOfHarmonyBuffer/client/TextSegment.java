package com.EyeOfHarmonyBuffer.client;

public class TextSegment {
    public final String text;
    public final long startMs;
    public final long durationMs;

    public TextSegment(String text, long startMs, long durationMs) {
        this.text = text;
        this.startMs = startMs;
        this.durationMs = durationMs;
    }

    public boolean isActive(long elapsedMs) {
        return elapsedMs >= startMs && elapsedMs < (startMs + durationMs);
    }
}
