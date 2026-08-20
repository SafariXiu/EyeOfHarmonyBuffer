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
 * 全息面板交互。每块屏都是平级的根屏（控制面板 viewType 0 / 堆芯大屏 viewType 1），
 * 各自拥有独立的交互逻辑，互相不是父子关系。
 * - ClientTickEvent：每 tick 用准星射线命中所有 RbmkHoloEntity，取最近的那块，记录悬停局部坐标
 * - MouseInputEvent：按命中屏幕的 viewType 分派到各屏自己的处理器
 * - KeyInputEvent：按键路由给聚焦控件（输入框）
 */
@SideOnly(Side.CLIENT)
public class RbmkHoloInteraction {

    /** 最大交互距离（方块）：超出则不准星命中 / 不可点击，避免隔着几十格远程操作。 */
    public static final double MAX_INTERACT_DIST = 8.0;

    private static boolean isPanel(Entity e) {
        return e instanceof RbmkHoloEntity r && r.viewType == 0;
    }

    private static boolean isCoreView(Entity e) {
        return e instanceof RbmkHoloEntity r && r.viewType == 1;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            RbmkHoloState.hovering = false;
            RbmkHoloState.hoveredEntity = null;
            return;
        }
        Entity hovered = pickNearest(mc, mc.thePlayer);
        RbmkHoloState.hoveredEntity = hovered;
        if (hovered == null) {
            RbmkHoloState.hovering = false;
            RbmkHoloPanel.INSTANCE.updateHover(0, 0, false);
            return;
        }
        if (isPanel(hovered)) {
            if (RbmkHoloState.activated) {
                RbmkHoloPanel.INSTANCE.updateHover(
                    RbmkHoloState.hoverX, RbmkHoloState.hoverY, RbmkHoloState.hovering);
            } else {
                // 未激活：不显示控件 hover（防误触），但仍记录悬停坐标用于检测"左键激活"
                RbmkHoloPanel.INSTANCE.updateHover(0, 0, false);
            }
        } else {
            // 堆芯大屏：纯展示，不参与控件 hover
            RbmkHoloPanel.INSTANCE.updateHover(0, 0, false);
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (!Mouse.getEventButtonState()) {
            return;
        }
        int button = Mouse.getEventButton();
        if (!RbmkHoloState.hovering || RbmkHoloState.hoveredEntity == null) {
            return;
        }
        Entity target = RbmkHoloState.hoveredEntity;
        // 每块屏都是同级别的根屏：按 viewType 分派到各自独立的交互逻辑
        if (isPanel(target)) {
            handlePanelMouse(button);
        } else if (isCoreView(target)) {
            handleCoreMouse(button);
        }
    }

    /** 控制面板（viewType 0）的鼠标逻辑：左键激活/点控件；右键 取消输入→弹回上层→关闭。 */
    private void handlePanelMouse(int button) {
        if (button == 1) {
            if (RbmkHoloState.activated) {
                handleRightClick();
            }
        } else if (button == 0) {
            if (!RbmkHoloState.activated) {
                RbmkHoloState.activated = true;
            } else {
                handleLeftClick();
            }
        }
    }

    /** 堆芯大屏（viewType 1）的鼠标逻辑：目前仅右键关闭；后续在此扩展大屏自己的操作。 */
    private void handleCoreMouse(int button) {
        if (button == 1) {
            closeEntity(RbmkHoloState.hoveredEntity);
        }
        // 左键暂不响应，留给后续堆芯大屏自己的操作逻辑
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

    /** 右键（控制面板）：有焦点 → 取消；有上层 → 弹回；否则关闭面板。 */
    private static void handleRightClick() {
        if (RbmkHoloPanel.INSTANCE.hasFocus()) {
            RbmkHoloPanel.INSTANCE.clearFocus();
            return;
        }
        if (RbmkHoloPanel.INSTANCE.goBack()) {
            return;
        }
        closeEntity(RbmkHoloState.hoveredEntity);
    }

    /** 关闭指定的一块全息屏（不再关闭全部）。 */
    private static void closeEntity(Entity e) {
        if (e == null || e.isDead) {
            return;
        }
        e.setDead();
        // 若关的是控制面板，清掉面板状态
        if (isPanel(e)) {
            RbmkHoloPanel.INSTANCE.clearFocus();
            RbmkHoloState.activated = false;
        }
        // 若这是最后一块屏，复位悬停
        if (RbmkHoloState.hoveredEntity == e) {
            RbmkHoloState.hoveredEntity = null;
            RbmkHoloState.hovering = false;
        }
    }

    /** 对所有全息屏做射线命中，返回最近命中的那块（并写入 hoverX/hoverY/hovering）。 */
    private static Entity pickNearest(Minecraft mc, EntityPlayer player) {
        RbmkHoloState.hovering = false;
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        double bestU = 0;
        double bestV = 0;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof RbmkHoloEntity)) {
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
            RbmkHoloState.hovering = true;
            RbmkHoloState.hoverX = (int) bestU;
            RbmkHoloState.hoverY = (int) bestV;
        }
        return best;
    }

    /** 准星射线与一块屏平面求交，得到局部坐标 (u,v)。命中时 uv[0]/uv[1] 为局部坐标。 */
    private static boolean pick(Entity e, EntityPlayer player, double[] uv) {
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        RbmkHoloMath.Frame f = RbmkHoloMath.frameFor(e, player);
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
        // 交互距离限制：太远不可交互
        if (t > MAX_INTERACT_DIST) {
            return false;
        }
        Vec3 q = Vec3.createVectorHelper(eye.xCoord + look.xCoord * t, eye.yCoord + look.yCoord * t, eye.zCoord + look.zCoord * t);
        // 视线遮挡检测：玩家到面板路径上有任何方块（含玻璃/栅栏等不完整方块）则不可交互
        if (!hasLineOfSight(player.worldObj, eye, q)) {
            return false;
        }
        double s = 0.0625 * RbmkHoloRender.SCALE;
        // 按 viewType 使用对应屏幕尺寸做命中判定与居中偏移
        int w = isPanel(e) ? RbmkHoloRender.W : RbmkHoloRender.CORE_W;
        int h = isPanel(e) ? RbmkHoloRender.H : RbmkHoloRender.CORE_H;
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
