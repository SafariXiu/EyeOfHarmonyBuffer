package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.IIcon;

import java.util.HashMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class BlockStaticDataClientOnly {

    @SideOnly(Side.CLIENT)
    public static Map<Integer, IIcon> iconsBlockMap01 = new HashMap<>();
    @SideOnly(Side.CLIENT)
    public static Map<Integer, IIcon> iconsSingularityStabilizationRingCasingsUpgradeMap = new HashMap<>();
    @SideOnly(Side.CLIENT)
    public static Map<Integer, IIcon> iconsSpaceStationAntiGravityCasingMap = new HashMap<>();
    @SideOnly(Side.CLIENT)
    public static Map<Integer, IIcon> iconsSpaceStationStructureCasingMap = new HashMap<>();
    @SideOnly(Side.CLIENT)
    public static Map<Integer, IIcon> iconsNuclearReactor = new HashMap<>();
    public static Map<Integer, IIcon> iconsMetaBlockCasing01 = new HashMap<>(16);

}
