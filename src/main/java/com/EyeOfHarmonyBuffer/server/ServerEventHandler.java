package com.EyeOfHarmonyBuffer.server;

import com.EyeOfHarmonyBuffer.network.EOHNetwork;
import com.EyeOfHarmonyBuffer.network.packet.PacketSyncStatus;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ServerEventHandler {

    private final Set<UUID> sentToPlayers = new HashSet<UUID>();

    public static void onServerStarting(FMLServerStartingEvent event) {
        System.out.println("[EOH] ServerEventHandler.onServerStarting called");
        EOHItemTableManager.resetOnServerStart();
    }

    public void resetSentPlayers() {
        sentToPlayers.clear();
        System.out.println("[EOH] ServerEventHandler.resetSentPlayers called");
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof EntityPlayerMP)) return;

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UUID uuid = player.getUniqueID();

        if (sentToPlayers.contains(uuid)) {
            return;
        }

        sentToPlayers.add(uuid);

        boolean init = EOHItemTableManager.isInitialized();
        boolean allow = EOHItemTableManager.isClientInitAllowed();

        System.out.println("[EOH] onPlayerTick: send PacketSyncStatus to "
            + player.getCommandSenderName()
            + ", init=" + init + ", allow=" + allow);

        EOHNetwork.NETWORK.sendTo(new PacketSyncStatus(init, allow), player);
    }
}
