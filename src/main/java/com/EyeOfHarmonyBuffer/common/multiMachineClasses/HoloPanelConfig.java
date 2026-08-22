package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.client.holo.HoloMath;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 机载全息屏配置（每个结构字母一套，写在 getStructureDefinition 的
 * {@code .addElement('X', holoPanelCasing(config))} 里）。
 *
 * <p>全部参数相对机器朝向（随机器旋转/翻转自动正确）：
 * <ul>
 *   <li>位置(3，各带格 + 像素两级)：forward（前+/后-）、left（左+/右-）、up（上+/下-），单位格；
 *       像素级微调用于错开与方块面完全重合的 z-fighting（1 像素 = 1/64 格）</li>
 *   <li>旋转(3)：yaw（绕上下轴）、pitch（绕左右轴）、roll（绕前后轴），单位度</li>
 *   <li>镜像(1)：flip（左右镜像）</li>
 * </ul>
 * 正角度 = 沿轴正方向看过去逆时针（右旋法则）；游戏里可微调正负。
 */
public class HoloPanelConfig {

    /** 1 屏像素对应的世界格数：0.0625 * HoloRender.SCALE(0.25) = 1/64 格（与 HoloRender 的缩放一致）。 */
    private static final double PIXEL_BLOCKS = 0.0625D * 0.25D;

    /** 往前(+)/往后(-)，格。 */
    public final double forward;
    /** 在前/后方向上的像素级微调（正=往前更多，负=往后更多；1 像素 = 1/64 格）。 */
    public final double forwardPx;
    /** 往左(+)/往右(-)，格。 */
    public final double left;
    /** 在左/右方向上的像素级微调。 */
    public final double leftPx;
    /** 往上(+)/往下(-)，格。 */
    public final double up;
    /** 在上/下方向上的像素级微调。 */
    public final double upPx;
    /** 绕机器上下轴旋转角度（度）。 */
    public final double yaw;
    /** 绕机器左右轴旋转角度（度）。 */
    public final double pitch;
    /** 绕机器前后轴旋转角度（度）。 */
    public final double roll;
    /** 是否左右镜像。 */
    public final boolean flip;
    /** 屏类型 id（客户端 renderTESR 按此创建/分派不同屏；null 回退默认总面板）。 */
    public final String screenId;

    private HoloPanelConfig(double forward, double forwardPx,
                            double left, double leftPx,
                            double up, double upPx,
                            double yaw, double pitch, double roll, boolean flip, String screenId) {
        this.forward = forward;
        this.forwardPx = forwardPx;
        this.left = left;
        this.leftPx = leftPx;
        this.up = up;
        this.upPx = upPx;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.flip = flip;
        this.screenId = screenId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double forward, forwardPx, left, leftPx, up, upPx, yaw, pitch, roll;
        private boolean flip;
        private String screenId;

        /** 往前(+)/往后(-) N 格。 */
        public Builder forward(double v) { this.forward = v; return this; }

        /** 往前(+)/往后(-) N 格 + 再往该方向微调 px 像素（1 像素 = 1/64 格）。 */
        public Builder forward(double v, double px) { this.forward = v; this.forwardPx = px; return this; }

        /** 往左(+)/往右(-) N 格。 */
        public Builder left(double v) { this.left = v; return this; }

        /** 往左(+)/往右(-) N 格 + 再往该方向微调 px 像素。 */
        public Builder left(double v, double px) { this.left = v; this.leftPx = px; return this; }

        /** 往上(+)/往下(-) N 格。 */
        public Builder up(double v) { this.up = v; return this; }

        /** 往上(+)/往下(-) N 格 + 再往该方向微调 px 像素。 */
        public Builder up(double v, double px) { this.up = v; this.upPx = px; return this; }

        /** 绕机器上下轴旋转（度）。 */
        public Builder yaw(double v) { this.yaw = v; return this; }

        /** 绕机器左右轴旋转（度）。 */
        public Builder pitch(double v) { this.pitch = v; return this; }

        /** 绕机器前后轴旋转（度）。 */
        public Builder roll(double v) { this.roll = v; return this; }

        /** 是否左右镜像。 */
        public Builder flip(boolean v) { this.flip = v; return this; }

        /** 屏类型 id（客户端按此创建/分派屏；null = 默认总面板）。 */
        public Builder screen(String v) { this.screenId = v; return this; }

        public HoloPanelConfig build() {
            return new HoloPanelConfig(forward, forwardPx, left, leftPx, up, upPx, yaw, pitch, roll, flip, screenId);
        }
    }

