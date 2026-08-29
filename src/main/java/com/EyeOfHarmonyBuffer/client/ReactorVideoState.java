package com.EyeOfHarmonyBuffer.client;

public enum ReactorVideoState {

    POWER_99999(
        "99999mw",
        231,
        "reactor.99999mw",
        25
    ),

    HYDROGEN_DETONATION(
        "hydrogendetonation",
        648,
        "reactor.hydrogen_detonation",
        25
    );

    public final String folder;
    public final int maxFrameIndex;
    public final String soundId;
    public final int fps;
    public final long totalDurationMs;

    ReactorVideoState(String folder,
                      int maxFrameIndex,
                      String soundId,
                      int fps) {
        this.folder = folder;
        this.maxFrameIndex = maxFrameIndex;
        this.soundId = soundId;
        this.fps = fps <= 0 ? 25 : fps;

        this.totalDurationMs = (this.maxFrameIndex + 1L) * 1000L / this.fps;
    }
}
