package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.common.item.ItemLoader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class AutoHealHandler {

    private static final float HEALTH_THRESHOLD = 6.0F;

    private static final int REGEN_DURATION_TICKS = 20 * 10;
    private static final int REGEN_AMPLIFIER = 2;

    private static final long COOLDOWN_MS = 5000L;

    private static java.util.Map<Integer, Long> lastUseTime = new java.util.HashMap<Integer, Long>();

    @SubscribeEvent
    public void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.entityLiving;

        if (player.worldObj.isRemote) {
            return;
        }

        float currentHealth = player.getHealth();
        float damage = event.ammount;

        float healthAfterDamage = currentHealth - damage;

        if (healthAfterDamage > HEALTH_THRESHOLD) {
            return;
        }

        int id = player.getEntityId();
        long now = System.currentTimeMillis();
        Long lastObj = lastUseTime.get(id);
        long last = (lastObj == null) ? 0L : lastObj;
        if (now - last < COOLDOWN_MS) {
            return;
        }

        PotionEffect currentRegen = player.getActivePotionEffect(Potion.regeneration);
        if (currentRegen != null) {
            if (currentRegen.getAmplifier() >= REGEN_AMPLIFIER &&
                currentRegen.getDuration() >= REGEN_DURATION_TICKS / 2) {
                return;
            }
        }

        int slot = findYaZhenZhenJiInInventory(player);
        if (slot == -1) {
            return;
        }

        ItemStack stack = player.inventory.getStackInSlot(slot);
        stack.stackSize--;
        if (stack.stackSize <= 0) {
            player.inventory.setInventorySlotContents(slot, null);
        }

        lastUseTime.put(id, now);

        player.addPotionEffect(new PotionEffect(
            Potion.regeneration.id,
            REGEN_DURATION_TICKS,
            REGEN_AMPLIFIER,
            false
        ));
    }

    private int findYaZhenZhenJiInInventory(EntityPlayer player) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == ItemLoader.YaZhenZhenJi) {
                return i;
            }
        }
        return -1;
    }
}
