package com.EyeOfHarmonyBuffer.client;

public class TimedSound {
    public final String soundId;
    public final long triggerMs;
    public boolean played = false;

    public TimedSound(String soundId, long triggerMs) {
        this.soundId = soundId;
        this.triggerMs = triggerMs;
    }
}
