package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.world.World;

/** RBMK 跳舞同步通道。 */
public final class RbmkDanceNetwork {

    public static final SimpleNetworkWrapper INSTANCE =
        NetworkRegistry.INSTANCE.newSimpleChannel("EOHB|RbmkDance");

    private RbmkDanceNetwork() {
    }

    public static void init() {
        INSTANCE.registerMessage(
            PacketRbmkDance.class,
            PacketRbmkDance.class,
            0,
            Side.CLIENT);
    }

    /** 服务端：广播当前跳舞轮次（多套）到该维度所有客户端。 */
    public static void sendWindow(World world, RbmkDanceDriver driver) {
        if (world == null || world.isRemote || driver.getSubWaves().isEmpty()) {
            return;
        }
        PacketRbmkDance packet = new PacketRbmkDance(driver.getSubWaves());
        INSTANCE.sendToDimension(packet, world.provider.dimensionId);
    }
}
