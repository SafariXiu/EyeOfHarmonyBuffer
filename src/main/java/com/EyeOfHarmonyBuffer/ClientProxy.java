package com.EyeOfHarmonyBuffer;

import com.EyeOfHarmonyBuffer.client.CommandOpenConfig;
import com.EyeOfHarmonyBuffer.client.ExternalBlockTextures;
import com.EyeOfHarmonyBuffer.client.renderer.block.RenderOverdomainEndStyle;
import com.EyeOfHarmonyBuffer.client.renderer.block.TileEntityWindmillRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.item.ItemUnactivatedYuanShiRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.item.ItemYuanShiRenderer;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemUnactivatedYuanShi;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemYuanShi;
import com.EyeOfHarmonyBuffer.example.tile.TileEntityOverdomainErosion;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.MinecraftForgeClient;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWindmill.class, new TileEntityWindmillRenderer());

        MinecraftForgeClient.registerItemRenderer(ItemYuanShi.instance,new ItemYuanShiRenderer());
        MinecraftForgeClient.registerItemRenderer(ItemUnactivatedYuanShi.instance,new ItemUnactivatedYuanShiRenderer());

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityOverdomainErosion.class, new RenderOverdomainEndStyle());
    }

    @Override
    public void registerTileEntitySpecialRenderer() {
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ExternalBlockTextures.register();

        ClientCommandHandler.instance.registerCommand(new CommandOpenConfig());
    }
}
