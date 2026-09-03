package com.EyeOfHarmonyBuffer;

import codechicken.nei.api.API;
import com.EyeOfHarmonyBuffer.client.CommandOpenConfig;
import com.EyeOfHarmonyBuffer.client.ExternalBlockTextures;
import com.EyeOfHarmonyBuffer.client.OrundumConnectorHudHandler;
import com.EyeOfHarmonyBuffer.client.ReactorClientEventHandler;
import com.EyeOfHarmonyBuffer.client.orbitalrailgun.RailgunClientEvents;
import com.EyeOfHarmonyBuffer.client.renderer.block.RbmkFuelChannelBlockRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.block.RenderOverdomainEndStyle;
import com.EyeOfHarmonyBuffer.client.renderer.block.TileEntityForgeOfTheSkyCoreRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.block.TileEntityRbmkFuelChannelRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.block.TileEntityWindmillRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.item.ItemUnactivatedYuanShiRenderer;
import com.EyeOfHarmonyBuffer.client.renderer.item.ItemYuanShiRenderer;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockRBMKRod;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityForgeOfTheSkyCore;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityOverdomainErosion;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemBottleBase;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemUnactivatedYuanShi;
import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemYuanShi;
import com.EyeOfHarmonyBuffer.entity.Arknights.EntityIndustrialExplosive;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWindmill.class, new TileEntityWindmillRenderer());

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityForgeOfTheSkyCore.class, new TileEntityForgeOfTheSkyCoreRenderer());

        MinecraftForgeClient.registerItemRenderer(ItemYuanShi.instance,new ItemYuanShiRenderer());
        MinecraftForgeClient.registerItemRenderer(ItemUnactivatedYuanShi.instance,new ItemUnactivatedYuanShiRenderer());

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityOverdomainErosion.class, new RenderOverdomainEndStyle(GTCMItemList.OverdomainErosion.getBlock()));

        RenderingRegistry.registerEntityRenderingHandler(EntityIndustrialExplosive.class, new RenderSnowball(GTCMItemList.GongYeBaoZhaWu.getItem()));

        // RBMK 燃料通道：8 格成型后由基座 TESR 绘制整根 FuelTube 模型，方块本体不变
        BlockRBMKRod.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(BlockRBMKRod.renderId, RbmkFuelChannelBlockRenderer.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRbmkFuelChannel.class, new TileEntityRbmkFuelChannelRenderer());
        // RBMK 通道 TE 的渲染包围盒随跳舞棒位动态变化，注册为 DYNAMIC（Angelica 每帧重算，不做缓存）
        try {
            com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry
                .registerDynamicClass(TileEntityRbmkFuelChannel.class.getName());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void registerTileEntitySpecialRenderer() {
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ExternalBlockTextures.registerClientTextures();

        ClientCommandHandler.instance.registerCommand(new CommandOpenConfig());

        hideDisallowedBottleFluids(GTCMItemList.ChiTongFluidBottle.getItem());
        hideDisallowedBottleFluids(GTCMItemList.GangZhiFluidBottle.getItem());
        hideDisallowedBottleFluids(GTCMItemList.LanTieFluidBottle.getItem());
        hideDisallowedBottleFluids(GTCMItemList.ZiJingZhiFluidBottle.getItem());
        hideDisallowedBottleFluids(GTCMItemList.GaoJingFluidBottle.getItem());
        //hideDisallowedBottleFluids(GTCMItemList.HeTongFluidBottle.getItem()); // 未设计完成，暂不注册
        hideDisallowedBottleFluids(GTCMItemList.ChiTongGasTank.getItem());

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new OrundumConnectorHudHandler());
        com.EyeOfHarmonyBuffer.space.blackhole.client.SkyProviderEmeraldThrone.registerKeyHandler();
        ClientCommandHandler.instance.registerCommand(new com.EyeOfHarmonyBuffer.command.CommandBlackHolePreset());
        com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.client.RbmkHoloPoC.register();

        ReactorClientEventHandler handler = new ReactorClientEventHandler();
        FMLCommonHandler.instance().bus().register(handler);
        MinecraftForge.EVENT_BUS.register(handler);

        RailgunClientEvents railgunEvents = new RailgunClientEvents();
        FMLCommonHandler.instance().bus().register(railgunEvents);
        MinecraftForge.EVENT_BUS.register(railgunEvents);

        com.EyeOfHarmonyBuffer.client.transition.TransitionRenderEvents transitionEvents =
            new com.EyeOfHarmonyBuffer.client.transition.TransitionRenderEvents();
        FMLCommonHandler.instance().bus().register(transitionEvents);
        MinecraftForge.EVENT_BUS.register(transitionEvents);
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