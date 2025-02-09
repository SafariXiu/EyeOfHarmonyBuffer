package com.EyeOfHarmonyBuffer;

import java.io.File;
import java.util.List;

import com.EyeOfHarmonyBuffer.Config.ItemConfig;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.utils.GemErgodic;
import com.EyeOfHarmonyBuffer.utils.RecipeLoader;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;

import com.EyeOfHarmonyBuffer.Config.Config;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = EyeOfHarmonyBuffer.MODID,
    name = "EyeOfHarmonyBuffer",
    dependencies = "required-after:gtnhintergalactic;required-after:gregtech;",
    acceptedMinecraftVersions = "[1.7.10]")
public class EyeOfHarmonyBuffer {

    public final GemErgodic gemErgodic = new GemErgodic();

    public static final String MODID = "EyeOfHarmonyBuffer";

    @SidedProxy(clientSide = "com.EyeOfHarmonyBuffer.ClientProxy", serverSide = "com.EyeOfHarmonyBuffer.CommonProxy")
    public static CommonProxy proxy;

    public static Configuration config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {

        File configDir = new File(event.getModConfigurationDirectory(), "EyeOfHarmonyBuffer");

        if(MainConfig.Grade2WaterPurificationEnabled){
            Launch.classLoader.registerTransformer("com.EyeOfHarmonyBuffer.ASMChange.Grade2WaterPurificationRecipeChange");
        }

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File mainConfigFile = new File(configDir, "main.cfg");
        File itemsConfigFile = new File(configDir, "items.cfg");
        File fluidsConfigFile = new File(configDir, "fluids.cfg");

        Config.init(mainConfigFile, itemsConfigFile, fluidsConfigFile);

        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);

        gemErgodic.init(event);
        ItemConfig.setGemErgodic(gemErgodic);
        ItemConfig.reloadConfig();
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        RecipeLoader.loadRecipes();
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandReloadConfig());
    }

}
