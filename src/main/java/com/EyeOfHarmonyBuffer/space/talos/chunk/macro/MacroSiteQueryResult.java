package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

import java.util.Collections;
import java.util.List;

public final class MacroSiteQueryResult {

    private final List<MacroSiteManager.SiteHit> hits;
    private final MacroSite primary;
    private final MacroSite secondary;
    private final double primaryDistance;
    private final double secondaryDistance;

    public MacroSiteQueryResult(List<MacroSiteManager.SiteHit> hits) {
        this.hits = Collections.unmodifiableList(hits);
        this.primary = hits.get(0).site;
        this.primaryDistance = hits.get(0).dist;
        if (hits.size() > 1) {
            this.secondary = hits.get(1).site;
            this.secondaryDistance = hits.get(1).dist;
        } else {
            this.secondary = null;
            this.secondaryDistance = Double.POSITIVE_INFINITY;
        }
    }

    public List<MacroSiteManager.SiteHit> hits() {
        return hits;
    }

    public MacroSite primary() {
        return primary;
    }

    public MacroSite secondary() {
        return secondary;
    }

    public double primaryDistance() {
        return primaryDistance;
    }

    public double secondaryDistance() {
        return secondaryDistance;
    }

    public double edgeMetric() {
        return (secondaryDistance == Double.POSITIVE_INFINITY)
            ? Double.POSITIVE_INFINITY
            : secondaryDistance - primaryDistance;
    }
}
