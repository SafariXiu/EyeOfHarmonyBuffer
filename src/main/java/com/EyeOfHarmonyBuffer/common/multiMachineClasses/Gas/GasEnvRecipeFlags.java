package com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas;

public final class GasEnvRecipeFlags {

    public static final int NONE    = 1 << 0;
    public static final int STABLE  = 1 << 1;
    public static final int HUMID   = 1 << 2;
    public static final int ACRID   = 1 << 3;
    public static final int XRANITE = 1 << 4;
    public static final int ANY = 0;

    private GasEnvRecipeFlags() {}

    public static int bitOf(GasEnvironmentType env) {
        return switch (env) {
            case NONE    -> NONE;
            case STABLE  -> STABLE;
            case HUMID   -> HUMID;
            case ACRID   -> ACRID;
            case XRANITE -> XRANITE;
        };
    }

    public static boolean isEnvAllowed(int envMask, GasEnvironmentType env) {
        if (envMask == ANY) {
            // specialValue = 0,
            return true;
        }
        int bit = bitOf(env);
        return (envMask & bit) != 0;
    }
}
