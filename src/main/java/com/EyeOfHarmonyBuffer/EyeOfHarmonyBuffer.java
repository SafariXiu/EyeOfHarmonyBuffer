package com.EyeOfHarmonyBuffer;

import java.io.File;
import java.util.List;

import com.EyeOfHarmonyBuffer.command.CommandReloadConfig;
import com.EyeOfHarmonyBuffer.command.CommandShowConfigLinks;
import com.EyeOfHarmonyBuffer.Config.ItemConfig;
import com.EyeOfHarmonyBuffer.Loader.LazyStaticsInitLoader;
import com.EyeOfHarmonyBuffer.Loader.MachineLoader;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Loader.MaterialLoader;
import com.EyeOfHarmonyBuffer.Loader.SpaceModuleRecipeLoader;
import com.EyeOfHarmonyBuffer.Recipe.AssemblyLineRecipesLoad;
import com.EyeOfHarmonyBuffer.client.ClientJoinWorldHandler;
import com.EyeOfHarmonyBuffer.client.CommandOpenConfig;
import com.EyeOfHarmonyBuffer.utils.FoodHelper;
import com.EyeOfHarmonyBuffer.utils.GemErgodic;
import com.EyeOfHarmonyBuffer.Recipe.RecipeLoader;
import com.EyeOfHarmonyBuffer.utils.TextHandler;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.EyeOfHarmonyBuffer.Config.Config;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = EyeOfHarmonyBuffer.MODID,
    name = "EyeOfHarmonyBuffer",
    dependencies = "required-after:gtnhintergalactic;required-after:gregtech;",
    acceptedMinecraftVersions = "[1.7.10]")
public class EyeOfHarmonyBuffer {
    public static Configuration config;

    public static final boolean isInDevMode = false;

    public static final Logger LOG = LogManager.getLogger("EOHBuffer");
    public static String DevResource = "";

    public final GemErgodic gemErgodic = new GemErgodic();

    public static final String MODID = "eyeofharmonybuffer";

    @SidedProxy(clientSide = "com.EyeOfHarmonyBuffer.ClientProxy", serverSide = "com.EyeOfHarmonyBuffer.CommonProxy")

    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {

        File configDir = new File(event.getModConfigurationDirectory(), "EyeOfHarmonyBuffer");
        TextHandler.initLangMap(isInDevMode);

        if(MainConfig.Grade2WaterPurificationEnabled){
            Launch.classLoader.registerTransformer("com.EyeOfHarmonyBuffer.ASMChange.Grade2WaterPurificationRecipeChange");
        }

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File mainConfigFile = new File(configDir, "main.cfg");
        File itemsConfigFile = new File(configDir, "items.cfg");
        File fluidsConfigFile = new File(configDir, "fluids.cfg");
        File MachineLoaderConfigFile = new File(configDir, "MachineLoaderConfig.cfg");

        Config.init(mainConfigFile, itemsConfigFile, fluidsConfigFile, MachineLoaderConfigFile);

        MaterialLoader.loadPreInit();

        proxy.preInit(event);

        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new ClientJoinWorldHandler());
        }
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        MachineLoader.loadMachines();
        proxy.registerRenderers();
        proxy.registerTileEntitySpecialRenderer();
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        TextHandler.initLangMap(isInDevMode);

        RecipeLoader.loadRecipes();
        RecipeLoader.registerRecipes();
        AssemblyLineRecipesLoad.RecipeLoad();

        new SpaceModuleRecipeLoader().run();
    }

    @Mod.EventHandler
    public void completeInit(FMLServerStartingEvent event) {
        new LazyStaticsInitLoader().initStaticsOnCompleteInit();
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        ItemConfig.setGemErgodic(gemErgodic);
        GemErgodic.processOreDictionary();
        ItemConfig.reloadConfig();

        List<ItemStack> foods = FoodHelper.getAllFoods();
        FMLLog.info("[EyeOfHarmonyBuffer] Found %d food items", foods.size());
        RecipeLoader.loadRecipesLate();

        event.registerServerCommand(new CommandReloadConfig());
        event.registerServerCommand(new CommandShowConfigLinks());
        event.registerServerCommand(new CommandOpenConfig());
    }

    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {

    }

}
