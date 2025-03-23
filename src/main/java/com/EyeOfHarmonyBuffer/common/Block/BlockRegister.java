package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.BlockClass.Casings.SingularityStabilizationRing.SingularityStabilizationRingCasingsItemBlocks;
import com.EyeOfHarmonyBuffer.common.Block.BlockClass.Casings.SingularityStabilizationRingCasings;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

import static com.EyeOfHarmonyBuffer.common.Block.BasicBlocks.SingularityStabilizationRingCasingsUpgrade;

public class BlockRegister {

    public static Block TrubineBlock;

    public static void registryBlocks(){
        TrubineBlock = new BlockWindmill().setBlockName("blockWindmill");
        GameRegistry.registerBlock(TrubineBlock, "blockWindmill");
        GameRegistry.registerTileEntity(TileEntityWindmill.class, "Windmill_TE");

        GameRegistry.registerBlock(
            SingularityStabilizationRingCasingsUpgrade,
            SingularityStabilizationRingCasingsItemBlocks.class,
            SingularityStabilizationRingCasingsUpgrade.getUnlocalizedName());
    }

    public static void registryBlockContainers(){
        GTCMItemList.SingularityStabilizationRingCasingsLV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier LV Tier", 0));
        GTCMItemList.SingularityStabilizationRingCasingsMV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier MV Tier", 1));
        GTCMItemList.SingularityStabilizationRingCasingsHV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier HV Tier", 2));
        GTCMItemList.SingularityStabilizationRingCasingsEV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier EV Tier", 3));
        GTCMItemList.SingularityStabilizationRingCasingsIV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier IV Tier", 4));
        GTCMItemList.SingularityStabilizationRingCasingsLuV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier LuV Tier", 5));
        GTCMItemList.SingularityStabilizationRingCasingsZPM
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier ZPM Tier", 6));
        GTCMItemList.SingularityStabilizationRingCasingsUV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier UV Tier", 7));
        GTCMItemList.SingularityStabilizationRingCasingsUHV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier UHV Tier", 8));
        GTCMItemList.SingularityStabilizationRingCasingsUEV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier UEV Tier", 9));
        GTCMItemList.SingularityStabilizationRingCasingsUIV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier UIV Tier", 10));
        GTCMItemList.SingularityStabilizationRingCasingsUMV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier UMV Tier", 11));
        GTCMItemList.SingularityStabilizationRingCasingsUXV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier UXV Tier", 12));
        GTCMItemList.SingularityStabilizationRingCasingsMAX
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Photonic Intensifier MAX Tier", 13));
    }

    public static void registry() {
        registryBlocks();
        registryBlockContainers();
    }
}
