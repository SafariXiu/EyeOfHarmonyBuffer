package com.EyeOfHarmonyBuffer.space.talos.chunk.coastline;

public final class CoastlineSample {

    private final boolean land;
    private final int distanceToCoast;
    private final int beachWidth;
    private final int shelfWidth;

    public CoastlineSample(boolean land, int distanceToCoast, int beachWidth, int shelfWidth) {
        this.land = land;
        this.distanceToCoast = distanceToCoast;
        this.beachWidth = beachWidth;
        this.shelfWidth = shelfWidth;
    }

    public boolean isLand() { return land; }
    public int distanceToCoast() { return distanceToCoast; }
    public int beachWidth() { return beachWidth; }
    public int shelfWidth() { return shelfWidth; }
}
