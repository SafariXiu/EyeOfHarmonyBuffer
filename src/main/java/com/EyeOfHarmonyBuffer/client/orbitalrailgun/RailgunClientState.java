package com.EyeOfHarmonyBuffer.client.orbitalrailgun;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.common.item.itemadders.ItemOrbitalRailgun;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 轨道炮客户端状态机（移植自 orbital-railgun 的 RailgunState）。
 * 单例，由 ClientTickEvent 驱动；渲染层只读。
 */
public class RailgunClientState {

    /** 打击特效总时长（tick），与时间轴 4s 蓄力 + 32s 扩张 + 收尾对齐。 */
    public static final int STRIKE_MAX_TICKS = 1600;
    public static final float STRIKE_START_SECONDS = 4.0F;
    public static final float STRIKE_EXPANSION_SECONDS = 32.0F;
    public static final float STRIKE_END_SECONDS = STRIKE_START_SECONDS + STRIKE_EXPANSION_SECONDS;

    private static final RailgunClientState INSTANCE = new RailgunClientState();

    public static RailgunClientState getInstance() {
        return INSTANCE;
    }

    private RailgunClientState() {}

    // ---- 充能状态 ----
    private boolean charging;
    private int chargeTicks;
    private boolean fired;
    private boolean hasTarget;
    private int hitX;
    private int hitY;
    private int hitZ;

    // ---- 打击状态（多人并发：最多保留最近 8 个，后处理只渲染"自己的"主打击） ----
    public static final int MAX_CLIENT_STRIKES = 8;

    private final List<ClientStrike> strikes = new ArrayList<ClientStrike>();



    private long clientTick;

    public void tick(Minecraft mc) {
        clientTick++;

        EntityPlayer player = mc.thePlayer;
        boolean wasCharging = charging;
        charging = player != null && player.isUsingItem() && isHoldingRailgun(player);

        if (charging) {
            chargeTicks++;
            updateTarget(mc, player);
        } else {
            if (wasCharging) {
                // 充能中断：允许下次重新充能开火
                fired = false;
            }
            chargeTicks = 0;
            hasTarget = false;
        }

        // 过期打击清理（渲染线程独占列表）
        if (!strikes.isEmpty()) {
            Iterator<ClientStrike> it = strikes.iterator();
            while (it.hasNext()) {
                ClientStrike s = it.next();
                if (mc.theWorld == null || clientTick - s.startClientTick >= STRIKE_MAX_TICKS) {
                    it.remove();
                }
            }
        }
    }

    private boolean isHoldingRailgun(EntityPlayer player) {
        return player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemOrbitalRailgun;
    }

