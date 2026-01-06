package com.EyeOfHarmonyBuffer.space.talos.chunk.hook;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManagerFactory;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Talos2Hooks {

    private static final ConcurrentMap<Integer, HookData> CONTEXTS = new ConcurrentHashMap<>();

    private Talos2Hooks() {}

    public static void register(int dim, FieldContext context) {
        Objects.requireNonNull(context, "context");
        HookData previous = CONTEXTS.put(dim, new HookData(context));
        if (previous != null) {
            previous.dispose();
        }
        log("register", dim, context);
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

        FieldContext context = FieldManagerFactory.create(world);
        register(dim, context);
    }

    public static HookData resolve(World world) {
        Objects.requireNonNull(world, "world");
        return resolve(world.provider.dimensionId);
    }

    public static HookData resolveOrCreate(World world) {
        Objects.requireNonNull(world, "world");
        int dim = world.provider.dimensionId;
        HookData data = CONTEXTS.computeIfAbsent(dim,
            ignored -> new HookData(FieldManagerFactory.create(world)));
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

    private static void log(String tag, int dim, FieldContext context) {
        System.out.println("[Talos2Hooks] " + tag +
            " dim=" + dim +
            " context=" + (context == null ? "null"
            : (" fieldManager=" + System.identityHashCode(context.getFieldManager()) +
            " strategy=" + context.getStrategy().getMode() +
            " macroProvider=" + context.getMacroFieldProvider().getClass().getSimpleName() +
            " coastlineProvider=" + context.getCoastlineProvider().getClass().getSimpleName())));
    }

    /**
     * 对外暴露的数据载体，包含 FieldContext 及常用引用。
     */
    public static final class HookData {

        private final FieldContext context;
        private final FieldManager fieldManager;
        private final IMacroCellProvider macroCellBuilder;

        private HookData(FieldContext context) {
            this.context = Objects.requireNonNull(context, "context");
            this.fieldManager = context.getFieldManager();
            this.macroCellBuilder = fieldManager.getMacroProvider();
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

        public void dispose() {
            context.dispose();
        }
    }
}
