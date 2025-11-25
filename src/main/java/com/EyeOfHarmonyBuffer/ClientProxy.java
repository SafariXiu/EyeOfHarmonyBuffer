package com.EyeOfHarmonyBuffer;

import com.EyeOfHarmonyBuffer.client.ExternalBlockTextures;
import com.EyeOfHarmonyBuffer.client.TileEntityWindmillRenderer;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWindmill.class, new TileEntityWindmillRenderer());
    }

    @Override
    public void registerTileEntitySpecialRenderer() {
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ExternalBlockTextures.register();
    }
}
