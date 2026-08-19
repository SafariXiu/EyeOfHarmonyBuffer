package com.EyeOfHarmonyBuffer.client.rbmk;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.settings.KeyBinding;

/**
 * 全息面板交互：
 * - ClientTickEvent：每 tick 更新准星悬停（射线 → 面板局部坐标）与控件 hover 状态
 * - MouseInputEvent：左键点击控件（聚焦输入框）；右键取消焦点 / 弹回上层 / 关闭面板
 * - KeyInputEvent：按键路由给聚焦控件（输入框）
 */
@SideOnly(Side.CLIENT)
public class RbmkHoloInteraction {

    /** 最大交互距离（方块）：超出则不准星命中 / 不可点击，避免隔着几十格远程操作。 */
    public static final double MAX_INTERACT_DIST = 8.0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            RbmkHoloState.hovering = false;
            return;
        }
        Entity panel = findPanel(mc);
        if (panel == null) {
            RbmkHoloState.hovering = false;
            return;
        }
        pick(panel, mc.thePlayer);
        if (RbmkHoloState.activated) {
            RbmkHoloPanel.INSTANCE.updateHover(
                RbmkHoloState.hoverX, RbmkHoloState.hoverY, RbmkHoloState.hovering);
        } else {
            // 未激活：不显示控件 hover（防误触），但仍记录悬停坐标用于检测"左键激活"
            RbmkHoloPanel.INSTANCE.updateHover(0, 0, false);
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (!Mouse.getEventButtonState()) {
            return;
        }
        int button = Mouse.getEventButton();
        if (!RbmkHoloState.hovering) {
            return;
        }
        if (button == 0) {
            if (!RbmkHoloState.activated) {
                // 左键点击面板任一位置 → 激活（之后才响应控件）
                RbmkHoloState.activated = true;
            } else {
                handleLeftClick();
            }
        } else if (button == 1) {
            // 右键：仅激活时承担"退出父面板"（取消输入 → 弹回上层 → 关闭面板）
            if (RbmkHoloState.activated) {
                handleRightClick();
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        int key = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        if (RbmkHoloPanel.INSTANCE.hasFocus()) {
            // 输入模式：输入框接收字符，同时吞掉该键的所有游戏动作（快速键 + 移动等持续动作）
            RbmkHoloPanel.INSTANCE.handleKey(c, key);
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
        for (net.minecraft.client.settings.KeyBinding kb : mc.gameSettings.keyBindings) {
            if (kb.getKeyCode() == key) {
                kb.isPressed();
            }
        }
        KeyBinding.setKeyBindState(key, false);
    }

    private static void handleLeftClick() {
        RbmkHoloControl c = RbmkHoloPanel.INSTANCE.hitAt(RbmkHoloState.hoverX, RbmkHoloState.hoverY);
        // 点击任何位置都先更新焦点：点输入框 → 聚焦；点别处 → 失焦
        RbmkHoloPanel.INSTANCE.requestFocus(c);
        if (c != null) {
            c.onClick();
        }
    }

    /** 右键：有焦点 → 取消；有上层 → 弹回；否则关闭面板。 */
    private static void handleRightClick() {
        if (RbmkHoloPanel.INSTANCE.hasFocus()) {
            RbmkHoloPanel.INSTANCE.clearFocus();
            return;
        }
        if (RbmkHoloPanel.INSTANCE.goBack()) {
            return;
        }
        closePanel();
    }

    private static void closePanel() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof RbmkHoloEntity) {
                ((RbmkHoloEntity) o).setDead();
            }
        }
        RbmkHoloPanel.INSTANCE.clearFocus();
        RbmkHoloState.hovering = false;
        RbmkHoloState.activated = false;
    }

    private static Entity findPanel(Minecraft mc) {
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof RbmkHoloEntity r && r.viewType == 0) {
                return r;
            }
        }
        return null;
    }

    /** 准星射线与面板平面求交，得到局部坐标 (u,v)。返回是否命中面板。 */
    private static boolean pick(Entity e, EntityPlayer player) {
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        RbmkHoloMath.Frame f = RbmkHoloMath.frameFor(e, player);
        Vec3 c = Vec3.createVectorHelper(e.posX, e.posY, e.posZ);
        Vec3 n = Vec3.createVectorHelper(f.nx, f.ny, f.nz);

        double denom = look.dotProduct(n);
        if (Math.abs(denom) < 1e-6) {
            RbmkHoloState.hovering = false;
            return false;
        }
        Vec3 cMinusEye = Vec3.createVectorHelper(c.xCoord - eye.xCoord, c.yCoord - eye.yCoord, c.zCoord - eye.zCoord);
        double t = cMinusEye.dotProduct(n) / denom;
        if (t < 0) {
            RbmkHoloState.hovering = false;
            return false;
        }
        // 交互距离限制：太远不可交互
        if (t > MAX_INTERACT_DIST) {
            RbmkHoloState.hovering = false;
            return false;
        }
        Vec3 q = Vec3.createVectorHelper(eye.xCoord + look.xCoord * t, eye.yCoord + look.yCoord * t, eye.zCoord + look.zCoord * t);
        // 视线遮挡检测：玩家到面板路径上有任何方块（含玻璃/栅栏等不完整方块）则不可交互
        if (!hasLineOfSight(player.worldObj, eye, q)) {
            RbmkHoloState.hovering = false;
            return false;
        }
        double s = 0.0625 * RbmkHoloRender.SCALE;
        Vec3 qc = Vec3.createVectorHelper(q.xCoord - c.xCoord, q.yCoord - c.yCoord, q.zCoord - c.zCoord);
        double u = qc.dotProduct(Vec3.createVectorHelper(f.rx, f.ry, f.rz)) / s + RbmkHoloRender.W / 2.0;
        double v = RbmkHoloRender.H / 2.0 - qc.dotProduct(Vec3.createVectorHelper(f.ux, f.uy, f.uz)) / s;

        // 放宽命中边界，避免触发区域比视觉面板小（边缘点不到）
        RbmkHoloState.hovering = u >= -16 && u <= RbmkHoloRender.W + 16
            && v >= -16 && v <= RbmkHoloRender.H + 16;
        RbmkHoloState.hoverX = (int) u;
        RbmkHoloState.hoverY = (int) v;
        return RbmkHoloState.hovering;
    }

    /**
     * 视线遮挡检测：从 eye 到 target 逐点遍历方块，路径上有任何非空气方块
     * （含玻璃/栅栏等不完整方块）即判定遮挡。跳过起点（玩家所在）与终点（面板所在）方块。
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