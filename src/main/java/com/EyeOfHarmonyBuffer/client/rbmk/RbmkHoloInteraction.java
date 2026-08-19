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
import org.lwjgl.input.Mouse;

/**
 * 全息面板交互：
 * - ClientTickEvent：每 tick 更新准星悬停（射线 → 面板局部坐标）与控件 hover 状态
 * - InputEvent.MouseInputEvent：左键按下沿 → 命中控件（按 z 上层优先）
 * （不依赖 Mouse.isButtonDown 边沿检测，避免 lwjgl3ify 下鼠标状态不可靠。）
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
        Entity panel = null;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof RbmkHoloEntity) {
                panel = (Entity) o;
                break;
            }
        }
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
        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
            if (RbmkHoloState.hovering) {
                handleClick();
            }
        }
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

    /** 点击：命中 z 上层优先的控件。 */
    private static void handleClick() {
        RbmkHoloControl c = RbmkHoloPanel.INSTANCE.hitAt(RbmkHoloState.hoverX, RbmkHoloState.hoverY);
        if (c != null) {
            c.onClick();
        }
    }
}
