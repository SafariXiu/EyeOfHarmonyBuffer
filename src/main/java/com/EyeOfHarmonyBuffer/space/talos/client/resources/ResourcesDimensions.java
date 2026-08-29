package com.EyeOfHarmonyBuffer.space.talos.client.resources;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.util.ResourceLocation;

public interface ResourcesDimensions {

    ResourceLocation TalosGasGiant = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/talos_gasgiant.png"
    );

    /** 塔罗斯-1 天空专用贴图（1024×1024，空间站视角的巨行星全景）。 */
    ResourceLocation TalosSky = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/Talos_Sky.png"
    );

    ResourceLocation Talos2Moon = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/talos2.png"
    );

    ResourceLocation TalosStar = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/talos_star.png"
    );

    ResourceLocation White = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/white.png"
    );

    /** 塔罗斯-1 空间站图标（取自 GC 原版空间站图标）。 */
    ResourceLocation SpaceStation = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/spaceStation.png"
    );
}
