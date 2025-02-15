package com.EyeOfHarmonyBuffer.utils;

import net.minecraft.util.StatCollector;

public enum TextEnums {
    Author_Goderium("Author_Goderium"),
    Author_Taskeren("Author_Taskeren"),
    AddByTwistSpaceTechnology("AddByTwistSpaceTechnology"),
    SpeedMultiplier("MachineInfoData.SpeedMultiplier"),
    Parallels("MachineInfoData.Parallels"),
    EuModifier("MachineInfoData.EuModifier"),
    GlassTier("MachineInfoData.GlassTier"),
    CoilTier("MachineInfoData.CoilTier"),
    CurrentPowerConsumption("MachineInfoData.CurrentPowerConsumption"),
    MachineMode("MachineInfoData.MachineMode"),
    MachineTier("MachineInfoData.MachineTier"),
    FieldGeneratorTier("MachineInfoData.FieldGeneratorTier"),
    FusionCoilTier("MachineInfoData.FusionCoilTier"),
    CompactFusionCoilTier("MachineInfoData.CompactFusionCoilTier"),
    NameCoreDeviceOfHumanPowerGenerationFacility("NameCoreDeviceOfHumanPowerGenerationFacility"),
    Word_Parallel("Word_Parallel"),
    Word_Overclock("Word_Overclock"),
    RiseOfDarkFog("RiseOfTheDarkFog"),
    ModularizedMachineSystem("ModularizedMachineSystem"),
    InstallingModuleNearControllerImproveMachine("InstallingModuleNearControllerImproveMachine"),
    ModularizedMachineSystemDescription01("ModularizedMachineSystemDescription01"),
    ModularizedMachineSystemDescription02("ModularizedMachineSystemDescription02"),
    OverclockControllerDescription("OverclockControllerDescription"),
    ParallelControllerDescription("ParallelControllerDescription"),
    PowerConsumptionControllerDescription("PowerConsumptionControllerDescription"),
    SpeedControllerDescription("SpeedControllerDescription"),
    ExecutionCoreDescription("ExecutionCoreDescription"),
    NotMultiplyInstallSameTypeModule("NotMultiplyInstallSameTypeModule"),
    NotMultiplyInstallSameTypeModuleAll("NotMultiplyInstallSameTypeModuleAll"),
    CanMultiplyInstallSameTypeModule("CanMultiplyInstallSameTypeModule"),
    ModularHatch("ModularHatch"),
    BigBroArrayName("BigBroArray.name"),
    BigBroArrayType("BigBroArray.type"),
    BigBroArrayDesc1("BigBroArray.desc.1"),
    BigBroArrayDesc2("BigBroArray.desc.2"),
    BigBroArrayDesc3("BigBroArray.desc.3"),
    BigBroArrayDesc4("BigBroArray.desc.4"),
    BigBroArrayDesc5("BigBroArray.desc.5"),
    BigBroArrayDesc6("BigBroArray.desc.6"),
    BigBroArrayDesc7("BigBroArray.desc.7"),
    BigBroArrayDesc8("BigBroArray.desc.8"),
    BigBroArrayDesc9("BigBroArray.desc.9"),
    BigBroArrayDesc10("BigBroArray.desc.10"),
    BigBroArrayDesc11("BigBroArray.desc.11"),
    BigBroArrayDesc12("BigBroArray.desc.12"),
    StructureTooComplex("StructureTooComplex");

    private final String text;
    private final String key;

    public static String tr(String key) {
        return StatCollector.translateToLocalFormatted(key, new Object[0]);
    }

    private TextEnums(String key) {
        this.key = key;
        this.text = tr(key);
    }

    public String toString() {
        return this.text;
    }

    public String getKey() {
        return this.key;
    }

    public String getText() {
        return this.text;
    }
}
