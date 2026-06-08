package com.EyeOfHarmonyBuffer.handler;

import com.EyeOfHarmonyBuffer.common.item.ItemLoader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class AutoInstantHealHandler {

    private static final float HEALTH_THRESHOLD = 6.0F;
    private static final long COOLDOWN_MS = 5000L;

    private static java.util.Map<Integer, Long> lastUseTime = new java.util.HashMap<Integer, Long>();

    private static class InstantHealItemConfig {
        public final Item item;
        public final float healAmount;

        public InstantHealItemConfig(Item item, float healAmount) {
            this.item = item;
            this.healAmount = healAmount;
        }
    }

    private static final InstantHealItemConfig[] INSTANT_HEAL_ITEMS = new InstantHealItemConfig[] {
        new InstantHealItemConfig(ItemLoader.YouZhiJinCaoRuanYin, 20.0F),
        new InstantHealItemConfig(ItemLoader.JinCaoRuanYin, 16.0F),
        new InstantHealItemConfig(ItemLoader.JingXuanQiaoYuJiaoNang, 12F),
        new InstantHealItemConfig(ItemLoader.YouZhiQiaoYuJiaoNang, 8F),
        new InstantHealItemConfig(ItemLoader.QiaoYuJiaoNang, 4F),
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

        ItemUseResult useResult = findInstantHealItemInInventory(player);
        if (useResult == null) {
            return;
        }

        int slot = useResult.slot;
        InstantHealItemConfig config = useResult.config;

        ItemStack stack = player.inventory.getStackInSlot(slot);
        if (stack == null) {
            return;
        }

        stack.stackSize--;
        if (stack.stackSize <= 0) {
            player.inventory.setInventorySlotContents(slot, null);
        }

        lastUseTime.put(id, now);

        player.heal(config.healAmount);
    }

    private static class ItemUseResult {
        public final int slot;
        public final InstantHealItemConfig config;

        public ItemUseResult(int slot, InstantHealItemConfig config) {
            this.slot = slot;
            this.config = config;
        }
    }

    private ItemUseResult findInstantHealItemInInventory(EntityPlayer player) {
        for (InstantHealItemConfig config : INSTANT_HEAL_ITEMS) {
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
