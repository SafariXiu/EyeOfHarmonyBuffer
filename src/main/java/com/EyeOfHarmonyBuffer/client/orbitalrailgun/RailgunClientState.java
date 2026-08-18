package com.EyeOfHarmonyBuffer.client.orbitalrailgun;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.common.item.itemadders.ItemOrbitalRailgun;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

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

    // ---- 打击状态 ----
    private boolean strikeActive;
    private long strikeStartClientTick;
    private int strikeX;
    private int strikeY;
    private int strikeZ;
    private float strikeRadius;
    private boolean explosionParticleFired;



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

        if (strikeActive) {
            if (mc.theWorld == null || clientTick - strikeStartClientTick >= STRIKE_MAX_TICKS) {
                strikeActive = false;
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

    // ---- 打击 ----

    public void onStrikeStarted(int x, int y, int z, float radius) {
        this.strikeActive = true;
        this.strikeStartClientTick = clientTick;
        this.strikeX = x;
        this.strikeY = y;
        this.strikeZ = z;
        this.strikeRadius = radius;
        this.explosionParticleFired = false;
    }

    public boolean isStrikeActive() {
        return strikeActive;
    }

    public int getStrikeX() {
        return strikeX;
    }

    public int getStrikeY() {
        return strikeY;
    }

    public int getStrikeZ() {
        return strikeZ;
    }

    public float getStrikeRadius() {
        return strikeRadius > 0 ? strikeRadius : (float) MainConfig.OrbitalRailgunRadius;
    }

    public float getStrikeSeconds(float partialTicks) {
        return (clientTick - strikeStartClientTick + partialTicks) / 20.0F;
    }

    public boolean isExplosionParticleFired() {
        return explosionParticleFired;
    }

    public void markExplosionParticleFired() {
        explosionParticleFired = true;
    }
}
