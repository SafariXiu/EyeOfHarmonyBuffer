package com.EyeOfHarmonyBuffer.Loader;

import bartworks.API.WerkstoffAdderRegistry;
import com.EyeOfHarmonyBuffer.common.Block.ArknightsBlockRegister;
import com.EyeOfHarmonyBuffer.common.Block.BlockRegister;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;

public class MaterialLoader {

    public static void loadPreInit() {
        BlockRegister.registry();
        ArknightsBlockRegister.registry();

        WerkstoffAdderRegistry.addWerkstoffAdder(new EOHBMaterialPool());
    }

    public static void loadCompleteInit() {

    }
}
