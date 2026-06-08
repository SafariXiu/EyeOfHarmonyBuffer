package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.common.item.ItemLoader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class AutoHealHandler {

    private static final float HEALTH_THRESHOLD = 6.0F;
    private static final long COOLDOWN_MS = 5000L;

    private static java.util.Map<Integer, Long> lastUseTime = new java.util.HashMap<Integer, Long>();

    private static class RegenItemConfig {
        public final Item item;
        public final int durationTicks;
        public final int amplifier;

        public RegenItemConfig(Item item, int durationTicks, int amplifier) {
            this.item = item;
            this.durationTicks = durationTicks;
            this.amplifier = amplifier;
        }
    }

    private static final RegenItemConfig[] REGEN_ITEMS = new RegenItemConfig[] {
        new RegenItemConfig(ItemLoader.YouZhiYaZhenZhenJi, 20 * 20, 4),
        new RegenItemConfig(ItemLoader.YaZhenZhenJi, 20 * 10, 3),
        new RegenItemConfig(ItemLoader.JingXuanGanShiGuanTou,20 * 10,2),
        new RegenItemConfig(ItemLoader.YouZhiGanShiGuanTou,10 * 10,2),
        new RegenItemConfig(ItemLoader.GanShiGuanTou,10 * 10,1),
    };

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
            RegenItemConfig best = REGEN_ITEMS[0];
            if (currentRegen.getAmplifier() >= best.amplifier &&
                currentRegen.getDuration() >= best.durationTicks / 2) {
                return;
            }
        }

        ItemUseResult useResult = findRegenItemInInventory(player);
        if (useResult == null) {
            return;
        }

        int slot = useResult.slot;
        RegenItemConfig config = useResult.config;

        ItemStack stack = player.inventory.getStackInSlot(slot);
        if (stack == null) {
            return;
        }

        stack.stackSize--;
        if (stack.stackSize <= 0) {
            player.inventory.setInventorySlotContents(slot, null);
        }

        lastUseTime.put(id, now);

        player.addPotionEffect(new PotionEffect(
            Potion.regeneration.id,
            config.durationTicks,
            config.amplifier,
            false
        ));
    }

    private static class ItemUseResult {
        public final int slot;
        public final RegenItemConfig config;

        public ItemUseResult(int slot, RegenItemConfig config) {
            this.slot = slot;
            this.config = config;
        }
    }

    private ItemUseResult findRegenItemInInventory(EntityPlayer player) {
        for (RegenItemConfig config : REGEN_ITEMS) {
            int slot = findItemSlot(player, config.item);
            if (slot != -1) {
                return new ItemUseResult(slot, config);
            }
        }
        return null;
    }

    private int findItemSlot(EntityPlayer player, Item target) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == target) {
                return i;
            }
        }
        return -1;
    }
}
