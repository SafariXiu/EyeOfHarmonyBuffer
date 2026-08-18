package com.EyeOfHarmonyBuffer.space.blackhole;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.util.ResourceLocation;

/** 黑洞星系天体图标与天空资源。 */
public interface ResourcesBlackHole {

    /** 黑洞（恒星）图标与天空贴图。 */
    ResourceLocation BlackHole = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/BlackHole.png"
    );

    /** 翡翠王座（类地行星）图标。 */
    ResourceLocation EmeraldThrone = new ResourceLocation(
        EyeOfHarmonyBuffer.MODID,
        "textures/gui/celestialbodies/talos/SiJiXingQiu.png"
    );
}
