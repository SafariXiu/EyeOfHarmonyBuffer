package com.EyeOfHarmonyBuffer.client.holo;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 全息屏交互框架：只做"世界层面"的事 —— 射线捡屏、算局部坐标、把事件路由给被悬停的屏。
 * 不再有屏类型分派（isPanel/isCoreView 已删）：每块屏自己的逻辑都在各自的 HoloScreen 子类里。
 */
@SideOnly(Side.CLIENT)
public class HoloInteraction {

    /** 上一帧悬停的屏：离开/切换时清掉旧屏的控件 hover，避免残留高亮。 */
    private static HoloScreen lastHovered = null;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            HoloState.hovering = false;
            HoloState.hoveredEntity = null;
            return;
        }
        Entity hovered = pickNearest(mc, mc.thePlayer);
        HoloState.hoveredEntity = hovered;
        HoloScreen now = hovered instanceof HoloEntity h ? h.getScreen() : null;
        // 离开旧屏/切到另一屏：清掉旧屏控件 hover
        if (lastHovered != null && lastHovered != now) {
            lastHovered.onHover(0, 0, false); // 通知旧屏"悬停结束"（清组件 hover + 屏内悬停态）
        }
        lastHovered = now;
        if (now == null) {
            HoloState.hovering = false;
            return;
        }
        now.onHover(HoloState.hoverX, HoloState.hoverY, HoloState.hovering);
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (!Mouse.getEventButtonState()) {
            return;
        }
        int button = Mouse.getEventButton();
        if (!HoloState.hovering || HoloState.hoveredEntity == null) {
            return;
        }
        Entity target = HoloState.hoveredEntity;
        if (!(target instanceof HoloEntity h)) {
            return;
        }
        HoloScreen screen = h.getScreen();
        if (screen == null) {
            return;
        }
        // 点击瞬间用当前准星重新拾取：消除 tick 与点击事件之间的滞后，保证"指哪点哪"
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        double[] uv = new double[2];
        if (!pick(target, mc.thePlayer, uv)) {
            return;
        }
        screen.onMouse(button, (int) Math.round(uv[0]), (int) Math.round(uv[1]));
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        int key = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        if (HoloState.hoveredEntity instanceof HoloEntity h
            && h.getScreen() != null
            && h.getScreen().hasFocus()) {
            // 输入模式：输入框接收字符，同时吞掉该键的所有游戏动作（快速键 + 移动等持续动作）
            h.getScreen().handleKey(c, key);
            eatBindings(key);
        }
    }

    /**
     * 输入模式锁定：吞掉绑定到指定键的所有游戏动作。
     * - isPressed()：吞下沿（开聊天栏、切换视角、投掷等快速键）
     * - setKeyBindState(key,false)：清 held（锁住移动等持续动作，1.7.10 移动读 KeyBinding.pressed）
     */
    private static void eatBindings(int key) {
        if (key == 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        for (KeyBinding kb : mc.gameSettings.keyBindings) {
            if (kb.getKeyCode() == key) {
                kb.isPressed();
            }
        }
        KeyBinding.setKeyBindState(key, false);
    }

    /** 关闭指定的一块全息屏（屏的 onClose 钩子会先执行）。 */
    public static void closeEntity(Entity e) {
        if (e == null || e.isDead) {
            return;
        }
        if (e instanceof HoloEntity h && h.getScreen() != null) {
            h.getScreen().onClose();
        }
        e.setDead();
        // 若这是最后一块屏，复位悬停
        if (HoloState.hoveredEntity == e) {
            HoloState.hoveredEntity = null;
            HoloState.hovering = false;
        }
    }

    /** 对所有全息屏做射线命中，返回最近命中的那块（并写入 hoverX/hoverY/hovering）。 */
    private static Entity pickNearest(Minecraft mc, EntityPlayer player) {
        HoloState.hovering = false;
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        double bestU = 0;
        double bestV = 0;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof HoloEntity)) {
                continue;
            }
            Entity e = (Entity) o;
            double[] uv = new double[2];
            if (pick(e, player, uv)) {
                double d = e.getDistanceSqToEntity(player);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                    bestU = uv[0];
                    bestV = uv[1];
                }
            }
        }
        if (best != null) {
            HoloState.hovering = true;
            HoloState.hoverX = (int) Math.round(bestU);
            HoloState.hoverY = (int) Math.round(bestV);
        }
        return best;
    }

    /** 准星射线与一块屏平面求交，得到局部坐标 (u,v)。命中时 uv[0]/uv[1] 为局部坐标。 */
    private static boolean pick(Entity e, EntityPlayer player, double[] uv) {
        HoloScreen screen = e instanceof HoloEntity h ? h.getScreen() : null;
        if (screen == null) {
            return false;
        }
        double s = 0.0625 * HoloRender.SCALE;
        int w = screen.w;
        int h = screen.h;
        // 交互距离随屏的物理大小缩放：屏半对角线（世界格）+ 玩家可站开的基本距离。
        // 面板半对角约 4.3 格 → 上限 ~8.3（与旧 8.0 接近）；大屏半对角约 9.1 格 → 上限 ~13.1，
        // 保证从能看全整块屏的距离内，下缘也一样能点。
        double halfDiag = 0.5 * Math.sqrt((double) w * w + (double) h * h) * s;
        double maxDist = 4.0 + halfDiag;

        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        HoloMath.Frame f = HoloMath.frameFor(e, player);
        Vec3 c = Vec3.createVectorHelper(e.posX, e.posY, e.posZ);
        Vec3 n = Vec3.createVectorHelper(f.nx, f.ny, f.nz);

        double denom = look.dotProduct(n);
        if (Math.abs(denom) < 1e-6) {
            return false;
        }
        Vec3 cMinusEye = Vec3.createVectorHelper(c.xCoord - eye.xCoord, c.yCoord - eye.yCoord, c.zCoord - eye.zCoord);
        double t = cMinusEye.dotProduct(n) / denom;
        if (t < 0) {
            return false;
        }
        if (t > maxDist) {
            return false;
        }
        Vec3 q = Vec3.createVectorHelper(eye.xCoord + look.xCoord * t, eye.yCoord + look.yCoord * t, eye.zCoord + look.zCoord * t);
        // 视线遮挡检测：玩家到屏路径上有任何方块（含玻璃/栅栏等不完整方块）则不可交互
        if (!hasLineOfSight(player.worldObj, eye, q)) {
            return false;
        }
        Vec3 qc = Vec3.createVectorHelper(q.xCoord - c.xCoord, q.yCoord - c.yCoord, q.zCoord - c.zCoord);
        double u = qc.dotProduct(Vec3.createVectorHelper(f.rx, f.ry, f.rz)) / s + w / 2.0;
        double v = h / 2.0 - qc.dotProduct(Vec3.createVectorHelper(f.ux, f.uy, f.uz)) / s;

        // 放宽命中边界，避免触发区域比视觉面板小（边缘点不到）
        if (u < -16 || u > w + 16 || v < -16 || v > h + 16) {
            return false;
        }
        uv[0] = u;
        uv[1] = v;
        return true;
    }

    /**
     * 视线遮挡检测：从 eye 到 target 逐点遍历方块，路径上有任何非空气方块
     * （含玻璃/栅栏等不完整方块）即判定遮挡。跳过起点（玩家所在）与终点（屏所在）方块。
     */
    private static boolean hasLineOfSight(net.minecraft.world.World world, Vec3 eye, Vec3 target) {
        int startX = MathHelper.floor_double(eye.xCoord);
        int startY = MathHelper.floor_double(eye.yCoord);
        int startZ = MathHelper.floor_double(eye.zCoord);
        int endX = MathHelper.floor_double(target.xCoord);
        int endY = MathHelper.floor_double(target.yCoord);
        int endZ = MathHelper.floor_double(target.zCoord);

        double dx = target.xCoord - eye.xCoord;
        double dy = target.yCoord - eye.yCoord;
        double dz = target.zCoord - eye.zCoord;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1e-4) {
            return true;
        }
        // 每格采样 2 点，避免错过薄方块
        int steps = (int) Math.ceil(dist * 2.0);
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int bx = MathHelper.floor_double(eye.xCoord + dx * t);
            int by = MathHelper.floor_double(eye.yCoord + dy * t);
            int bz = MathHelper.floor_double(eye.zCoord + dz * t);
            if (bx == startX && by == startY && bz == startZ) {
                continue;
            }
            if (bx == endX && by == endY && bz == endZ) {
                continue;
            }
            if (!world.isAirBlock(bx, by, bz)) {
                return false;
            }
        }
        return true;
    }
}
