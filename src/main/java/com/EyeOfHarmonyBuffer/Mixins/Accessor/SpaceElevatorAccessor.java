package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import gtnhintergalactic.spaceprojects.ProjectAsteroidOutpost;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import tectech.thing.metaTileEntity.multi.base.Parameters;

@Mixin(value = TileEntityModuleMiner.class, remap = false)
public interface SpaceElevatorAccessor {

    @Accessor("asteroidOutpost")
    ProjectAsteroidOutpost getAsteroidOutpost();

    @Accessor("parallelSetting")
    Parameters.Group.ParameterIn getParallelSetting();

}
