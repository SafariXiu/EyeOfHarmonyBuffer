package com.EyeOfHarmonyBuffer.utils;

public class TextLocalization {
    public static final String Waila_WirelessMode;
    public static final String Waila_CurrentEuCost;
    public static final String NameVendingMachines;
    public static final String WirelessNetwork;
    public static final String Tooltip_VendingMachines_MachineType;
    public static final String Tooltip_VendingMachines_Controller;
    public static final String Tooltip_VendingMachines_00;
    public static final String Tooltip_VendingMachines_01;

    public TextLocalization() {
    }

    static{
        //默认
        Waila_WirelessMode = TextHandler.texter("Wireless Mode", "Waila.General.WirelessMode");
        Waila_CurrentEuCost = TextHandler.texter("Current EU Cost", "Waila.General.CurrentEuCost");
        WirelessNetwork = TextHandler.texter("Directly get EU from the Wireless EU Net.", "WirelessNetwork");

        //大型贸易机
        NameVendingMachines = TextHandler.texter("Vending Machines", "NameVendingMachines");
        Tooltip_VendingMachines_MachineType = TextHandler.texter("Vending Machines", "Tooltip_VendingMachines_MachineType");
        Tooltip_VendingMachines_Controller = TextHandler.texter("Controller block for the Vending Machines", "Tooltip_VendingMachines_Controller");
        Tooltip_VendingMachines_00 = TextHandler.texter("A large trade machine, Vending Machines, marks items in the Crafting Input Buffer (ME) NC slot to set the machine's output items.", "Tooltip_VendingMachines_00");
        Tooltip_VendingMachines_01 = TextHandler.texter("When powered by a wireless EU Net, it supports parallel multi-recipe processing with a fixed working time of 6.4 seconds.", "Tooltip_VendingMachines_01");
    }
}
