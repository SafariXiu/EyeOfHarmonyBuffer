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
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings LV Tier", 0));
        GTCMItemList.SingularityStabilizationRingCasingsMV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings MV Tier", 1));
        GTCMItemList.SingularityStabilizationRingCasingsHV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings HV Tier", 2));
        GTCMItemList.SingularityStabilizationRingCasingsEV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings EV Tier", 3));
        GTCMItemList.SingularityStabilizationRingCasingsIV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings IV Tier", 4));
        GTCMItemList.SingularityStabilizationRingCasingsLuV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings LuV Tier", 5));
        GTCMItemList.SingularityStabilizationRingCasingsZPM
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings ZPM Tier", 6));
        GTCMItemList.SingularityStabilizationRingCasingsUV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UV Tier", 7));
        GTCMItemList.SingularityStabilizationRingCasingsUHV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UHV Tier", 8));
        GTCMItemList.SingularityStabilizationRingCasingsUEV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UEV Tier", 9));
        GTCMItemList.SingularityStabilizationRingCasingsUIV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UIV Tier", 10));
        GTCMItemList.SingularityStabilizationRingCasingsUMV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UMV Tier", 11));
        GTCMItemList.SingularityStabilizationRingCasingsUXV
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UXV Tier", 12));
        GTCMItemList.SingularityStabilizationRingCasingsMAX
            .set(SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings MAX Tier", 13));
    }

    public static void registry() {
        registryBlocks();
        registryBlockContainers();
    }
}
