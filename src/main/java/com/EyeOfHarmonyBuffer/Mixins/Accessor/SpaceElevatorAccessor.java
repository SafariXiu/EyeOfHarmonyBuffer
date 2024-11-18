package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.gtnewhorizons.gtnhintergalactic.spaceprojects.ProjectAsteroidOutpost;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;

import tectech.thing.metaTileEntity.multi.base.Parameters;

@Mixin(value = TileEntityModuleMiner.class, remap = false)
public interface SpaceElevatorAccessor {

    @Accessor("asteroidOutpost")
    ProjectAsteroidOutpost getAsteroidOutpost();

    @Accessor("parallelSetting")
    Parameters.Group.ParameterIn getParallelSetting();

}
