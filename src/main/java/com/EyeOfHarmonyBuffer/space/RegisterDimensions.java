package com.EyeOfHarmonyBuffer.space;

import micdoodle8.mods.galacticraft.api.GalacticraftRegistry;
import micdoodle8.mods.galacticraft.api.galaxies.*;
import micdoodle8.mods.galacticraft.api.vector.Vector3;
import com.EyeOfHarmonyBuffer.space.talos.WorldProviderTalos2;

import static com.EyeOfHarmonyBuffer.space.talos.ResourcesDimensions.*;

public class RegisterDimensions {

    public static SolarSystem talosSystem;
    public static Star talosStar;
    public static Planet talosGasGiant;
    public static Moon talos2;

    public static final int ID_TALOS2_DIM = 14001;
    public static final int tier_Talos2 = 4;

    public static void init() {

        talosSystem = new SolarSystem("talosSystem", "milkyWay")
            .setMapPosition(new Vector3(1.2, -0.6, 0.0));
        GalaxyRegistry.registerSolarSystem(talosSystem);

        talosStar = (Star) new Star("talosStar")
            .setParentSolarSystem(talosSystem)
            .setTierRequired(-1);
        talosStar.setBodyIcon(TalosStar);
        talosSystem.setMainStar(talosStar);

        talosGasGiant = new Planet("Talos")
            .setParentSolarSystem(talosSystem);
        talosGasGiant.setRingColorRGB(0.9F, 0.7F, 0.3F);
        //talosGasGiant.setTierRequired(999);
        talosGasGiant.setBodyIcon(TalosGasGiant);
        talosGasGiant.setRelativeDistanceFromCenter(new CelestialBody.ScalableDistance(0.8F, 0.8F));
        talosGasGiant.setRelativeOrbitTime(2.0F);
        talosGasGiant.setUnreachable();
        GalaxyRegistry.registerPlanet(talosGasGiant);

        talos2 = new Moon("Talos2")
            .setParentPlanet(talosGasGiant);
        talos2.setRingColorRGB(0.6F, 0.9F, 1.0F);
        talos2.setTierRequired(tier_Talos2);
        talos2.setBodyIcon(Talos2Moon);
        talos2.setRelativeDistanceFromCenter(new CelestialBody.ScalableDistance(10.0F, 10.0F));
        talos2.setRelativeOrbitTime(100.0F);
        talos2.setDimensionInfo(ID_TALOS2_DIM, WorldProviderTalos2.class);

        GalaxyRegistry.registerMoon(talos2);
        GalacticraftRegistry.registerTeleportType(WorldProviderTalos2.class, new WorldProviderTalos2());

        GalaxyRegistry.refreshGalaxies();
    }
}
