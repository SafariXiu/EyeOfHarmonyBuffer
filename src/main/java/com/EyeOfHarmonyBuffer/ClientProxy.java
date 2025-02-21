package com.EyeOfHarmonyBuffer;

import com.EyeOfHarmonyBuffer.client.TileEntityWindmillRenderer;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import cpw.mods.fml.client.registry.ClientRegistry;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWindmill.class, new TileEntityWindmillRenderer());
    }

    @Override
    public void registerTileEntitySpecialRenderer() {
    }

    @Override
    public void init() {

    }
}
