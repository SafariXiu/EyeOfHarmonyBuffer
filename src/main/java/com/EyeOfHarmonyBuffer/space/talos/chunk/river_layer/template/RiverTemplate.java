package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import java.util.List;

public final class RiverTemplate {
    public static final double WORLD_SIZE = 100000.0;

    public final long seed; // RVR2 里的 seed
    public final List<TemplateEdge> edges;
    public final List<TemplateBody> bodies;

    public RiverTemplate(long seed,
                         List<TemplateEdge> edges,
                         List<TemplateBody> bodies) {
        this.seed = seed;
        this.edges = java.util.Collections.unmodifiableList(edges);
        this.bodies = java.util.Collections.unmodifiableList(bodies);
    }
}
