package com.EyeOfHarmonyBuffer.utils;

public class TextLocalization {
    public static final String Waila_WirelessMode;
    public static final String Waila_CurrentEuCost;
    public static final String NameVendingMachines;
    public static final String Tooltip_VendingMachines_MachineType;
    public static final String Tooltip_VendingMachines_Controller;

    public TextLocalization() {
    }

    static{
        Waila_WirelessMode = TextHandler.texter("Wireless Mode", "Waila.General.WirelessMode");
        Waila_CurrentEuCost = TextHandler.texter("Current EU Cost", "Waila.General.CurrentEuCost");

        //大型贸易机
        NameVendingMachines = TextHandler.texter("Vending Machines", "NameVendingMachines");
        Tooltip_VendingMachines_MachineType = TextHandler.texter("Vending Machines", "Tooltip_VendingMachines_MachineType");
        Tooltip_VendingMachines_Controller = TextHandler.texter("Controller block for the Vending Machines", "Tooltip_VendingMachines_Controller");
    }
}