    /**
     * 计算该配置在机器当前朝向下、锚点为 (bx,by,bz) 时的最终面板实例。
     *
     * @param ef 机器 ExtendedFacing（含方向/旋转/翻转），为 null 时按零配置处理（不推动、朝向世界前方）
     */
    public HoloPanelInstance compute(ExtendedFacing ef, int bx, int by, int bz) {
        ForgeDirection fwd;
        ForgeDirection lft;
        ForgeDirection upw;
        if (ef != null) {
            fwd = ef.getRelativeForwardInWorld();
            lft = ef.getRelativeLeftInWorld();
            upw = ef.getRelativeUpInWorld();
        } else {
            fwd = ForgeDirection.NORTH;
            lft = ForgeDirection.WEST;
            upw = ForgeDirection.UP;
        }
        // 位置：锚点方块中心 + 三轴推动（格 + 像素级微调，各转世界方向）
        double fwdAmt = forward + forwardPx * PIXEL_BLOCKS;
        double lftAmt = left + leftPx * PIXEL_BLOCKS;
        double upAmt = up + upPx * PIXEL_BLOCKS;
        double cx = bx + 0.5 + fwd.offsetX * fwdAmt + lft.offsetX * lftAmt + upw.offsetX * upAmt;
        double cy = by + 0.5 + fwd.offsetY * fwdAmt + lft.offsetY * lftAmt + upw.offsetY * upAmt;
        double cz = bz + 0.5 + fwd.offsetZ * fwdAmt + lft.offsetZ * lftAmt + upw.offsetZ * upAmt;
        // 基础朝向：法向 = 机器前方，up = 世界上方（与旧 renderTESR 完全一致）
        HoloMath.Frame f = HoloMath.frameForDirection(fwd.offsetX, 0f, fwd.offsetZ);
        // 旋转：依次绕 上下轴(yaw) → 左右轴(pitch) → 前后轴(roll)
        if (yaw != 0) {
            rotateFrame(f, upw, yaw);
        }
        if (pitch != 0) {
            rotateFrame(f, lft, pitch);
        }
        if (roll != 0) {
            rotateFrame(f, fwd, roll);
        }
        // 镜像：right 取反（左右镜像）
        if (flip) {
            f.rx = -f.rx;
            f.ry = -f.ry;
            f.rz = -f.rz;
        }
        return new HoloPanelInstance(cx, cy, cz, f.rx, f.ry, f.rz, f.ux, f.uy, f.uz, f.nx, f.ny, f.nz, screenId);
    }

    /** Rodrigues 旋转：把 frame 的三个基向量绕轴 (kx,ky,kz) 旋转 rad 弧度。 */
    private static void rotateFrame(HoloMath.Frame f, ForgeDirection axis, double deg) {
        double rad = Math.toRadians(deg);
        double kx = axis.offsetX, ky = axis.offsetY, kz = axis.offsetZ;
        double c = Math.cos(rad), s = Math.sin(rad), t = 1 - c;
        double[] rx = { f.rx, f.ux, f.nx };
        double[] ry = { f.ry, f.uy, f.ny };
        double[] rz = { f.rz, f.uz, f.nz };
        for (int i = 0; i < 3; i++) {
            double vx = rx[i], vy = ry[i], vz = rz[i];
            double dot = kx * vx + ky * vy + kz * vz;
            double cxvx = ky * vz - kz * vy;
            double cxvy = kz * vx - kx * vz;
            double cxvz = kx * vy - ky * vx;
            rx[i] = vx * c + cxvx * s + kx * dot * t;
            ry[i] = vy * c + cxvy * s + ky * dot * t;
            rz[i] = vz * c + cxvz * s + kz * dot * t;
        }
        f.rx = (float) rx[0]; f.ry = (float) ry[0]; f.rz = (float) rz[0];
        f.ux = (float) rx[1]; f.uy = (float) ry[1]; f.uz = (float) rz[1];
        f.nx = (float) rx[2]; f.ny = (float) ry[2]; f.nz = (float) rz[2];
    }
}
