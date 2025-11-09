package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.BlockClass.Casings.SingularityStabilizationRingCasings;
import net.minecraft.block.Block;

public class BasicBlocks {

    public static final Block MetaBlock01 = new BlockBase01("MetaBlock01", "MetaBlock01");

    public static final Block SingularityStabilizationRingCasingsUpgrade = new SingularityStabilizationRingCasings(
        "SingularityStabilizationRingCasings",
        "Singularity Stabilization Ring Casings");
}
