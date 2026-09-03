package com.EyeOfHarmonyBuffer.common.Block.Arknights.fluids;

import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.fluids.Fluid;

public class EOHBFluidBlockRegistry {

    public static BlockPrecipitationAcid precipitationAcidBlock;

    public static void registerFluidBlocks() {
        Fluid fluid = EOHBMaterialPool.PrecipitationAcid
            .getFluidOrGas(1)
            .getFluid();

        if (fluid == null) {
            throw new RuntimeException(
                "PrecipitationAcid fluid not found! Bartworks 流体未注册完成。");
        }

        if (fluid.getBlock() == null) {
            precipitationAcidBlock = new BlockPrecipitationAcid(fluid);
            precipitationAcidBlock.setBlockName("fluid.precipitationacid");
            GameRegistry.registerBlock(
                precipitationAcidBlock, "fluid_precipitationacid");
            fluid.setBlock(precipitationAcidBlock);
        }
    }
}
