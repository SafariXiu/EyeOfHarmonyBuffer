package com.EyeOfHarmonyBuffer.entity.Arknights;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

public class EntityIndustrialExplosive extends EntityThrowable {

    public EntityIndustrialExplosive(World world) {
        super(world);
    }

    public EntityIndustrialExplosive(World world, EntityLivingBase thrower) {
        super(world, thrower);
    }

    private float getExplosionPower() {
        return 4.0F;
    }

    @Override
    protected void onImpact(MovingObjectPosition mop) {
        if (!this.worldObj.isRemote) {
            float power = getExplosionPower();

            this.worldObj.createExplosion(
                this,
                this.posX,
                this.posY,
                this.posZ,
                power,
                true
            );

            this.setDead();
        }
    }
}
