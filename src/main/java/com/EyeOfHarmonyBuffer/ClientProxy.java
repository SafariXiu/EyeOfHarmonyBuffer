package com.EyeOfHarmonyBuffer;

import codechicken.nei.api.API;
import com.EyeOfHarmonyBuffer.client.CommandOpenConfig;
import com.EyeOfHarmonyBuffer.client.ExternalBlockTextures;
import com.EyeOfHarmonyBuffer.client.renderer.block.RenderOverdomainEndStyle;
import com.EyeOfHarmonyBuffer.client.renderer.block.TileEntityWindmillRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.item.ItemUnactivatedYuanShiRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.item.ItemYuanShiRenderer;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityOverdomainErosion;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import com.EyeOfHarmonyBuffer.common.item.ItemLoader;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemBottleBase;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemUnactivatedYuanShi;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemYuanShi;
import com.EyeOfHarmonyBuffer.entity.Arknights.EntityIndustrialExplosive;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.MinecraftForgeClient;

import static com.EyeOfHarmonyBuffer.common.Block.ArknightsBlockRegister.OverdomainErosion;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWindmill.class, new TileEntityWindmillRenderer());

        MinecraftForgeClient.registerItemRenderer(ItemYuanShi.instance,new ItemYuanShiRenderer());
        MinecraftForgeClient.registerItemRenderer(ItemUnactivatedYuanShi.instance,new ItemUnactivatedYuanShiRenderer());

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityOverdomainErosion.class, new RenderOverdomainEndStyle(OverdomainErosion));

        RenderingRegistry.registerEntityRenderingHandler(EntityIndustrialExplosive.class, new RenderSnowball(ItemLoader.GongYeBaoZhaWu));
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

        hideDisallowedBottleFluids(ItemLoader.ChiTongFluidBottle);
        hideDisallowedBottleFluids(ItemLoader.GangZhiFluidBottle);
        hideDisallowedBottleFluids(ItemLoader.LanTieFluidBottle);
        hideDisallowedBottleFluids(ItemLoader.ZiJingZhiFluidBottle);
        hideDisallowedBottleFluids(ItemLoader.GaoJingFluidBottle);
        hideDisallowedBottleFluids(ItemLoader.HeTongFluidBottle);
    }

    @SideOnly(Side.CLIENT)
    private void hideDisallowedBottleFluids(Item item) {
        if (!(item instanceof ItemBottleBase)) return;
        ItemBottleBase bottle = (ItemBottleBase) item;

        for (EnumBottleFluid fluid : EnumBottleFluid.values()) {
            if (fluid == EnumBottleFluid.EMPTY || !bottle.isFluidAllowed(fluid)) {
                API.hideItem(new ItemStack(item, 1, fluid.meta));
            }
        }
    }
}
