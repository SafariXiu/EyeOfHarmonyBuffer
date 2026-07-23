package com.EyeOfHarmonyBuffer;

import java.io.File;
import java.util.List;

import com.EyeOfHarmonyBuffer.Loader.*;
import com.EyeOfHarmonyBuffer.Recipe.RemoverRecipe;
import com.EyeOfHarmonyBuffer.client.renderer.block.OverdomainFogHandler;
import com.EyeOfHarmonyBuffer.command.*;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.botany.BlockIntermediateResources;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.fluids.EOHBFluidBlockRegistry;
import com.EyeOfHarmonyBuffer.common.Block.ArknightsBlockRegister;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityOverdomainErosion;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemIntermediateProducts;
import com.EyeOfHarmonyBuffer.common.misc.GlobalOrundumWorldSavedData;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessTickHandler;
import com.EyeOfHarmonyBuffer.example.ExampleQuestRegistration;
import com.EyeOfHarmonyBuffer.handler.AutoHealHandler;
import com.EyeOfHarmonyBuffer.handler.AutoInstantHealHandler;
import com.EyeOfHarmonyBuffer.handler.CommonEventHandler;
import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import com.EyeOfHarmonyBuffer.Config.ItemConfig;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Recipe.AssemblyLineRecipesLoad;
import com.EyeOfHarmonyBuffer.client.ClientJoinWorldHandler;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.RiverRegistry;
import com.EyeOfHarmonyBuffer.utils.FoodHelper;
import com.EyeOfHarmonyBuffer.utils.GemErgodic;
import com.EyeOfHarmonyBuffer.Loader.RecipeLoader;
import com.EyeOfHarmonyBuffer.utils.TextHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

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

    public static final Logger LOGGER = LogManager.getLogger("EOHBuffer");
    public static String DevResource = "";

    public final GemErgodic gemErgodic = new GemErgodic();

    public static final String MODID = "eyeofharmonybuffer";

    @Mod.Instance(EyeOfHarmonyBuffer.MODID)
    public static EyeOfHarmonyBuffer instance;

    @SidedProxy(clientSide = "com.EyeOfHarmonyBuffer.ClientProxy", serverSide = "com.EyeOfHarmonyBuffer.CommonProxy")

    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {

        RiverRegistry.onPreInit(event);

        File configDir = new File(event.getModConfigurationDirectory(), "EyeOfHarmonyBuffer");
        TextHandler.initLangMap(isInDevMode);

        if(MainConfig.Grade2WaterPurificationEnabled){
            Launch.classLoader.registerTransformer("com.EyeOfHarmonyBuffer.ASMChange.Grade2WaterPurificationRecipeChange");
        }

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        /*File mainConfigFile = new File(configDir, "main.cfg");
        File itemsConfigFile = new File(configDir, "items.cfg");
        File fluidsConfigFile = new File(configDir, "fluids.cfg");
        File MachineLoaderConfigFile = new File(configDir, "MachineLoaderConfig.cfg");
        File FieldManagerCacheConfig = new File(configDir, "FieldManagerConfigSpec.cfg");

        Config.init(mainConfigFile, itemsConfigFile, fluidsConfigFile, MachineLoaderConfigFile, FieldManagerCacheConfig);*/

        MaterialLoader.loadPreInit();

        proxy.preInit(event);

        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new ClientJoinWorldHandler());
        }

        ItemIntermediateProducts.initAndRegister(MODID);
        BlockIntermediateResources.registerAll(MODID);

        GameRegistry.registerTileEntity(TileEntityOverdomainErosion.class, "tile_overdomain_erosion");

        TalosBiomes.init();
        GTUtility.addTexturePage((byte) 30);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);

        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new WirelessTickHandler());

        EntityLoader.registerEntities();

        MachineLoader.loadMachines();
        proxy.registerRenderers();
        proxy.registerTileEntitySpecialRenderer();
        MinecraftForge.EVENT_BUS.register(new GlobalOrundumWorldSavedData());

        ExampleQuestRegistration.registerAll();

        RegisterDimensions.init();

        MinecraftForge.EVENT_BUS.register(new OverdomainFogHandler());
        MinecraftForge.EVENT_BUS.register(new CommonEventHandler());
        MinecraftForge.EVENT_BUS.register(new AutoHealHandler());
        MinecraftForge.EVENT_BUS.register(new AutoInstantHealHandler());

        ArknightsBlockRegister.registryCasingBlocks();
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        TextHandler.initLangMap(isInDevMode);

        RecipeLoader.loadRecipes();
        RecipeLoader.registerRecipes();
        AssemblyLineRecipesLoad.RecipeLoad();
        EOHBFluidBlockRegistry.registerFluidBlocks();

        new SpaceModuleRecipeLoader().run();

        RemoverRecipe.run();
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
        event.registerServerCommand(new CommandOrundum());
        event.registerServerCommand(new CommandShowConfigLinks());
        event.registerServerCommand(new CommandTalosRiverNearest());
        event.registerServerCommand(new CommandTalosHere());
        event.registerServerCommand(new CommandReactorVideo());
        event.registerServerCommand(new CommandGasEnvironment());
        event.registerServerCommand(new CommandComputeGroup());
        event.registerServerCommand(new CommandComputeDebug());
        event.registerServerCommand(new CommandTalosSuperCenter());
    }

    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {

    }

}
