package com.EyeOfHarmonyBuffer.command;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.AirMassType;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * /talosmap - V2 海陆/环流/气团/洋流出图（docs/TerrainV2/design.md 十：测试方案）。
 *
 * 用法：/talosmap &lt;layer&gt; [cx cz radius] [stride]
 *   layer:
 *     land       海陆（V2 噪声场 NoiseContinentGrid——新 L1）
 *     landlegacy 海陆（旧生产 TectonicWorld，X1 阶段2 前与 land 对照用）
 *     coast      海岸距离带（块级有符号距离；近岸 ~30k 内渐变色）
 *     gyre       洋流占位层：底色=纬度冷暖占位(gyreWarmth)，箭头=盛行风（S6.1 前近似）
 *     airmass    气团类型（cP/mP/mT/cT 四色）
 *     current    洋流：海温底色 + 海上洋流箭头（矢量）
 *     wind       盛行风（矢量：底色=风向色相，箭头=方向）
 *     pressure   气压干湿
 *     rain       潜在降水
 *     band       纬度带
 *   cx cz radius: 区域中心与半径（缺省 = 玩家所在位置，radius 默认 100000）
 *   stride: 采样步长（缺省自动按 radius 使图 ≤ 1024px）
 *
 * 所有图层统一采样 GlobalClimate.sample（唯一口径）；风/洋流为矢量场叠加箭头。
 * 输出：run/talos_maps/&lt;layer&gt;.png
 */
public class CommandTalosMap extends CommandBase {

    private static final int MAX_PX = 1024;
    /** 箭头间隔（px）。 */
    private static final int ARROW_SPACING = 100;
    /** 箭头最大长度（px）。 */
    private static final int ARROW_MAX_LEN = 34;
    /** coast 图层近岸过渡半宽（block）。 */
    private static final double COAST_BAND = 30_000.0;

