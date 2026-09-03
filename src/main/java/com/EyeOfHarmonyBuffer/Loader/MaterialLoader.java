package com.EyeOfHarmonyBuffer.Loader;

import bartworks.API.WerkstoffAdderRegistry;
import com.EyeOfHarmonyBuffer.common.Block.EOHBBlockRegistry;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;

public class MaterialLoader {

    public static void loadPreInit() {
        EOHBBlockRegistry.registryBlocks();

        WerkstoffAdderRegistry.addWerkstoffAdder(new EOHBMaterialPool());
    }

    public static void loadCompleteInit() {

    }
}
