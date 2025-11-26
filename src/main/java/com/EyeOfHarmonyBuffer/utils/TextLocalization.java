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
    public static final String add_inputHatch;
    public static final String add_outputHatch;
    public static final String add_DynamoHatch;
    public static final String add_MaintenanceHatch;
    public static final String EOHB_Text_SeparatingLine;
    public static final String EOHB_Starry_Miracle_Project;
    public static final String EOHB_Legendary_Machine_Project;
    public static final String EOHB_Client_PlayerJoin00;
    public static final String EOHB_Client_PlayerJoin01;
    public static final String EOHB_Client_PlayerJoin01_1;
    public static final String EOHB_Client_PlayerJoin01_2;
    public static final String EOHB_Client_PlayerJoin02;
    public static final String EOHB_Client_PlayerJoin03;
    public static final String EOHB_Client_PlayerJoin04;
    public static final String EOHB_Client_PlayerJoin05;
    public static final String EOHB_Client_PlayerJoin06;
    public static final String EOHB_Client_PlayerJoin07;
    public static final String EOHB_Client_PlayerJoin07_1;
    public static final String EOHB_Client_PlayerJoin07_2;
    public static final String EOHB_Config_Not_Exist;
    public static final String EOHB_Open_Config;
    public static final String EOHB_Opened;
    public static final String EOHB_Open_Failed;

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
    public static final String Tooltip_WindTurbine_00;
    public static final String Tooltip_WindTurbine_01;
    public static final String Tooltip_WindTurbine_02;
    public static final String Tooltip_WindTurbine_03;
    public static final String StructureInfo_WindTurbine_00;
    public static final String StructureInfo_WindTurbine_01;
    public static final String StructureInfo_WindTurbine_02;
    public static final String StructureInfo_WindTurbine_03;
    public static final String StructureInfo_WindTurbine_04;
    public static final String StructureInfo_WindTurbine_05;
    public static final String StructureInfo_WindTurbine_06;
    public static final String StructureInfo_WindTurbine_07;
    public static final String StructureInfo_WindTurbine_08;
    public static final String StructureInfo_WindTurbine_09;

    public static final String NameSolarEnergyArray;
    public static final String Tooltip_SolarEnergyArray_MachineType;
    public static final String Tooltip_SolarEnergyArray_Controller;
    public static final String Tooltip_SolarEnergyArray_00;
    public static final String Tooltip_SolarEnergyArray_01;
    public static final String Tooltip_SolarEnergyArray_02;
    public static final String Tooltip_SolarEnergyArray_03;
    public static final String Tooltip_SolarEnergyArray_04;
    public static final String Tooltip_SolarEnergyArray_05;

    public static final String NameSubstanceReshapingDevice;
    public static final String Tooltip_SubstanceReshapingDevice_MachineType;
    public static final String Tooltip_SubstanceReshapingDevice_Controller;
    public static final String Tooltip_SubstanceReshapingDevice_00;
    public static final String Tooltip_SubstanceReshapingDevice_01;
    public static final String Tooltip_SubstanceReshapingDevice_02;
    public static final String Tooltip_SubstanceReshapingDevice_03;
    public static final String Tooltip_SubstanceReshapingDevice_04;
    public static final String Tooltip_SubstanceReshapingDevice_05;
    public static final String Tooltip_SubstanceReshapingDevice_06;
    public static final String Tooltip_SubstanceReshapingDevice_07;
    public static final String Tooltip_SubstanceReshapingDevice_08;
    public static final String Tooltip_SubstanceReshapingDevice_09;
    public static final String Tooltip_SubstanceReshapingDevice_10;
    public static final String SubstanceReshapingDeviceRecipes;

    public static final String NameBlueDogMachine;
    public static final String Tooltip_BlueDogMachine_MachineType;
    public static final String Tooltip_BlueDogMachine_Controller;
    public static final String Tooltip_BlueDogMachine_00;
    public static final String Tooltip_BlueDogMachine_01;
    public static final String Tooltip_BlueDogMachine_02;
    public static final String Tooltip_BlueDogMachine_03;
    public static final String Tooltip_BlueDogMachine_04;
    public static final String Tooltip_BlueDogMachine_05;
    public static final String Tooltip_BlueDogMachine_06;

    public static final String NameMonkeyShit;
    public static final String Tooltip_MonkeyShit_MachineType;
    public static final String Tooltip_MonkeyShit_Controller;
    public static final String Tooltip_MonkeyShit_00;
    public static final String Tooltip_MonkeyShit_01;
    public static final String Tooltip_MonkeyShit_02;
    public static final String Tooltip_MonkeyShit_03;
    public static final String Tooltip_MonkeyShit_04;
    public static final String Tooltip_MonkeyShit_05;

    public static final String EOHB_Harmony;
    public static final String EOHB_Harmony_Tooltip;
    public static final String EOHB_ChengDuHeart;
    public static final String EOHB_ChengDuHeart_Tooltip_01;
    public static final String EOHB_ChengDuHeart_Tooltip_02;


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
        add_inputHatch = TextHandler.texter("Any casing","add_inputHatch");
        add_outputHatch = TextHandler.texter("Any casing","add_outputHatch");
        add_DynamoHatch = TextHandler.texter("Any casing","add_DynamoHatch");
        add_MaintenanceHatch = TextHandler.texter("Any casing","add_MaintenanceHatch");
        EOHB_Text_SeparatingLine = TextHandler.texter("-----------------------------------------","EOHB_Text_SeparatingLine");
        EOHB_Starry_Miracle_Project = TextHandler.texter("EOHB — Starry Miracle Project","EOHB_Starry_Miracle_Project");
        EOHB_Legendary_Machine_Project = TextHandler.texter("EOHB-Legendary Machine Project","EOHB_Legendary_Machine_Project");

        //载入提示性文本
        EOHB_Client_PlayerJoin00 = TextHandler.texter("Welcome to use EyeOfHarmonyBuffer!","EOHB_Client_PlayerJoin00");
        EOHB_Client_PlayerJoin01 = TextHandler.texter("Remember to check your config file to see which features you’ve actually turned on.","EOHB_Client_PlayerJoin01");
        EOHB_Client_PlayerJoin01_1 = TextHandler.texter("right now, everything’s snoozing by default!","EOHB_Client_PlayerJoin01_1");
        EOHB_Client_PlayerJoin01_2 = TextHandler.texter("Type /EOHBconfiglist to show the four config file links below once more!","EOHB_Client_PlayerJoin01_2");
        EOHB_Client_PlayerJoin02 = TextHandler.texter("Main Configuration File","EOHB_Client_PlayerJoin02");
        EOHB_Client_PlayerJoin03 = TextHandler.texter("EOH Extra Item Output Config File","EOHB_Client_PlayerJoin03");
        EOHB_Client_PlayerJoin04 = TextHandler.texter("EOH Extra Fluid Output Config File","EOHB_Client_PlayerJoin04");
        EOHB_Client_PlayerJoin05 = TextHandler.texter("Machine Loading Config File","EOHB_Client_PlayerJoin05");
        EOHB_Client_PlayerJoin06 = TextHandler.texter("Open configuration folder","EOHB_Client_PlayerJoin06");
        EOHB_Client_PlayerJoin07 = TextHandler.texter("Done with your configs? Save them and hit /eoh_reloadconfig in-game for a hot reload!","EOHB_Client_PlayerJoin07");
        EOHB_Client_PlayerJoin07_1 = TextHandler.texter("Just a heads-up: recipe tweaks and machine loads need a full restart to kick in.","EOHB_Client_PlayerJoin07_1");
        EOHB_Client_PlayerJoin07_2 = TextHandler.texter("Have fun tweaking and playing!","EOHB_Client_PlayerJoin07_2");
        EOHB_Config_Not_Exist = TextHandler.texter("Configuration directory does not exist!","EOHB_Config_Not_Exist");
        EOHB_Open_Config = TextHandler.texter("Configuration folder opened.","EOHB_Open_Config");
        EOHB_Opened = TextHandler.texter("Opened:","EOHB_Opened");
        EOHB_Open_Failed = TextHandler.texter("Failed to open configuration:","EOHB_Open_Failed");

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
        Tooltip_WindTurbine_00 = TextHandler.texter("A large wind turbine that starts operating once the rotor is placed into the controller.", "Tooltip_WindTurbine_00");
        Tooltip_WindTurbine_01 = TextHandler.texter("The higher the grade of the rotor, the more power it generates.", "Tooltip_WindTurbine_01");
        Tooltip_WindTurbine_02 = TextHandler.texter("The power generation formula is: UHV * Wind Factor * Rotor Level.\n" +
            "The Wind Factor refreshes every 30 seconds and ranges from 0.5 to 1.5.", "Tooltip_WindTurbine_02");
        Tooltip_WindTurbine_03 = TextHandler.texter("Clean energy! May your skies remain pollution-free!","Tooltip_WindTurbine_03");
        StructureInfo_WindTurbine_00 = TextHandler.texter("The rotor material is classified as follows:", "StructureInfo_WindTurbine_00");
        StructureInfo_WindTurbine_01 = TextHandler.texter("Wood-Level 1-1x", "StructureInfo_WindTurbine_01");
        StructureInfo_WindTurbine_02 = TextHandler.texter("Iron-Level 2-4x", "StructureInfo_WindTurbine_02");
        StructureInfo_WindTurbine_03 = TextHandler.texter("Steel-Level 2-8x", "StructureInfo_WindTurbine_03");
        StructureInfo_WindTurbine_04 = TextHandler.texter("Carbon-Level 4-16x", "StructureInfo_WindTurbine_04");
        StructureInfo_WindTurbine_05 = TextHandler.texter("EnergyTic Alloy-Level 5-32x", "StructureInfo_WindTurbine_05");
        StructureInfo_WindTurbine_06 = TextHandler.texter("Tungsten Steel-Level 6-64x", "StructureInfo_WindTurbine_06");
        StructureInfo_WindTurbine_07 = TextHandler.texter("Vibrant-Level 7-128x", "StructureInfo_WindTurbine_07");
        StructureInfo_WindTurbine_08 = TextHandler.texter("Iridium-Level 8-256x", "StructureInfo_WindTurbine_08");
        StructureInfo_WindTurbine_09 = TextHandler.texter("They are terrifyingly powerful!", "StructureInfo_WindTurbine_09");

        //太阳能阵列
        NameSolarEnergyArray = TextHandler.texter("Solar Energy Array", "NameSolarEnergyArray");
        Tooltip_SolarEnergyArray_MachineType = TextHandler.texter("Solar Energy Array", "Tooltip_SolarEnergyArray_MachineType");
        Tooltip_SolarEnergyArray_Controller = TextHandler.texter("Controller block for the Solar Energy Array", "Tooltip_SolarEnergyArray_Controller");
        Tooltip_SolarEnergyArray_00 = TextHandler.texter("A large solar panel, better than the smaller one!", "Tooltip_SolarEnergyArray_00");
        Tooltip_SolarEnergyArray_01 = TextHandler.texter("It works at all times, don't ask why! This is a highly advanced machine!", "Tooltip_SolarEnergyArray_01");
        Tooltip_SolarEnergyArray_02 = TextHandler.texter("The power generation formula is MAX * MAX.", "Tooltip_SolarEnergyArray_02");
        Tooltip_SolarEnergyArray_03 = TextHandler.texter("Clean energy! May your skies remain pollution-free!","Tooltip_SolarEnergyArray_03");
        Tooltip_SolarEnergyArray_04 = TextHandler.texter("Place the Astral Array Fabricator in the Controller Block to activate Wireless Mode.", "Tooltip_SolarEnergyArray_04");
        Tooltip_SolarEnergyArray_05 = TextHandler.texter("After activating Wireless Mode, Dynamo Hatches are no longer needed.", "Tooltip_SolarEnergyArray_05");

        //物质重塑仪
        NameSubstanceReshapingDevice = TextHandler.texter("Substance Reshaping Device", "NameSubstanceReshapingDevice");
        Tooltip_SubstanceReshapingDevice_MachineType = TextHandler.texter("Substance Reshaping Device", "Tooltip_SubstanceReshapingDevice_MachineType");
        Tooltip_SubstanceReshapingDevice_Controller = TextHandler.texter("Controller block for the Core Drill", "Tooltip_SubstanceReshapingDevice_Controller");
        Tooltip_SubstanceReshapingDevice_00 = TextHandler.texter("Void begets all things, and chaos forges gods. All matter can be reshaped, but great power cannot be defied.", "Tooltip_SubstanceReshapingDevice_00");
        Tooltip_SubstanceReshapingDevice_01 = TextHandler.texter("The laws of the universe are both a cage and a shackle. All things follow the path of entropy—life will inevitably decay, stars will burn out, and dust will return to dust. But we refuse to accept this fate.", "Tooltip_SubstanceReshapingDevice_01");
        Tooltip_SubstanceReshapingDevice_02 = TextHandler.texter("They are nameless entities, echoes from beyond reality, remnants we brought back from the unknown when we tampered with the laws of the universe","Tooltip_SubstanceReshapingDevice_02");
        Tooltip_SubstanceReshapingDevice_03 = TextHandler.texter("We are the creators of stars, the manipulators of reality, and the ultimate masters of gravity!                                                          ","Tooltip_SubstanceReshapingDevice_03");
        Tooltip_SubstanceReshapingDevice_04 = TextHandler.texter("Some particles exhibit arrangements that defy known physical rules, some substances exist in ways that violate the laws of thermodynamics.","Tooltip_SubstanceReshapingDevice_04");
        Tooltip_SubstanceReshapingDevice_05 = TextHandler.texter("and some creations… vanish the moment we attempt to document them, as if they never existed at all.","Tooltip_SubstanceReshapingDevice_05");
        Tooltip_SubstanceReshapingDevice_06 = TextHandler.texter("The machine supports cross-recipe parallel processing!","Tooltip_SubstanceReshapingDevice_06");
        Tooltip_SubstanceReshapingDevice_07 = TextHandler.texter("Supports up to 10 recipes running simultaneously! The optimal ratio for each recipe is 64*10!","Tooltip_SubstanceReshapingDevice_07");
        Tooltip_SubstanceReshapingDevice_08 = TextHandler.texter("If any recipe exceeds 64 units in operation, it will cause a decrease in total parallel efficiency!","Tooltip_SubstanceReshapingDevice_08");
        Tooltip_SubstanceReshapingDevice_09 = TextHandler.texter("But remember this—creators are also destroyers.","Tooltip_SubstanceReshapingDevice_09");
        Tooltip_SubstanceReshapingDevice_10 = TextHandler.texter("\"When the gods fall silent, mortals may seize the mantle of power. But remember, the mantle was never truly ours to claim.\"","Tooltip_SubstanceReshapingDevice_10");
        SubstanceReshapingDeviceRecipes = TextHandler.texter("Singularity Stabilization Ring Casings Live:  %s","SubstanceReshapingDeviceRecipes");

        //蓝狗喷泉
        NameBlueDogMachine = TextHandler.texter("Blue Dog Fountain","NameBlueDogMachine");
        Tooltip_BlueDogMachine_MachineType = TextHandler.texter("Blue Dog Fountain","Tooltip_BlueDogMachine_MachineType");
        Tooltip_BlueDogMachine_Controller = TextHandler.texter("Controller Block of Blue Dog Fountain","Tooltip_BlueDogMachine_Controller");
        Tooltip_BlueDogMachine_00 = TextHandler.texter("In honor of BlueDog — the legendary creator of unofficial mods!","Tooltip_BlueDogMachine_00");
        Tooltip_BlueDogMachine_01 = TextHandler.texter("A legendary device that turns regular water into ultra-pure water — with nothing else!\n" +
            "Rumor has it the idea came from a team outing…","Tooltip_BlueDogMachine_01");
        Tooltip_BlueDogMachine_02 = TextHandler.texter("They say if you place some curious items inside the machine’s controller, you might witness surprising results!","Tooltip_BlueDogMachine_02");
        Tooltip_BlueDogMachine_03 = TextHandler.texter("Oh, and you’d better not look too closely at where the water comes out… It’s not exactly elegant.","Tooltip_BlueDogMachine_03");
        Tooltip_BlueDogMachine_04 = TextHandler.texter("“He doesn’t look very comfortable, does he?”","Tooltip_BlueDogMachine_04");
        Tooltip_BlueDogMachine_05 = TextHandler.texter("“Not really. He might actually be enjoying it…”","Tooltip_BlueDogMachine_05");
        Tooltip_BlueDogMachine_06 = TextHandler.texter("Enjoy yourself — you’re definitely going to like him!","Tooltip_BlueDogMachine_06");

        //爱搬屎的猴子
        NameMonkeyShit = TextHandler.texter("Monkey Shit","NameMonkeyShit");
        Tooltip_MonkeyShit_MachineType = TextHandler.texter("Monkey Shit","Tooltip_MonkeyShit_MachineType");
        Tooltip_MonkeyShit_Controller = TextHandler.texter("Controller Block of Monkey Shit","Tooltip_MonkeyShit_Controller");
        Tooltip_MonkeyShit_00 = TextHandler.texter("The Relentless Poop Mover!","Tooltip_MonkeyShit_00");
        Tooltip_MonkeyShit_01 = TextHandler.texter("Seriously?! Where on earth did he get all this crap!?","Tooltip_MonkeyShit_01");
        Tooltip_MonkeyShit_02 = TextHandler.texter("At times, he walks near the gods... yet strays far from mankind.","Tooltip_MonkeyShit_02");
        Tooltip_MonkeyShit_03 = TextHandler.texter("“Please, post something else, I’m begging you!”","Tooltip_MonkeyShit_03");
        Tooltip_MonkeyShit_04 = TextHandler.texter("F*** it, you might as well post crap instead of this nonsense!","Tooltip_MonkeyShit_04");
        Tooltip_MonkeyShit_05 = TextHandler.texter("Just pour some smelly liquid into the machine — who knows what kind of surprise you’ll get!","Tooltip_MonkeyShit_05");

        EOHB_Harmony = TextHandler.texter("Harmony","EOHB_Harmony");
        EOHB_Harmony_Tooltip = TextHandler.texter("Harmony","EOHB_Harmony");
        EOHB_ChengDuHeart = TextHandler.texter("Heart Of ChengDu","chengdu_heart");
        EOHB_ChengDuHeart_Tooltip_01 = TextHandler.texter("A... thing? Or maybe a gem? Looks pretty valuable","EOHB_ChengDuHeart_Tooltip_01");
        EOHB_ChengDuHeart_Tooltip_02 = TextHandler.texter("and judging by where it came from… yeah, let’s not think too hard about that.","EOHB_ChengDuHeart_Tooltip_02");
    }
}
