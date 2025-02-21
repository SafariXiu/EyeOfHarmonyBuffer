package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

public class BlockRegister {

    public static Block TrubineBlock;

    public static void registryBlocks(){
        TrubineBlock = new BlockWindmill().setBlockName("blockWindmill");
        GameRegistry.registerBlock(TrubineBlock, "blockWindmill");
        GameRegistry.registerTileEntity(TileEntityWindmill.class, "Windmill_TE");
    }

    public static void registryBlockContainers(){

    }

    public static void registry() {
        registryBlocks();
        registryBlockContainers();
    }
}
