package com.EyeOfHarmonyBuffer.utils;

public class WriteOnceOnly {

    private Boolean value = null;

    public void set(boolean newValue) {
        if (value != null) return;
        this.value = newValue;
    }

    public boolean get() {
        if (value == null) {
            throw new IllegalStateException("Value has not been initialized yet");
        }
        return value;
    }

    public boolean isSet() {
        return value != null;
    }
}
