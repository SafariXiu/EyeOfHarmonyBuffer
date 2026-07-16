package com.EyeOfHarmonyBuffer.client;

public enum ReactorVideoState {
    POWER_0("00000MW", 95, "reactor.powerup"),
    POWER_300("00300MW", 91, "reactor.power_increase"),
    POWER_99999("99999MW", 97, "reactor.hydrogen_detonation");

    public final String folder;
    public final int maxFrameIndex;
    public final String soundId;

    ReactorVideoState(String folder, int maxFrameIndex, String soundId) {
        this.folder = folder;
        this.maxFrameIndex = maxFrameIndex;
        this.soundId = soundId;
    }
}
