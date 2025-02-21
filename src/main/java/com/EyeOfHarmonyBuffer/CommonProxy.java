package com.EyeOfHarmonyBuffer;

import java.io.File;

import com.EyeOfHarmonyBuffer.Config.Config;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {

        File configDir = new File(event.getModConfigurationDirectory(), "EyeOfHarmonyBuffer");

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File mainConfigFile = new File(configDir, "main.cfg");
        File itemsConfigFile = new File(configDir, "items.cfg");
        File fluidsConfigFile = new File(configDir, "fluids.cfg");
        File MachineLoaderConfigFile = new File(configDir, "MachineLoaderConfig.cfg");

        Config.init(mainConfigFile, itemsConfigFile, fluidsConfigFile,MachineLoaderConfigFile);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}

    public void registerRenderers() {
    }

    public void registerTileEntitySpecialRenderer() {
    }

}