    private void updateTarget(Minecraft mc, EntityPlayer player) {
        MovingObjectPosition mop = player.rayTrace(MainConfig.OrbitalRailgunRange, 1.0F);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            hasTarget = true;
            hitX = mop.blockX;
            hitY = mop.blockY;
            hitZ = mop.blockZ;
        } else {
            hasTarget = false;
        }
    }

    // ---- 充能 ----

    public boolean isCharging() {
        return charging;
    }

    public int getChargeTicks() {
        return chargeTicks;
    }

    public boolean isReady() {
        return chargeTicks >= getChargeWarmupTicks();
    }

    public int getChargeWarmupTicks() {
        return Math.max(1, MainConfig.OrbitalRailgunWarmupTicks);
    }

    public boolean isFired() {
        return fired;
    }

    public void markFired() {
        fired = true;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    public int getHitX() {
        return hitX;
    }

    public int getHitY() {
        return hitY;
    }

    public int getHitZ() {
        return hitZ;
    }

    // ---- 打击（多人并发：列表 + 归属分流） ----

    /**
     * 收到服务端打击广播：加入列表。
     * 归属判断：自己发起 或 同队发起（机器打击带队伍）→ 全特效候选；其余只显示几何特效。
     */
    public void onStrikeStarted(int x, int y, int z, float radius, UUID shooterUuid, UUID teamId) {
        boolean own = resolveOwn(shooterUuid, teamId);
        strikes.add(new ClientStrike(x, y, z, radius, clientTick, own));
        while (strikes.size() > MAX_CLIENT_STRIKES) {
            strikes.remove(0);
        }
    }

    /** 归属判断：本地玩家自己发起，或（客户端可解析队伍时）同队发起。 */
    private boolean resolveOwn(UUID shooterUuid, UUID teamId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return true;
        }
        UUID me = mc.thePlayer.getUniqueID();
        if (shooterUuid != null && me.equals(shooterUuid)) {
            return true;
        }
        if (teamId != null) {
            try {
                UUID myTeam = OrundumEnergyService.getTeamIdForUser(me);
                if (myTeam != null && myTeam.equals(teamId)) {
                    return true;
                }
            } catch (Throwable t) {
                // 客户端队伍数据不可用时忽略，回退为非自己
            }
        }
        return false;
    }

    /** 全部进行中的打击（几何特效逐个渲染）。 */
    public List<ClientStrike> getStrikes() {
        return strikes;
    }

    /**
     * 主打击（后处理链专用）：最近一个"自己的"打击。
     * 无自己的打击时返回 null（后处理不渲染，避免把别人的打击特效占为己有）。
     */
    public ClientStrike getPrimaryStrike() {
        for (int i = strikes.size() - 1; i >= 0; i--) {
            if (strikes.get(i).own) {
                return strikes.get(i);
            }
        }
        return null;
    }

    /** 是否存在任意进行中的打击（几何渲染门控）。 */
    public boolean isStrikeActive() {
        return !strikes.isEmpty();
    }

    /** 兼容旧接口：主打击坐标（无主打击时取最近一个）。 */
    public int getStrikeX() {
        ClientStrike s = getPrimaryStrike();
        return s != null ? s.x : (strikes.isEmpty() ? 0 : strikes.get(strikes.size() - 1).x);
    }

    public int getStrikeY() {
        ClientStrike s = getPrimaryStrike();
        return s != null ? s.y : (strikes.isEmpty() ? 0 : strikes.get(strikes.size() - 1).y);
    }

    public int getStrikeZ() {
        ClientStrike s = getPrimaryStrike();
        return s != null ? s.z : (strikes.isEmpty() ? 0 : strikes.get(strikes.size() - 1).z);
    }

    public float getStrikeRadius() {
        ClientStrike s = getPrimaryStrike();
        if (s != null) {
            return s.radius;
        }
        if (!strikes.isEmpty()) {
            return strikes.get(strikes.size() - 1).radius;
        }
        return (float) MainConfig.OrbitalRailgunRadius;
    }

    public float getStrikeSeconds(float partialTicks) {
        ClientStrike s = getPrimaryStrike();
        if (s == null && !strikes.isEmpty()) {
            s = strikes.get(strikes.size() - 1);
        }
        return s == null ? 0 : s.getSeconds(clientTick, partialTicks);
    }

    /** 充能已进行的秒数（供后处理 GUI 时间轴）。 */
    public float getChargeSeconds(float partialTicks) {
        return (chargeTicks + partialTicks) / 20.0F;
    }

    /** 单个打击的客户端状态。 */
    public static final class ClientStrike {
        public final int x;
        public final int y;
        public final int z;
        public final float radius;
        public final long startClientTick;
        /** 是否属于本地玩家（自己发起或同队）。 */
        public final boolean own;
        /** 湮灭粒子是否已触发（每个打击各自一次）。 */
        private boolean explosionParticleFired;

        ClientStrike(int x, int y, int z, float radius, long startClientTick, boolean own) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.startClientTick = startClientTick;
            this.own = own;
        }

        public float getSeconds(long currentClientTick, float partialTicks) {
            return (currentClientTick - startClientTick + partialTicks) / 20.0F;
        }

        public boolean isExplosionParticleFired() {
            return explosionParticleFired;
        }

        public void markExplosionParticleFired() {
            explosionParticleFired = true;
        }
    }

    /** 当前客户端 tick（渲染层计算打击秒数用）。 */
    public long getClientTick() {
        return clientTick;
    }
}
