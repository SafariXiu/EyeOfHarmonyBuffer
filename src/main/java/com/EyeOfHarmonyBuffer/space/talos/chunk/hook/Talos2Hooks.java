package com.EyeOfHarmonyBuffer.space.talos.chunk.hook;

import com.EyeOfHarmonyBuffer.Events.ConfigReloadedEvent;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.BiomeDecisionStrategy;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.TalosFieldContextBootstrap;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Talos2Hooks {

    private static final ConcurrentMap<Integer, HookData> CONTEXTS = new ConcurrentHashMap<>();

    static {
        MinecraftForge.EVENT_BUS.register(new ReloadListener());
    }

    private Talos2Hooks() {}

    public static void register(int dim, FieldContext context) {
        Objects.requireNonNull(context, "context");
        replaceContext(dim, context, "register");
    }

    @Deprecated
    public static void register(int dim,
                                World world,
                                long seed,
                                Object legacyCoastline,
                                Object legacyMacroField,
                                Object legacyMacroConfig,
                                Object legacyMacroBuilder,
                                Object legacyTerrainNoise) {

        FieldContext context = TalosFieldContextBootstrap.create(world);
        replaceContext(dim, context, "register");
    }

    public static HookData resolve(World world) {
        Objects.requireNonNull(world, "world");
        return resolve(world.provider.dimensionId);
    }

    public static HookData resolveOrCreate(World world) {
        Objects.requireNonNull(world, "world");
        int dim = world.provider.dimensionId;
        HookData data = CONTEXTS.computeIfAbsent(dim,
            ignored -> new HookData(TalosFieldContextBootstrap.create(world)));
        log("resolveOrCreate", dim, data == null ? null : data.context());
        return data;
    }

    public static HookData resolve(int dim) {
        HookData data = CONTEXTS.get(dim);
        log("resolve", dim, data == null ? null : data.context());
        return data;
    }

    public static void unregister(int dim) {
        HookData removed = CONTEXTS.remove(dim);
        if (removed != null) {
            removed.dispose();
        }
        log("unregister", dim, removed == null ? null : removed.context());
    }

    private static void replaceContext(int dim, FieldContext context, String tag) {
        HookData previous = CONTEXTS.put(dim, new HookData(context));
        if (previous != null) {
            previous.dispose();
        }
        log(tag, dim, context);
    }

    private static void log(String tag, int dim, FieldContext context) {
        System.out.println("[Talos2Hooks] " + tag +
            " dim=" + dim +
            " context=" + (context == null ? "null"
            : (" fieldManager=" + System.identityHashCode(context.getFieldManager()) +
            " strategy=" + context.getStrategy().getMode() +
            " macroProvider=" + context.getMacroFieldProvider().getClass().getSimpleName() +
            " coastlineProvider=" + context.getCoastlineProvider().getClass().getSimpleName())));
    }

    public static final class ReloadListener {

        @SubscribeEvent
        public void onConfigReload(ConfigReloadedEvent event) {
            CONTEXTS.forEach((dim, hookData) -> {
                FieldContext oldContext = hookData.context();
                FieldManager oldManager = oldContext.getFieldManager();
                FieldDiagnostics diagnostics = hookData.diagnostics();

                if (diagnostics != null) {
                    System.out.println("[Talos2Hooks] reset diagnostics for dim " + dim);
                    diagnostics.resetMacroCacheStats();
                }

                if (oldManager != null) {
                    System.out.println("[Talos2Hooks] invalidate old fieldManager for dim " + dim);
                    oldManager.invalidateCaches();
                }

                World world = oldContext.getWorld();
                if (world == null) {
                    return;
                }

                FieldContext newContext = TalosFieldContextBootstrap.create(world);
                replaceContext(dim, newContext, "reload");
            });
        }
    }

    /**
     * 对外暴露的数据载体，包含 FieldContext 及常用引用。
     */
    public static final class HookData {

        private final FieldContext context;
        private final FieldManager fieldManager;
        private final IMacroCellProvider macroCellBuilder;
        private final FieldDiagnostics diagnostics;

        private HookData(FieldContext context) {
            this.context = Objects.requireNonNull(context, "context");
            this.fieldManager = context.getFieldManager();
            this.macroCellBuilder = fieldManager.getMacroProvider();
            this.diagnostics = context.getDiagnostics();
        }

        public FieldContext context() {
            return context;
        }

        public FieldManager fieldManager() {
            return fieldManager;
        }

        public IMacroCellProvider macroCellBuilder() {
            return macroCellBuilder;
        }

        public FieldDiagnostics diagnostics() {
            return diagnostics;
        }

        public BiomeDecisionStrategy strategy() {
            return context.getStrategy();
        }

        public CoastlineProvider coastlineProvider() {
            return context.getCoastlineProvider();
        }

        public void dispose() {
            context.dispose();
        }
    }
}
