package com.EyeOfHarmonyBuffer.client.rbmk;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
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
        RbmkHoloPanel.INSTANCE.updateHover(
            RbmkHoloState.hoverX, RbmkHoloState.hoverY, RbmkHoloState.hovering);
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
            handleLeftClick();
        } else if (button == 1) {
            handleRightClick();
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
    }

    private static Entity findPanel(Minecraft mc) {
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof RbmkHoloEntity) {
                return (Entity) o;
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
        Vec3 q = Vec3.createVectorHelper(eye.xCoord + look.xCoord * t, eye.yCoord + look.yCoord * t, eye.zCoord + look.zCoord * t);
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
}