    @Override
    public String getCommandName() {
        return "talosmap";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/talosmap <land|landlegacy|coast|gyre|airmass|current|wind|pressure|rain|band> [cx cz radius] [stride]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("必须由玩家在服务端执行。"));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentText("用法: " + getCommandUsage(sender)));
            return;
        }
        String layer = args[0].toLowerCase();
        World world = player.worldObj;
        int worldSeedInt = TalosLandMask.getWorldSeedInt(world);

        int cx = (int) Math.floor(player.posX);
        int cz = (int) Math.floor(player.posZ);
        int radius = 100_000;
        if (args.length >= 4) {
            try {
                cx = Integer.parseInt(args[1]);
                cz = Integer.parseInt(args[2]);
                radius = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.addChatMessage(new ChatComponentText("坐标/半径无效。"));
                return;
            }
        }
        int stride = Math.max(1, radius * 2 / MAX_PX);
        if (args.length >= 5) {
            try {
                stride = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                sender.addChatMessage(new ChatComponentText("stride 无效。"));
                return;
            }
        }
        if (radius < 100 || radius > 500_000) {
            sender.addChatMessage(new ChatComponentText("radius 需在 100 ~ 500000 之间。"));
            return;
        }

        long t0 = System.nanoTime();
        BufferedImage img = render(layer, cx, cz, radius, stride, worldSeedInt);
        if (img == null) {
            sender.addChatMessage(new ChatComponentText("未知图层: " + layer));
            return;
        }

        try {
            File dir = new File("talos_maps");
            if (!dir.exists()) dir.mkdirs();
            String name = layer + "_c" + cx + "_" + cz + "_r" + radius + ".png";
            File out = new File(dir, name);
            ImageIO.write(img, "png", out);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            sender.addChatMessage(new ChatComponentText(
                "[talosmap] " + layer + " -> " + out.getCanonicalPath()
                    + "  (" + img.getWidth() + "x" + img.getHeight() + ", " + ms + "ms)"
            ));
        } catch (Exception ex) {
            sender.addChatMessage(new ChatComponentText(
                "[talosmap] 写文件失败: " + ex.getMessage()
            ));
        }
    }

    private BufferedImage render(String layer, int cx, int cz, int radius,
                                 int stride, int seed) {
        int w = Math.min(MAX_PX, radius * 2 / stride);
        int h = w;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        int x0 = cx - radius;
        int z0 = cz - radius;
        boolean vectorArrows =
            layer.equals("wind") || layer.equals("gyre") || layer.equals("current");

        for (int py = 0; py < h; py++) {
            int wz = z0 + py * stride;
            for (int px = 0; px < w; px++) {
                int wx = x0 + px * stride;
                img.setRGB(px, py, colorFor(layer, wx, wz, seed));
            }
        }

        if (vectorArrows) {
            drawArrows(g, layer, w, h, x0, z0, stride, seed);
        }
        g.dispose();
        return img;
    }

    /** 矢量箭头叠加：wind/gyre = 盛行风箭头；current = 海上洋流箭头。 */
    private void drawArrows(Graphics2D g, String layer, int w, int h,
                            int x0, int z0, int stride, int seed) {
        g.setStroke(new BasicStroke(2.0f));
        for (int py = ARROW_SPACING / 2; py < h; py += ARROW_SPACING) {
            for (int px = ARROW_SPACING / 2; px < w; px += ARROW_SPACING) {
                int wx = x0 + px * stride;
                int wz = z0 + py * stride;
                ClimateSample s = GlobalClimate.sample(wx, wz, seed);
                double fx, fz, mag;
                if (layer.equals("current")) {
                    if (s.isLand) continue;          // 洋流只画海上
                    fx = s.currentX;
                    fz = s.currentZ;
                    mag = s.currentSpeed;
                } else {
                    fx = s.windX;
                    fz = s.windZ;
                    double len = Math.sqrt(fx * fx + fz * fz);
                    mag = Math.min(1.0, len);
                    if (len > 1.0e-9) {
                        fx /= len;
                        fz /= len;
                    }
                }
                if (mag < 1.0e-3) continue;
                int arrowLen = Math.max(6, (int) (ARROW_MAX_LEN * Math.min(1.0, mag)));
                int ex = px + (int) (fx * arrowLen);
                int ey = py + (int) (fz * arrowLen);
                g.setColor(layer.equals("gyre") ? Color.WHITE : Color.BLACK);
                g.drawLine(px, py, ex, ey);
                drawArrowHead(g, px, py, ex, ey);
            }
        }
    }

    private void drawArrowHead(Graphics2D g, int sx, int sy, int ex, int ey) {
        double ang = Math.atan2(ey - sy, ex - sx);
        int headLen = 8;
        int hx1 = ex - (int) (Math.cos(ang - Math.PI / 6) * headLen);
        int hy1 = ey - (int) (Math.sin(ang - Math.PI / 6) * headLen);
        int hx2 = ex - (int) (Math.cos(ang + Math.PI / 6) * headLen);
        int hy2 = ey - (int) (Math.sin(ang + Math.PI / 6) * headLen);
        g.drawLine(ex, ey, hx1, hy1);
        g.drawLine(ex, ey, hx2, hy2);
    }

    private int colorFor(String layer, int wx, int wz, int seed) {
        ClimateSample s = GlobalClimate.sample(wx, wz, seed);
        switch (layer) {
            case "land": {
                return s.isLand ? rgb(60, 150, 70) : rgb(20, 70, 140);
            }
            case "landlegacy": {
                boolean land = TalosLandMask.isLandCheap(wx, wz, seed);
                return land ? rgb(150, 110, 40) : rgb(40, 60, 120);   // 旧系统配色区分
            }
            case "coast": {
                // 海陆底 + 近岸 ±COAST_BAND 内混入白/青渐变（带宽 = 真实块距离）
                double d = s.coastDist;
                double t = clamp(1.0 - Math.abs(d) / COAST_BAND, 0, 1);  // 1=岸线
                if (s.isLand) {
                    return mix(rgb(60, 150, 70), rgb(150, 160, 90), t);
                }
                int deep = rgb(20, 70, 140);
                return mix(deep, rgb(60, 130, 160), t);
            }
            case "gyre": {
                // 占位层：纬度冷暖底色（S6.1 前不代表真实环流温湿）
                double g = clamp(s.gyreWarmth, -1, 1);
                return g >= 0 ? heatColor(g) : coldColor(-g);
            }
            case "airmass": {
                switch (s.airType) {
                    case MARITIME_TROPICAL:   return rgb(60, 120, 200);   // mT 热带海洋 蓝
                    case MARITIME_POLAR:      return rgb(90, 180, 220);   // mP 极地海洋 浅蓝
                    case CONTINENTAL_TROPICAL:return rgb(220, 140, 40);   // cT 热带大陆 橙
                    default:                   return rgb(180, 190, 200); // cP 极地大陆 灰
                }
            }
            case "current": {
                if (s.isLand) {
                    return rgb(60, 150, 70);   // 陆地绿
                }
                double t = clamp(s.seaTemperature, -1, 1);
                return t >= 0 ? heatColor(t) : coldColor(-t);   // 海温（纬度基准）
            }
            case "wind": {
                double a = Math.atan2(s.windZ, s.windX);
                double hue = Math.toDegrees(a) + 180;
                return Color.HSBtoRGB((float) (hue / 360.0), 0.7f, 1.0f);
            }
            case "pressure": {
                double d = clamp(s.pressureDry, 0, 1);
                int r = (int) (200 * d + 40 * (1 - d));
                int g = (int) (180 * (1 - d) + 120 * d);
                int b = (int) (220 * (1 - d) + 30 * d);
                return rgb(r, g, b);
            }
            case "rain": {
                double r = clamp(s.rainfallBase, 0, 1);
                int gr = (int) (255 * r);
                int bl = (int) (200 * r);
                int rr = (int) (80 * (1 - r) + 40 * r);
                return rgb(rr, gr, bl);
            }
            default: { // band
                double b = clamp(s.bandD, 0, 1);
                if (b < 0.5) {
                    return lerpColor(new int[]{230, 60, 40}, new int[]{60, 180, 60}, b * 2);
                }
                return lerpColor(new int[]{60, 180, 60}, new int[]{220, 220, 255}, (b - 0.5) * 2);
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int c1, int c2, double t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return rgb((int) (r1 + (r2 - r1) * t), (int) (g1 + (g2 - g1) * t), (int) (b1 + (b2 - b1) * t));
    }

    private static int heatColor(double t) {
        int r = 255;
        int g = (int) (200 * (1 - t));
        int b = (int) (40 * (1 - t));
        return rgb(r, g, b);
    }

    private static int coldColor(double t) {
        int r = (int) (60 * (1 - t));
        int g = (int) (140 * (1 - t));
        int b = 200 + (int) (55 * t);
        return rgb(r, g, b);
    }

    private static int lerpColor(int[] c0, int[] c1, double t) {
        int r = (int) (c0[0] + (c1[0] - c0[0]) * t);
        int g = (int) (c0[1] + (c1[1] - c0[1]) * t);
        int b = (int) (c0[2] + (c1[2] - c0[2]) * t);
        return rgb(r, g, b);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                "land", "landlegacy", "coast", "gyre", "airmass", "current", "wind",
                "pressure", "rain", "band");
        }
        return new ArrayList<>();
    }
}
