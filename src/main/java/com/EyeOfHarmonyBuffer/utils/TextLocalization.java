package com.EyeOfHarmonyBuffer.utils;

import net.minecraft.util.EnumChatFormatting;

public class TextLocalization {
    public static final String ModName = "Eye Of Harmony Buffer";
    public static final String Waila_WirelessMode;
    public static final String Waila_CurrentEuCost;
    public static final String WirelessNetwork;
    public static final String StructureTooComplex;
    public static final String BLUE_PRINT_INFO;
    public static final String Tooltip_Details;
    public static final String Disable_loading;
    public static final String add_InputBus;
    public static final String add_OutputBus;

    public static final String NameVendingMachines;
    public static final String Tooltip_VendingMachines_MachineType;
    public static final String Tooltip_VendingMachines_Controller;
    public static final String Tooltip_VendingMachines_00;
    public static final String Tooltip_VendingMachines_01;
    public static final String Tooltip_VendingMachines_02;
    public static final String Tooltip_VendingMachines_03;

    public static final String NameWindTurbine;
    public static final String Tooltip_WindTurbine_MachineType;
    public static final String Tooltip_WindTurbine_Controller;

    public TextLocalization() {
    }

    static{
        //默认
        Waila_WirelessMode = TextHandler.texter("Wireless Mode", "Waila.General.WirelessMode");
        Waila_CurrentEuCost = TextHandler.texter("Current EU Cost", "Waila.General.CurrentEuCost");
        WirelessNetwork = TextHandler.texter("Directly get EU from the Wireless EU Net.", "WirelessNetwork");
        StructureTooComplex = TextHandler.texter("The structure is too complex!", "StructureTooComplex");
        BLUE_PRINT_INFO = TextHandler.texter(
            "Follow the" + EnumChatFormatting.BLUE
                + " Structure"
                + EnumChatFormatting.DARK_BLUE
                + "Lib"
                + EnumChatFormatting.GRAY
                + " hologram projector to build the main structure.",
            "BLUE_PRINT_INFO");
        Tooltip_Details = TextHandler.texter(EnumChatFormatting.LIGHT_PURPLE + "Details: ", "Tooltip_Details");
        Disable_loading = TextHandler.texter("§4§l§oMachine is prohibited from loading!Please go to the configuration file MachineLoaderConfig to enable the machine!", "Disable_loading");
        add_InputBus = TextHandler.texter("Any casing","add_InputBus");
        add_OutputBus = TextHandler.texter("Any casing","add_OutputBus");


        //大型贸易机
        NameVendingMachines = TextHandler.texter("Vending Machines", "NameVendingMachines");
        Tooltip_VendingMachines_MachineType = TextHandler.texter("Vending Machines", "Tooltip_VendingMachines_MachineType");
        Tooltip_VendingMachines_Controller = TextHandler.texter("Controller block for the Vending Machines", "Tooltip_VendingMachines_Controller");
        Tooltip_VendingMachines_00 = TextHandler.texter("A large trade machine, Vending Machines, Place the marked item into the machine's main block slot to set it as the machine's output item.", "Tooltip_VendingMachines_00");
        Tooltip_VendingMachines_01 = TextHandler.texter("When powered by a wireless EU Net, it supports parallel multi-recipe processing with a fixed working time of 6.4 seconds.", "Tooltip_VendingMachines_01");
        Tooltip_VendingMachines_02 = TextHandler.texter("Put the desired output item into the machine's main block. Any input item will produce the desired output item!", "Tooltip_VendingMachines_02");
        Tooltip_VendingMachines_03 = TextHandler.texter("The machine has a default of int parallel processing, a fixed working time of 6.4 seconds, and consumes no power.", "Tooltip_VendingMachines_03");

        //大型风力发电机
        NameWindTurbine = TextHandler.texter("Large Wind Turbine", "NameWindTurbine");
        Tooltip_WindTurbine_MachineType = TextHandler.texter("Wind Turbine", "Tooltip_WindTurbine_MachineType");
        Tooltip_WindTurbine_Controller = TextHandler.texter("Controller block for the Large Wind Turbine", "Tooltip_WindTurbine_Controller");
    }
}
