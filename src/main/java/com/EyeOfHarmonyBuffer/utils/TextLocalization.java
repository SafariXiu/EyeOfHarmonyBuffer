package com.EyeOfHarmonyBuffer.utils;

@Deprecated
public class TextLocalization {
    public static final String Waila_WirelessMode;
    public static final String Waila_CurrentEuCost;

    public TextLocalization() {
    }

    static{
        Waila_WirelessMode = TextHandler.texter("Wireless Mode", "Waila.General.WirelessMode");
        Waila_CurrentEuCost = TextHandler.texter("Current EU Cost", "Waila.General.CurrentEuCost");
    }
}
