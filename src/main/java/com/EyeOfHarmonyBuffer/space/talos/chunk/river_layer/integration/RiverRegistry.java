package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.Rvr2Loader;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.RiverTemplate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.RiverTemplateFactory;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.MODID;

public final class RiverRegistry {

    private static final Logger LOGGER = LogManager.getLogger("RiverRegistry");

    private static Map<Long, RiverNetwork> NETWORKS_BY_SEED = Collections.emptyMap();
    private static Map<String, RiverTemplate> TEMPLATES_BY_ID = Collections.emptyMap();

    private RiverRegistry() {}

    public static void onPreInit(FMLPreInitializationEvent event) {
        try {
            loadAllFromOwnJar(event);
        } catch (IOException e) {
            LOGGER.error("Failed to load river networks", e);
        }
    }

    private static void loadAllFromOwnJar(FMLPreInitializationEvent event) throws IOException {
        java.io.File jarFile = event.getSourceFile();
        if (jarFile == null || !jarFile.isFile()) {
            LOGGER.warn("Source file is not a jar, river networks will not be loaded automatically: {}", jarFile);
            NETWORKS_BY_SEED = Collections.emptyMap();
            TEMPLATES_BY_ID  = Collections.emptyMap();
            return;
        }

        String basePath = "data/" + MODID + "/worldgen/river/";

        Map<Long, RiverNetwork> networksBySeed = new HashMap<Long, RiverNetwork>();
        Map<String, RiverTemplate> templatesById = new HashMap<String, RiverTemplate>();

        ZipFile zip = new ZipFile(jarFile);
        try {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith(basePath) || !name.endsWith(".rvr")) {
                    continue;
                }

                LOGGER.info("Found river network resource: {}", name);

                InputStream raw = zip.getInputStream(entry);
                if (raw == null) {
                    LOGGER.warn("Unable to open river resource: {}", name);
                    continue;
                }

                BufferedInputStream buf = new BufferedInputStream(raw);
                RiverNetwork network;
                try {
                    network = Rvr2Loader.load(buf);
                } catch (Exception ex) {
                    LOGGER.error("Error loading river network from " + name, ex);
                    try {
                        buf.close();
                    } catch (IOException ignored) {}
                    continue;
                } finally {
                    try {
                        buf.close();
                    } catch (IOException ignored) {}
                }

                long seed = network.getSeed();
                RiverNetwork previous = networksBySeed.put(seed, network);
                if (previous != null) {
                    LOGGER.warn("Duplicate river network for seed {}: {} and another entry, overriding", seed, name);
                } else {
                    LOGGER.info("Loaded river network for seed {} from {}", seed, name);
                }

                String fileName = name.substring(basePath.length());
                String templateId = fileName.substring(0, fileName.length() - 4);

                RiverTemplate tpl = RiverTemplateFactory.fromNetwork(network);
                RiverTemplate prevTpl = templatesById.put(templateId, tpl);
                if (prevTpl != null) {
                    LOGGER.warn("Duplicate river templateId {} from {}", templateId, name);
                } else {
                    LOGGER.info("Created river template {} from {}", templateId, name);
                }
            }
        } finally {
            zip.close();
        }

        NETWORKS_BY_SEED = Collections.unmodifiableMap(networksBySeed);
        TEMPLATES_BY_ID  = Collections.unmodifiableMap(templatesById);

        LOGGER.info("Loaded {} river network(s) and {} river template(s) from {}",
            NETWORKS_BY_SEED.size(),
            TEMPLATES_BY_ID.size(),
            jarFile.getName()
        );
    }

    /**
     * 根据世界 seed 获取对应的河网，如果找不到返回 null。
     */
    public static RiverNetwork getNetworkForSeed(long seed) {
        return NETWORKS_BY_SEED.get(seed);
    }

    /**
     * 方便调试：直接拿到全部已加载的网络。
     */
    public static Map<Long, RiverNetwork> getAllNetworks() {
        return NETWORKS_BY_SEED;
    }

    public static RiverTemplate getTemplate(String id) {
        return TEMPLATES_BY_ID.get(id);
    }

    public static java.util.Collection<String> getAllTemplateIds() {
        return TEMPLATES_BY_ID.keySet();
    }
}
