package com.EyeOfHarmonyBuffer.overdomain.entity;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

public class OverdomainErosionData implements IExtendedEntityProperties {

    public static final String IDENTIFIER = "OverdomainErosionData";

    private EntityLivingBase entity;

    private int erosionTicks = 0;

    private boolean inErosionThisTick = false;

    private long lastUpdateWorldTime = -1;

    public OverdomainErosionData(EntityLivingBase entity) {
        this.entity = entity;
    }

    public static void register(EntityLivingBase entity) {
        if (entity.getExtendedProperties(IDENTIFIER) == null) {
            entity.registerExtendedProperties(IDENTIFIER, new OverdomainErosionData(entity));
        }
    }

    public static OverdomainErosionData get(EntityLivingBase entity) {
        return (OverdomainErosionData) entity.getExtendedProperties(IDENTIFIER);
    }

    public void markInErosionThisTick() {
        this.inErosionThisTick = true;
    }

    public void onEntityUpdate() {
        if (entity.worldObj.isRemote) {
            return;
        }

        long time = entity.worldObj.getTotalWorldTime();
        if (time == lastUpdateWorldTime) {
            return;
        }
        lastUpdateWorldTime = time;

        if (inErosionThisTick) {

            applyErosionDebuffs(entity);

            erosionTicks++;

            if (erosionTicks >= 200) {

                if (entity instanceof EntityLivingBase) {
                    EntityLivingBase living = (EntityLivingBase) entity;

                    if (living instanceof EntityPlayer) {
                        EntityPlayer player = (EntityPlayer) living;
                        if (player.capabilities.isCreativeMode) {
                            erosionTicks = 0;
                            inErosionThisTick = false;
                            return;
                        }
                    }

                    if (isWearingFullInfinityArmor(living)) {
                        erosionTicks = 0;
                        inErosionThisTick = false;
                        return;
                    }

                    living.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);

                    if (!living.isDead && living.getHealth() > 0.0F) {
                        living.setHealth(0.0F);
                        living.onDeath(DamageSource.outOfWorld);
                    }

                    if (!living.isDead) {
                        living.setDead();
                    }
                }

                erosionTicks = 0;
            }
        } else {
            erosionTicks = 0;
        }

        inErosionThisTick = false;
    }

    private boolean isWearingFullInfinityArmor(EntityLivingBase living) {
        if (!(living instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) living;

        ItemStack boots = player.getCurrentArmor(0);
        ItemStack leggings = player.getCurrentArmor(1);
        ItemStack chest = player.getCurrentArmor(2);
        ItemStack helm = player.getCurrentArmor(3);

        if (boots == null || leggings == null || chest == null || helm == null) {
            return false;
        }

        return isInfinityItem(helm,   "Avaritia", "Infinity_Helm")  &&
            isInfinityItem(chest,  "Avaritia", "Infinity_Chest") &&
            isInfinityItem(leggings, "Avaritia", "Infinity_Pants") &&
            isInfinityItem(boots,  "Avaritia", "Infinity_Shoes");
    }

    private void applyErosionDebuffs(EntityLivingBase living) {
        living.motionX *= 0.3D;
        living.motionZ *= 0.3D;
        living.motionY *= 0.6D;

        if (living instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) living;

            if (player.capabilities.isCreativeMode) {
                return;
            }

            if (isWearingFullInfinityArmor(living)) {
                return;
            }

            player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 4, true));
            player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 40, 4, true));
            player.addPotionEffect(new PotionEffect(Potion.jump.id, 40, 250, true));
            player.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 1, true));

            if (player.capabilities.allowFlying || player.capabilities.isFlying) {
                player.capabilities.isFlying = false;
                player.capabilities.allowFlying = false;
                player.fallDistance = 0.0F;

                if (player.motionY > -0.2D) {
                    player.motionY = -0.2D;
                }

                player.sendPlayerAbilities();
            }

            player.motionX *= 0.2D;
            player.motionZ *= 0.2D;
        }
    }

    private boolean isInfinityItem(ItemStack stack, String modId, String name) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item == null) return false;

        GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(item);
        if (uid == null) return false;

        return uid.modId.equalsIgnoreCase(modId) && uid.name.equalsIgnoreCase(name);
    }

    @Override
    public void saveNBTData(NBTTagCompound compound) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("ErosionTicks", erosionTicks);
        compound.setTag(IDENTIFIER, tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        if (compound.hasKey(IDENTIFIER)) {
            NBTTagCompound tag = compound.getCompoundTag(IDENTIFIER);
            erosionTicks = tag.getInteger("ErosionTicks");
        }
    }

    @Override
    public void init(Entity entity, World world) {
    }
}
