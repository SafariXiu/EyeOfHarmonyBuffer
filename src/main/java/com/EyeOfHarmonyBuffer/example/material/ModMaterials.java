package com.EyeOfHarmonyBuffer.example.material;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;

public class ModMaterials {

    public static final Material portalLiquid = new Material(MapColor.purpleColor) {
        @Override
        public boolean isLiquid() {
            return false;
        }

        @Override
        public boolean isSolid() {
            return false;
        }
    };
}
